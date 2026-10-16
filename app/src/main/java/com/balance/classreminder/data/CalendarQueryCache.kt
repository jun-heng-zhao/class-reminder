// 系统日历的有界缓存：单调时钟、同范围请求合并与失败节流。
package com.balance.classreminder.data

import java.util.concurrent.FutureTask

internal class CalendarQueryCache<K, V>(
    private val ttlMillis: Long = 15 * 60_000L,
    private val refreshIntervalMillis: Long = 30_000L,
    private val maxRanges: Int = 4,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class Entry<V>(val result: Result<V>, val started: Long, val completed: Long)
    private val lock = Any() // 只保护缓存状态，不在锁内执行数据源查询
    private val entries = LinkedHashMap<K, Entry<V>>(maxRanges, 0.75f, true)
    private val inFlight = mutableMapOf<K, FutureTask<Result<V>>>() // 正在执行的请求不可被 LRU 淘汰

    init {
        require(ttlMillis > 0 && refreshIntervalMillis >= 0 && maxRanges > 0)
    }

    fun get(key: K, refresh: Boolean = false, load: () -> V): V {
        val task: FutureTask<Result<V>> // 所有等待者共享同一个结果或原始异常
        synchronized(lock) {
            val now = clockMillis()
            val running = inFlight[key]
            val cached = entries[key]
            if (running != null) {
                task = running
            } else {
                if (cached != null) {
                    val throttled = now - cached.started < refreshIntervalMillis
                    val fresh = cached.result.isSuccess && now - cached.completed < ttlMillis
                    if (throttled || (!refresh && fresh)) return cached.result.getOrThrow()
                }
                task = FutureTask {
                    val result = runCatching(load)
                    synchronized(lock) {
                        entries[key] = Entry(result, now, clockMillis())
                        inFlight.remove(key)
                        while (entries.size > maxRanges) entries.remove(entries.keys.first())
                    }
                    result
                }
                inFlight[key] = task
            }
        }
        // FutureTask.run 只会执行一次；命中进行中的请求只等待，不重复读取。
        task.run()
        return task.get().getOrThrow()
    }
}

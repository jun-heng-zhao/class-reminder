// 日历缓存策略回归：不依赖 Android、真实时钟或休眠。
package com.balance.classreminder

import com.balance.classreminder.data.CalendarQueryCache
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CalendarQueryCacheTest {
    @Test fun sameRangeUsesCacheUntilExactTtlBoundary() {
        var now = 0L // 可控单调时间，单位毫秒
        var calls = 0 // 实际数据源调用次数
        val cache = CalendarQueryCache<String, Int>(clockMillis = { now })
        assertEquals(1, cache.get("a") { ++calls })
        now = 899_999
        assertEquals(1, cache.get("a") { ++calls })
        now = 900_000
        assertEquals(2, cache.get("a") { ++calls })
        assertEquals(2, calls)
    }

    @Test fun refreshIsThrottledPerRangeForThirtySeconds() {
        var now = 0L // 刷新间隔从实际查询开始计时
        var calls = 0
        val cache = CalendarQueryCache<String, Int>(clockMillis = { now })
        cache.get("a") { ++calls }
        now = 29_999
        assertEquals(1, cache.get("a", refresh = true) { ++calls })
        assertEquals(2, cache.get("b", refresh = true) { ++calls })
        now = 30_000
        assertEquals(3, cache.get("a", refresh = true) { ++calls })
        assertEquals(3, calls)
    }

    @Test fun fifthRangeEvictsLeastRecentlyUsedRange() {
        var calls = 0 // 访问命中也应更新 LRU 顺序
        val cache = CalendarQueryCache<String, Int>(clockMillis = { 0L })
        listOf("a", "b", "c", "d").forEach { cache.get(it) { ++calls } }
        cache.get("a") { ++calls }
        cache.get("e") { ++calls }
        assertEquals(1, cache.get("a") { ++calls })
        assertEquals(6, cache.get("b") { ++calls })
        assertEquals(6, calls)
    }

    @Test fun failureKeepsOriginalErrorAndRetriesAfterThrottleNotTtl() {
        var now = 0L
        var calls = 0
        val failure = IllegalStateException("系统日历读取失败") // 不转换成空成功
        val cache = CalendarQueryCache<String, Int>(clockMillis = { now })
        repeat(2) {
            try {
                cache.get("a", refresh = true) { calls++; throw failure }
                fail("应抛出原始错误")
            } catch (error: IllegalStateException) {
                assertSame(failure, error)
            }
        }
        assertEquals(1, calls)
        now = 30_000
        assertEquals(2, cache.get("a") { ++calls })
    }

    @Test fun failedRefreshIsNotReportedAsOldSuccessAndEmptySuccessIsCached() {
        var now = 0L // 空列表属于成功，与读取异常必须区分
        var calls = 0
        val cache = CalendarQueryCache<String, List<Int>>(clockMillis = { now })
        assertTrue(cache.get("a") { calls++; emptyList() }.isEmpty())
        assertTrue(cache.get("a") { calls++; listOf(1) }.isEmpty())
        assertEquals(1, calls)
        now = 30_000
        try {
            cache.get("a", refresh = true) { calls++; error("刷新失败") }
            fail("刷新失败不可返回旧的空列表")
        } catch (error: IllegalStateException) {
            assertEquals("刷新失败", error.message)
        }
        assertEquals(2, calls)
    }

    @Test fun concurrentRequestsShareOneQueryIncludingRefresh() {
        val calls = AtomicInteger() // 跨线程计数，不靠等待时间推测查询次数
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        val cache = CalendarQueryCache<String, Int>(clockMillis = { 0L })
        try {
            val first = pool.submit<Int> {
                cache.get("a") {
                    calls.incrementAndGet()
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    42
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val others = (1..3).map {
                pool.submit<Int> { cache.get("a", refresh = true) { calls.incrementAndGet(); -1 } }
            }
            release.countDown()
            assertEquals(42, first.get(5, TimeUnit.SECONDS))
            others.forEach { assertEquals(42, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, calls.get())
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }
}

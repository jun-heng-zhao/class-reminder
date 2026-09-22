package com.balance.classreminder.ocr

import com.balance.classreminder.data.PeriodTime
import kotlin.math.abs

/**
 * 从"作息时间表"或校历右上角的"课堂教学时间表"里读出每节课的起止时间。
 * 识别失败不影响手填——设置页本来就能逐个改。
 */
object PeriodParser {

    private val TIME_RANGE = Regex("(\\d{1,2})\\s*[:：]\\s*(\\d{2})\\s*[-~—－至]\\s*(\\d{1,2})\\s*[:：]\\s*(\\d{2})")
    private val PERIOD_NO = Regex("第\\s*(\\d{1,2})\\s*节")
    private val BARE_NO = Regex("^\\s*(\\d{1,2})\\s*$")

    /**
     * 返回按节次排好的时间表：结果下标 0 就是第 1 节。
     * 缺的节次用 null 占位。
     */
    fun parse(boxes: List<OcrBox>): List<PeriodTime?> {
        val items = boxes.filter { it.text.isNotBlank() }
        if (items.isEmpty()) return emptyList()

        // 同一行的"第N节"和"08:00-08:45"配成一对
        val rows = items.groupBy { it.centerY / 24 }
        val byPeriod = sortedMapOf<Int, PeriodTime>()

        rows.values.forEach { row ->
            val sorted = row.sortedBy { it.left }
            val joined = sorted.joinToString(" ") { it.text }
            val range = TIME_RANGE.find(joined) ?: sorted.firstNotNullOfOrNull { TIME_RANGE.find(it.text) }
                ?: return@forEach
            val start = range.groupValues[1].toIntOrNull()?.times(60)?.plus(range.groupValues[2].toIntOrNull() ?: 0)
                ?: return@forEach
            val end = range.groupValues[3].toIntOrNull()?.times(60)?.plus(range.groupValues[4].toIntOrNull() ?: 0)
                ?: return@forEach
            if (start !in 0..1439 || end !in 0..1439 || end < start) return@forEach

            val period = sorted.firstNotNullOfOrNull { box ->
                PERIOD_NO.find(box.text)?.groupValues?.get(1)?.toIntOrNull()
                    ?: BARE_NO.find(box.text)?.groupValues?.get(1)?.toIntOrNull()
            } ?: return@forEach
            if (period !in 1..30) return@forEach

            val time = PeriodTime(start, end)
            val old = byPeriod[period]
            // 同一节识别到多条时，取时长短一点的那个（更可能是真正的 45 分钟一节）
            if (old == null || (time.endMinute - time.startMinute) < (old.endMinute - old.startMinute)) {
                byPeriod[period] = time
            }
        }

        if (byPeriod.isEmpty()) return emptyList()
        val max = byPeriod.keys.max()
        return (1..max).map { byPeriod[it] }
    }

    /** 解析结果里有多少节是连续的、时间合理的。 */
    fun looksSane(parsed: List<PeriodTime?>): Boolean {
        val filled = parsed.filterNotNull()
        if (filled.size < 4) return false
        val sorted = filled.sortedBy { it.startMinute }
        return sorted.zipWithNext().all { (a, b) -> b.startMinute > a.startMinute && abs(b.startMinute - a.startMinute) < 240 }
    }
}

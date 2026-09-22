package com.balance.classreminder.data

import java.time.LocalDate

/**
 * 国务院办公厅公布的 2026 年部分节假日安排（国办发明电〔2025〕7 号）。
 * 手机日历里没订阅「中国法定节假日」时，用这份内置数据一键铺上，省得一天天点。
 *
 * 调休上班日（比如 10 月 10 日周六上班）到底补哪天的课，各校执行不完全一样，
 * 这里按国务院通知里对应的放假日期给个默认值，用户可以在日历页改成实际安排。
 */
object BuiltInHolidays {

    data class Entry(
        val date: String,
        val kind: DayKind,
        val note: String,
        val swapToDayOfWeek: Int = 1,
    )

    private val RANGES = listOf(
        Triple("2026-01-01", "2026-01-03", "元旦"),
        Triple("2026-02-15", "2026-02-23", "春节"),
        Triple("2026-04-04", "2026-04-06", "清明节"),
        Triple("2026-05-01", "2026-05-05", "劳动节"),
        Triple("2026-06-19", "2026-06-21", "端午节"),
        Triple("2026-09-25", "2026-09-27", "中秋节"),
        Triple("2026-10-01", "2026-10-07", "国庆节"),
    )

    /** 调休上班日：日期、按星期几上课、说明。 */
    private val WORKDAYS = listOf(
        Triple("2026-01-04", 5, "元旦调休（周日上班）"),
        Triple("2026-02-14", 4, "春节调休（周六上班）"),
        Triple("2026-02-28", 4, "春节调休（周六上班）"),
        Triple("2026-05-09", 1, "劳动节调休（周六上班）"),
        Triple("2026-09-20", 2, "国庆调休（周日上班，补 10 月 6 日）"),
        Triple("2026-10-10", 3, "国庆调休（周六上班，补 10 月 7 日）"),
    )

    /** 取某段日期里覆盖到的放假/调休安排。 */
    fun entriesBetween(from: LocalDate, to: LocalDate): List<Entry> {
        val out = mutableListOf<Entry>()
        RANGES.forEach { (startText, endText, name) ->
            val start = runCatching { LocalDate.parse(startText) }.getOrNull() ?: return@forEach
            val end = runCatching { LocalDate.parse(endText) }.getOrNull() ?: return@forEach
            var day = start
            while (!day.isAfter(end)) {
                if (!day.isBefore(from) && !day.isAfter(to)) {
                    out += Entry(day.toString(), DayKind.HOLIDAY, name)
                }
                day = day.plusDays(1)
            }
        }
        WORKDAYS.forEach { (dateText, swapTo, note) ->
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return@forEach
            if (!date.isBefore(from) && !date.isAfter(to)) {
                out += Entry(date.toString(), DayKind.SWAP, note, swapTo)
            }
        }
        return out.sortedBy { it.date }
    }

    fun entriesForTerm(termStart: LocalDate?, totalWeeks: Int): List<Entry> {
        val start = (termStart ?: LocalDate.now()).minusDays(14)
        val end = start.plusDays(totalWeeks * 7L + 28)
        return entriesBetween(start, end)
    }
}

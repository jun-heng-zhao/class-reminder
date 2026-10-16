package com.balance.classreminder.data

import java.time.LocalDate

/**
 * 内置法定节假日数据源。
 *
 * MIUI / HyperOS 的「中国法定节假日」不通过 CalendarContract 暴露给第三方应用，
 * 所以这里自备一份，读不到系统日历时自动套用，保证假期不再发提醒：
 *   - 2025 / 2026：国务院办公厅公布的完整安排（放假区间 + 调休上班日），最准；
 *   - 其他年份：退到「算得出来的法定假日」——元旦、春节（除夕~初三）、清明、劳动节、国庆，
 *     不用联网也不用每年更新数据。此范围外的端午 / 中秋请从手机日历导入或手动标。
 *
 * 调休上班日到底补哪天的课各校执行不一致，这里一律留空 =「待安排」，
 * 这天先不排课也不提醒，等用户在日历页指定。
 */
object BuiltInHolidays {

    data class Entry(
        val date: String,
        val kind: DayKind,
        val note: String,
        val swapToDayOfWeek: Int = 0,
    )

    /** 官方放假区间，按年份分组：起 / 止 / 节日名。 */
    private val OFFICIAL_RANGES: Map<Int, List<Triple<String, String, String>>> = mapOf(
        2025 to listOf(
            Triple("2025-01-01", "2025-01-01", "元旦"),
            Triple("2025-01-28", "2025-02-04", "春节"),
            Triple("2025-04-04", "2025-04-06", "清明节"),
            Triple("2025-05-01", "2025-05-05", "劳动节"),
            Triple("2025-05-31", "2025-06-02", "端午节"),
            Triple("2025-10-01", "2025-10-08", "国庆节·中秋节"),
        ),
        2026 to listOf(
            Triple("2026-01-01", "2026-01-03", "元旦"),
            Triple("2026-02-15", "2026-02-23", "春节"),
            Triple("2026-04-04", "2026-04-06", "清明节"),
            Triple("2026-05-01", "2026-05-05", "劳动节"),
            Triple("2026-06-19", "2026-06-21", "端午节"),
            Triple("2026-09-25", "2026-09-27", "中秋节"),
            Triple("2026-10-01", "2026-10-07", "国庆节"),
        ),
    )

    /** 官方调休上班日，按年份分组：日期 + 说明。 */
    private val OFFICIAL_WORKDAYS: Map<Int, List<Pair<String, String>>> = mapOf(
        2025 to listOf(
            "2025-01-26" to "春节调休（周日上班）",
            "2025-02-08" to "春节调休（周六上班）",
            "2025-04-27" to "劳动节调休（周日上班）",
            "2025-09-28" to "国庆调休（周日上班）",
            "2025-10-11" to "国庆调休（周六上班）",
        ),
        2026 to listOf(
            "2026-01-04" to "元旦调休（周日上班）",
            "2026-02-14" to "春节调休（周六上班）",
            "2026-02-28" to "春节调休（周六上班）",
            "2026-05-09" to "劳动节调休（周六上班）",
            "2026-09-20" to "国庆调休（周日上班）",
            "2026-10-10" to "国庆调休（周六上班）",
        ),
    )

    /** 有完整官方数据的年份，界面用它告诉用户数据覆盖到哪一年。 */
    val officialYears: List<Int> get() = OFFICIAL_RANGES.keys.sorted()

    /** 春节（农历正月初一）的公历日期，兜底年份靠它推除夕~初三。 */
    private val SPRING_FESTIVAL = mapOf(
        2024 to "2024-02-10",
        2025 to "2025-01-29",
        2026 to "2026-02-17",
        2027 to "2027-02-06",
        2028 to "2028-01-26",
        2029 to "2029-02-13",
        2030 to "2030-02-03",
        2031 to "2031-01-23",
        2032 to "2032-02-11",
        2033 to "2033-01-31",
        2034 to "2034-02-19",
        2035 to "2035-02-08",
    )

    private fun parse(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    /** 清明在 4 月 4~6 日之间，用 21 世纪通用的寿星公式：2025 → 4/4、2026 → 4/5。 */
    private fun qingmingDay(year: Int): Int {
        val y = year - 2000
        return (y * 0.2422 + 4.81).toInt() - y / 4
    }

    /** 没有官方数据的年份：只标法定假日本身（不含调休连休），够挡住「假期还发提醒」。 */
    private fun statutoryEntries(year: Int): List<Entry> {
        val out = mutableListOf<Entry>()
        fun add(date: LocalDate, note: String) {
            out += Entry(date.toString(), DayKind.HOLIDAY, note)
        }
        add(LocalDate.of(year, 1, 1), "元旦")
        SPRING_FESTIVAL[year]?.let { text ->
            parse(text)?.let { firstDay ->
                // 法定 4 天：除夕、正月初一至初三
                (-1..2).forEach { add(firstDay.plusDays(it.toLong()), "春节") }
            }
        }
        add(LocalDate.of(year, 4, qingmingDay(year)), "清明节")
        add(LocalDate.of(year, 5, 1), "劳动节")
        add(LocalDate.of(year, 5, 2), "劳动节")
        add(LocalDate.of(year, 10, 1), "国庆节")
        add(LocalDate.of(year, 10, 2), "国庆节")
        add(LocalDate.of(year, 10, 3), "国庆节")
        return out
    }

    /** 某一年的全部安排：有官方数据用官方，没有就用可计算的法定假日。 */
    private fun entriesOfYear(year: Int): List<Entry> {
        val ranges = OFFICIAL_RANGES[year] ?: return statutoryEntries(year)
        val out = mutableListOf<Entry>()
        ranges.forEach { (startText, endText, name) ->
            val start = parse(startText) ?: return@forEach
            val end = parse(endText) ?: return@forEach
            var day = start
            while (!day.isAfter(end)) {
                out += Entry(day.toString(), DayKind.HOLIDAY, name)
                day = day.plusDays(1)
            }
        }
        OFFICIAL_WORKDAYS[year].orEmpty().forEach { (dateText, note) ->
            // 上哪天的课由学校定，先留空等用户在日历页安排
            parse(dateText)?.let { out += Entry(it.toString(), DayKind.SWAP, note, swapToDayOfWeek = 0) }
        }
        return out
    }

    /** 取某段日期里覆盖到的放假/调休安排。 */
    fun entriesBetween(from: LocalDate, to: LocalDate): List<Entry> =
        (from.year..to.year)
            .flatMap { entriesOfYear(it) }
            .filter { entry -> parse(entry.date)?.let { !it.isBefore(from) && !it.isAfter(to) } == true }
            .distinctBy { it.date }
            .sortedBy { it.date }

    fun entriesForTerm(termStart: LocalDate?, totalWeeks: Int): List<Entry> {
        val start = (termStart ?: LocalDate.now()).minusDays(14)
        val end = start.plusDays(totalWeeks * 7L + 28)
        return entriesBetween(start, end)
    }
}

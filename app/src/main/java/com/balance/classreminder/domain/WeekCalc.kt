package com.balance.classreminder.domain

import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.DayKind
import com.balance.classreminder.data.DayOverride
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.PeriodTime
import com.balance.classreminder.data.WeekParity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 一次具体的上课：某天某个时刻，落到具体的日期。 */
data class Occurrence(
    val course: Course,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val week: Int,
)

object WeekCalc {

    /** 把任意日期对齐到所在周的周一。 */
    fun mondayOf(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    /** 第几周（1 起）。早于第一周返回 <=0。 */
    fun weekOf(termStartMonday: LocalDate?, date: LocalDate): Int {
        if (termStartMonday == null) return 0
        val base = mondayOf(termStartMonday)
        // 用 floorDiv：开学前那几天要算成第 0 周，而不是第 1 周
        return Math.floorDiv(ChronoUnit.DAYS.between(base, date).toInt(), 7) + 1
    }

    fun parseDate(iso: String): LocalDate? = runCatching { LocalDate.parse(iso.trim()) }.getOrNull()

    /**
     * 这门课到底上哪些周。
     * 有 [Course.weekSpec] 就按它解析（"15,16" 这种只在第 15、16 周上的课靠它表达），
     * 否则回退到"起止周 + 单双周"。
     */
    fun weeksOf(course: Course, totalWeeks: Int = 52): Set<Int> {
        if (course.weekSpec.isNotBlank()) {
            val parsed = parseWeeks(course.weekSpec, totalWeeks)
            // 单双周单独存在 parity 里，spec 只管周次集合
            if (parsed.isNotEmpty()) return parsed.filter { parityMatches(course.parity, it) }.toSet()
        }
        return (course.startWeek..course.endWeek)
            .filter { parityMatches(course.parity, it) }
            .toSet()
    }

    /** 解析 "1-16" / "15,16" / "1-4,6-8" / "1-16单" / "13-14" 这类写法。 */
    fun parseWeeks(spec: String, totalWeeks: Int = 52): Set<Int> {
        val weeks = sortedSetOf<Int>()
        val odd = spec.contains("单")
        val even = spec.contains("双")
        var rest = spec

        Regex("(\\d{1,2})\\s*[-~—－至]\\s*(\\d{1,2})").findAll(spec).forEach { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@forEach
            val b = m.groupValues[2].toIntOrNull() ?: return@forEach
            val from = minOf(a, b)
            val to = maxOf(a, b)
            (from..to).forEach { if (it in 1..totalWeeks) weeks += it }
            rest = rest.replace(m.value, " ")
        }
        Regex("\\d{1,2}").findAll(rest).forEach { m ->
            val w = m.value.toIntOrNull() ?: return@forEach
            if (w in 1..totalWeeks) weeks += w
        }
        return when {
            odd && !even -> weeks.filter { it % 2 == 1 }.toSortedSet()
            even && !odd -> weeks.filter { it % 2 == 0 }.toSortedSet()
            else -> weeks
        }
    }

    private fun parityMatches(parity: WeekParity, week: Int): Boolean = when (parity) {
        WeekParity.ALL -> true
        WeekParity.ODD -> week % 2 == 1
        WeekParity.EVEN -> week % 2 == 0
    }

    /** 该周次是否落在课程的周次范围内（含单双周与不规则周次）。 */
    fun weekMatches(course: Course, week: Int): Boolean = weeksOf(course).contains(week)

    /** 这天实际按星期几排课：放假返回 null，调休日返回被调整到的星期几。 */
    fun effectiveDayOfWeek(settings: AppSettings, date: LocalDate): Int? {
        val override = settings.overrides.firstOrNull { it.date == date.toString() } ?: return date.dayOfWeek.value
        return when (override.kind) {
            DayKind.NORMAL -> date.dayOfWeek.value
            DayKind.HOLIDAY -> null
            DayKind.SWAP -> override.swapToDayOfWeek
        }
    }

    fun overrideOf(settings: AppSettings, date: LocalDate): DayOverride? =
        settings.overrides.firstOrNull { it.date == date.toString() }

    /** 这天有没有这门课（考虑放假与调休）。 */
    fun courseOnDate(course: Course, termStartMonday: LocalDate, settings: AppSettings, date: LocalDate): Boolean {
        val effectiveDay = effectiveDayOfWeek(settings, date) ?: return false
        if (course.dayOfWeek != effectiveDay) return false
        val override = overrideOf(settings, date)
        // 调休可以指定"按第几周的课表"；没指定就用这天所在的周次
        val week = override?.takeIf { it.kind == DayKind.SWAP && it.swapToWeek > 0 }?.swapToWeek
            ?: weekOf(termStartMonday, date)
        return weekMatches(course, week)
    }

    /** 把节次换算成具体时刻；节次超出作息表返回 null。 */
    fun timeOfPeriod(periods: List<PeriodTime>, period: Int, end: Boolean): LocalTime? {
        val p = periods.getOrNull(period - 1) ?: return null
        val minute = if (end) p.endMinute else p.startMinute
        return LocalTime.of((minute / 60) % 24, minute % 60)
    }

    /** 某天这门课的具体起止时间（考虑放假与调休）。 */
    fun occurrenceOn(
        course: Course,
        termStartMonday: LocalDate,
        settings: AppSettings,
        date: LocalDate,
    ): Occurrence? {
        if (!courseOnDate(course, termStartMonday, settings, date)) return null
        val periods = settings.periods
        val start = timeOfPeriod(periods, course.startPeriod, end = false) ?: return null
        val end = timeOfPeriod(periods, course.endPeriod, end = true) ?: start
        return Occurrence(course, LocalDateTime.of(date, start), LocalDateTime.of(date, end), weekOf(termStartMonday, date))
    }

    /** 某天要上的全部课，按时间排好（日历页用）。 */
    fun coursesOn(settings: AppSettings, courses: List<Course>, date: LocalDate): List<Occurrence> {
        val termStart = parseDate(settings.termStartDate) ?: return emptyList()
        return courses.mapNotNull { occurrenceOn(it, termStart, settings, date) }.sortedBy { it.start }
    }

    /**
     * 从现在起 [horizonDays] 天内还没开始的所有课，按时间排序。
     * 提醒调度就是拿这个列表去挂 AlarmManager。
     */
    fun upcoming(
        courses: List<Course>,
        settings: AppSettings,
        now: LocalDateTime,
        horizonDays: Int = 8,
    ): List<Occurrence> {
        val termStart = parseDate(settings.termStartDate) ?: return emptyList()
        val out = mutableListOf<Occurrence>()
        val today = now.toLocalDate()
        for (offset in 0..horizonDays) {
            val date = today.plusDays(offset.toLong())
            courses.forEach { c ->
                val occ = occurrenceOn(c, termStart, settings, date) ?: return@forEach
                if (occ.start.isAfter(now)) out += occ
            }
        }
        return out.sortedBy { it.start }
    }

    fun reminderMinutes(course: Course, settings: AppSettings): Int =
        if (course.reminderMinutes >= 0) course.reminderMinutes else settings.defaultReminderMinutes
}

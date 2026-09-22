package com.balance.classreminder.domain

import com.balance.classreminder.data.AppSettings
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
        return (ChronoUnit.DAYS.between(base, date).toInt() / 7) + 1
    }

    fun parseDate(iso: String): LocalDate? = runCatching { LocalDate.parse(iso.trim()) }.getOrNull()

    /** 该周次是否落在课程的周次范围内（含单双周过滤）。 */
    fun weekMatches(course: Course, week: Int): Boolean {
        if (week < course.startWeek || week > course.endWeek) return false
        return when (course.parity) {
            WeekParity.ALL -> true
            WeekParity.ODD -> week % 2 == 1
            WeekParity.EVEN -> week % 2 == 0
        }
    }

    /** 这天有没有这门课。 */
    fun courseOnDate(course: Course, termStartMonday: LocalDate, date: LocalDate): Boolean {
        if (course.dayOfWeek != date.dayOfWeek.value) return false
        return weekMatches(course, weekOf(termStartMonday, date))
    }

    /** 把节次换算成具体时刻；节次超出作息表返回 null。 */
    fun timeOfPeriod(periods: List<PeriodTime>, period: Int, end: Boolean): LocalTime? {
        val p = periods.getOrNull(period - 1) ?: return null
        val minute = if (end) p.endMinute else p.startMinute
        return LocalTime.of((minute / 60) % 24, minute % 60)
    }

    /** 某天这门课的具体起止时间。 */
    fun occurrenceOn(
        course: Course,
        termStartMonday: LocalDate,
        periods: List<PeriodTime>,
        date: LocalDate,
    ): Occurrence? {
        if (!courseOnDate(course, termStartMonday, date)) return null
        val start = timeOfPeriod(periods, course.startPeriod, end = false) ?: return null
        val end = timeOfPeriod(periods, course.endPeriod, end = true) ?: start
        return Occurrence(course, LocalDateTime.of(date, start), LocalDateTime.of(date, end), weekOf(termStartMonday, date))
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
                val occ = occurrenceOn(c, termStart, settings.periods, date) ?: return@forEach
                if (occ.start.isAfter(now)) out += occ
            }
        }
        return out.sortedBy { it.start }
    }

    fun reminderMinutes(course: Course, settings: AppSettings): Int =
        if (course.reminderMinutes >= 0) course.reminderMinutes else settings.defaultReminderMinutes
}

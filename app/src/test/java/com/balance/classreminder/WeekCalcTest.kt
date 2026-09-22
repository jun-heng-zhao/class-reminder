package com.balance.classreminder

import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.WeekParity
import com.balance.classreminder.domain.WeekCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WeekCalcTest {

    private val term = LocalDate.of(2026, 9, 7) // 周一

    private fun course(
        id: String = "c1",
        dayOfWeek: Int = 1,
        startPeriod: Int = 1,
        endPeriod: Int = 1,
        startWeek: Int = 1,
        endWeek: Int = 16,
        parity: WeekParity = WeekParity.ALL,
    ) = Course(
        id = id,
        name = "高等数学",
        location = "教三201",
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        startWeek = startWeek,
        endWeek = endWeek,
        parity = parity,
    )

    @Test
    fun weekOf_countsFromFirstMonday() {
        assertEquals(1, WeekCalc.weekOf(term, term))
        assertEquals(1, WeekCalc.weekOf(term, term.plusDays(6)))
        assertEquals(2, WeekCalc.weekOf(term, term.plusDays(7)))
        assertEquals(3, WeekCalc.weekOf(term, LocalDate.of(2026, 9, 21)))
        assertEquals(0, WeekCalc.weekOf(term, term.minusDays(1)))
    }

    @Test
    fun weekOf_returnsZeroWithoutTermStart() {
        assertEquals(0, WeekCalc.weekOf(null, LocalDate.now()))
    }

    @Test
    fun mondayOf_snapsToMonday() {
        assertEquals(term, WeekCalc.mondayOf(LocalDate.of(2026, 9, 13))) // 周日
        assertEquals(term, WeekCalc.mondayOf(LocalDate.of(2026, 9, 7)))
    }

    @Test
    fun weekMatches_filtersRangeAndParity() {
        val odd = course(startWeek = 1, endWeek = 8, parity = WeekParity.ODD)
        assertTrue(WeekCalc.weekMatches(odd, 1))
        assertFalse(WeekCalc.weekMatches(odd, 2))
        assertFalse(WeekCalc.weekMatches(odd, 9))

        val even = course(parity = WeekParity.EVEN)
        assertFalse(WeekCalc.weekMatches(even, 3))
        assertTrue(WeekCalc.weekMatches(even, 4))
    }

    @Test
    fun occurrenceOn_mapsPeriodsToClockTime() {
        val settings = AppSettings(termStartDate = term.toString())
        val occ = WeekCalc.occurrenceOn(course(startPeriod = 1, endPeriod = 2), term, settings.periods, term)
        assertNotNull(occ)
        assertEquals(LocalDateTime.of(term, LocalTime.of(8, 0)), occ!!.start)
        assertEquals(LocalDateTime.of(term, LocalTime.of(9, 40)), occ.end)
        assertEquals(1, occ.week)
    }

    @Test
    fun occurrenceOn_returnsNullOnWrongWeekday() {
        val settings = AppSettings(termStartDate = term.toString())
        // 课程在周二，查周一
        val occ = WeekCalc.occurrenceOn(course(dayOfWeek = 2), term, settings.periods, term)
        assertEquals(null, occ)
    }

    @Test
    fun upcoming_skipsClassesAlreadyStarted() {
        val settings = AppSettings(termStartDate = term.toString())
        val monday = course(id = "mon", dayOfWeek = 1)
        val tuesday = course(id = "tue", dayOfWeek = 2)
        val now = LocalDateTime.of(term, LocalTime.of(9, 0)) // 周一第 1 节已开始

        val list = WeekCalc.upcoming(listOf(monday, tuesday), settings, now, horizonDays = 6)

        assertEquals(1, list.size)
        assertEquals("tue", list[0].course.id)
        assertEquals(2, list[0].start.dayOfWeek.value)
    }

    @Test
    fun parseWeeks_handlesListsAndRanges() {
        assertEquals(setOf(15, 16), WeekCalc.parseWeeks("15,16", 20))
        assertEquals(setOf(1, 2, 3, 4, 6, 7, 8), WeekCalc.parseWeeks("1-4,6-8", 20))
        assertEquals(setOf(11), WeekCalc.parseWeeks("11-11", 20))
        assertEquals(setOf(1, 3, 5), WeekCalc.parseWeeks("1-5单", 20))
        assertEquals(setOf(2, 4, 6), WeekCalc.parseWeeks("1-6", 20).filter { it % 2 == 0 }.toSet())
    }

    @Test
    fun weeksOf_usesSpecAndParity() {
        val onlyLate = course().copy(weekSpec = "15,16", startWeek = 15, endWeek = 16)
        assertTrue(WeekCalc.weekMatches(onlyLate, 15))
        assertTrue(WeekCalc.weekMatches(onlyLate, 16))
        assertFalse(WeekCalc.weekMatches(onlyLate, 1))
        assertFalse(WeekCalc.weekMatches(onlyLate, 17))

        val oddSpec = course().copy(weekSpec = "2-16", parity = WeekParity.ODD)
        assertTrue(WeekCalc.weekMatches(oddSpec, 3))
        assertFalse(WeekCalc.weekMatches(oddSpec, 4))
    }

    @Test
    fun upcoming_onlySchedulesWeeksTheCourseRuns() {
        // 九月七日开学，第 1 周；这门课只在第 15、16 周上
        val settings = AppSettings(termStartDate = term.toString())
        val late = course(dayOfWeek = 1).copy(
            weekSpec = "15,16",
            startWeek = 15,
            endWeek = 16,
        )
        val week15Monday = term.plusWeeks(14)   // 第 15 周的周一
        val now = LocalDateTime.of(week15Monday, LocalTime.of(7, 0))

        val list = WeekCalc.upcoming(listOf(late), settings, now, horizonDays = 6)

        assertEquals(1, list.size)
        assertEquals(15, list[0].week)
        assertEquals(week15Monday, list[0].start.toLocalDate())
    }

    @Test
    fun upcoming_respectsReminderOverride() {
        val settings = AppSettings(termStartDate = term.toString(), defaultReminderMinutes = 20)
        val custom = course().copy(reminderMinutes = 5)
        assertEquals(5, WeekCalc.reminderMinutes(custom, settings))
        assertEquals(20, WeekCalc.reminderMinutes(course(), settings))
    }
}

package com.balance.classreminder

import com.balance.classreminder.data.BuiltInHolidays
import com.balance.classreminder.data.DayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 内置节假日数据：有官方安排的年份要对得上国务院通知，兜底年份也得挡住法定假日。 */
class BuiltInHolidaysTest {

    private fun entriesOf(year: Int): List<BuiltInHolidays.Entry> =
        BuiltInHolidays.entriesBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

    private fun kindOf(date: String): DayKind? =
        entriesOf(date.substring(0, 4).toInt()).firstOrNull { it.date == date }?.kind

    @Test
    fun official2026_matchesStateCouncilArrangement() {
        (1..7).forEach { day ->
            assertEquals("国庆第 $day 天没标成放假", DayKind.HOLIDAY, kindOf("2026-10-%02d".format(day)))
        }
        assertEquals(DayKind.HOLIDAY, kindOf("2026-02-16"))   // 除夕
        assertEquals(DayKind.HOLIDAY, kindOf("2026-02-17"))   // 正月初一
        assertEquals(DayKind.HOLIDAY, kindOf("2026-09-25"))   // 中秋
        assertEquals(DayKind.HOLIDAY, kindOf("2026-06-19"))   // 端午
        assertEquals(DayKind.HOLIDAY, kindOf("2026-04-05"))   // 清明

        // 调休上班日标成 SWAP 且默认「待安排」——这天先不排课、不提醒
        val workday = entriesOf(2026).first { it.date == "2026-10-10" }
        assertEquals(DayKind.SWAP, workday.kind)
        assertEquals(0, workday.swapToDayOfWeek)
    }

    @Test
    fun official2025_coversNationalDayBreak() {
        (1..8).forEach { day ->
            assertEquals(DayKind.HOLIDAY, kindOf("2025-10-%02d".format(day)))
        }
        assertEquals(DayKind.HOLIDAY, kindOf("2025-01-29"))   // 正月初一
    }

    @Test
    fun fallbackYear_stillBlocksStatutoryHolidays() {
        // 2027 年国务院安排还没公布，但可计算的法定假日必须算对
        assertEquals(DayKind.HOLIDAY, kindOf("2027-01-01"))
        assertEquals(DayKind.HOLIDAY, kindOf("2027-02-05"))   // 除夕
        assertEquals(DayKind.HOLIDAY, kindOf("2027-02-06"))   // 正月初一
        assertEquals(DayKind.HOLIDAY, kindOf("2027-04-05"))   // 清明
        assertEquals(DayKind.HOLIDAY, kindOf("2027-05-01"))
        assertEquals(DayKind.HOLIDAY, kindOf("2027-10-02"))
        // 春节假期之后的普通日子不能被误标
        assertEquals(null, kindOf("2027-02-20"))
    }

    @Test
    fun entriesForTerm_onlyCoversTermRange() {
        val term = LocalDate.of(2026, 9, 7)
        val dates = BuiltInHolidays.entriesForTerm(term, 20).map { LocalDate.parse(it.date) }

        assertTrue(dates.any { it.toString() == "2026-10-01" })      // 学期内的国庆
        assertFalse(dates.any { it.toString() == "2026-06-19" })     // 上学期的事，不该带进来
        assertTrue(dates.all { !it.isBefore(term.minusDays(14)) })
    }

    @Test
    fun datesAreUnique() {
        val dates = entriesOf(2026).map { it.date }
        assertEquals(dates.size, dates.toSet().size)
    }
}

package com.balance.classreminder

import com.balance.classreminder.ocr.CalendarParser
import com.balance.classreminder.ocr.OcrBox
import com.balance.classreminder.ocr.PeriodParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodAndCalendarParserTest {

    private fun box(text: String, cx: Int, cy: Int, w: Int = 120, h: Int = 20) =
        OcrBox(text, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)

    @Test
    fun periodParser_readsTimesNextToPeriodNumbers() {
        val boxes = listOf(
            box("上午", 100, 40),
            box("第1节", 100, 80, w = 60),
            box("08:00-08:45", 260, 80),
            box("第2节", 100, 120, w = 60),
            box("08:55-09:40", 260, 120),
            box("第3节", 100, 160, w = 60),
            box("10:00-10:45", 260, 160),
            box("第4节", 100, 200, w = 60),
            box("10:55-11:40", 260, 200),
            box("下午", 100, 240),
            box("第5节", 100, 280, w = 60),
            box("14:30-15:15", 260, 280),
        )

        val parsed = PeriodParser.parse(boxes)

        assertEquals(5, parsed.size)
        assertNotNull(parsed[0])
        assertEquals("08:00", parsed[0]!!.startLabel)
        assertEquals("08:45", parsed[0]!!.endLabel)
        assertEquals("14:30", parsed[4]!!.startLabel)
        assertTrue(PeriodParser.looksSane(parsed))
    }

    @Test
    fun periodParser_returnsEmptyOnGarbage() {
        val parsed = PeriodParser.parse(listOf(box("随便写点东西", 100, 100), box("12:34 i", 20, 20)))
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun cnNumber_handlesChineseNumerals() {
        assertEquals(16, CalendarParser.cnNumber("十六"))
        assertEquals(10, CalendarParser.cnNumber("十"))
        assertEquals(20, CalendarParser.cnNumber("二十"))
        assertEquals(24, CalendarParser.cnNumber("二十四"))
        assertEquals(18, CalendarParser.cnNumber("18"))
        assertEquals(null, CalendarParser.cnNumber("周"))
    }

    @Test
    fun builtInHolidays_coverNationalDayAndMakeupDays() {
        val entries = com.balance.classreminder.data.BuiltInHolidays.entriesBetween(
            java.time.LocalDate.of(2026, 9, 1),
            java.time.LocalDate.of(2026, 11, 30),
        )
        fun has(date: String, kind: com.balance.classreminder.data.DayKind) =
            entries.any { it.date == date && it.kind == kind }

        assertTrue(has("2026-09-25", com.balance.classreminder.data.DayKind.HOLIDAY))  // 中秋
        assertTrue(has("2026-10-01", com.balance.classreminder.data.DayKind.HOLIDAY))  // 国庆首日
        assertTrue(has("2026-10-07", com.balance.classreminder.data.DayKind.HOLIDAY))  // 国庆末日
        assertTrue(has("2026-09-20", com.balance.classreminder.data.DayKind.SWAP))     // 周日上班
        assertTrue(has("2026-10-10", com.balance.classreminder.data.DayKind.SWAP))     // 周六上班
        // 10 月 8 日已经收假，不该被当成假期
        assertTrue(entries.none { it.date == "2026-10-08" })
        assertEquals(2, entries.first { it.date == "2026-09-20" }.swapToDayOfWeek)
    }

    @Test
    fun calendarParser_readsFirstWeekAndTotalWeeks() {
        val boxes = listOf(
            box("2026-2027学年秋季学期校历", 600, 60, w = 400),
            box("教学周：2026年9月14日至2027年1月1日（第一至十六周）", 500, 300, w = 700),
            box("考试周：2027年1月4日至2027年1月15日", 500, 360, w = 500),
        )

        val result = CalendarParser.parse(boxes, fallbackYear = 2026)

        assertEquals(16, result.totalWeeks)
        // 2026-09-14 本身是周一
        assertEquals("2026-09-14", result.firstWeekMonday)
    }
}

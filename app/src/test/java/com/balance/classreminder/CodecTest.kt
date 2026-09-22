package com.balance.classreminder

import com.balance.classreminder.data.AppSettings
import com.balance.classreminder.data.Codec
import com.balance.classreminder.data.Course
import com.balance.classreminder.data.WeekParity
import com.balance.classreminder.data.defaultPeriods
import org.junit.Assert.assertEquals
import org.junit.Test

class CodecTest {

    private val course = Course(
        id = "a\tb",
        name = "高等数学\n下",
        location = "教三\t201",
        teacher = "张老师",
        dayOfWeek = 3,
        startPeriod = 2,
        endPeriod = 3,
        startWeek = 2,
        endWeek = 9,
        parity = WeekParity.EVEN,
        reminderMinutes = 15,
        colorIndex = 4,
    )

    private val settings = AppSettings(
        termStartDate = "2026-09-07",
        totalWeeks = 18,
        defaultReminderMinutes = 25,
        strongReminder = true,
    )

    @Test
    fun encodeDecode_roundTripsIncludingSpecialChars() {
        val text = Codec.encode(listOf(course), settings)
        val (courses, decoded) = Codec.decode(text)

        assertEquals(listOf(course), courses)
        assertEquals(settings, decoded.copy(periods = defaultPeriods()))
    }

    @Test
    fun decode_toleratesUnknownAndBrokenLines() {
        val text = buildString {
            appendLine("v1")
            appendLine("这不是一条记录")
            appendLine("S\t2026-09-07\t20\t20\t0")
            appendLine("C\tx1\t高数\t\t\t1\t1\t1\t1\t16\tALL\t-1\t0")
        }
        val (courses, settings) = Codec.decode(text)

        assertEquals(1, courses.size)
        assertEquals("高数", courses[0].name)
        assertEquals("2026-09-07", settings.termStartDate)
    }

    @Test
    fun decode_ofEmptyTextGivesDefaults() {
        val (courses, settings) = Codec.decode("")
        assertEquals(0, courses.size)
        assertEquals(20, settings.totalWeeks)
        assertEquals(defaultPeriods(), settings.periods)
    }
}

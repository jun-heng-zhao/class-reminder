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

    @Test
    fun calibrationAndPendingSwap_roundTrip() {
        val calibrated = settings.copy(manualWeek = 6, manualWeekMonday = "2026-10-12",
            overrides = listOf(com.balance.classreminder.data.DayOverride("2026-10-17", com.balance.classreminder.data.DayKind.SWAP)))
        assertEquals(calibrated, Codec.decode(Codec.encode(emptyList(), calibrated)).second)
        assertEquals(0, Codec.decode("v1\nS\t2026-09-07\t20\t20\t0").second.manualWeek)
    }

    @Test
    fun oldSwapRecords_preservePendingAndExplicitWeeks() {
        val text = "v1\nS\t2026-09-07\t20\t20\t0\nD\t2026-10-17\tSWAP\t0\t待安排\nD\t2026-10-18\tSWAP\t3\t补课\t5"
        val decoded = Codec.decode(text).second // 旧备份没有校准字段与部分调休周字段
        assertEquals("", decoded.manualWeekMonday)
        assertEquals(0, decoded.overrides[0].swapToDayOfWeek)
        assertEquals(-1, decoded.overrides[0].swapToWeek)
        assertEquals(3, decoded.overrides[1].swapToDayOfWeek)
        assertEquals(5, decoded.overrides[1].swapToWeek)
        assertEquals(decoded, Codec.decode(Codec.encode(emptyList(), decoded)).second)
    }

    @Test
    fun brokenCalibrationFields_useSafeDefaults() {
        val broken = Codec.decode("v1\nS\t2026-09-07\t20\t20\t0\tbad").second // 损坏字段不得阻止旧数据恢复
        val negative = Codec.decode("v1\nS\t2026-09-07\t20\t20\t0\t-2\t2026-10-12").second
        assertEquals(0, broken.manualWeek)
        assertEquals("", broken.manualWeekMonday)
        assertEquals(0, negative.manualWeek)
    }
}

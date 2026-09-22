package com.balance.classreminder

import com.balance.classreminder.data.WeekParity
import com.balance.classreminder.ocr.GridParser
import com.balance.classreminder.ocr.OcrBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridParserTest {

    /** 造一个以 (cx, cy) 为中心的文本框，模拟 ML Kit 的输出。 */
    private fun box(text: String, cx: Int, cy: Int, w: Int = 90, h: Int = 20) =
        OcrBox(text, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)

    private fun headerRow(y: Int = 15) = listOf(
        box("周一", 110, y), box("周二", 210, y), box("周三", 310, y),
        box("周四", 410, y), box("周五", 510, y),
    )

    @Test
    fun dayNumberOf_recognisesChineseAndDigits() {
        assertEquals(1, GridParser.dayNumberOf("周一"))
        assertEquals(3, GridParser.dayNumberOf("星期三"))
        assertEquals(7, GridParser.dayNumberOf("周日"))
        assertEquals(7, GridParser.dayNumberOf("周天"))
        assertEquals(5, GridParser.dayNumberOf("周5"))
        assertEquals(null, GridParser.dayNumberOf("高等数学"))
    }

    @Test
    fun parse_singleCellWithLocationAndWeeks() {
        val boxes = headerRow() + listOf(
            box("1", 20, 55, w = 20),
            box("2", 20, 115, w = 20),
            box("高等数学", 115, 50),
            box("教三201", 115, 70),
            box("1-16周", 115, 90),
        )

        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)

        assertEquals(1, result.courses.size)
        val c = result.courses[0]
        assertEquals(1, c.dayOfWeek)
        assertEquals(1, c.period)
        assertEquals("高等数学", c.name)
        assertEquals("教三201", c.location)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
    }

    @Test
    fun parse_mergesConsecutivePeriodsOfSameCourse() {
        val boxes = headerRow() + listOf(
            box("1", 20, 55, w = 20),
            box("2", 20, 115, w = 20),
            box("大学英语", 115, 50),
            box("外语楼101", 115, 70),
            box("大学英语", 115, 110),
            box("外语楼101", 115, 130),
        )

        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)

        assertEquals(1, result.courses.size)
        assertEquals(1, result.courses[0].period)
        assertEquals(2, result.courses[0].endPeriod)
        assertEquals("大学英语", result.courses[0].name)
    }

    @Test
    fun parse_readsParityAndSeparatesColumns() {
        val boxes = headerRow() + listOf(
            box("1", 20, 55, w = 20),
            box("线性代数", 115, 50),
            box("单周", 115, 70),
            box("综合楼305", 115, 90),
            box("体育", 315, 50),
            box("操场", 315, 70),
        )

        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)

        assertEquals(2, result.courses.size)
        val math = result.courses.first { it.name == "线性代数" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(WeekParity.ODD, math.parity)
        assertEquals("综合楼305", math.location)

        val pe = result.courses.first { it.name == "体育" }
        assertEquals(3, pe.dayOfWeek)
        assertEquals("操场", pe.location)
    }

    @Test
    fun parse_reportsWarningWhenNoHeader() {
        val boxes = listOf(
            box("高等数学", 115, 50),
            box("教三201", 115, 70),
        )
        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)
        assertTrue(result.warnings.any { it.contains("表头") })
    }

    @Test
    fun parse_emptyInputIsNotAnError() {
        val result = GridParser.parse(emptyList(), defaultTotalWeeks = 20)
        assertEquals(0, result.courses.size)
        assertTrue(result.warnings.isNotEmpty())
    }
}

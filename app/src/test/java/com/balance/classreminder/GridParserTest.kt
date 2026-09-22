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

    /** 真机上踩到的坑：「实验楼C102」和「大学物理实验」被认反了。 */
    @Test
    fun parse_doesNotSwapCourseNameAndRoom() {
        val boxes = headerRow() + listOf(
            box("5", 20, 55, w = 20),
            box("大学物理实验", 415, 50),
            box("实验楼C102", 415, 70),
            box("4-18周", 415, 90),
        )

        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)

        assertEquals(1, result.courses.size)
        assertEquals("大学物理实验", result.courses[0].name)
        assertEquals("实验楼C102", result.courses[0].location)
        assertEquals(4, result.courses[0].startWeek)
        assertEquals(18, result.courses[0].endWeek)
    }

    /** 「1-16周(单)」这种写法之前识别不出单双周，还把「单」当成了老师。 */
    @Test
    fun parse_readsParityWrittenInParentheses() {
        val boxes = headerRow() + listOf(
            box("7", 20, 55, w = 20),
            box("数据结构", 315, 50),
            box("信息楼B301", 315, 70),
            box("1-16周(单)", 315, 90),
        )

        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)

        assertEquals(1, result.courses.size)
        val course = result.courses[0]
        assertEquals("数据结构", course.name)
        assertEquals("信息楼B301", course.location)
        assertEquals(WeekParity.ODD, course.parity)
        assertEquals("", course.teacher)
        assertEquals(1, course.startWeek)
        assertEquals(16, course.endWeek)
    }

    /** 课名本身带数字（大学英语2）时，教室应该选更像门牌号的 A402。 */
    @Test
    fun parse_prefersRoomCodeOverNameEndingWithDigit() {
        val boxes = headerRow() + listOf(
            box("1", 20, 55, w = 20),
            box("大学英语2", 115, 50),
            box("A402", 115, 70),
        )

        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)

        assertEquals(1, result.courses.size)
        assertEquals("大学英语2", result.courses[0].name)
        assertEquals("A402", result.courses[0].location)
    }

    // ---- 纵向课表：教务系统那种"星期是行、节次是列"的表格 ----

    /** 表头：星期/节次 + 上午/下午 + 第X-Y节 时间带。 */
    private fun bandHeader() = listOf(
        box("星期/节次", 60, 205, w = 90),
        box("上午", 250, 190, w = 60),
        box("第1-2节", 250, 222, w = 70),
        box("上午", 450, 190, w = 60),
        box("第3-5节", 450, 222, w = 70),
        box("下午", 650, 190, w = 60),
        box("第6-7节", 650, 222, w = 70),
    )

    private fun dayColumn() = listOf(
        box("一", 30, 300, w = 24),
        box("二", 30, 400, w = 24),
        box("三", 30, 500, w = 24),
        box("四", 30, 600, w = 24),
        box("五", 30, 700, w = 24),
    )

    @Test
    fun parse_verticalTimetable_withBrowserChromeAround() {
        val boxes = listOf(
            // 截图里的浏览器标签、地址栏、状态栏，都不该进网格
            box("12:34", 30, 10, w = 60),
            box("我的课表", 130, 60, w = 90),
            box("华侨大学教务处", 200, 40, w = 150),
            box("jwapp.hqu.edu.cn/jwapp/sys/wdkb", 400, 110, w = 400),
        ) + bandHeader() + dayColumn() + listOf(
            // 周一那一行：格子里的 4 行文字垂直排布在"一"这一行附近
            box("移动软件开发(3学分)", 250, 265, w = 220),
            box("郭武斌", 250, 285, w = 80),
            box("经3-1", 250, 305, w = 70),
            box("1-16周 第1节-第2节", 250, 325, w = 180),
            // 周二那一行
            box("大学英语(2学分)", 450, 365, w = 180),
            box("张老师", 450, 385, w = 80),
            box("外语楼305", 450, 405, w = 110),
            box("2-16周(双) 第3节-第4节", 450, 425, w = 200),
        )

        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)
        assertEquals(2, result.courses.size)

        val mm = result.courses.first { it.name == "移动软件开发" }
        assertEquals(1, mm.dayOfWeek)          // 一
        assertEquals(1, mm.period)             // 第1节
        assertEquals(2, mm.endPeriod)          // 第2节
        assertEquals("经3-1", mm.location)
        assertEquals("郭武斌", mm.teacher)
        assertEquals(1, mm.startWeek)
        assertEquals(16, mm.endWeek)

        val en = result.courses.first { it.name == "大学英语" }
        assertEquals(2, en.dayOfWeek)          // 二
        assertEquals(3, en.period)             // 第3节
        assertEquals(4, en.endPeriod)          // 第4节
        assertEquals("外语楼305", en.location)
        assertEquals(WeekParity.EVEN, en.parity)
        assertEquals(2, en.startWeek)
        assertEquals(16, en.endWeek)
    }

    @Test
    fun parse_verticalTimetable_usesBandWhenTextHasNoPeriod() {
        val boxes = bandHeader() + dayColumn() + listOf(
            box("体育", 450, 300, w = 70),
            box("操场", 450, 330, w = 70),
            box("1-16周", 450, 360, w = 90),
        )

        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)

        assertEquals(1, result.courses.size)
        val pe = result.courses[0]
        assertEquals("体育", pe.name)
        assertEquals("操场", pe.location)
        assertEquals(3, pe.period)   // 时间带"第3-5节"的起点
        assertEquals(5, pe.endPeriod)
    }

    /** 只上某几周的课（华大课表里真有 15、16 周这种写法）。 */
    @Test
    fun parse_keepsIrregularWeekSpec() {
        val boxes = headerRow() + listOf(
            box("3", 20, 55, w = 20),
            box("形势与政策(五)(0.25学分)", 315, 50, w = 240),
            box("徐文福", 315, 70, w = 80),
            box("经302", 315, 90, w = 70),
            box("15,16周 第3节-第4节", 315, 110, w = 200),
        )

        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)

        assertEquals(1, result.courses.size)
        val c = result.courses[0]
        assertEquals("形势与政策(五)", c.name)
        assertEquals("经302", c.location)
        assertEquals("15,16", c.weekSpec)
        assertEquals(15, c.startWeek)
        assertEquals(16, c.endWeek)
    }

    @Test
    fun parse_reportsWarningWhenNoHeader() {        val boxes = listOf(
            box("高等数学", 115, 50),
            box("教三201", 115, 70),
        )
        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)
        assertTrue(result.warnings.isNotEmpty())
        assertEquals(0, result.courses.size)
    }

    @Test
    fun parse_emptyInputIsNotAnError() {
        val result = GridParser.parse(emptyList(), defaultTotalWeeks = 20)
        assertEquals(0, result.courses.size)
        assertTrue(result.warnings.isNotEmpty())
    }

    /**
     * 用手机上真机跑出来的 OCR 原文（带坐标）回放一遍，
     * 保证"教务系统截图 + 浏览器边框 + 缺星期字"这种真实情况不会退化。
     */
    @Test
    fun parse_realJwappScreenshotDump() {
        val text = javaClass.classLoader!!.getResourceAsStream("ocr_dump_real.txt")!!
            .bufferedReader().readText()
        val boxes = text.lineSequence().mapNotNull { line ->
            val m = Regex("^x=(-?\\d+) y=(-?\\d+) \\| (.*)$").find(line) ?: return@mapNotNull null
            val cx = m.groupValues[1].toInt()
            val cy = m.groupValues[2].toInt()
            // 原文只留了中心点，这里按平均字宽还原成一个框
            OcrBox(m.groupValues[3], cx - 40, cy - 10, cx + 40, cy + 10)
        }.toList()
        assertTrue(boxes.size > 50)

        val result = GridParser.parse(boxes, defaultTotalWeeks = 20)
        println("REAL warnings=${result.warnings}")
        result.courses.forEach { println("REAL COURSE: $it") }

        val names = result.courses.map { it.name }
        assertTrue("应该认出移动软件开发，实际=$names", names.any { it.contains("移动软件") })
        assertTrue("应该认出决策支持系统，实际=$names", names.any { it.contains("决策支持") })
        assertTrue("应该认出形势与政策，实际=$names", names.any { it.contains("形势与政策") })
    }
}

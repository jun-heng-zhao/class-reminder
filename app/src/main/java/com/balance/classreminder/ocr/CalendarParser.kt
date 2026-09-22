package com.balance.classreminder.ocr

/**
 * 从"教学校历"图片里读开学第一周是哪天、一共几个教学周。
 * 校历排版五花八门，这里只做保守提取：读不到就让用户自己在设置页填，
 * 绝不猜一个日期悄悄写进去。
 */
object CalendarParser {

    data class Result(
        val firstWeekMonday: String? = null,   // yyyy-MM-dd
        val totalWeeks: Int? = null,
        val hints: List<String> = emptyList(),
    )

    private val WEEK_RANGE_CN = Regex("第\\s*([一二三四五六七八九十]{1,3}|\\d{1,2})\\s*[至~—－-]\\s*([一二三四五六七八九十]{1,3}|\\d{1,2})\\s*周")
    private val DATE_CN = Regex("(\\d{4}\\s*年\\s*)?(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?")
    private val TEACHING = Regex("教学周|教学周\\(|课堂教学|上课时间|学期起止|开学")

    fun parse(boxes: List<OcrBox>, fallbackYear: Int): Result {
        val lines = boxes.filter { it.text.isNotBlank() }
            .sortedWith(compareBy({ it.centerY / 24 }, { it.left }))
            .map { it.text.trim() }

        var totalWeeks: Int? = null
        WEEK_RANGE_CN.find(lines.joinToString(" "))?.let { m ->
            val end = cnNumber(m.groupValues[2])
            if (end != null && end in 1..30) totalWeeks = end
        }

        // 优先看带"教学周"的那几行，从里面找起始日期
        val teachingLines = lines.filter { TEACHING.containsMatchIn(it) }
        val searchPool = if (teachingLines.isNotEmpty()) teachingLines else lines
        val dates = searchPool.flatMap { line ->
            DATE_CN.findAll(line).mapNotNull { m ->
                val year = m.groupValues[1].replace(Regex("[^0-9]"), "").toIntOrNull() ?: fallbackYear
                val month = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val day = m.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                if (month !in 1..12 || day !in 1..31) null
                else java.time.LocalDate.of(year, month, day.coerceAtMost(java.time.YearMonth.of(year, month).lengthOfMonth()))
            }.toList()
        }

        val hints = buildList {
            if (totalWeeks != null) add("读到教学周共 $totalWeeks 周")
            if (dates.isNotEmpty()) add("读到日期：" + dates.joinToString("、") { it.toString() })
            if (teachingLines.isNotEmpty()) add("关键行：" + teachingLines.take(3).joinToString(" / "))
        }

        val first = dates.minOrNull()
        val monday = first?.let { com.balance.classreminder.domain.WeekCalc.mondayOf(it).toString() }
        return Result(monday, totalWeeks, hints)
    }

    /** 中文数字转阿拉伯数字，支持 1..99 的常见写法（十六、二十、二十四）。 */
    fun cnNumber(text: String): Int? {
        val t = text.trim()
        t.toIntOrNull()?.let { return it }
        if (t.isEmpty()) return null
        val digits = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (t == "十") return 10
        if (t.startsWith("十")) return 10 + (digits[t.getOrNull(1)] ?: return null)
        if (t.contains('十')) {
            val parts = t.split('十')
            val tens = digits[parts[0].firstOrNull()] ?: return null
            val ones = parts.getOrNull(1)?.firstOrNull()?.let { digits[it] } ?: 0
            return tens * 10 + ones
        }
        return digits[t.firstOrNull()] ?: null
    }
}

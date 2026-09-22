package com.balance.classreminder.ocr

/** ML Kit 识别出的一个文本行（带像素坐标）。坐标原点在图片左上角。 */
data class OcrBox(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val width: Int get() = (right - left).coerceAtLeast(1)
}

/**
 * 从课表图片里抠出来的一条课，字段已经分类好，但还没经过用户确认。
 */
data class ParsedCourse(
    val dayOfWeek: Int,
    val period: Int,
    val endPeriod: Int,
    val name: String,
    val location: String,
    val teacher: String,
    val startWeek: Int,
    val endWeek: Int,
    val parity: com.balance.classreminder.data.WeekParity,
    val rawText: String,
    val confidence: Float,
    /** 原始周次写法，如 "15,16"（不规则周次要靠它）。 */
    val weekSpec: String = "",
)

data class ParseResult(
    val courses: List<ParsedCourse>,
    val warnings: List<String>,
)

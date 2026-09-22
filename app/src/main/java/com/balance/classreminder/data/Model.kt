package com.balance.classreminder.data

/** 单双周过滤方式。 */
enum class WeekParity { ALL, ODD, EVEN }

/**
 * 一条课程安排。星期与节次都是一次课的固定位置，周次决定它在哪些周出现。
 */
data class Course(
    val id: String,
    val name: String,
    val location: String = "",
    val teacher: String = "",
    val dayOfWeek: Int = 1,      // 1=周一 .. 7=周日
    val startPeriod: Int = 1,    // 第几节开始，1 起
    val endPeriod: Int = 1,      // 第几节结束（含）
    val startWeek: Int = 1,
    val endWeek: Int = 16,
    val parity: WeekParity = WeekParity.ALL,
    val reminderMinutes: Int = -1, // -1 表示跟随全局默认
    val colorIndex: Int = 0,
)

/** 第 n 节的起止时间，用"当天第几分钟"表示，方便算差值。 */
data class PeriodTime(val startMinute: Int, val endMinute: Int) {
    val startLabel: String get() = formatMinute(startMinute)
    val endLabel: String get() = formatMinute(endMinute)

    companion object {
        fun of(startLabel: String, endLabel: String): PeriodTime =
            PeriodTime(parseMinute(startLabel), parseMinute(endLabel))
    }
}

fun formatMinute(minute: Int): String {
    val m = ((minute % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

/** 解析 "8:00" / "08:00" / "800" 这类写法，失败返回 0。 */
fun parseMinute(label: String): Int {
    val t = label.trim()
    if (t.isEmpty()) return 0
    val parts = t.split(':', '：')
    return if (parts.size == 2) {
        val h = parts[0].trim().toIntOrNull() ?: 0
        val m = parts[1].trim().toIntOrNull() ?: 0
        h * 60 + m
    } else {
        val digits = t.filter { it.isDigit() }
        when (digits.length) {
            3 -> (digits.substring(0, 1).toIntOrNull() ?: 0) * 60 + (digits.substring(1).toIntOrNull() ?: 0)
            4 -> (digits.substring(0, 2).toIntOrNull() ?: 0) * 60 + (digits.substring(2).toIntOrNull() ?: 0)
            else -> 0
        }
    }
}

data class AppSettings(
    val termStartDate: String = "",          // ISO yyyy-MM-dd，第一周的周一
    val totalWeeks: Int = 20,
    val defaultReminderMinutes: Int = 20,
    val strongReminder: Boolean = false,     // true=响铃震动并尝试全屏提醒
    val periods: List<PeriodTime> = defaultPeriods(),
)

/** 常见高校作息：上午 4 节、下午 4 节、晚上 4 节。设置页可改。 */
fun defaultPeriods(): List<PeriodTime> = listOf(
    PeriodTime.of("08:00", "08:45"),
    PeriodTime.of("08:55", "09:40"),
    PeriodTime.of("10:00", "10:45"),
    PeriodTime.of("10:55", "11:40"),
    PeriodTime.of("14:00", "14:45"),
    PeriodTime.of("14:55", "15:40"),
    PeriodTime.of("16:00", "16:45"),
    PeriodTime.of("16:55", "17:40"),
    PeriodTime.of("19:00", "19:45"),
    PeriodTime.of("19:55", "20:40"),
    PeriodTime.of("20:50", "21:35"),
    PeriodTime.of("21:45", "22:30"),
)

val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun dayName(dayOfWeek: Int): String = DAY_NAMES.getOrElse(dayOfWeek - 1) { "周?" }

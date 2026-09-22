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
    /**
     * 不规则周次，例如 "15,16" 或 "1-4,6-8" 或 "1-16双"。
     * 非空时以它为准（有些课只在第 15、16 周上，光靠起止周表达不了）。
     */
    val weekSpec: String = "",
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

/** 某一天的例外安排：放假，或者按别的星期几上课（法定调休）。 */
enum class DayKind { NORMAL, HOLIDAY, SWAP }

/**
 * 调休/放假日。国家法定节假日调休经常把周六变成"上周三的课"，
 * 这种日子光靠"第几周+星期几"算不出来，必须逐日覆盖。
 */
data class DayOverride(
    val date: String,                 // yyyy-MM-dd
    val kind: DayKind,
    val swapToDayOfWeek: Int = 1,     // kind=SWAP：这天按星期几的课表上
    val note: String = "",
)

data class AppSettings(
    val termStartDate: String = "",          // ISO yyyy-MM-dd，第一周的周一
    val totalWeeks: Int = 20,
    val defaultReminderMinutes: Int = 20,
    val strongReminder: Boolean = false,     // true=响铃震动并尝试全屏提醒
    val periods: List<PeriodTime> = defaultPeriods(),
    val overrides: List<DayOverride> = emptyList(),
)

/** 华侨大学作息时间表（教务处 2022-08-22 发布），第 1…13 节。设置页可改。 */
fun defaultPeriods(): List<PeriodTime> = listOf(
    PeriodTime.of("08:00", "08:45"),
    PeriodTime.of("08:55", "09:40"),
    PeriodTime.of("10:00", "10:45"),
    PeriodTime.of("10:55", "11:40"),
    PeriodTime.of("11:45", "12:30"),
    PeriodTime.of("14:30", "15:15"),
    PeriodTime.of("15:25", "16:10"),
    PeriodTime.of("16:20", "17:05"),
    PeriodTime.of("17:15", "18:00"),
    PeriodTime.of("18:20", "19:05"),
    PeriodTime.of("19:10", "19:55"),
    PeriodTime.of("20:05", "20:50"),
    PeriodTime.of("20:55", "21:40"),
)

val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun dayName(dayOfWeek: Int): String = DAY_NAMES.getOrElse(dayOfWeek - 1) { "周?" }

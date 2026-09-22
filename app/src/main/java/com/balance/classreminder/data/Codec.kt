package com.balance.classreminder.data

/**
 * 课表落盘格式：每行一条记录，制表符分隔，首行是版本号。
 * 故意不用 JSON/Room —— 数据量极小，纯 Kotlin 实现可以直接跑 JVM 单测。
 */
object Codec {

    private const val VERSION = "v1"

    fun encode(courses: List<Course>, settings: AppSettings): String = buildString {
        appendLine(VERSION)
        appendLine(
            listOf(
                "S",
                esc(settings.termStartDate),
                settings.totalWeeks.toString(),
                settings.defaultReminderMinutes.toString(),
                if (settings.strongReminder) "1" else "0",
            ).joinToString("\t")
        )
        settings.periods.forEach { p ->
            appendLine(listOf("P", p.startMinute.toString(), p.endMinute.toString()).joinToString("\t"))
        }
        courses.forEach { c ->
            appendLine(
                listOf(
                    "C",
                    esc(c.id), esc(c.name), esc(c.location), esc(c.teacher),
                    c.dayOfWeek.toString(), c.startPeriod.toString(), c.endPeriod.toString(),
                    c.startWeek.toString(), c.endWeek.toString(), c.parity.name,
                    c.reminderMinutes.toString(), c.colorIndex.toString(),
                ).joinToString("\t")
            )
        }
    }

    /** 容忍损坏行：解析不了的记录直接跳过，不抛异常。 */
    fun decode(text: String): Pair<List<Course>, AppSettings> {
        var settings = AppSettings()
        val periods = mutableListOf<PeriodTime>()
        val courses = mutableListOf<Course>()
        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r', '\n')
            if (line.isBlank() || line == VERSION) return@forEach
            val f = line.split('\t')
            when (f.getOrNull(0)) {
                "S" -> {
                    settings = settings.copy(
                        termStartDate = unesc(f.getOrNull(1).orEmpty()),
                        totalWeeks = f.getOrNull(2)?.toIntOrNull() ?: settings.totalWeeks,
                        defaultReminderMinutes = f.getOrNull(3)?.toIntOrNull() ?: settings.defaultReminderMinutes,
                        strongReminder = f.getOrNull(4) == "1",
                    )
                }
                "P" -> {
                    val s = f.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val e = f.getOrNull(2)?.toIntOrNull() ?: return@forEach
                    periods += PeriodTime(s, e)
                }
                "C" -> {
                    val id = unesc(f.getOrNull(1).orEmpty())
                    if (id.isEmpty()) return@forEach
                    courses += Course(
                        id = id,
                        name = unesc(f.getOrNull(2).orEmpty()),
                        location = unesc(f.getOrNull(3).orEmpty()),
                        teacher = unesc(f.getOrNull(4).orEmpty()),
                        dayOfWeek = (f.getOrNull(5)?.toIntOrNull() ?: 1).coerceIn(1, 7),
                        startPeriod = (f.getOrNull(6)?.toIntOrNull() ?: 1).coerceAtLeast(1),
                        endPeriod = (f.getOrNull(7)?.toIntOrNull() ?: 1).coerceAtLeast(1),
                        startWeek = (f.getOrNull(8)?.toIntOrNull() ?: 1).coerceAtLeast(1),
                        endWeek = (f.getOrNull(9)?.toIntOrNull() ?: 16).coerceAtLeast(1),
                        parity = runCatching { WeekParity.valueOf(f.getOrNull(10).orEmpty()) }
                            .getOrDefault(WeekParity.ALL),
                        reminderMinutes = f.getOrNull(11)?.toIntOrNull() ?: -1,
                        colorIndex = f.getOrNull(12)?.toIntOrNull() ?: 0,
                    )
                }
            }
        }
        if (periods.isNotEmpty()) settings = settings.copy(periods = periods)
        return courses to settings
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unesc(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    '\\' -> sb.append('\\')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

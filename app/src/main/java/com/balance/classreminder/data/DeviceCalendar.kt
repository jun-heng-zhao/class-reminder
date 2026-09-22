package com.balance.classreminder.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 读手机系统日历里的节假日。
 * 国内手机的日历里通常有「中国法定节假日」这类日历，事件标题写着"休""班"，
 * 拿它自动生成放假/调休，比手动一天天点省事，也跟系统日历保持一致。
 */
object DeviceCalendar {

    data class HolidayEvent(
        val date: LocalDate,
        val title: String,
        val calendarName: String,
        val isWorkday: Boolean,
    )

    private val HOLIDAY_WORDS = listOf("休", "假", "节")
    private val WORKDAY_WORDS = listOf("班", "补", "调休", "上课")

    fun query(context: Context, from: LocalDate, to: LocalDate): List<HolidayEvent> {
        val zone = ZoneId.systemDefault()
        val startMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )

        val out = mutableListOf<HolidayEvent>()
        runCatching {
            context.contentResolver.query(builder.build(), projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val begin = cursor.getLong(0)
                    val title = cursor.getString(1).orEmpty()
                    val calendarName = cursor.getString(2).orEmpty()
                    if (title.isBlank()) continue
                    val date = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                    val isWorkday = WORKDAY_WORDS.any { title.contains(it) } &&
                        HOLIDAY_WORDS.none { title.contains(it) }
                    val isHoliday = HOLIDAY_WORDS.any { title.contains(it) }
                    if (!isWorkday && !isHoliday) continue
                    out += HolidayEvent(date, title, calendarName, isWorkday)
                }
            }
        }
        return out.distinctBy { it.date to it.title }.sortedBy { it.date }
    }
}

package com.balance.classreminder.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

    // 放假事件的标题通常就是「休」「假」「节」，调休上班是「班」「补」；
    // 先判上班词再判放假词 —— 「调休」里带「休」，顺序反了会把调休上班日当成放假
    private val WORKDAY_WORDS = listOf("班", "补", "调休")
    private val HOLIDAY_WORDS = listOf("休", "假", "节")
    private val MAX_TITLE_LENGTH = 8   // 只认短标题，免得把人家自己的「高等数学」当成节假日

    fun query(context: Context, from: LocalDate, to: LocalDate): List<HolidayEvent> {
        val zone = ZoneId.systemDefault()
        val startMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )

        val out = mutableListOf<HolidayEvent>()
        runCatching {
            context.contentResolver.query(builder.build(), projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val begin = cursor.getLong(0)
                    val end = cursor.getLong(1)
                    val allDay = cursor.getInt(2) == 1
                    val title = cursor.getString(3).orEmpty()
                    val calendarName = cursor.getString(4).orEmpty()
                    if (title.isBlank() || title.length > MAX_TITLE_LENGTH) continue
                    val isWorkday = WORKDAY_WORDS.any { title.contains(it) }
                    val isHoliday = !isWorkday && HOLIDAY_WORDS.any { title.contains(it) }
                    if (!isWorkday && !isHoliday) continue
                    // 一个事件可能跨多天（春节连休），逐天标出来才挡得住中间那几天的提醒
                    var day = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                    var lastDay = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
                    if (allDay) lastDay = lastDay.minusDays(1)   // 全天事件的 END 是次日 0 点，不含
                    if (lastDay.isBefore(day)) lastDay = day
                    while (!day.isAfter(lastDay) && ChronoUnit.DAYS.between(day, lastDay) < 31) {
                        out += HolidayEvent(day, title, calendarName, isWorkday)
                        day = day.plusDays(1)
                    }
                }
            }
        }
        return out.distinctBy { it.date to it.title }.sortedBy { it.date }
    }
}

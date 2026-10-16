package com.balance.classreminder.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private data class Range(val from: LocalDate, val to: LocalDate, val zone: ZoneId)
    private val cache = CalendarQueryCache<Range, List<HolidayEvent>>() // 仅保留最近四个范围

    suspend fun query(
        context: Context,
        from: LocalDate,
        to: LocalDate,
        refresh: Boolean = false,
    ): List<HolidayEvent> = withContext(Dispatchers.IO) {
        require(!to.isBefore(from)) { "日历查询结束日期不能早于开始日期" }
        val zone = ZoneId.systemDefault()
        cache.get(Range(from, to, zone), refresh) { read(context, from, to, zone) }
    }

    private fun read(context: Context, from: LocalDate, to: LocalDate, zone: ZoneId): List<HolidayEvent> {
        // 全天事件用 UTC 日期编码，查询边界也覆盖 UTC，避免时区导致漏读。
        val startMillis = minOf(
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val endMillis = maxOf(
            to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )

        val out = mutableListOf<HolidayEvent>()
        val cursor = context.contentResolver.query(builder.build(), projection, null, null, null)
            ?: error("系统日历未返回查询结果，请稍后刷新")
        cursor.use {
            while (cursor.moveToNext()) {
                val begin = cursor.getLong(0)
                val title = cursor.getString(1).orEmpty()
                val calendarName = cursor.getString(2).orEmpty()
                val end = cursor.getLong(3)
                val eventZone = if (cursor.getInt(4) != 0) ZoneOffset.UTC else zone
                val firstDate = Instant.ofEpochMilli(begin).atZone(eventZone).toLocalDate()
                // END 不包含在事件中；减一毫秒可避免把次日零点算作额外一天。
                val lastDate = Instant.ofEpochMilli(if (end > begin) end - 1 else begin)
                    .atZone(eventZone).toLocalDate()
                val isWorkday = WORKDAY_WORDS.any { title.contains(it) }
                val isHoliday = HOLIDAY_WORDS.any { title.contains(it) }
                var date = maxOf(firstDate, from)
                val last = minOf(lastDate, to)
                if (title.isBlank() || (!isWorkday && !isHoliday)) continue
                while (!date.isAfter(last)) {
                    out += HolidayEvent(date, title, calendarName, isWorkday)
                    date = date.plusDays(1)
                }
            }
        }
        return out.distinctBy { it.date to it.title }.sortedBy { it.date }
    }
}

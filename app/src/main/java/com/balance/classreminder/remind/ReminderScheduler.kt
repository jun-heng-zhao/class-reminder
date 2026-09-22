package com.balance.classreminder.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.balance.classreminder.data.Store
import com.balance.classreminder.domain.WeekCalc
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 用 AlarmManager 挂精确闹钟。只排未来 8 天的课，每次响铃/开机/改数据都重排一次，
 * 这样不用常驻后台也不会漏。
 */
object ReminderScheduler {

    private const val ACTION = "com.balance.classreminder.REMIND"
    private const val PREFS = "reminder_pending"
    private const val KEY_CODES = "codes"
    private const val HORIZON_DAYS = 8
    private const val MAX_ALARMS = 64

    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        cancelAll(context)

        val store = Store.get(context)
        val upcoming = WeekCalc.upcoming(store.courses, store.settings, LocalDateTime.now(), HORIZON_DAYS)
            .take(MAX_ALARMS)

        val nowMillis = System.currentTimeMillis()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        val codes = mutableListOf<Int>()

        upcoming.forEach { occ ->
            val minutes = WeekCalc.reminderMinutes(occ.course, store.settings)
            val triggerAt = occ.start.minusMinutes(minutes.toLong())
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // 提醒时刻已经过了就不再补发，避免重排时连环弹通知
            if (triggerAt <= nowMillis) return@forEach

            val code = requestCode(occ.course.id, occ.start.toLocalDate().toEpochDay())
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                intentFor(
                    context = context,
                    courseId = occ.course.id,
                    name = occ.course.name,
                    location = occ.course.location,
                    startLabel = "%02d:%02d".format(occ.start.hour, occ.start.minute),
                    minutes = minutes,
                    strong = store.settings.strongReminder,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                if (exactAllowed) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
                codes += code
            }
        }
        saveCodes(context, codes)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        loadCodes(context).forEach { code ->
            val template = Intent(context, ReminderReceiver::class.java).setAction(ACTION)
            val pi = PendingIntent.getBroadcast(
                context, code, template,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
        saveCodes(context, emptyList())
    }

    private fun intentFor(
        context: Context,
        courseId: String,
        name: String,
        location: String,
        startLabel: String,
        minutes: Int,
        strong: Boolean,
    ) = Intent(context, ReminderReceiver::class.java).apply {
        action = ACTION
        putExtra(ReminderReceiver.EXTRA_COURSE_ID, courseId)
        putExtra(ReminderReceiver.EXTRA_NAME, name)
        putExtra(ReminderReceiver.EXTRA_LOCATION, location)
        putExtra(ReminderReceiver.EXTRA_START, startLabel)
        putExtra(ReminderReceiver.EXTRA_MINUTES, minutes)
        putExtra(ReminderReceiver.EXTRA_STRONG, strong)
    }

    private fun requestCode(courseId: String, epochDay: Long): Int =
        courseId.hashCode() * 31 + epochDay.toInt()

    private fun loadCodes(context: Context): List<Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CODES, "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }

    private fun saveCodes(context: Context, codes: List<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODES, codes.joinToString(","))
            .apply()
    }
}

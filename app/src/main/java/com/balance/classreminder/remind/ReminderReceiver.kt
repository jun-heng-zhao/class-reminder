package com.balance.classreminder.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 闹钟到点：弹通知，然后把后续 8 天的课重排一遍。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val location = intent.getStringExtra(EXTRA_LOCATION).orEmpty()
        val startLabel = intent.getStringExtra(EXTRA_START).orEmpty()
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val strong = intent.getBooleanExtra(EXTRA_STRONG, false)
        val courseId = intent.getStringExtra(EXTRA_COURSE_ID).orEmpty()

        val notificationId = (courseId.hashCode() * 31 + startLabel.hashCode()).let { it and 0x7fffffff }
        Notifier.show(context, notificationId, name, location, startLabel, minutes, strong)

        runCatching { ReminderScheduler.reschedule(context) }
    }

    companion object {
        const val EXTRA_COURSE_ID = "courseId"
        const val EXTRA_NAME = "name"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_START = "start"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_STRONG = "strong"
    }
}

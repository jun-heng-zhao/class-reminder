package com.balance.classreminder.remind

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 收尾提醒：点通知上的「停止响铃」或者直接把通知划掉都走这里 —— 停铃声 + 收掉这条通知。
 * 这样强提醒响了之后不用再杀进程。
 */
class StopAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AlarmPlayer.stop()
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (id != -1) {
            runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(id) }
        }
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}

package com.balance.classreminder.remind

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.balance.classreminder.MainActivity

/** 通知渠道 + 发通知。强提醒走单独的渠道（响铃 + 震动）。 */
object Notifier {

    const val CHANNEL_NORMAL = "class_reminder"
    const val CHANNEL_STRONG = "class_reminder_strong"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val normal = NotificationChannel(
            CHANNEL_NORMAL, "上课提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "课前 N 分钟提醒上课地点"
            enableVibration(true)
        }
        val strong = NotificationChannel(
            CHANNEL_STRONG, "上课强提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "响铃 + 震动，不容易错过"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannel(normal)
        nm.createNotificationChannel(strong)
    }

    fun show(
        context: Context,
        id: Int,
        courseName: String,
        location: String,
        startLabel: String,
        minutes: Int,
        strong: Boolean,
    ) {
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = buildString {
            append(startLabel)
            if (location.isNotBlank()) append(" 在 ").append(location)
            append(" 上课")
            if (minutes > 0) append("，还有 ").append(minutes).append(" 分钟")
        }
        // 「停止响铃」：点了或把通知划掉都走这里，铃声马上停、通知也收掉
        val stop = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, StopAlarmReceiver::class.java)
                .setAction(ACTION_STOP)
                .putExtra(StopAlarmReceiver.EXTRA_NOTIFICATION_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, if (strong) CHANNEL_STRONG else CHANNEL_NORMAL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(courseName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (strong) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            // 划掉通知也要把铃声停掉，不能只剩杀进程这一条路
            .setDeleteIntent(stop)
        if (strong) {
            builder.setVibrate(longArrayOf(0, 600, 300, 600, 300, 600))
            builder.addAction(android.R.drawable.ic_lock_silent_mode, "停止响铃", stop)
        }

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(id, builder.build()) }
        // 强提醒直接自己放铃声：MIUI 上渠道声音经常被系统按"通知"处理，靠渠道响不起来
        if (strong) AlarmPlayer.start(context)
    }

    /** 点开应用、或点通知上的「停止响铃」时调用：把还在响的铃声收掉。 */
    fun stopAlarm() = AlarmPlayer.stop()

    /** 停止响铃的广播 action，通知按钮与划掉通知都用它。 */
    const val ACTION_STOP = "com.balance.classreminder.STOP_ALARM"
}

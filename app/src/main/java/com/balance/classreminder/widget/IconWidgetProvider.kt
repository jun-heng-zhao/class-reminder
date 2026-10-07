package com.balance.classreminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.balance.classreminder.MainActivity
import com.balance.classreminder.R
import com.balance.classreminder.data.IconStore

/**
 * 桌面图标小组件：在桌面上摆一个 1x1 的格子显示用户选的图，点一下进应用。
 *
 * 为什么要用小组件：安卓不允许应用在运行时换掉自己的 launcher 图标（图标必须是编译期资源），
 * 所以「用自己的图当图标」在第三方应用里只能这么做。
 */
class IconWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    companion object {

        private const val ICON_PIXELS = 288   // 1x1 格子够清晰，位图再大就超过 RemoteViews 的传输限制了

        /** 换了图之后刷新已经放到桌面的那些图标。 */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, IconWidgetProvider::class.java))
            ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
        }

        /** 请求把图标固定到桌面；返回 false 说明这个桌面不支持，要去小组件列表里手动加。 */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            val provider = ComponentName(context, IconWidgetProvider::class.java)
            return runCatching { manager.requestPinAppWidget(provider, null, null) }.getOrDefault(false)
        }

        private fun buildViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_app_icon).apply {
                val bitmap = IconStore.loadRounded(context, ICON_PIXELS)
                if (bitmap != null) {
                    setImageViewBitmap(R.id.widget_icon_image, bitmap)
                } else {
                    // 还没选图就先显示自带图标，摆上桌面也不会是空白
                    setImageViewResource(R.id.widget_icon_image, R.mipmap.ic_launcher_round)
                }
                setOnClickPendingIntent(R.id.widget_icon_image, openApp(context))
            }

        private fun openApp(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

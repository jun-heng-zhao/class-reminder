package com.balance.classreminder.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.balance.classreminder.MainActivity
import java.io.File
import java.io.FileOutputStream

/**
 * 自定义图标的图片仓库：把用户选的图存到 filesDir/app_icon.jpg。
 *
 * 安卓不允许应用在运行时替换自己的 launcher 图标（图标必须是编译期资源），
 * 所以这张图交给桌面图标小组件（`widget/IconWidgetProvider.kt`）显示在桌面上，
 * 效果就是「用自己的图当了图标」。
 */
object IconStore {

    private const val FILE_NAME = "app_icon.jpg"
    private const val SHORTCUT_ID = "custom-icon"
    private const val MAX_WIDTH = 512       // 图标用不了多大，512 够清晰又省内存
    private const val QUALITY = 92

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun hasIcon(context: Context): Boolean = file(context).let { it.exists() && it.length() > 0 }

    /** 读出图片；没设过或读失败返回 null。 */
    fun load(context: Context): Bitmap? {
        val target = file(context)
        if (!target.exists()) return null
        return runCatching { BitmapFactory.decodeFile(target.absolutePath) }.getOrNull()
    }

    /** 读成正方形：居中裁一刀再缩到 [size]，免得图标被拉扁。 */
    fun loadSquare(context: Context, size: Int = MAX_WIDTH): Bitmap? {
        val bitmap = load(context) ?: return null
        val side = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        return runCatching {
            Bitmap.createBitmap(bitmap, x, y, side, side).let {
                if (it.width == size) it else Bitmap.createScaledBitmap(it, size, size, true)
            }
        }.getOrNull()
    }

    /** 读成圆角正方形位图，给桌面图标小组件用；圆角比例和主流桌面图标差不多。 */
    fun loadRounded(context: Context, size: Int, cornerFraction: Float = 0.22f): Bitmap? {
        val square = loadSquare(context, size) ?: return null
        return runCatching {
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            val radius = size * cornerFraction
            Canvas(output).drawRoundRect(
                RectF(0f, 0f, size.toFloat(), size.toFloat()),
                radius,
                radius,
                paint,
            )
            output
        }.getOrNull()
    }

    /** 把相册选的图拷进来；成功返回 true。 */
    fun save(context: Context, uri: Uri): Boolean {
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return false
        val scaled = scaleDown(bitmap, MAX_WIDTH)
        return runCatching {
            FileOutputStream(file(context)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            }
            true
        }.getOrDefault(false)
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** 这个桌面支不支持固定快捷方式。 */
    fun canPinShortcut(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * 把当前图片固定成一个桌面图标（快捷方式）。
     * 效果最像真图标：会带应用名，点开进应用。返回 false 表示桌面不支持或还没选图。
     */
    fun pinShortcut(context: Context): Boolean {
        if (!canPinShortcut(context)) return false
        // 图标会按原样显示、不会被系统裁形状，所以先做成圆角方块
        val bitmap = loadRounded(context, 288) ?: return false
        val label = runCatching {
            context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        }.getOrDefault("课表提醒")
        return runCatching {
            val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(IconCompat.createWithBitmap(bitmap))
                // 点这个图标和点原图标一样，直接进主界面
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
                .build()
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            true
        }.getOrDefault(false)
    }

    private fun scaleDown(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val height = (bitmap.height.toFloat() * maxWidth / bitmap.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
    }
}

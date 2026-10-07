package com.balance.classreminder.remind

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper

/**
 * 强提醒的响铃播放器（全局只有一个）。
 *
 * 以前直接在 Notifier 里 new MediaPlayer 循环放 20 秒，播放器没人持有，
 * 用户只能杀进程才能让它闭嘴。现在铃声由这个单例管着：通知上的「停止响铃」、
 * 划掉通知、点开应用都会调 [stop]，最长也只响 [MAX_RING_MILLIS] 就自己停。
 */
object AlarmPlayer {

    private const val MAX_RING_MILLIS = 60_000L   // 响 1 分钟还不停谁都嫌吵，兜个底

    private var player: MediaPlayer? = null                            // 当前播放器，null = 没在响
    private val handler = Handler(Looper.getMainLooper())              // 主线程定时器，负责超时自停
    private val autoStop = Runnable { stop() }                         // 超时回调

    /** 开始响铃。重复调用会先停掉上一条，避免两段铃声叠在一起。 */
    @Synchronized
    fun start(context: Context) {
        stop()
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
            handler.postDelayed(autoStop, MAX_RING_MILLIS)
        }.onFailure { player = null }
    }

    /** 停止响铃并释放播放器。没在响时调它是安全的。 */
    @Synchronized
    fun stop() {
        handler.removeCallbacks(autoStop)
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
    }
}

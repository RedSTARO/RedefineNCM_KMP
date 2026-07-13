package com.leejlredstar.redefinencm.kmp.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.leejlredstar.redefinencm.kmp.util.canPostNotifications

/**
 * 实况歌词通知（Android 平台的 now-playing 歌词面）。
 *
 * 只负责展示歌词文字（原版 LiveUpdateLyricController 形态）——**不带**播放控制按钮：
 * 进度条/封面/上一首/播放/下一首由 Media3 的原生 MediaStyle 媒体通知提供
 * （PlaybackService 的 MediaSession + DefaultMediaNotificationProvider），
 * 两个通知各司其职，与原版行为一致。
 */
actual object LyricNotificationController {
    private const val CHANNEL_ID = "live_update_lyric"
    private const val NOTIFICATION_ID = 0x4C595243 // "LYRC"

    actual val supportsOptionalSurfaceControl: Boolean = true
    actual val optionalSurfaceSettingLabel: String = "启用额外 Live Update 歌词"

    private var latestPayload: AndroidLyricPayload? = null
    private var lastPostedPayload: AndroidLyricPayload? = null
    private var optionalSurfaceEnabled = false
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        ensureChannel(context)
        if (optionalSurfaceEnabled) latestPayload?.let(::postPayload)
    }

    @Synchronized
    actual fun setOptionalSurfaceEnabled(enabled: Boolean) {
        optionalSurfaceEnabled = enabled
        if (enabled) {
            latestPayload?.let(::postPayload)
        } else {
            lastPostedPayload = null
            appContext?.let { NotificationManagerCompat.from(it).cancel(NOTIFICATION_ID) }
        }
    }

    @Synchronized
    actual fun updateLyric(
        title: String?,
        artist: String?,
        currentLyric: String?,
        nextLyric: String?,
        artworkUri: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        val normalizedTitle = title?.trim().orEmpty()
        val lyric = currentLyric?.trim().orEmpty().ifEmpty { normalizedTitle }
        if (lyric.isEmpty()) return
        val payload = AndroidLyricPayload(
            title = normalizedTitle,
            artist = artist?.trim().orEmpty(),
            currentLyric = lyric,
            nextLyric = nextLyric?.trim().orEmpty(),
            artworkUri = artworkUri?.trim().orEmpty(),
            isPlaying = isPlaying,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs,
        )
        latestPayload = payload
        postPayload(payload)
    }

    private fun postPayload(payload: AndroidLyricPayload) {
        if (!optionalSurfaceEnabled) return
        val context = appContext ?: return
        if (!canPostNotifications(context)) return
        if (payload == lastPostedPayload) return

        // 原版 "Use lyric as title in LiveUpdate"：通知标题直接用当前歌词行
        val displayTitle = payload.currentLyric
        val trimmedArtist = payload.artist
        val trimmedNext = payload.nextLyric

        val contentText = buildString {
            if (trimmedArtist.isNotEmpty()) {
                append(trimmedArtist)
                append(" · ")
            }
            append(payload.currentLyric)
        }

        val detailText = buildString {
            append(payload.currentLyric)
            if (trimmedNext.isNotEmpty()) {
                appendLine()
                append(trimmedNext)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (payload.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
            )
            .setContentTitle(displayTitle)
            .setContentText(contentText)
            .setSubText(trimmedArtist.ifEmpty { null })
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(detailText)
                    .setSummaryText(trimmedArtist.ifEmpty { null }),
            )
            .setContentIntent(createNowPlayingPendingIntent(context, requestCode = NOTIFICATION_ID))

        if (payload.durationMs > 0L) {
            val durationSeconds = (payload.durationMs / 1_000L)
                .coerceIn(1L, Int.MAX_VALUE.toLong())
                .toInt()
            val positionSeconds = (payload.positionMs / 1_000L)
                .coerceIn(0L, durationSeconds.toLong())
                .toInt()
            builder.setProgress(durationSeconds, positionSeconds, false)
        }

        if (shouldRequestLiveUpdate(context)) {
            builder.setRequestPromotedOngoing(true)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        lastPostedPayload = payload
    }

    @Synchronized
    actual fun clearFocus() {
        latestPayload = null
        lastPostedPayload = null
        appContext?.let { NotificationManagerCompat.from(it).cancel(NOTIFICATION_ID) }
    }

    @Synchronized
    actual fun reset() {
        latestPayload = null
        lastPostedPayload = null
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live Update Lyric",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows current song lyrics in the notification shade"
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager?.createNotificationChannel(channel)
    }

    private fun shouldRequestLiveUpdate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager?.canPostPromotedNotifications() == true
    }

}

private data class AndroidLyricPayload(
    val title: String,
    val artist: String,
    val currentLyric: String,
    val nextLyric: String,
    val artworkUri: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

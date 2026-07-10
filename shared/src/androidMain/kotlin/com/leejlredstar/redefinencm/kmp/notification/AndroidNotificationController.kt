package com.leejlredstar.redefinencm.kmp.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

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

    @Volatile
    private var lastPayload: AndroidLyricPayload? = null

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureChannel(context)
    }

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
        val context = appContext ?: return
        if (!canPostNotifications(context)) return
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
        if (payload == lastPayload) return

        // 原版 "Use lyric as title in LiveUpdate"：通知标题直接用当前歌词行
        val displayTitle = lyric
        val trimmedArtist = payload.artist
        val trimmedNext = payload.nextLyric

        val contentText = buildString {
            if (trimmedArtist.isNotEmpty()) {
                append(trimmedArtist)
                append(" · ")
            }
            append(lyric)
        }

        val detailText = buildString {
            append(lyric)
            if (trimmedNext.isNotEmpty()) {
                appendLine()
                append(trimmedNext)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (payload.isPlaying) android.R.drawable.ic_media_play
                else android.R.drawable.ic_media_pause,
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
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    context.packageManager
                        .getLaunchIntentForPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

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
        lastPayload = payload
    }

    actual fun clearFocus() {
        val context = appContext ?: return
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        lastPayload = null
    }

    actual fun reset() {
        lastPayload = null
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

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
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

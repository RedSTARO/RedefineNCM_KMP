package com.leejlredstar.redefinencm.kmp.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.leejlredstar.redefinencm.kmp.i18n.AppLanguage
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.util.canPostNotifications

/**
 * 实况歌词通知（Android 平台的 now-playing 歌词面）。
 *
 * 只负责展示歌词文字（原版 LiveUpdateLyricController 形态），**不带**播放控制按钮：
 * 进度条/封面/上一首/播放/下一首由 Media3 的原生 MediaStyle 媒体通知提供
 * （PlaybackService 的 MediaSession + DefaultMediaNotificationProvider），
 * 两个通知各司其职，与原版行为一致。
 *
 * An [OptionalLyricSurface]: Settings can switch this one off, and the Media3 notification
 * stays either way.
 */
object AndroidLyricNotification : OptionalLyricSurface {
    private const val CHANNEL_ID = "live_update_lyric"
    private const val NOTIFICATION_ID = 0x4C595243 // "LYRC"

    override val settingLabel: String get() = strings.enableLiveUpdateLyrics

    private var latestPayload: LyricPayload? = null
    private var lastPostedPayload: LyricPayload? = null
    private var optionalSurfaceEnabled = false
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        ensureChannel(context)
        if (optionalSurfaceEnabled) latestPayload?.let(::postPayload)
    }

    @Synchronized
    override fun setEnabled(enabled: Boolean) {
        optionalSurfaceEnabled = enabled
        if (enabled) {
            latestPayload?.let(::postPayload)
        } else {
            lastPostedPayload = null
            appContext?.let { NotificationManagerCompat.from(it).cancel(NOTIFICATION_ID) }
        }
    }

    @Synchronized
    override fun updateLyric(
        title: String?,
        artist: String?,
        currentLyric: String?,
        nextLyric: String?,
        artworkUri: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        val payload = lyricPayloadOf(
            title = title,
            artist = artist,
            currentLyric = currentLyric,
            nextLyric = nextLyric,
            artworkUri = artworkUri,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
        ).asSingleLineSurface() ?: return
        latestPayload = payload
        postPayload(payload)
    }

    private fun postPayload(payload: LyricPayload) {
        if (!optionalSurfaceEnabled) return
        val context = appContext ?: return
        if (!canPostNotifications(context)) return
        if (payload == lastPostedPayload) return

        // 原版 "Use lyric as title in LiveUpdate"：通知标题直接用当前歌词行
        val displayTitle = payload.currentLyric
        val trimmedArtist = payload.artist
        val trimmedNext = payload.nextLyric

        // The line under the lyric names the song. When the title already stands in for a missing
        // lyric line, the artist does instead; repeating the headline told the user nothing.
        val contentText = notificationContentText(payload)
        val detailText = listOf(contentText, trimmedNext).filter(String::isNotEmpty).joinToString("\n")

        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (payload.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
            )
            .setContentTitle(displayTitle)
            .setContentText(contentText.ifEmpty { null })
            .setSubText(trimmedArtist.ifEmpty { null })
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setContentIntent(createNowPlayingPendingIntent(context, requestCode = NOTIFICATION_ID))
        if (trimmedNext.isNotEmpty()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(detailText)
                    .setSummaryText(trimmedArtist.ifEmpty { null }),
            )
        }

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
    override fun clearFocus() {
        latestPayload = null
        lastPostedPayload = null
        appContext?.let { NotificationManagerCompat.from(it).cancel(NOTIFICATION_ID) }
    }

    @Synchronized
    override fun reset() {
        latestPayload = null
        lastPostedPayload = null
    }

    private var channelLanguage: AppLanguage? = null

    /**
     * Creates the channel, or renames it after a language switch: creating a channel that exists
     * only updates its name and description, and keeps the settings the user gave it.
     *
     * Channels arrived in Android 8.0. Below it the class does not exist, and init() runs from
     * Application.onCreate, so building one there would crash every launch on Android 7.x.
     */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val language = I18n.language
        if (channelLanguage == language) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        channelLanguage = language

        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.lyricChannelName,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = strings.lyricChannelDescription
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun shouldRequestLiveUpdate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager?.canPostPromotedNotifications() == true
    }

}

/**
 * The text under the lyric headline: the song's title, or the artist when the title is already
 * the headline because the track has no lyric line at this moment.
 */
internal fun notificationContentText(payload: LyricPayload): String =
    if (payload.currentLyric == payload.title) payload.artist else payload.title

actual val lyricSurface: LyricSurface = AndroidLyricNotification

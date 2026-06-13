package com.leejlredstar.redefinencm.kmp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.player.ExoPlayerPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import org.koin.android.ext.android.get

/**
 * Foreground service for background media playback.
 *
 * Android requires [startForeground] to be called within seconds of receiving a
 * [android.content.Context.startForegroundService] intent — otherwise the service is killed.
 * Media3's DefaultMediaNotificationProvider then takes over: when the player transitions to a
 * ready/playing state it calls [startForeground] with a notification that includes play/pause,
 * prev/next transport buttons and a seekbar.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = (get<PlatformPlayer>() as ExoPlayerPlatformPlayer).exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("RedefineNCM")
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        LyricNotificationController.clearFocus()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Music playback controls"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "media_playback"
        private const val NOTIFICATION_ID = 1
    }
}

package com.leejlredstar.redefinencm.kmp

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.player.ExoPlayerPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import org.koin.android.ext.android.get

/**
 * Foreground MediaSessionService that wraps the ExoPlayer singleton from Koin in a
 * MediaSession, enabling OS media controls (lock screen, notification, Bluetooth).
 *
 * ExoPlayer is owned by [ExoPlayerPlatformPlayer] as a Koin singleton for the app lifetime;
 * this service only owns the MediaSession, which is created in onCreate and released in
 * onDestroy. Releasing the session does NOT release ExoPlayer.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = (get<PlatformPlayer>() as ExoPlayerPlatformPlayer).exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        LyricNotificationController.clearFocus()
        super.onDestroy()
    }
}

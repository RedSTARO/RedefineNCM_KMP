package com.leejlredstar.redefinencm.kmp

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.player.ExoPlayerPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import org.koin.android.ext.android.get

/**
 * Foreground MediaSessionService for OS media controls and notification.
 *
 * Media3's DefaultMediaNotificationProvider auto-creates a media notification with album art,
 * title, artist, and transport controls when the player is active.
 * The service is started from MainActivity and goes foreground automatically via the provider.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = (get<PlatformPlayer>() as ExoPlayerPlatformPlayer).exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                // Media3's notification provider handles startForeground automatically
                // when the player state changes. This listener just keeps the service alive.
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        LyricNotificationController.clearFocus()
        super.onDestroy()
    }
}

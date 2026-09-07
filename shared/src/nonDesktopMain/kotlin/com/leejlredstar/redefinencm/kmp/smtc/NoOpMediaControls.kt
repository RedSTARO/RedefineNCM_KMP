package com.leejlredstar.redefinencm.kmp.smtc

/**
 * Android, iOS and the browser drive the OS transport from inside their own players.
 *
 * ExoPlayer keeps its MediaSession, `IosAVPlayer` writes `MPNowPlayingInfoCenter` and installs
 * `MPRemoteCommandCenter` targets, and `WebPlatformPlayer` writes the Media Session and installs
 * its action handlers. All three hold the playback state first-hand, so routing it back out
 * through the view model would publish it a second time and later.
 */
internal object NoOpMediaControlsSink : MediaControlsSink {
    override fun updateMetadata(
        title: String?,
        artist: String?,
        album: String?,
        artworkUri: String?,
        duration: Long?,
        position: Long?,
        isPlaying: Boolean?,
    ) = Unit

    override fun clear() = Unit
}

actual val osMediaControls: MediaControlsSink = NoOpMediaControlsSink

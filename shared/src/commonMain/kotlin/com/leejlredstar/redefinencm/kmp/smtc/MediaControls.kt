package com.leejlredstar.redefinencm.kmp.smtc

/**
 * Platform-agnostic now-playing metadata for OS media controls
 * (Windows SMTC, macOS MPNowPlayingInfoCenter, Linux MPRIS, Android MediaSession).
 */
data class MediaControlMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String = "",
    val duration: Long = 0,
    val position: Long = 0,
    val isPlaying: Boolean = false,
)

/**
 * Where the playback layer sends what the OS transport should show.
 *
 * Only the desktop has one. Android's MediaSession, iOS's `MPNowPlayingInfoCenter` and the
 * browser's Media Session are all driven from inside those targets' players, which hold the
 * state first-hand; the desktop's JVM player has no OS transport of its own, so a separate
 * binding observes this instead.
 *
 * It used to be one shared object that every target wrote to on every position tick while only
 * the desktop read it, so three targets allocated and published a metadata value per tick that
 * nothing consumed. The three that publish from inside their players now have a sink that does
 * nothing, and it is a type rather than a comment nobody was reading.
 *
 * Every argument is null-for-unchanged, so a caller can update the position without restating
 * the track.
 */
interface MediaControlsSink {
    fun updateMetadata(
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        artworkUri: String? = null,
        duration: Long? = null,
        position: Long? = null,
        isPlaying: Boolean? = null,
    )

    /** Nothing is playing: take the entry out of the OS transport. */
    fun clear()
}

/** This target's OS transport, if it has one fed from here. */
expect val osMediaControls: MediaControlsSink

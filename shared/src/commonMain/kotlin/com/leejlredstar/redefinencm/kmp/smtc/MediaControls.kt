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
 * The three targets that publish from inside their players have a sink that does nothing. A
 * single shared object that every target writes on every position tick, read only by the
 * desktop, would make those three allocate and publish a metadata value per tick that nothing
 * consumes.
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

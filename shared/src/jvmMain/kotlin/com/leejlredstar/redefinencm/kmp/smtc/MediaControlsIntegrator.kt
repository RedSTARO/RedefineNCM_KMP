package com.leejlredstar.redefinencm.kmp.smtc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The desktop's channel between the playback layer and whichever native transport this host has.
 *
 * The JVM player has no OS transport of its own, so `DesktopMediaControls` picks a backend for
 * the host — Windows SMTC, MPRIS on Linux, `MPNowPlayingInfoCenter` on macOS — and each of them
 * observes [metadata] and pushes it out. The other three targets have no equivalent: their
 * players talk to the OS directly, so their [MediaControlsSink] does nothing.
 */
object MediaControlsIntegrator : MediaControlsSink {
    private val _metadata = MutableStateFlow(MediaControlMetadata())
    val metadata: StateFlow<MediaControlMetadata> = _metadata.asStateFlow()

    override fun updateMetadata(
        title: String?,
        artist: String?,
        album: String?,
        artworkUri: String?,
        duration: Long?,
        position: Long?,
        isPlaying: Boolean?,
    ) {
        val current = _metadata.value
        _metadata.value = MediaControlMetadata(
            title = title ?: current.title,
            artist = artist ?: current.artist,
            album = album ?: current.album,
            artworkUri = artworkUri ?: current.artworkUri,
            duration = duration ?: current.duration,
            position = position ?: current.position,
            isPlaying = isPlaying ?: current.isPlaying,
        )
    }

    override fun clear() {
        _metadata.value = MediaControlMetadata()
    }
}

actual val osMediaControls: MediaControlsSink = MediaControlsIntegrator

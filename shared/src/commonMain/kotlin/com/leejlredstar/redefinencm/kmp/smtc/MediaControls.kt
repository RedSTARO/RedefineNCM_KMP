package com.leejlredstar.redefinencm.kmp.smtc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * Shared state holder feeding OS media-control integrations. The playback layer calls
 * [updateMetadata] (see `NowPlayingViewModel`); each platform's media-controls binding observes
 * [metadata] and pushes it to the OS. The data model is platform-agnostic — only the OS push is
 * platform-specific (e.g. `jvmMain/smtc/WindowsMediaControls`).
 */
object MediaControlsIntegrator {
    private val _metadata = MutableStateFlow(MediaControlMetadata())
    val metadata: StateFlow<MediaControlMetadata> = _metadata.asStateFlow()

    fun updateMetadata(
        title: String = _metadata.value.title,
        artist: String = _metadata.value.artist,
        album: String = _metadata.value.album,
        artworkUri: String = _metadata.value.artworkUri,
        duration: Long = _metadata.value.duration,
        position: Long = _metadata.value.position,
        isPlaying: Boolean = _metadata.value.isPlaying,
    ) {
        _metadata.value = MediaControlMetadata(
            title = title,
            artist = artist,
            album = album,
            artworkUri = artworkUri,
            duration = duration,
            position = position,
            isPlaying = isPlaying,
        )
    }

    fun clear() {
        _metadata.value = MediaControlMetadata()
    }
}

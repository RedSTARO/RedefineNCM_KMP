package com.leejlredstar.redefinencm.kmp.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop actual implementation of LyricNotificationController.
 *
 * Instead of a notification, this drives a floating desktop lyrics window.
 * The window is created by the desktop app's main.kt using Compose Desktop.
 * This controller manages the state that the window renders.
 */
actual object LyricNotificationController {
    private val _floatingLyricData = MutableStateFlow<FloatingLyricData?>(null)
    val floatingLyricData: StateFlow<FloatingLyricData?> = _floatingLyricData.asStateFlow()

    private val _isWindowVisible = MutableStateFlow(false)
    val isWindowVisible: StateFlow<Boolean> = _isWindowVisible.asStateFlow()
    private var currentTrackKey: String? = null
    private var dismissedTrackKey: String? = null

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
        val data = FloatingLyricData(
            title = title?.trim().orEmpty(),
            artist = artist?.trim().orEmpty(),
            currentLyric = currentLyric?.trim().orEmpty(),
            nextLyric = nextLyric?.trim().orEmpty(),
            artworkUri = artworkUri?.trim().orEmpty(),
            isPlaying = isPlaying,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs,
        )
        if (_floatingLyricData.value == data) return
        _floatingLyricData.value = data
        val trackKey = "${data.title}\u0000${data.artist}\u0000${data.artworkUri}"
        if (trackKey != currentTrackKey) currentTrackKey = trackKey
        if (dismissedTrackKey != trackKey) _isWindowVisible.value = true
    }

    actual fun clearFocus() {
        _floatingLyricData.value = null
        _isWindowVisible.value = false
        currentTrackKey = null
        dismissedTrackKey = null
    }

    actual fun reset() {
        _floatingLyricData.value = null
        _isWindowVisible.value = false
        currentTrackKey = null
        dismissedTrackKey = null
    }

    fun show() {
        dismissedTrackKey = null
        _isWindowVisible.value = true
    }
    fun hide() {
        dismissedTrackKey = currentTrackKey
        _isWindowVisible.value = false
    }
    fun toggle() { if (_isWindowVisible.value) hide() else show() }
}

data class FloatingLyricData(
    val title: String,
    val artist: String,
    val currentLyric: String,
    val nextLyric: String,
    val artworkUri: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

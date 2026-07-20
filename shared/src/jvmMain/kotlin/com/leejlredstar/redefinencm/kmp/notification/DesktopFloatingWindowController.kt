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
    actual val supportsOptionalSurfaceControl: Boolean = true
    actual val optionalSurfaceSettingLabel: String = "启用桌面歌词"

    private val _floatingLyricData = MutableStateFlow<FloatingLyricData?>(null)
    val floatingLyricData: StateFlow<FloatingLyricData?> = _floatingLyricData.asStateFlow()

    private val _playbackProgress = MutableStateFlow(FloatingLyricProgress())
    val playbackProgress: StateFlow<FloatingLyricProgress> = _playbackProgress.asStateFlow()

    private val _isWindowVisible = MutableStateFlow(false)
    val isWindowVisible: StateFlow<Boolean> = _isWindowVisible.asStateFlow()
    private var currentTrackKey: String? = null
    private var dismissedTrackKey: String? = null
    private var optionalSurfaceEnabled = false
    private var latestLyricData: FloatingLyricData? = null
    private var latestProgress = FloatingLyricProgress()

    @Synchronized
    actual fun setOptionalSurfaceEnabled(enabled: Boolean) {
        optionalSurfaceEnabled = enabled
        if (enabled) {
            latestLyricData?.let { publish(it, latestProgress) }
        } else {
            clearDisplayedState()
        }
    }

    @Synchronized
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
        )
        latestLyricData = data
        latestProgress = FloatingLyricProgress(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs,
        )
        if (!optionalSurfaceEnabled) return
        publish(data, latestProgress)
    }

    private fun publish(data: FloatingLyricData, progress: FloatingLyricProgress = latestProgress) {
        // Position advances every 100 ms on JVM. Keep that high-frequency state out of the
        // metadata payload so Compose only redraws the progress indicator instead of the whole
        // floating window (artwork, gradients, lyric transitions and controls).
        if (_floatingLyricData.value != data) _floatingLyricData.value = data
        if (_playbackProgress.value != progress) _playbackProgress.value = progress
        val trackKey = "${data.title}\u0000${data.artist}\u0000${data.artworkUri}"
        if (trackKey != currentTrackKey) currentTrackKey = trackKey
        if (dismissedTrackKey != trackKey) _isWindowVisible.value = true
    }

    @Synchronized
    actual fun clearFocus() {
        latestLyricData = null
        latestProgress = FloatingLyricProgress()
        clearDisplayedState()
    }

    @Synchronized
    actual fun reset() {
        latestLyricData = null
        latestProgress = FloatingLyricProgress()
        clearDisplayedState()
    }

    private fun clearDisplayedState() {
        _floatingLyricData.value = null
        _playbackProgress.value = FloatingLyricProgress()
        _isWindowVisible.value = false
        currentTrackKey = null
        dismissedTrackKey = null
    }

    @Synchronized
    fun show() {
        if (!optionalSurfaceEnabled || _floatingLyricData.value == null) return
        dismissedTrackKey = null
        _isWindowVisible.value = true
    }
    @Synchronized
    fun hide() {
        dismissedTrackKey = currentTrackKey
        _isWindowVisible.value = false
    }
    @Synchronized
    fun toggle() { if (_isWindowVisible.value) hide() else show() }
}

data class FloatingLyricData(
    val title: String,
    val artist: String,
    val currentLyric: String,
    val nextLyric: String,
    val artworkUri: String,
    val isPlaying: Boolean,
)

data class FloatingLyricProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = -1L,
) {
    val fraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

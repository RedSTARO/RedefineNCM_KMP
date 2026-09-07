package com.leejlredstar.redefinencm.kmp.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The desktop's lyric surface: a floating always-on-top window.
 *
 * Instead of a notification, this drives a floating desktop lyrics window. The window is created
 * by the desktop app's main.kt using Compose Desktop; this object holds the state it renders.
 *
 * The only [WindowedLyricSurface] — it is the one surface with a position and a layout of its
 * own, which is why locking and alignment are its interface rather than members every target has
 * to answer for. Its window state below is desktop-only and deliberately not on any interface.
 */
object DesktopLyricWindow : WindowedLyricSurface {
    override val settingLabel: String = "显示桌面歌词"

    private val _floatingLyricData = MutableStateFlow<FloatingLyricData?>(null)
    val floatingLyricData: StateFlow<FloatingLyricData?> = _floatingLyricData.asStateFlow()

    private val _playbackProgress = MutableStateFlow(FloatingLyricProgress())
    val playbackProgress: StateFlow<FloatingLyricProgress> = _playbackProgress.asStateFlow()

    private val _isWindowVisible = MutableStateFlow(false)
    val isWindowVisible: StateFlow<Boolean> = _isWindowVisible.asStateFlow()

    private val _isWindowLocked = MutableStateFlow(false)
    /** Whether the window ignores the pointer and stays where it is. Settings toggles it. */
    val isWindowLocked: StateFlow<Boolean> = _isWindowLocked.asStateFlow()

    private val _windowAlignment = MutableStateFlow(LyricSurfaceAlignment.DEFAULT)
    /** How the two lyric lines sit inside the window. */
    val windowAlignment: StateFlow<LyricSurfaceAlignment> = _windowAlignment.asStateFlow()
    private var currentTrackKey: String? = null
    private var dismissedTrackKey: String? = null
    private var optionalSurfaceEnabled = false
    private var latestLyricData: FloatingLyricData? = null
    private var latestProgress = FloatingLyricProgress()

    @Synchronized
    override fun setEnabled(enabled: Boolean) {
        optionalSurfaceEnabled = enabled
        if (enabled) {
            latestLyricData?.let { publish(it, latestProgress) }
        } else {
            clearDisplayedState()
        }
    }

    override fun setLocked(locked: Boolean) {
        _isWindowLocked.value = locked
    }

    override fun setAlignment(alignment: LyricSurfaceAlignment) {
        _windowAlignment.value = alignment
    }

    @Synchronized
    override fun updateLyric(
        title: String?,
        artist: String?,
        currentLyric: String?,
        nextLyric: String?,
        artworkUri: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        // Deliberately not asSingleLineSurface(): this window draws the title, the artist and
        // the lyric on separate lines, so substituting the title for a blank lyric line would
        // show it twice. A blank line between lyrics is what the window is supposed to show.
        val payload = lyricPayloadOf(
            title = title,
            artist = artist,
            currentLyric = currentLyric,
            nextLyric = nextLyric,
            artworkUri = artworkUri,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
        )
        val data = FloatingLyricData(
            title = payload.title,
            artist = payload.artist,
            currentLyric = payload.currentLyric,
            nextLyric = payload.nextLyric,
            artworkUri = payload.artworkUri,
            isPlaying = payload.isPlaying,
        )
        latestLyricData = data
        latestProgress = FloatingLyricProgress(
            positionMs = payload.positionMs,
            durationMs = payload.durationMs,
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
    override fun clearFocus() {
        latestLyricData = null
        latestProgress = FloatingLyricProgress()
        clearDisplayedState()
    }

    @Synchronized
    override fun reset() {
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

actual val lyricSurface: LyricSurface = DesktopLyricWindow

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

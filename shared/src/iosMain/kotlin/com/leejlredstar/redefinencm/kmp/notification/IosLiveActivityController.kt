package com.leejlredstar.redefinencm.kmp.notification

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.posix.time

/**
 * iOS lyric surface.
 *
 * On iOS, this bridges to ActivityKit Live Activities for 灵动岛 (Dynamic Island)
 * and Lock Screen lyric display via a shared data mechanism.
 *
 * The actual ActivityKit interaction is done from Swift code in the iOS app target.
 * This Kotlin object owns the lyric data and exposes a Swift-friendly observer bridge.
 *
 * Architectural note (implemented):
 * - Kotlin owns the lyric data as a StateFlow and exposes [startObserving]/[stopObserving].
 * - The Swift `LiveActivityManager` (main app) observes it and drives ActivityKit:
 *   `Activity.request` / `activity.update(ContentState)` / `activity.end`.
 * - The `LyricWidget` extension renders the ContentState on the Lock Screen + Dynamic Island.
 * - Text flows via ActivityKit ContentState (no App Group needed). Album artwork inside the
 *   Live Activity would require App-Group image caching (TODO).
 */
/**
 * iOS's lyric surface: a Live Activity on the Lock Screen and in the Dynamic Island.
 *
 * Plain [LyricSurface]. ActivityKit owns whether it appears, so there is nothing for Settings to
 * switch and it had carried six stub members saying so.
 */
object IosLiveActivity : LyricSurface {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _liveActivityData = MutableStateFlow<LiveActivityData?>(null)
    val liveActivityData: StateFlow<LiveActivityData?> = _liveActivityData.asStateFlow()

    private var observerJob: Job? = null
    private var lastPayload: LyricPayload? = null

    /**
     * Swift bridge: the Swift `LiveActivityManager` calls this once at startup. [onChange] fires
     * on the main thread with each new value (null = clear / end the Live Activity). Replaces any
     * previously registered observer.
     */
    fun startObserving(onChange: (LiveActivityData?) -> Unit) {
        observerJob?.cancel()
        observerJob = scope.launch {
            liveActivityData.collect { onChange(it) }
        }
    }

    /** Stop the Swift observer started by [startObserving]. */
    fun stopObserving() {
        observerJob?.cancel()
        observerJob = null
    }

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
        val payload = lyricPayloadOf(
            title = title,
            artist = artist,
            currentLyric = currentLyric,
            nextLyric = nextLyric,
            artworkUri = artworkUri,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
        ).asSingleLineSurface() ?: return
        if (payload == lastPayload) return
        lastPayload = payload

        _liveActivityData.value = LiveActivityData(
            title = payload.title,
            artist = payload.artist,
            currentLyric = payload.currentLyric,
            nextLyric = payload.nextLyric,
            artworkUri = payload.artworkUri,
            isPlaying = payload.isPlaying,
            positionMs = payload.positionMs,
            durationMs = payload.durationMs,
            timestamp = currentTimeMillis(),
        )
    }

    override fun clearFocus() {
        lastPayload = null
        _liveActivityData.value = null
    }

    override fun reset() {
        lastPayload = null
        _liveActivityData.value = null
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    private fun currentTimeMillis(): Long = time(null) * 1000L
}

/**
 * Data class for Live Activity content.
 * Mirrored in Swift as a Codable struct for ActivityKit.
 */
actual val lyricSurface: LyricSurface = IosLiveActivity

data class LiveActivityData(
    val title: String,
    val artist: String,
    val currentLyric: String,
    val nextLyric: String,
    val artworkUri: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long,
)



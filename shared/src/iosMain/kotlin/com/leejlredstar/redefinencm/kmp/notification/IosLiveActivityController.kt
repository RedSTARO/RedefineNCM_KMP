package com.leejlredstar.redefinencm.kmp.notification

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS actual implementation of LyricNotificationController.
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
actual object LyricNotificationController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _liveActivityData = MutableStateFlow<LiveActivityData?>(null)
    val liveActivityData: StateFlow<LiveActivityData?> = _liveActivityData.asStateFlow()

    private var observerJob: Job? = null

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

    actual fun updateLyric(
        title: String?,
        artist: String?,
        currentLyric: String?,
        nextLyric: String?,
        artworkUri: String?,
    ) {
        val lyric = currentLyric?.trim().takeUnless { it.isNullOrEmpty() } ?: return

        _liveActivityData.value = LiveActivityData(
            title = title ?: "",
            artist = artist ?: "",
            currentLyric = lyric,
            nextLyric = nextLyric?.trim().orEmpty(),
            artworkUri = artworkUri ?: "",
            timestamp = currentTimeMillis(),
        )
    }

    actual fun clearFocus() {
        _liveActivityData.value = null
    }

    actual fun reset() {
        _liveActivityData.value = null
    }

    private fun currentTimeMillis(): Long {
        return platform.Foundation.NSDate().timeIntervalSince1970.toLong() * 1000
    }
}

/**
 * Data class for Live Activity content.
 * Mirrored in Swift as a Codable struct for ActivityKit.
 */
data class LiveActivityData(
    val title: String,
    val artist: String,
    val currentLyric: String,
    val nextLyric: String,
    val artworkUri: String,
    val timestamp: Long,
)

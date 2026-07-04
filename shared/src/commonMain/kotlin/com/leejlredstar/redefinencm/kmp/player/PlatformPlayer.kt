package com.leejlredstar.redefinencm.kmp.player

import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-platform music player abstraction.
 * Each platform provides an actual implementation.
 */
interface PlatformPlayer {

    /** Current player state. */
    val state: StateFlow<PlayerState>

    /** Current playback position in milliseconds. */
    val position: StateFlow<Long>

    /** Whether audio is currently playing. */
    val isPlaying: StateFlow<Boolean>

    /** Total song duration in milliseconds (-1 if unknown). */
    val duration: StateFlow<Long>

    /** Current media item metadata. */
    val currentMedia: StateFlow<MediaInfo?>

    /** The current play queue. */
    val queue: StateFlow<List<MediaInfo>>

    /** Index of the current item in the queue. */
    val currentIndex: StateFlow<Int>

    /** Whether shuffle mode is enabled. */
    val shuffleEnabled: StateFlow<Boolean>

    // ── Playback control ──

    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun seekToPrevious()
    fun seekToNext()

    // ── Queue management ──

    fun setQueue(items: List<MediaInfo>, startIndex: Int = 0)
    fun addToQueue(item: MediaInfo)
    fun clearQueue()
    fun skipToIndex(index: Int)

    /**
     * 恢复上次的播放状态：装载队列并 seek 到上次位置，但**不**自动开始播放
     * （与原版 restorePlayerStatus 行为一致）。平台实现可覆写以避免默认的先播再暂停。
     */
    fun restoreQueue(items: List<MediaInfo>, startIndex: Int, positionMs: Long) {
        setQueue(items, startIndex)
        pause()
        seekTo(positionMs)
    }

    // ── Shuffle ──

    fun setShuffleEnabled(enabled: Boolean)

    // ── Lifecycle ──

    fun release()
}

enum class PlayerState {
    IDLE,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

data class MediaInfo(
    val id: String,
    val title: String,
    val artist: String,
    val albumTitle: String = "",
    val artworkUri: String = "",
    val placeholderUri: String = "",   // redefinencm://playbackPlaceHolder?id=xxx
    val duration: Long = 0,
)

/**
 * Called when a placeholder URI needs to be resolved to a real stream URL.
 */
fun interface StreamUrlResolver {
    suspend fun resolve(mediaId: String): String?
}

package com.leejlredstar.redefinencm.kmp.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * In-memory [PlatformPlayer] with **no real audio output**.
 *
 * It is the shared reference implementation of the player contract: queue + shuffle are managed
 * by the unit-tested [PlayQueue], play/pause and a *simulated* position are tracked, and every
 * StateFlow the ViewModels/UI consume is emitted. This lets the whole DI graph + Compose UI run
 * on every target before the native audio backends (Android media3, iOS AVPlayer, JVM audio)
 * exist.
 *
 * Real platform players replace it by binding their own [PlatformPlayer] in `platformModule()`
 * (and removing this default from `sharedModule`, or loading with Koin override). They should
 * still delegate ordering to [PlayQueue] so the shuffle invariant cannot regress.
 *
 * Uses [Dispatchers.Default] for the position ticker (not `Main`) so it has no platform main-
 * dispatcher dependency; StateFlow reads/writes are thread-safe.
 */
class InMemoryPlatformPlayer(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : PlatformPlayer {

    private var queueModel: PlayQueue<MediaInfo> = PlayQueue.empty()

    private val _state = MutableStateFlow(PlayerState.IDLE)
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    override val position: StateFlow<Long> = _position.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _duration = MutableStateFlow(-1L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentMedia = MutableStateFlow<MediaInfo?>(null)
    override val currentMedia: StateFlow<MediaInfo?> = _currentMedia.asStateFlow()

    private val _queue = MutableStateFlow<List<MediaInfo>>(emptyList())
    override val queue: StateFlow<List<MediaInfo>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private var ticker: Job? = null

    /** Mirror the [PlayQueue] state into the public StateFlows (single source of truth). */
    private fun publishQueue() {
        _queue.value = queueModel.items
        _currentIndex.value = queueModel.currentIndex.coerceAtLeast(0)
        _currentMedia.value = queueModel.currentItem
        _shuffleEnabled.value = queueModel.shuffleEnabled
        _duration.value = queueModel.currentItem?.duration?.takeIf { it > 0 } ?: -1L
    }

    override fun play() {
        if (queueModel.currentItem == null) return
        _isPlaying.value = true
        _state.value = PlayerState.PLAYING
        startTicker()
    }

    override fun pause() {
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        stopTicker()
    }

    override fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        val dur = _duration.value
        _position.value = if (dur > 0) positionMs.coerceIn(0, dur) else positionMs.coerceAtLeast(0)
    }

    override fun seekToPrevious() {
        queueModel = queueModel.previous()
        onTrackChanged()
    }

    override fun seekToNext() {
        queueModel = queueModel.next()
        onTrackChanged()
    }

    override fun setQueue(items: List<MediaInfo>, startIndex: Int) {
        queueModel = PlayQueue.of(items, startIndex)
        onTrackChanged()
    }

    override fun addToQueue(item: MediaInfo) {
        queueModel = queueModel.addItem(item)
        publishQueue()
    }

    override fun clearQueue() {
        queueModel = PlayQueue.empty()
        stopTicker()
        _isPlaying.value = false
        _state.value = PlayerState.IDLE
        _position.value = 0L
        publishQueue()
    }

    override fun skipToIndex(index: Int) {
        queueModel = queueModel.skipTo(index)
        onTrackChanged()
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        queueModel = queueModel.setShuffle(enabled)
        publishQueue()
    }

    override fun setVolume(volume: Float) {
        _volume.value = normalizePlayerVolume(volume)
    }

    override fun release() {
        stopTicker()
        scope.cancel()
    }

    private fun onTrackChanged() {
        _position.value = 0L
        publishQueue()
        if (queueModel.currentItem != null) {
            _state.value = if (_isPlaying.value) PlayerState.PLAYING else PlayerState.READY
            if (_isPlaying.value) startTicker()
        } else {
            _state.value = PlayerState.IDLE
            _isPlaying.value = false
            stopTicker()
        }
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(1000)
                if (!_isPlaying.value) break
                val dur = _duration.value
                val next = _position.value + 1000
                if (dur > 0 && next >= dur) {
                    // Auto-advance to the next track in play order.
                    queueModel = queueModel.next()
                    _position.value = 0L
                    publishQueue()
                    if (queueModel.currentItem == null) {
                        _isPlaying.value = false
                        _state.value = PlayerState.ENDED
                        break
                    }
                } else {
                    _position.value = next
                }
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}

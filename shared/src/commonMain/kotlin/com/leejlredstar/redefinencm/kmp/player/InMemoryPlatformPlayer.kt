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
import org.koin.mp.KoinPlatformTools
import org.koin.mp.Lockable

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
    private val tickerIntervalMs: Long = 1_000L,
) : BasePlatformPlayer() {

    init {
        require(tickerIntervalMs > 0L) { "tickerIntervalMs must be positive" }
    }

    private val stateLock = Lockable()
    private var queueModel: PlayQueue<MediaInfo> = PlayQueue.empty()

    private var ticker: Job? = null

    /** Mirror the lock-protected [PlayQueue] state into the public StateFlows. */
    private fun publishQueueLocked() {
        publishQueue(queueModel)
    }

    override fun play() {
        withStateLock { playLocked() }
    }

    override fun pause() {
        withStateLock { pauseLocked() }
    }

    override fun togglePlayPause() {
        withStateLock {
            if (_isPlaying.value) pauseLocked() else playLocked()
        }
    }

    override fun seekTo(positionMs: Long) {
        withStateLock {
            val dur = _duration.value
            _position.value = if (dur > 0) positionMs.coerceIn(0, dur) else positionMs.coerceAtLeast(0)
            if (_state.value == PlayerState.ENDED && (dur <= 0L || _position.value < dur)) {
                _state.value = PlayerState.PAUSED
            }
        }
    }

    override fun seekToPrevious() {
        withStateLock {
            val previous = queueModel.previous(repeat = false)
            if (previous.currentIndex == queueModel.currentIndex) return@withStateLock
            queueModel = previous
            onTrackChangedLocked()
        }
    }

    override fun seekToNext() {
        withStateLock {
            val next = queueModel.next(repeat = false)
            if (next.currentIndex == queueModel.currentIndex) return@withStateLock
            queueModel = next
            onTrackChangedLocked()
        }
    }

    override fun setQueue(items: List<MediaInfo>, startIndex: Int) {
        withStateLock {
            queueModel = PlayQueue.of(items, startIndex)
            onTrackChangedLocked()
        }
    }

    override fun addToQueue(item: MediaInfo) {
        withStateLock {
            queueModel = queueModel.addItem(item)
            publishQueueLocked()
        }
    }

    override fun clearQueue() {
        withStateLock {
            queueModel = PlayQueue.empty()
            stopTickerLocked()
            _isPlaying.value = false
            _state.value = PlayerState.IDLE
            _position.value = 0L
            publishQueueLocked()
        }
    }

    override fun skipToIndex(index: Int) {
        withStateLock {
            val selected = queueModel.skipToPlayOrderPosition(index)
            if (selected.currentIndex == queueModel.currentIndex) return@withStateLock
            queueModel = selected
            onTrackChangedLocked()
        }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        withStateLock {
            queueModel = queueModel.setShuffle(enabled)
            publishQueueLocked()
        }
    }

    override fun setVolume(volume: Float) {
        _volume.value = normalizePlayerVolume(volume)
    }

    override fun release() {
        withStateLock {
            stopTickerLocked()
            scope.cancel()
        }
    }

    private fun playLocked() {
        if (queueModel.currentItem == null) return
        if (_state.value == PlayerState.ENDED) {
            _position.value = 0L
            _playbackOccurrence.advancePlaybackOccurrence()
        }
        _isPlaying.value = true
        _state.value = PlayerState.PLAYING
        startTickerLocked()
    }

    private fun pauseLocked() {
        if (_state.value == PlayerState.ENDED) return
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        stopTickerLocked()
    }

    private fun onTrackChangedLocked() {
        publishQueueLocked()
        _position.value = 0L
        if (queueModel.currentItem != null) {
            _state.value = if (_isPlaying.value) PlayerState.PLAYING else PlayerState.READY
            _playbackOccurrence.advancePlaybackOccurrence()
            if (_isPlaying.value) startTickerLocked()
        } else {
            _state.value = PlayerState.IDLE
            _isPlaying.value = false
            stopTickerLocked()
        }
    }

    private fun startTickerLocked() {
        stopTickerLocked()
        ticker = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(tickerIntervalMs)
                if (!_isPlaying.value) break
                val shouldContinue = withStateLock {
                    if (!_isPlaying.value) return@withStateLock false
                    val dur = _duration.value
                    val next = _position.value + tickerIntervalMs
                    if (dur > 0 && next >= dur) {
                        val nextQueue = queueModel.next(repeat = false)
                        if (nextQueue.currentIndex == queueModel.currentIndex) {
                            _position.value = dur
                            _isPlaying.value = false
                            _state.value = PlayerState.ENDED
                            false
                        } else {
                            queueModel = nextQueue
                            publishQueueLocked()
                            _position.value = 0L
                            _playbackOccurrence.advancePlaybackOccurrence()
                            true
                        }
                    } else {
                        _position.value = next
                        true
                    }
                }
                if (!shouldContinue) break
            }
        }
    }

    private fun stopTickerLocked() {
        ticker?.cancel()
        ticker = null
    }

    private fun <T> withStateLock(block: () -> T): T =
        KoinPlatformTools.synchronized(stateLock, block)
}

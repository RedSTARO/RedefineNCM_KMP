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
) : PlatformPlayer {

    init {
        require(tickerIntervalMs > 0L) { "tickerIntervalMs must be positive" }
    }

    private val stateLock = Lockable()
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

    private val _currentIndex = MutableStateFlow(-1)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _queueSnapshot = MutableStateFlow(PlayerQueueSnapshot())
    override val queueSnapshot: StateFlow<PlayerQueueSnapshot> = _queueSnapshot.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private var ticker: Job? = null

    /** Mirror the lock-protected [PlayQueue] state into the public StateFlows. */
    private fun publishQueueLocked() {
        val model = queueModel
        val snapshot = PlayerQueueSnapshot(
            items = model.itemsInPlayOrder,
            currentIndex = model.positionInPlayOrder,
            currentMedia = model.currentItem,
            shuffleEnabled = model.shuffleEnabled,
        )
        _queueSnapshot.value = snapshot
        _queue.value = snapshot.items
        _currentIndex.value = snapshot.currentIndex
        _currentMedia.value = snapshot.currentMedia
        _shuffleEnabled.value = snapshot.shuffleEnabled
        _duration.value = snapshot.currentMedia?.duration?.takeIf { it > 0 } ?: -1L
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
            val originalIndex = queueModel.playOrder.getOrNull(index) ?: return@withStateLock
            queueModel = queueModel.skipTo(originalIndex)
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
        if (_state.value == PlayerState.ENDED) _position.value = 0L
        _isPlaying.value = true
        _state.value = PlayerState.PLAYING
        startTickerLocked()
    }

    private fun pauseLocked() {
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        stopTickerLocked()
    }

    private fun onTrackChangedLocked() {
        _position.value = 0L
        publishQueueLocked()
        if (queueModel.currentItem != null) {
            _state.value = if (_isPlaying.value) PlayerState.PLAYING else PlayerState.READY
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
                            _position.value = 0L
                            publishQueueLocked()
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

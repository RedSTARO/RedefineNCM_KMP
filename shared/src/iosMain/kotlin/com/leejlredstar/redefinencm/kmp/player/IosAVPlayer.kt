@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFoundation.*
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.Foundation.NSURL

/**
 * iOS PlatformPlayer backed by AVPlayer.
 *
 * Position tracking uses system-clock elapsed time rather than CMTime interop,
 * which avoids Kotlin/Native CValue unwrapping issues with AVFoundation types.
 */
class IosAVPlayer(
    private val repo: Repository,
    private val settings: PlatformSettings,
) : PlatformPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val avPlayer = AVPlayer()

    private val resolver = StreamUrlResolver { mediaId ->
        val id = mediaId.toLong()

        // Check for a locally-downloaded offline file first.
        DownloadedSongsCache.snapshot()[id]?.uri?.let { uri ->
            return@StreamUrlResolver uri
        }

        val qualityName = settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.EXHIGH.name)
        val quality = runCatching { SoundQuality.valueOf(qualityName) }.getOrDefault(SoundQuality.EXHIGH)
        repo.getSongUrl(mediaId.toLong(), quality.name.lowercase())
    }

    private var queueModel: PlayQueue<MediaInfo> = PlayQueue.empty()

    private val _state = MutableStateFlow(PlayerState.IDLE)
    override val state: StateFlow<PlayerState> = _state

    private val _position = MutableStateFlow(0L)
    override val position: StateFlow<Long> = _position

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _duration = MutableStateFlow(-1L)
    override val duration: StateFlow<Long> = _duration

    private val _currentMedia = MutableStateFlow<MediaInfo?>(null)
    override val currentMedia: StateFlow<MediaInfo?> = _currentMedia

    private val _queue = MutableStateFlow<List<MediaInfo>>(emptyList())
    override val queue: StateFlow<List<MediaInfo>> = _queue

    private val _currentIndex = MutableStateFlow(0)
    override val currentIndex: StateFlow<Int> = _currentIndex

    private val _shuffleEnabled = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled

    // Clock-based position tracking (avoids Kotlin/Native CMTime interop issues)
    private var playStartTimeMs = 0L     // (CFAbsoluteTimeGetCurrent() * 1000.0).toLong() when play started
    private var seekOffsetMs = 0L        // Seek offset within the current track
    private var pollJob: Job? = null
    private var resolveJob: Job? = null
    private var playbackGeneration = 0L

    private fun publishQueue() {
        _queue.value = queueModel.itemsInPlayOrder
        _currentIndex.value = queueModel.positionInPlayOrder
        _currentMedia.value = queueModel.currentItem
        _shuffleEnabled.value = queueModel.shuffleEnabled
        _duration.value = queueModel.currentItem?.duration?.takeIf { it > 0 } ?: -1L
    }

    private fun playCurrentFromQueue(autoplay: Boolean = true) {
        val media = queueModel.currentItem ?: run {
            _state.value = PlayerState.IDLE
            _isPlaying.value = false
            return
        }
        _position.value = 0L
        seekOffsetMs = 0L
        playStartTimeMs = 0L
        publishQueue()
        resolveAndPlay(media, startMs = 0L, autoplay = autoplay)
    }

    private fun resetPositionTrack(startMs: Long = 0L, durationHint: Long = -1L, playing: Boolean) {
        playStartTimeMs = if (playing) (CFAbsoluteTimeGetCurrent() * 1000.0).toLong() else 0L
        seekOffsetMs = startMs.coerceAtLeast(0L)
        _position.value = seekOffsetMs
        _duration.value = durationHint
    }

    private fun startPolling() {
        stopPolling()
        pollJob = scope.launch {
            while (isActive) {
                val item = avPlayer.currentItem
                if (item != null) {
                    // Track is-playing state from AVPlayer rate
                    _isPlaying.value = avPlayer.rate > 0.0f

                    // Derive player state
                    when {
                        avPlayer.rate > 0.0f -> _state.value = PlayerState.PLAYING
                        item.status == AVPlayerItemStatusFailed -> _state.value = PlayerState.ERROR
                        item.status == AVPlayerItemStatusReadyToPlay -> {
                            if (_state.value != PlayerState.PLAYING && _state.value != PlayerState.PAUSED)
                                _state.value = PlayerState.READY
                        }
                        else -> {}
                    }

                    // Clock-based position: when playing, advance by elapsed time
                    if (_isPlaying.value && playStartTimeMs > 0) {
                        val elapsed = (CFAbsoluteTimeGetCurrent() * 1000.0).toLong() - playStartTimeMs
                        _position.value = seekOffsetMs + elapsed
                        // 自然播完自动切下一首（时钟近似；duration 来自曲目元数据 dt）。
                        // break 让新曲目的 startPolling 接管，避免旧循环重复推进。
                        val dur = _duration.value
                        if (dur > 0 && _position.value >= dur) {
                            seekToNext()
                            break
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun beginPlaybackSession(clearItem: Boolean): Long {
        playbackGeneration += 1
        val generation = playbackGeneration
        resolveJob?.cancel()
        resolveJob = null
        stopPolling()
        avPlayer.pause()
        if (clearItem) {
            avPlayer.replaceCurrentItemWithPlayerItem(null)
        }
        return generation
    }

    private fun isPlaybackCurrent(generation: Long): Boolean = generation == playbackGeneration

    private fun cancelPlaybackSession(clearItem: Boolean) {
        beginPlaybackSession(clearItem)
    }

    // ── Track resolution and playback ──

    private fun resolveAndPlay(
        media: MediaInfo,
        startMs: Long = 0L,
        autoplay: Boolean = true,
    ) {
        val generation = beginPlaybackSession(clearItem = true)
        _state.value = PlayerState.BUFFERING
        resolveJob = scope.launch {
            _state.value = PlayerState.BUFFERING
            val streamUrl = resolver.resolve(media.id)
            if (!isPlaybackCurrent(generation)) return@launch
            if (streamUrl == null) {
                _state.value = PlayerState.ERROR
                return@launch
            }

            val url = NSURL.URLWithString(streamUrl) ?: run {
                if (!isPlaybackCurrent(generation)) return@launch
                _state.value = PlayerState.ERROR
                return@launch
            }

            val playerItem = AVPlayerItem(uRL = url)
            if (!isPlaybackCurrent(generation)) return@launch
            avPlayer.replaceCurrentItemWithPlayerItem(playerItem)
            if (startMs > 0L) {
                val cmTime = platform.CoreMedia.CMTimeMake(startMs, 1000)
                avPlayer.seekToTime(cmTime)
            }

            // Brief delay to let AVPlayer start loading
            delay(200)
            if (!isPlaybackCurrent(generation)) return@launch
            resetPositionTrack(startMs, media.duration.takeIf { it > 0 } ?: -1L, autoplay)
            if (autoplay) {
                avPlayer.play()
                _isPlaying.value = true
                _state.value = PlayerState.PLAYING
                startPolling()
            } else {
                avPlayer.pause()
                _isPlaying.value = false
                _state.value = PlayerState.PAUSED
            }
        }
    }

    // ── PlatformPlayer controls ──

    override fun play() {
        if (_isPlaying.value || _state.value == PlayerState.BUFFERING) return
        val media = _currentMedia.value
        if (avPlayer.currentItem == null && media != null) {
            resolveAndPlay(media, _position.value.coerceAtLeast(0L), autoplay = true)
            return
        }
        avPlayer.play()
        _isPlaying.value = true
        if (_state.value != PlayerState.ENDED) _state.value = PlayerState.PLAYING
        // Resume from current position
        playStartTimeMs = (CFAbsoluteTimeGetCurrent() * 1000.0).toLong()
        seekOffsetMs = _position.value
        startPolling()
    }

    override fun pause() {
        resolveJob?.cancel()
        resolveJob = null
        playbackGeneration += 1
        avPlayer.pause()
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        // Freeze position: accumulate elapsed into seekOffset
        if (playStartTimeMs > 0) {
            seekOffsetMs += (CFAbsoluteTimeGetCurrent() * 1000.0).toLong() - playStartTimeMs
        }
        playStartTimeMs = 0L
    }

    override fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    override fun seekTo(positionMs: Long) {
        seekOffsetMs = positionMs
        playStartTimeMs = (CFAbsoluteTimeGetCurrent() * 1000.0).toLong()
        _position.value = positionMs
        // Use AVPlayer seek
        val cmTime = platform.CoreMedia.CMTimeMake(positionMs, 1000)
        avPlayer.seekToTime(cmTime)
        val current = _currentMedia.value
        if (current != null && _state.value == PlayerState.BUFFERING) {
            resolveAndPlay(current, seekOffsetMs, autoplay = true)
        }
    }

    override fun seekToPrevious() {
        val nextQueue = queueModel.previous(repeat = false)
        if (nextQueue.currentIndex != queueModel.currentIndex) {
            queueModel = nextQueue
            playCurrentFromQueue(autoplay = true)
        }
    }

    override fun seekToNext() {
        val nextQueue = queueModel.next(repeat = false)
        if (nextQueue.currentIndex != queueModel.currentIndex) {
            queueModel = nextQueue
            playCurrentFromQueue(autoplay = true)
        } else {
            pause()
            _state.value = PlayerState.ENDED
        }
    }

    override fun setQueue(items: List<MediaInfo>, startIndex: Int) {
        if (items.isEmpty()) {
            clearQueue()
            return
        }
        queueModel = PlayQueue.of(items, startIndex)
        playCurrentFromQueue(autoplay = true)
    }

    override fun restoreQueue(items: List<MediaInfo>, startIndex: Int, positionMs: Long) {
        if (items.isEmpty()) {
            clearQueue()
            return
        }
        cancelPlaybackSession(clearItem = true)
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        val safePosition = positionMs.coerceAtLeast(0L)
        queueModel = PlayQueue.of(items, safeIndex, shuffle = queueModel.shuffleEnabled)
        publishQueue()
        _position.value = safePosition
        _duration.value = queueModel.currentItem?.duration?.takeIf { it > 0 } ?: -1L
        seekOffsetMs = safePosition
        playStartTimeMs = 0L
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
    }

    override fun addToQueue(item: MediaInfo) {
        queueModel = queueModel.addItem(item)
        publishQueue()
    }

    override fun clearQueue() {
        cancelPlaybackSession(clearItem = true)
        queueModel = PlayQueue.empty()
        publishQueue()
        _state.value = PlayerState.IDLE
        _isPlaying.value = false
        _position.value = 0L
        _duration.value = -1L
        seekOffsetMs = 0L
        playStartTimeMs = 0L
    }

    override fun skipToIndex(index: Int) {
        val itemIndex = queueModel.playOrder.getOrNull(index) ?: index
        if (itemIndex in queueModel.items.indices) {
            val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
            queueModel = queueModel.skipTo(itemIndex)
            val media = queueModel.currentItem ?: return
            publishQueue()
            _position.value = 0L
            _duration.value = media.duration.takeIf { it > 0 } ?: -1L
            seekOffsetMs = 0L
            playStartTimeMs = 0L
            if (autoplay) {
                resolveAndPlay(media, 0L, autoplay = true)
            } else {
                cancelPlaybackSession(clearItem = true)
                _isPlaying.value = false
                _state.value = PlayerState.PAUSED
            }
        }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        queueModel = queueModel.setShuffle(enabled)
        publishQueue()
    }

    override fun release() {
        cancelPlaybackSession(clearItem = true)
        scope.cancel()
    }
}

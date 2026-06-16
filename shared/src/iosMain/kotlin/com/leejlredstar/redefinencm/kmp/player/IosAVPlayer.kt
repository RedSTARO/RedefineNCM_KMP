package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.Repository
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
import kotlinx.cinterop.ExperimentalForeignApi
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
        val qualityName = settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.EXHIGH.name)
        val quality = runCatching { SoundQuality.valueOf(qualityName) }.getOrDefault(SoundQuality.EXHIGH)
        repo.getSongUrl(mediaId.toLong(), quality.name.lowercase())
    }

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

    private fun resetPositionTrack(durationHint: Long = -1L) {
        playStartTimeMs = (CFAbsoluteTimeGetCurrent() * 1000.0).toLong()
        seekOffsetMs = 0L
        _position.value = 0L
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
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // ── Track resolution and playback ──

    private fun resolveAndPlay(media: MediaInfo) {
        scope.launch {
            _state.value = PlayerState.BUFFERING
            val streamUrl = resolver.resolve(media.id)
            if (streamUrl == null) {
                _state.value = PlayerState.ERROR
                return@launch
            }

            val url = NSURL.URLWithString(streamUrl) ?: run {
                _state.value = PlayerState.ERROR
                return@launch
            }

            val playerItem = AVPlayerItem(uRL = url)
            avPlayer.replaceCurrentItemWithPlayerItem(playerItem)

            // Brief delay to let AVPlayer start loading
            delay(200)
            avPlayer.play()
            _isPlaying.value = true
            _state.value = PlayerState.PLAYING
            resetPositionTrack(media.duration.takeIf { it > 0 } ?: -1L)
            startPolling()
        }
    }

    // ── PlatformPlayer controls ──

    override fun play() {
        avPlayer.play()
        _isPlaying.value = true
        if (_state.value != PlayerState.ENDED) _state.value = PlayerState.PLAYING
        // Resume from current position
        playStartTimeMs = (CFAbsoluteTimeGetCurrent() * 1000.0).toLong()
        seekOffsetMs = _position.value
        startPolling()
    }

    override fun pause() {
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
    }

    override fun seekToPrevious() {
        if (_currentIndex.value > 0) {
            skipToIndex(_currentIndex.value - 1)
            play()
        }
    }

    override fun seekToNext() {
        val next = _currentIndex.value + 1
        if (next < _queue.value.size) {
            skipToIndex(next)
            play()
        } else {
            pause()
            _state.value = PlayerState.ENDED
        }
    }

    override fun setQueue(items: List<MediaInfo>, startIndex: Int) {
        _queue.value = items
        _currentIndex.value = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val media = items.getOrNull(_currentIndex.value) ?: return
        _currentMedia.value = media
        resolveAndPlay(media)
    }

    override fun addToQueue(item: MediaInfo) {
        _queue.value = _queue.value + item
    }

    override fun clearQueue() {
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
        stopPolling()
        _queue.value = emptyList()
        _currentMedia.value = null
        _state.value = PlayerState.IDLE
        _isPlaying.value = false
        _position.value = 0L
    }

    override fun skipToIndex(index: Int) {
        if (index in _queue.value.indices) {
            _currentIndex.value = index
            val media = _queue.value[index]
            _currentMedia.value = media
            resolveAndPlay(media)
        }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        _shuffleEnabled.value = enabled
    }

    override fun release() {
        avPlayer.pause()
        stopPolling()
        scope.cancel()
    }
}

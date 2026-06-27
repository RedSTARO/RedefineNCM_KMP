package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.isSongDownloaded
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URI
import javax.sound.sampled.*

/**
 * Desktop (JVM) [PlatformPlayer] backed by [javax.sound.sampled] with MP3 support
 * via the mp3spi (JavaZOOM) service-provider interface.
 *
 * Placeholder URIs are resolved by [StreamUrlResolver]: if the song has been downloaded
 * to `~/Downloads/RedefineNCM/<id>.mp3` it uses the local file directly; otherwise it
 * fetches a CDN stream URL via [Repository.getSongUrl].
 *
 * Position is tracked via system-clock elapsed time to handle variable-bit-rate MP3
 * without relying on frame-level seeking.
 */
class JvmMediaPlayer(
    private val repo: Repository,
    private val settings: PlatformSettings,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : PlatformPlayer {

    // ── URL resolver with offline check ──

    private val resolver = StreamUrlResolver { mediaId ->
        val id = mediaId.toLong()

        // Check for a locally-downloaded offline file first.
        if (isSongDownloaded(id)) {
            val file = File(System.getProperty("user.home"), "Downloads/RedefineNCM/$id.mp3")
            if (file.isFile) {
                return@StreamUrlResolver file.toURI().toString()
            }
        }

        // Fall through to online CDN resolution.
        val qualityName = settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.EXHIGH.name)
        val quality = runCatching { SoundQuality.valueOf(qualityName) }.getOrDefault(SoundQuality.EXHIGH)
        repo.getSongUrl(id, quality.name.lowercase())
    }

    // ── Queue state ──

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

    // ── Audio playback state ──

    @Volatile private var playbackThread: Thread? = null
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var audioStream: AudioInputStream? = null

    @Volatile private var playStartNano = 0L
    @Volatile private var seekOffsetMs = 0L

    private var pollJob: Job? = null

    // ── Internal helpers ──

    private fun publishQueue() {
        _queue.value = queueModel.items
        _currentIndex.value = queueModel.currentIndex.coerceAtLeast(0)
        _currentMedia.value = queueModel.currentItem
        _shuffleEnabled.value = queueModel.shuffleEnabled
        _duration.value = queueModel.currentItem?.duration?.takeIf { it > 0 } ?: -1L
    }

    private fun startPolling() {
        stopPolling()
        pollJob = scope.launch {
            while (isActive) {
                if (_isPlaying.value && playStartNano > 0) {
                    val elapsed = (System.nanoTime() - playStartNano) / 1_000_000L
                    _position.value = seekOffsetMs + elapsed
                }
                delay(200)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun openAndPlay(streamUrl: String) {
        stopPlayback()

        playbackThread = Thread({
            try {
                val url = URI(streamUrl).toURL()
                val stream = AudioSystem.getAudioInputStream(url)
                val fmt = stream.format
                val info = DataLine.Info(SourceDataLine::class.java, fmt)
                val audioLine = AudioSystem.getLine(info) as SourceDataLine

                audioLine.open(fmt)
                audioLine.start()

                this.line = audioLine
                this.audioStream = stream

                val totalFrames = stream.frameLength
                if (totalFrames > 0) {
                    val durMs = (totalFrames * 1_000L / fmt.frameRate.toLong()).coerceAtLeast(0)
                    _duration.value = durMs
                }

                val buf = ByteArray(4096)
                playStartNano = System.nanoTime()
                seekOffsetMs = 0L
                _position.value = 0L
                _isPlaying.value = true
                _state.value = PlayerState.PLAYING

                while (!Thread.interrupted()) {
                    val read = stream.read(buf)
                    if (read < 0) break
                    audioLine.write(buf, 0, read)
                }

                audioLine.drain()
                audioLine.stop()
                audioLine.close()
                stream.close()

                if (!Thread.interrupted()) {
                    _isPlaying.value = false
                    queueModel = queueModel.next()
                    publishQueue()
                    queueModel.currentItem?.let { resolveAndPlay(it) } ?: run {
                        _state.value = PlayerState.ENDED
                    }
                }
            } catch (e: Exception) {
                _state.value = PlayerState.ERROR
                _isPlaying.value = false
            }
        }, "JvmMediaPlayer-audio").apply {
            isDaemon = true
            start()
        }

        startPolling()
    }

    private fun stopPlayback() {
        playbackThread?.interrupt()
        playbackThread = null
        try { audioStream?.close() } catch (_: Exception) {}
        try { line?.stop(); line?.close() } catch (_: Exception) {}
        audioStream = null
        line = null
    }

    private fun resolveAndPlay(media: MediaInfo) {
        scope.launch {
            _state.value = PlayerState.BUFFERING
            val streamUrl = resolver.resolve(media.id)
            if (streamUrl == null) {
                _state.value = PlayerState.ERROR
                return@launch
            }
            openAndPlay(streamUrl)
        }
    }

    private fun onTrackChanged() {
        _position.value = 0L
        publishQueue()
        queueModel.currentItem?.let { resolveAndPlay(it) } ?: run {
            _state.value = PlayerState.IDLE
            _isPlaying.value = false
        }
    }

    // ── PlatformPlayer implementation ──

    override fun play() {
        if (_isPlaying.value) return
        if (line == null) {
            queueModel.currentItem?.let { resolveAndPlay(it) }
            return
        }
        if (playStartNano == 0L) seekOffsetMs = _position.value
        playStartNano = System.nanoTime()
        line?.start()
        _isPlaying.value = true
        _state.value = PlayerState.PLAYING
        startPolling()
    }

    override fun pause() {
        if (!_isPlaying.value) return
        if (playStartNano > 0) seekOffsetMs += (System.nanoTime() - playStartNano) / 1_000_000L
        playStartNano = 0L
        line?.stop()
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        stopPolling()
    }

    override fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        seekOffsetMs = positionMs.coerceAtLeast(0)
        _position.value = seekOffsetMs
        val current = _currentMedia.value
        if (current != null) {
            stopPlayback()
            resolveAndPlay(current)
        }
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
        stopPlayback()
        stopPolling()
        queueModel = PlayQueue.empty()
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

    override fun release() {
        stopPlayback()
        stopPolling()
        scope.cancel()
    }
}

package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.*

/**
 * Desktop (JVM) [PlatformPlayer] backed by [javax.sound.sampled] with MP3 support
 * via the mp3spi (JavaZOOM) service-provider interface.
 *
 * Placeholder URIs are resolved by [StreamUrlResolver]: if the song has been downloaded
 * to `~/Music/RedefineNCM/` it uses the scanned local file URI directly; otherwise it
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

        // Check for a locally-downloaded offline file first. The downloader preserves the
        // server-provided extension, so this must use the snapshot URI instead of `$id.mp3`.
        DownloadedSongsCache.snapshot()[id]?.uri?.let { uri ->
            return@StreamUrlResolver uri
        }

        // Fall through to online CDN resolution.
        val qualityName = settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.EXHIGH.name)
        val quality = runCatching { SoundQuality.valueOf(qualityName) }.getOrDefault(SoundQuality.EXHIGH)
        repo.getSongUrl(id, quality.name.lowercase())
    }

    // ── Queue state ──

    // 队列模型被音频播放线程（自然换曲）与调用线程（切歌/洗牌/换单）并发访问，
    // 必须串行化其读-改-写，否则会丢更新 / 读到过期队列（本项目高度敏感的队列错位问题）。
    private val queueLock = Any()

    @Volatile
    private var queueModel: PlayQueue<MediaInfo> = PlayQueue.empty()

    /** 原子地读-改-写队列模型。 */
    private inline fun mutateQueue(block: (PlayQueue<MediaInfo>) -> PlayQueue<MediaInfo>) {
        synchronized(queueLock) { queueModel = block(queueModel) }
    }

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

    private val playbackLock = Any()
    private val pauseLock = ReentrantLock()
    private val pauseCondition = pauseLock.newCondition()

    @Volatile private var playbackGeneration = 0L
    @Volatile private var resolveJob: Job? = null
    @Volatile private var playbackThread: Thread? = null
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var audioStream: AudioInputStream? = null
    @Volatile private var pauseRequested = false

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
                delay(100)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun isPlaybackCurrent(generation: Long): Boolean =
        synchronized(playbackLock) { generation == playbackGeneration }

    private fun signalPauseStateChanged() {
        pauseLock.lock()
        try {
            pauseCondition.signalAll()
        } finally {
            pauseLock.unlock()
        }
    }

    private fun beginPlaybackSession(): Long {
        val generation = synchronized(playbackLock) {
            playbackGeneration += 1
            pauseRequested = false
            resolveJob?.cancel()
            resolveJob = null
            stopPlaybackLocked()
            playbackGeneration
        }
        signalPauseStateChanged()
        stopPolling()
        return generation
    }

    private fun cancelPlaybackSession() {
        synchronized(playbackLock) {
            playbackGeneration += 1
            pauseRequested = false
            resolveJob?.cancel()
            resolveJob = null
            stopPlaybackLocked()
        }
        signalPauseStateChanged()
        stopPolling()
    }

    private fun stopPlaybackLocked() {
        val threadToStop = playbackThread
        val streamToClose = audioStream
        val lineToClose = line

        playbackThread = null
        audioStream = null
        line = null
        pauseRequested = false

        if (threadToStop !== Thread.currentThread()) {
            threadToStop?.interrupt()
        }
        try {
            lineToClose?.flush()
            lineToClose?.stop()
            lineToClose?.close()
        } catch (_: Exception) {
        }
        try {
            streamToClose?.close()
        } catch (_: Exception) {
        }
        playStartNano = 0L
    }

    private fun openAndPlay(generation: Long, streamUrl: String, startMs: Long) {
        val thread = Thread(
            { runPlayback(generation, streamUrl, startMs) },
            "JvmMediaPlayer-audio-$generation",
        ).apply { isDaemon = true }

        synchronized(playbackLock) {
            if (generation != playbackGeneration) return
            pauseRequested = false
            stopPlaybackLocked()
            playbackThread = thread
        }

        thread.start()
        startPolling()
    }

    private fun runPlayback(generation: Long, streamUrl: String, startMs: Long) {
        var rawStream: AudioInputStream? = null
        var stream: AudioInputStream? = null
        var audioLine: SourceDataLine? = null
        var completedNaturally = false

        try {
            val url = URI(streamUrl).toURL()
            rawStream = AudioSystem.getAudioInputStream(url)
            val baseFormat = rawStream.format

            // MP3(MPEG) 帧不能直接喂 SourceDataLine —— 必须经 mp3spi 转成 PCM_SIGNED
            val fmt: AudioFormat
            stream = if (baseFormat.encoding == AudioFormat.Encoding.PCM_SIGNED ||
                baseFormat.encoding == AudioFormat.Encoding.PCM_UNSIGNED
            ) {
                fmt = baseFormat
                rawStream
            } else {
                fmt = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels,
                    baseFormat.channels * 2,
                    baseFormat.sampleRate,
                    false,
                )
                AudioSystem.getAudioInputStream(fmt, rawStream)
            }

            val info = DataLine.Info(SourceDataLine::class.java, fmt)
            audioLine = AudioSystem.getLine(info) as SourceDataLine
            audioLine.open(fmt)

            synchronized(playbackLock) {
                if (generation != playbackGeneration) return
                line = audioLine
                audioStream = stream
            }

            val totalFrames = stream.frameLength
            if (totalFrames > 0 && fmt.frameRate > 0) {
                val durMs = (totalFrames * 1_000L / fmt.frameRate.toLong()).coerceAtLeast(0)
                _duration.value = durMs
            }

            // 跳到 seek 位置：按 PCM 字节率丢弃解码流前段（VBR 无帧索引，近似即可）
            if (startMs > 0 && fmt.frameRate > 0 && fmt.frameSize > 0) {
                var toSkip = (startMs * fmt.frameRate.toLong() * fmt.frameSize / 1000L)
                // 对齐帧边界，避免声道错位产生噪音
                toSkip -= toSkip % fmt.frameSize
                while (toSkip > 0 && !Thread.currentThread().isInterrupted && isPlaybackCurrent(generation)) {
                    val skipped = stream.skip(toSkip)
                    if (skipped <= 0) break
                    toSkip -= skipped
                }
            }

            if (!isPlaybackCurrent(generation)) return

            val buf = ByteArray(4096)
            audioLine.start()
            playStartNano = System.nanoTime()
            seekOffsetMs = startMs
            _position.value = startMs
            _isPlaying.value = true
            _state.value = PlayerState.PLAYING

            while (!Thread.currentThread().isInterrupted && isPlaybackCurrent(generation)) {
                var shouldStop = false
                pauseLock.lock()
                try {
                    while (pauseRequested && generation == playbackGeneration && !Thread.currentThread().isInterrupted) {
                        try {
                            pauseCondition.await(100, TimeUnit.MILLISECONDS)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                } finally {
                    pauseLock.unlock()
                }
                if (generation != playbackGeneration || Thread.currentThread().isInterrupted) {
                    shouldStop = true
                } else if (!audioLine.isRunning) {
                    audioLine.start()
                    playStartNano = System.nanoTime()
                }
                if (shouldStop) break
                val read = stream.read(buf)
                if (read < 0) {
                    completedNaturally = true
                    break
                }
                if (!isPlaybackCurrent(generation)) break
                audioLine.write(buf, 0, read)
            }

            if (completedNaturally && isPlaybackCurrent(generation)) {
                audioLine.drain()
            }
        } catch (e: Exception) {
            if (isPlaybackCurrent(generation)) {
                _state.value = PlayerState.ERROR
                _isPlaying.value = false
            }
        } finally {
            try {
                audioLine?.stop()
                audioLine?.close()
            } catch (_: Exception) {
            }
            try {
                stream?.close()
            } catch (_: Exception) {
            }
            if (stream !== rawStream) {
                try {
                    rawStream?.close()
                } catch (_: Exception) {
                }
            }
            synchronized(playbackLock) {
                if (generation == playbackGeneration) {
                    if (line === audioLine) line = null
                    if (audioStream === stream) audioStream = null
                    if (playbackThread === Thread.currentThread()) playbackThread = null
                }
            }
        }

        if (completedNaturally && isPlaybackCurrent(generation)) {
            _isPlaying.value = false
            seekOffsetMs = 0L
            mutateQueue { it.next() }
            publishQueue()
            queueModel.currentItem?.let { resolveAndPlay(it, 0L) } ?: run {
                _state.value = PlayerState.ENDED
            }
        }
    }

    private fun resolveAndPlay(media: MediaInfo, startMs: Long = seekOffsetMs.coerceAtLeast(0L)) {
        val generation = beginPlaybackSession()
        _isPlaying.value = false
        _state.value = PlayerState.BUFFERING

        val job = scope.launch {
            val streamUrl = resolver.resolve(media.id)
            if (!isPlaybackCurrent(generation)) return@launch
            if (streamUrl == null) {
                _state.value = PlayerState.ERROR
                return@launch
            }
            openAndPlay(generation, streamUrl, startMs.coerceAtLeast(0L))
        }

        synchronized(playbackLock) {
            if (generation == playbackGeneration) {
                resolveJob = job
            } else {
                job.cancel()
            }
        }
    }

    private fun onTrackChanged(autoplay: Boolean) {
        _position.value = 0L
        seekOffsetMs = 0L
        publishQueue()
        val current = queueModel.currentItem
        when {
            current == null -> {
                cancelPlaybackSession()
                _state.value = PlayerState.IDLE
                _isPlaying.value = false
            }
            autoplay -> resolveAndPlay(current, 0L)
            else -> {
                cancelPlaybackSession()
                _isPlaying.value = false
                _state.value = PlayerState.PAUSED
            }
        }
    }

    // ── PlatformPlayer implementation ──

    override fun play() {
        if (_isPlaying.value) return
        if (_state.value == PlayerState.BUFFERING) return
        val resumed = synchronized(playbackLock) {
            val thread = playbackThread
            val currentLine = line
            if (_state.value == PlayerState.PAUSED &&
                pauseRequested &&
                thread != null &&
                thread.isAlive &&
                currentLine != null
            ) {
                pauseRequested = false
                playStartNano = System.nanoTime()
                currentLine.start()
                _isPlaying.value = true
                _state.value = PlayerState.PLAYING
                true
            } else {
                false
            }
        }
        if (resumed) {
            signalPauseStateChanged()
            startPolling()
            return
        }
        queueModel.currentItem?.let { resolveAndPlay(it, _position.value.coerceAtLeast(0L)) }
    }

    override fun pause() {
        if (!_isPlaying.value && _state.value != PlayerState.BUFFERING) return
        if (playStartNano > 0) seekOffsetMs += (System.nanoTime() - playStartNano) / 1_000_000L
        playStartNano = 0L
        _position.value = seekOffsetMs
        val pausedActivePlayback = synchronized(playbackLock) {
            val currentLine = line
            val thread = playbackThread
            if (_isPlaying.value && currentLine != null && thread != null && thread.isAlive) {
                pauseRequested = true
                currentLine.stop()
                _isPlaying.value = false
                _state.value = PlayerState.PAUSED
                true
            } else {
                false
            }
        }
        if (pausedActivePlayback) {
            signalPauseStateChanged()
            return
        }
        cancelPlaybackSession()
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
    }

    override fun togglePlayPause() {
        if (_isPlaying.value || _state.value == PlayerState.BUFFERING) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        seekOffsetMs = positionMs.coerceAtLeast(0)
        _position.value = seekOffsetMs
        val current = _currentMedia.value
        if (current != null && (_isPlaying.value || _state.value == PlayerState.BUFFERING)) {
            resolveAndPlay(current, seekOffsetMs)
        } else if (current != null && _state.value == PlayerState.PAUSED && playbackThread != null) {
            cancelPlaybackSession()
            _isPlaying.value = false
            _state.value = PlayerState.PAUSED
        }
    }

    override fun seekToPrevious() {
        val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
        mutateQueue { it.previous() }
        onTrackChanged(autoplay)
    }

    override fun seekToNext() {
        val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
        mutateQueue { it.next() }
        onTrackChanged(autoplay)
    }

    override fun setQueue(items: List<MediaInfo>, startIndex: Int) {
        mutateQueue { PlayQueue.of(items, startIndex) }
        onTrackChanged(autoplay = true)
    }

    override fun restoreQueue(items: List<MediaInfo>, startIndex: Int, positionMs: Long) {
        // 恢复队列但不自动播放（默认实现的 setQueue→pause 存在异步竞态：
        // resolveAndPlay 解析完成晚于 pause，会照样出声）。play() 时会从
        // seekOffsetMs 起播（openAndPlay 尊重该偏移）。
        cancelPlaybackSession()
        mutateQueue { PlayQueue.of(items, startIndex) }
        seekOffsetMs = positionMs.coerceAtLeast(0L)
        _position.value = seekOffsetMs
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        publishQueue()
    }

    override fun addToQueue(item: MediaInfo) {
        mutateQueue { it.addItem(item) }
        publishQueue()
    }

    override fun clearQueue() {
        cancelPlaybackSession()
        mutateQueue { PlayQueue.empty() }
        _isPlaying.value = false
        _state.value = PlayerState.IDLE
        _position.value = 0L
        publishQueue()
    }

    override fun skipToIndex(index: Int) {
        val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
        mutateQueue { it.skipTo(index) }
        onTrackChanged(autoplay)
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        mutateQueue { it.setShuffle(enabled) }
        publishQueue()
    }

    override fun release() {
        cancelPlaybackSession()
        scope.cancel()
    }
}

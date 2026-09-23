package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.transition.TransitionCapability
import com.leejlredstar.redefinencm.kmp.transition.TransitionPlan
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.*
import kotlin.math.log10

/**
 * Desktop (JVM) [PlatformPlayer]: FFmpeg decodes, [DeckMixer] renders, and a Java Sound
 * [SourceDataLine] plays.
 *
 * Placeholder URIs are resolved by [StreamUrlResolver]: if the song has been downloaded
 * to `~/Music/RedefineNCM/` in a decodable format it uses the scanned local file URI
 * directly; otherwise it fetches a CDN stream URL via [Repository.getSongUrl].
 *
 * Position comes from the line's own count of frames played, mapped back to a track by the
 * mixer's [PlaybackClock]. A wall clock cannot follow a track whose tempo a song transition is
 * stretching, nor the second track that takes over halfway through a blend.
 */
class JvmMediaPlayer(
    private val repo: Repository,
    private val settings: PlatformSettings,
    private val providers: MusicProviderRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BasePlatformPlayer(settings.persistedPlayerVolume()) {

    // ── URL resolver with offline check ──

    private fun resolverFor(recordFailures: Boolean) = StreamUrlResolver { mediaId ->
        resolveStreamUrl(
            mediaId = mediaId,
            providers = providers,
            localAudioUri = { id ->
                DownloadedSongsCache.ensureInitialized()
                DownloadedSongsCache.snapshot()[id]?.uri?.takeIf(::isJvmPlayableAudioUri)
            },
            onlineUrl = { id, quality -> repo.getSongUrl(id, jvmPlaybackQualityLevel(quality)) },
            quality = { settings.onlinePlaybackQuality() },
            recordFailures = recordFailures,
        )
    }

    private val resolver = resolverFor(recordFailures = true)

    // The next track of a song transition is prepared before anyone is listening to it; its
    // failure must not reach the "this track cannot play" channel.
    private val quietResolver = resolverFor(recordFailures = false)

    // ── Queue state ──

    // Queue mutations, their publication, and the immediate playback decision are serialized.
    // Publication itself happens while JvmQueueState owns its lock, so mutation A can never publish
    // an old snapshot after mutation B has already published a newer one.
    private val queueOperationLock = Any()
    private val queueState = JvmQueueState<MediaInfo>()

    // ── Audio playback state ──

    private val playbackLock = Any()
    private val pauseLock = ReentrantLock()
    private val pauseCondition = pauseLock.newCondition()

    @Volatile private var playbackGeneration = 0L
    @Volatile private var resolveJob: Job? = null
    @Volatile private var playbackThread: Thread? = null
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var mixer: DeckMixer? = null
    @Volatile private var clock: PlaybackClock? = null
    @Volatile private var pauseRequested = false

    /** Where playback resumes when no line is open: after a pause that closed it, or a seek. */
    @Volatile private var seekOffsetMs = 0L

    // ── Song transitions ──

    @Volatile private var armedPlan: TransitionPlan? = null
    @Volatile private var preparingPlan: TransitionPlan? = null

    private var pollJob: Job? = null

    // ── Internal helpers ──

    private fun publishQueueLocked(snapshot: PlayQueue<MediaInfo>) {
        val publication = snapshot.asJvmQueuePublication()
        publishQueueSnapshot(
            PlayerQueueSnapshot(
                items = publication.items,
                currentIndex = publication.currentIndex,
                currentMedia = publication.currentMedia,
                shuffleEnabled = publication.shuffleEnabled,
            ),
        )
        publishDurationFromMedia(publication.currentMedia)
    }

    private fun mutateQueue(
        invalidatesPlaybackClaim: Boolean,
        block: (PlayQueue<MediaInfo>) -> PlayQueue<MediaInfo>,
    ): JvmQueueClaim<MediaInfo> = queueState.mutateAndPublish(
        invalidatesPlaybackClaim = invalidatesPlaybackClaim,
        block = block,
        publish = ::publishQueueLocked,
    )

    private fun currentQueueClaim(): JvmQueueClaim<MediaInfo> = queueState.current()

    private fun currentQueueModel(): PlayQueue<MediaInfo> = currentQueueClaim().model

    /** The line's position mapped back to a track, or null when nothing is playing. */
    private fun clockReading(): ClockReading? {
        val audioLine = line ?: return null
        val playbackClock = clock ?: return null
        return playbackClock.at(runCatching { audioLine.longFramePosition }.getOrDefault(0L))
    }

    @Synchronized
    private fun startPolling(generation: Long) {
        stopPolling()
        pollJob = scope.launch {
            while (isActive) {
                if (_isPlaying.value) {
                    clockReading()?.let { reading -> onClockReading(reading, generation) }
                }
                _transitionAudible.value = _isPlaying.value && mixer?.blendIncomingMediaId != null
                delay(100)
            }
        }
    }

    private fun onClockReading(reading: ClockReading, generation: Long) {
        val current = currentQueueModel().currentItem ?: return
        if (reading.mediaId == current.id) {
            _position.value = reading.positionMs
            return
        }
        publishTransitionSwap(reading, generation)
    }

    /**
     * The blend reached the point where the incoming track becomes the current one: advance the
     * queue through the one publication path, without restarting playback, and count the new
     * selection. When the queue no longer has that track next, the blend is abandoned instead.
     */
    private fun publishTransitionSwap(reading: ClockReading, generation: Long) {
        synchronized(queueOperationLock) {
            if (!isPlaybackCurrent(generation)) return
            val model = currentQueueModel()
            val current = model.currentItem ?: return
            if (reading.mediaId == current.id) return
            val next = model.next(repeat = false)
            if (next.currentIndex == model.currentIndex || next.currentItem?.id != reading.mediaId) {
                mixer?.abandonBlend(keepMediaId = current.id)
                return
            }
            mutateQueue(invalidatesPlaybackClaim = true) { it.next(repeat = false) }
            armedPlan = null
            seekOffsetMs = reading.positionMs
            _position.value = reading.positionMs
            mixer?.audibleDurationMs?.takeIf { it > 0L }?.let { _duration.value = it }
            // Last, so observers reading currentMedia on this change already see the new track.
            _playbackOccurrence.advancePlaybackOccurrence()
        }
    }

    @Synchronized
    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _transitionAudible.value = false
    }

    private fun stopPollingIfCurrent(generation: Long) {
        synchronized(playbackLock) {
            if (generation == playbackGeneration) stopPolling()
        }
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
        val mixerToClose = mixer
        val lineToClose = line

        playbackThread = null
        mixer = null
        clock = null
        line = null
        pauseRequested = false
        preparingPlan = null

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
            mixerToClose?.close()
        } catch (_: Exception) {
        }
    }

    private fun applyVolumeToLine(targetLine: SourceDataLine?, volume: Float = _volume.value) {
        val audioLine = targetLine ?: return
        val safeVolume = normalizePlayerVolume(volume)
        runCatching {
            when {
                audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN) -> {
                    val control = audioLine.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                    val gain = if (safeVolume <= 0f) {
                        control.minimum
                    } else {
                        (20f * log10(safeVolume)).coerceIn(control.minimum, control.maximum)
                    }
                    control.value = gain
                }
                audioLine.isControlSupported(FloatControl.Type.VOLUME) -> {
                    val control = audioLine.getControl(FloatControl.Type.VOLUME) as FloatControl
                    control.value = (control.minimum + (control.maximum - control.minimum) * safeVolume)
                        .coerceIn(control.minimum, control.maximum)
                }
            }
        }
    }

    private fun openAndPlay(
        generation: Long,
        selectionRevision: Long,
        media: MediaInfo,
        streamUrl: String,
        startMs: Long,
    ) {
        if (!queueState.isPlaybackClaimCurrent(selectionRevision, media)) return
        val thread = Thread(
            { runPlayback(generation, media, streamUrl, startMs) },
            "JvmMediaPlayer-audio-$generation",
        ).apply { isDaemon = true }

        synchronized(playbackLock) {
            if (generation != playbackGeneration) return
            pauseRequested = false
            stopPlaybackLocked()
            playbackThread = thread
        }

        thread.start()
        startPolling(generation)
    }

    private fun runPlayback(generation: Long, media: MediaInfo, streamUrl: String, startMs: Long) {
        var deckMixer: DeckMixer? = null
        var audioLine: SourceDataLine? = null
        var completedNaturally = false

        try {
            // 每次开流都重新读设置：换设备后当前这首也要跟着走，而不是等下一首。
            val deviceId = settings.getString(
                SettingKeys.AUDIO_OUTPUT_DEVICE,
                SYSTEM_DEFAULT_AUDIO_OUTPUT_ID,
            )
            // FFmpeg seeks the decoder itself, so nothing discards PCM bytes at the head of the
            // stream to approximate a seek; that approximation could misalign the channels.
            var decoded = FfmpegAudioSource.open(streamUrl, startMs)
            audioLine = try {
                openAudioOutputLine(decoded.format, deviceId)
            } catch (rateRefused: Exception) {
                if (rateRefused !is LineUnavailableException &&
                    rateRefused !is IllegalArgumentException
                ) {
                    decoded.close()
                    throw rateRefused
                }
                // A master can carry a rate no output line will take. Resampling costs one
                // reopen and keeps the track playing; failing here would look like silence.
                System.err.println(
                    "JvmMediaPlayer: no line for ${decoded.format}, " +
                        "resampling to ${FfmpegAudioSource.FallbackSampleRate} Hz",
                )
                decoded.close()
                decoded = FfmpegAudioSource.open(
                    streamUrl,
                    startMs,
                    FfmpegAudioSource.FallbackSampleRate,
                )
                try {
                    openAudioOutputLine(decoded.format, deviceId)
                } catch (failure: Exception) {
                    decoded.close()
                    throw failure
                }
            }
            applyVolumeToLine(audioLine)
            val playbackClock = PlaybackClock(decoded.sampleRate)
            deckMixer = DeckMixer(decoded, media.id, startMs, playbackClock)
            deckMixer.armedPlan = armedPlan?.takeIf { it.outgoingMediaId == media.id }

            synchronized(playbackLock) {
                if (generation != playbackGeneration) return
                line = audioLine
                mixer = deckMixer
                clock = playbackClock
            }

            decoded.durationMs.takeIf { it > 0L }?.let { _duration.value = it }

            if (!isPlaybackCurrent(generation)) return

            val channels = deckMixer.channels
            val block = FloatArray(BLOCK_FRAMES * channels)
            val bytes = ByteArray(BLOCK_FRAMES * channels * 2)
            audioLine.start()
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
                }
                if (shouldStop) break
                val frames = deckMixer.render(block, BLOCK_FRAMES)
                if (frames == 0) {
                    completedNaturally = true
                    break
                }
                if (!isPlaybackCurrent(generation)) break
                audioLine.write(bytes, 0, floatToPcm16(block, frames, channels, bytes))
                deckMixer.planNeedingIncoming(PREPARE_LEAD_MS)?.let { plan ->
                    prepareIncoming(generation, deckMixer, plan)
                }
            }

            if (completedNaturally && isPlaybackCurrent(generation)) {
                audioLine.drain()
            }
        } catch (e: Throwable) {
            // Throwable, not Exception: a decoder SPI that fails to initialise throws
            // ExceptionInInitializerError from the first read(), which is an Error. If it got
            // past this handler, the playback thread would die with the state flows still
            // reading PLAYING, and the UI would keep advancing its progress bar over silence
            // instead of reporting a failure.
            if (isPlaybackCurrent(generation)) {
                System.err.println("JvmMediaPlayer failed to play audio stream: ${e.javaClass.name}: ${e.message}")
                _state.value = PlayerState.ERROR
                _isPlaying.value = false
                stopPollingIfCurrent(generation)
            }
        } finally {
            try {
                audioLine?.stop()
                audioLine?.close()
            } catch (_: Exception) {
            }
            try {
                deckMixer?.close()
            } catch (_: Exception) {
            }
            synchronized(playbackLock) {
                if (generation == playbackGeneration) {
                    if (line === audioLine) line = null
                    if (mixer === deckMixer) {
                        mixer = null
                        clock = null
                    }
                    if (playbackThread === Thread.currentThread()) playbackThread = null
                }
            }
        }

        if (completedNaturally && isPlaybackCurrent(generation)) {
            stopPollingIfCurrent(generation)
            synchronized(queueOperationLock) {
                if (!isPlaybackCurrent(generation)) return@synchronized
                _isPlaying.value = false
                seekOffsetMs = 0L
                val previous = currentQueueModel()
                val next = mutateQueue(invalidatesPlaybackClaim = true) { it.next(repeat = false) }
                if (next.model.currentIndex != previous.currentIndex) {
                    onTrackChanged(next, autoplay = true)
                } else if (queueState.isPlaybackClaimCurrent(
                        next.selectionRevision,
                        next.model.currentItem,
                    )
                ) {
                    _position.value = _duration.value.coerceAtLeast(0L)
                    _state.value = PlayerState.ENDED
                }
            }
        }
    }

    /**
     * Opens the incoming track of [plan] on an IO thread, in the outgoing track's own sample rate
     * and channel count, and hands it to [deckMixer]. A failure just means no blend: the mixer
     * lets the outgoing track play out when the blend's start arrives with nothing to blend in.
     */
    private fun prepareIncoming(generation: Long, deckMixer: DeckMixer, plan: TransitionPlan) {
        if (preparingPlan == plan) return
        preparingPlan = plan
        scope.launch(Dispatchers.IO) {
            val url = runCatching { quietResolver.resolve(plan.incomingMediaId) }.getOrNull()
            if (url == null || !isPlaybackCurrent(generation)) return@launch
            val source = runCatching {
                // Decoded from the start and skipped to the entry rather than seeked. The entry is
                // a downbeat analysis found by decoding from the start, and a seek into an MP3
                // lands up to a frame away from it, which is heard as a flam against the other
                // track.
                FfmpegAudioSource.open(url, 0L, deckMixer.sampleRate, deckMixer.channels).also { opened ->
                    opened.skip(plan.incomingEntryMs * deckMixer.sampleRate / 1_000L)
                }
            }.onFailure { failure ->
                System.err.println("JvmMediaPlayer: next track not opened for the blend: ${failure.message}")
            }.getOrNull() ?: return@launch
            if (!isPlaybackCurrent(generation) || mixer !== deckMixer) {
                source.close()
                return@launch
            }
            deckMixer.offerIncoming(plan, source)
        }
    }

    private fun resolveAndPlay(
        media: MediaInfo,
        startMs: Long = seekOffsetMs.coerceAtLeast(0L),
        selectionRevision: Long = currentQueueClaim().selectionRevision,
        onPrepared: () -> Unit = {},
    ) {
        if (!queueState.isPlaybackClaimCurrent(selectionRevision, media)) return
        val generation = beginPlaybackSession()
        _isPlaying.value = false
        _state.value = PlayerState.BUFFERING
        onPrepared()

        val job = scope.launch {
            val streamUrl = resolver.resolve(media.id)
            if (!isPlaybackCurrent(generation) ||
                !queueState.isPlaybackClaimCurrent(selectionRevision, media)
            ) return@launch
            if (streamUrl == null) {
                _state.value = PlayerState.ERROR
                return@launch
            }
            openAndPlay(
                generation = generation,
                selectionRevision = selectionRevision,
                media = media,
                streamUrl = streamUrl,
                startMs = startMs.coerceAtLeast(0L),
            )
        }

        synchronized(playbackLock) {
            if (generation == playbackGeneration) {
                resolveJob = job
            } else {
                job.cancel()
            }
        }
    }

    private fun onTrackChanged(claim: JvmQueueClaim<MediaInfo>, autoplay: Boolean) {
        val snapshot = claim.model
        if (!queueState.isPlaybackClaimCurrent(claim.selectionRevision, snapshot.currentItem)) return
        val current = snapshot.currentItem
        _position.value = 0L
        seekOffsetMs = 0L
        when {
            current == null -> {
                cancelPlaybackSession()
                _state.value = PlayerState.IDLE
                _isPlaying.value = false
            }
            autoplay -> resolveAndPlay(
                media = current,
                startMs = 0L,
                selectionRevision = claim.selectionRevision,
                onPrepared = { _playbackOccurrence.advancePlaybackOccurrence() },
            )
            else -> {
                cancelPlaybackSession()
                _isPlaying.value = false
                _state.value = PlayerState.PAUSED
                _playbackOccurrence.advancePlaybackOccurrence()
            }
        }
    }

    /**
     * After a queue change that keeps the current track playing: a plan for a pair that is no
     * longer current-and-next is dropped, and a blend towards a track that is no longer next is
     * abandoned, keeping the track the queue says is current.
     */
    private fun revalidateTransition() {
        val model = currentQueueModel()
        val current = model.currentItem
        val next = model.next(repeat = false).takeIf { it.currentIndex != model.currentIndex }?.currentItem
        val plan = armedPlan
        if (plan != null && (plan.outgoingMediaId != current?.id || plan.incomingMediaId != next?.id)) {
            armedPlan = null
            mixer?.let { deckMixer -> if (deckMixer.blendIncomingMediaId == null) deckMixer.armedPlan = null }
        }
        val deckMixer = mixer ?: return
        val incoming = deckMixer.blendIncomingMediaId ?: return
        if (current != null && incoming != next?.id && incoming != current.id) {
            deckMixer.abandonBlend(keepMediaId = current.id)
        }
    }

    // ── PlatformPlayer implementation ──

    override fun play() {
        if (_isPlaying.value) return
        if (_state.value == PlayerState.BUFFERING) return
        val replayingEndedItem = _state.value == PlayerState.ENDED
        if (replayingEndedItem) {
            seekOffsetMs = 0L
            _position.value = 0L
        }
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
            startPolling(playbackGeneration)
            return
        }
        synchronized(queueOperationLock) {
            val claim = currentQueueClaim()
            claim.model.currentItem?.let {
                if (replayingEndedItem) _playbackOccurrence.advancePlaybackOccurrence()
                resolveAndPlay(it, _position.value.coerceAtLeast(0L), claim.selectionRevision)
            }
        }
    }

    override fun pause() {
        if (!_isPlaying.value && _state.value != PlayerState.BUFFERING) return
        val currentId = currentQueueModel().currentItem?.id
        seekOffsetMs = clockReading()?.takeIf { it.mediaId == currentId }?.positionMs ?: _position.value
        _position.value = seekOffsetMs
        val pausedActivePlayback = synchronized(playbackLock) {
            val currentLine = line
            val thread = playbackThread
            if (_isPlaying.value && currentLine != null && thread != null && thread.isAlive) {
                pauseRequested = true
                currentLine.stop()
                // A transport action ends a blend; the track the queue calls current stays.
                currentId?.let { mixer?.abandonBlend(keepMediaId = it) }
                _isPlaying.value = false
                _state.value = PlayerState.PAUSED
                stopPolling()
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
        if (_isPlaying.value) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        seekOffsetMs = positionMs.coerceAtLeast(0)
        _position.value = seekOffsetMs
        if (
            _state.value == PlayerState.ENDED &&
            (_duration.value <= 0L || seekOffsetMs < _duration.value)
        ) {
            _state.value = PlayerState.PAUSED
        }
        synchronized(queueOperationLock) {
            val claim = currentQueueClaim()
            val current = claim.model.currentItem
            if (current != null && (_isPlaying.value || _state.value == PlayerState.BUFFERING)) {
                resolveAndPlay(current, seekOffsetMs, claim.selectionRevision)
            } else if (current != null && _state.value == PlayerState.PAUSED && playbackThread != null) {
                cancelPlaybackSession()
                _isPlaying.value = false
                _state.value = PlayerState.PAUSED
            }
        }
    }

    override fun seekToPrevious() {
        synchronized(queueOperationLock) {
            val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
            val previous = currentQueueModel()
            val next = mutateQueue(invalidatesPlaybackClaim = true) { it.previous(repeat = false) }
            if (next.model.currentIndex != previous.currentIndex) onTrackChanged(next, autoplay)
        }
    }

    override fun seekToNext() {
        synchronized(queueOperationLock) {
            val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
            val previous = currentQueueModel()
            val next = mutateQueue(invalidatesPlaybackClaim = true) { it.next(repeat = false) }
            if (next.model.currentIndex != previous.currentIndex) {
                onTrackChanged(next, autoplay)
            } else if (autoplay && queueState.isPlaybackClaimCurrent(
                    next.selectionRevision,
                    next.model.currentItem,
                )
            ) {
                cancelPlaybackSession()
                _isPlaying.value = false
                _state.value = PlayerState.ENDED
            }
        }
    }

    override fun setQueue(items: List<MediaInfo>, startIndex: Int) {
        synchronized(queueOperationLock) {
            val claim = mutateQueue(invalidatesPlaybackClaim = true) { PlayQueue.of(items, startIndex) }
            onTrackChanged(claim, autoplay = true)
        }
    }

    override fun restoreQueue(items: List<MediaInfo>, startIndex: Int, positionMs: Long) {
        // 恢复队列但不自动播放（默认实现的 setQueue→pause 存在异步竞态：
        // resolveAndPlay 解析完成晚于 pause，会照样出声）。play() 时会从
        // seekOffsetMs 起播（openAndPlay 尊重该偏移）。
        synchronized(queueOperationLock) {
            val claim = mutateQueue(invalidatesPlaybackClaim = true) { PlayQueue.of(items, startIndex) }
            onTrackChanged(claim, autoplay = false)
            seekOffsetMs = positionMs.coerceAtLeast(0L)
            _position.value = seekOffsetMs
        }
    }

    override fun addToQueue(item: MediaInfo) {
        synchronized(queueOperationLock) {
            mutateQueue(invalidatesPlaybackClaim = false) { it.addItem(item) }
            revalidateTransition()
        }
    }

    override fun clearQueue() {
        synchronized(queueOperationLock) {
            val claim = mutateQueue(invalidatesPlaybackClaim = true) { PlayQueue.empty() }
            onTrackChanged(claim, autoplay = false)
        }
    }

    override fun skipToIndex(index: Int) {
        synchronized(queueOperationLock) {
            val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
            val previous = currentQueueModel()
            val itemIndex = previous.playOrder.getOrNull(index) ?: return@synchronized
            if (itemIndex == previous.currentIndex) return@synchronized
            val claim = mutateQueue(invalidatesPlaybackClaim = true) { queue ->
                queue.skipTo(itemIndex)
            }
            onTrackChanged(claim, autoplay)
        }
    }

    override fun removeFromQueue(position: Int) {
        synchronized(queueOperationLock) {
            val previous = currentQueueModel()
            val itemIndex = previous.playOrder.getOrNull(position) ?: return@synchronized
            if (itemIndex != previous.currentIndex) {
                // The current track keeps playing; only the rows around it change.
                mutateQueue(invalidatesPlaybackClaim = false) { it.removeAtPlayOrderPosition(position) }
                revalidateTransition()
                return@synchronized
            }
            val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
            val claim = mutateQueue(invalidatesPlaybackClaim = true) { it.removeAtPlayOrderPosition(position) }
            onTrackChanged(claim, autoplay && claim.model.currentItem != null)
        }
    }

    override fun moveInQueue(from: Int, to: Int) {
        synchronized(queueOperationLock) {
            mutateQueue(invalidatesPlaybackClaim = false) { it.movePlayOrderPosition(from, to) }
            revalidateTransition()
        }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        synchronized(queueOperationLock) {
            mutateQueue(invalidatesPlaybackClaim = false) { it.setShuffle(enabled) }
            revalidateTransition()
        }
    }

    override fun setVolume(volume: Float) {
        applyVolume(volume, settings) { safeVolume ->
            synchronized(playbackLock) {
                applyVolumeToLine(line, safeVolume)
            }
        }
    }

    override val transitionCapability: TransitionCapability = TransitionCapability.TEMPO_MATCHED

    override fun armTransition(plan: TransitionPlan) {
        synchronized(queueOperationLock) {
            val model = currentQueueModel()
            val next = model.next(repeat = false).takeIf { it.currentIndex != model.currentIndex }
            if (model.currentItem?.id != plan.outgoingMediaId || next?.currentItem?.id != plan.incomingMediaId) {
                return
            }
            armedPlan = plan
            mixer?.let { deckMixer ->
                if (deckMixer.currentMediaId == plan.outgoingMediaId) deckMixer.armedPlan = plan
            }
        }
    }

    override fun disarmTransition() {
        synchronized(queueOperationLock) {
            armedPlan = null
            mixer?.let { deckMixer ->
                // A blend already under way finishes; only a plan that has not started is dropped.
                if (deckMixer.blendIncomingMediaId == null) deckMixer.armedPlan = null
            }
        }
    }

    override fun release() {
        cancelPlaybackSession()
        scope.cancel()
    }

    private companion object {
        const val BLOCK_FRAMES = 2_048

        /** How long before a blend its incoming track is opened: a CDN round trip plus a skip. */
        const val PREPARE_LEAD_MS = 12_000L
    }
}

internal data class JvmQueuePublication<T>(
    val items: List<T>,
    val currentIndex: Int,
    val currentMedia: T?,
    val shuffleEnabled: Boolean,
)

internal fun <T> PlayQueue<T>.asJvmQueuePublication(): JvmQueuePublication<T> =
    JvmQueuePublication(
        items = itemsInPlayOrder,
        currentIndex = positionInPlayOrder,
        currentMedia = currentItem,
        shuffleEnabled = shuffleEnabled,
    )

internal data class JvmQueueClaim<T>(
    val model: PlayQueue<T>,
    val revision: Long,
    val selectionRevision: Long,
)

/** Serializes queue mutation and publication as one atomic operation. */
internal class JvmQueueState<T>(initial: PlayQueue<T> = PlayQueue.empty()) {
    private val lock = Any()
    private var model = initial
    private var revision = 0L
    private var selectionRevision = 0L

    fun mutateAndPublish(
        invalidatesPlaybackClaim: Boolean,
        block: (PlayQueue<T>) -> PlayQueue<T>,
        publish: (PlayQueue<T>) -> Unit,
    ): JvmQueueClaim<T> = synchronized(lock) {
        check(revision != Long.MAX_VALUE) { "JVM queue revision exhausted" }
        if (invalidatesPlaybackClaim) {
            check(selectionRevision != Long.MAX_VALUE) { "JVM selection revision exhausted" }
            selectionRevision += 1L
        }
        model = block(model)
        revision += 1L
        publish(model)
        JvmQueueClaim(model, revision, selectionRevision)
    }

    fun current(): JvmQueueClaim<T> = synchronized(lock) {
        JvmQueueClaim(model, revision, selectionRevision)
    }

    fun isPlaybackClaimCurrent(expectedSelectionRevision: Long, expectedMedia: T?): Boolean =
        synchronized(lock) {
            selectionRevision == expectedSelectionRevision && model.currentItem == expectedMedia
        }
}

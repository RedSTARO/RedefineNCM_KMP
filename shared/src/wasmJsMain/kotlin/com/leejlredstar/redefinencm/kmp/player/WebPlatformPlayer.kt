@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.WebDownloadStorage
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
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
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.events.Event
import kotlin.JsFun

/**
 * Browser [PlatformPlayer] backed by one persistent [HTMLAudioElement].
 *
 * Stream URLs are resolved only when a track is selected for playback and are never stored in
 * the queue. Queue ordering is owned by [PlayQueue], and every public queue flow is published
 * from one [PlayerQueueSnapshot] so shuffle order and the highlighted row cannot drift apart.
 *
 * Browser playback can be rejected by the user agent's autoplay policy. A rejected `play()`
 * promise leaves the prepared track paused, allowing the next explicit play click to start the
 * same audio element without another URL resolution.
 */
class WebPlatformPlayer(
    private val repo: Repository,
    private val settings: PlatformSettings,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : PlatformPlayer {

    private val audio = document.createElement("audio") as HTMLAudioElement
    private val resolver = StreamUrlResolver { mediaId ->
        val id = mediaId.toLongOrNull() ?: return@StreamUrlResolver null
        val qualityName = settings.getString(
            SettingKeys.ONLINE_PLAY_QUALITY,
            SoundQuality.EXHIGH.name,
        )
        val quality = runCatching { SoundQuality.valueOf(qualityName) }
            .getOrDefault(SoundQuality.EXHIGH)
        repo.getSongUrl(id, quality.name.lowercase())
    }

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

    private val _playbackOccurrence = MutableStateFlow(0L)
    override val playbackOccurrence: StateFlow<Long> = _playbackOccurrence.asStateFlow()

    private val _queue = MutableStateFlow<List<MediaInfo>>(emptyList())
    override val queue: StateFlow<List<MediaInfo>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _queueSnapshot = MutableStateFlow(PlayerQueueSnapshot())
    override val queueSnapshot: StateFlow<PlayerQueueSnapshot> = _queueSnapshot.asStateFlow()

    private val _volume = MutableStateFlow(
        playerVolumeFromPercent(
            settings.getLong(SettingKeys.PLAYER_VOLUME, DEFAULT_PLAYER_VOLUME_PERCENT),
        ),
    )
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private var resolveJob: Job? = null
    private var positionJob: Job? = null
    private var playbackGeneration = 0L
    private var loadedMediaId: String? = null
    private var activeObjectUrl: String? = null
    private var pendingSeekMs = 0L
    private var playRequested = false
    private var released = false
    private var lastMediaSessionPositionSecond = -1L

    private val audioListeners = mutableListOf<Pair<String, (Event) -> Unit>>()

    private val playbackLifecycle = BrowserPlaybackLifecycle(::pauseForPageExit)

    private val mediaSessionPlayHandler: () -> Unit = { play() }
    private val mediaSessionPauseHandler: () -> Unit = { pause() }
    private val mediaSessionNextHandler: () -> Unit = { seekToNext() }
    private val mediaSessionPreviousHandler: () -> Unit = { seekToPrevious() }
    private val mediaSessionSeekHandler: (Double) -> Unit = { seconds ->
        if (seconds.isFinite()) seekTo((seconds.coerceAtLeast(0.0) * 1_000.0).toLong())
    }
    private val mediaSessionRelativeSeekHandler: (Double) -> Unit = { seconds ->
        if (seconds.isFinite()) seekTo(_position.value + (seconds * 1_000.0).toLong())
    }

    init {
        audio.preload = "metadata"
        audio.autoplay = false
        audio.volume = _volume.value.toDouble()
        installAudioListeners()
        installWebMediaSessionHandlers(
            onPlay = mediaSessionPlayHandler,
            onPause = mediaSessionPauseHandler,
            onNext = mediaSessionNextHandler,
            onPrevious = mediaSessionPreviousHandler,
            onSeek = mediaSessionSeekHandler,
            onRelativeSeek = mediaSessionRelativeSeekHandler,
        )
    }

    private fun installAudioListeners() {
        listenToAudio("loadedmetadata") {
            if (!eventBelongsToCurrentMedia()) return@listenToAudio
            publishDurationFromAudio()
            applyPendingSeek()
        }
        listenToAudio("durationchange") {
            if (eventBelongsToCurrentMedia()) publishDurationFromAudio()
        }
        listenToAudio("canplay") {
            if (!eventBelongsToCurrentMedia()) return@listenToAudio
            publishDurationFromAudio()
            if (!playRequested && _state.value == PlayerState.BUFFERING) {
                _state.value = PlayerState.PAUSED
                updateWebMediaSessionPlaybackState("paused")
            }
        }
        listenToAudio("playing") {
            if (!eventBelongsToCurrentMedia() || !playRequested) return@listenToAudio
            _isPlaying.value = true
            _state.value = PlayerState.PLAYING
            syncPositionFromAudio()
            startPositionSync()
            updateWebMediaSessionPlaybackState("playing")
            publishMediaSessionPosition(force = true)
        }
        listenToAudio("waiting") { publishBufferingIfRequested() }
        listenToAudio("stalled") { publishBufferingIfRequested() }
        listenToAudio("pause") {
            if (released || !eventBelongsToCurrentMedia()) return@listenToAudio
            stopPositionSync()
            syncPositionFromAudio()
            _isPlaying.value = false
            if (!playRequested && _state.value !in terminalOrEmptyStates) {
                _state.value = PlayerState.PAUSED
            }
            updateWebMediaSessionPlaybackState("paused")
            publishMediaSessionPosition(force = true)
        }
        listenToAudio("timeupdate") {
            if (eventBelongsToCurrentMedia()) syncPositionFromAudio()
        }
        listenToAudio("ended") {
            if (eventBelongsToCurrentMedia()) handleNaturalEnd()
        }
        listenToAudio("error") {
            if (!released && eventBelongsToCurrentMedia()) publishPlaybackError()
        }
    }

    private fun listenToAudio(type: String, listener: (Event) -> Unit) {
        audio.addEventListener(type, listener)
        audioListeners += type to listener
    }

    private fun eventBelongsToCurrentMedia(): Boolean =
        loadedMediaId != null && loadedMediaId == queueModel.currentItem?.id

    private fun publishBufferingIfRequested() {
        if (!eventBelongsToCurrentMedia() || !playRequested) return
        _isPlaying.value = false
        _state.value = PlayerState.BUFFERING
        stopPositionSync()
        syncPositionFromAudio()
        updateWebMediaSessionPlaybackState("paused")
    }

    private fun publishPlaybackError() {
        playRequested = false
        _isPlaying.value = false
        _state.value = PlayerState.ERROR
        stopPositionSync()
        syncPositionFromAudio()
        updateWebMediaSessionPlaybackState("paused")
    }

    /** Publish the visible play order, its current index, and current media as one snapshot. */
    private fun publishQueue() {
        val snapshot = PlayerQueueSnapshot(
            items = queueModel.itemsInPlayOrder,
            currentIndex = queueModel.positionInPlayOrder,
            currentMedia = queueModel.currentItem,
            shuffleEnabled = queueModel.shuffleEnabled,
        )
        _queueSnapshot.value = snapshot
        _queue.value = snapshot.items
        _currentIndex.value = snapshot.currentIndex
        _currentMedia.value = snapshot.currentMedia
        _shuffleEnabled.value = snapshot.shuffleEnabled
        _duration.value = snapshot.currentMedia?.duration?.takeIf { it > 0L } ?: -1L
        lastMediaSessionPositionSecond = -1L
        if (snapshot.currentMedia == null) {
            clearWebMediaSession()
        } else {
            updateWebMediaSessionMetadata(
                title = snapshot.currentMedia.title,
                artist = snapshot.currentMedia.artist,
                album = snapshot.currentMedia.albumTitle,
                artworkUri = snapshot.currentMedia.artworkUri,
            )
            publishMediaSessionPosition(force = true)
        }
    }

    private fun startPositionSync() {
        stopPositionSync()
        positionJob = scope.launch {
            while (isActive && playRequested && !audio.paused) {
                syncPositionFromAudio()
                publishDurationFromAudio()
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionSync() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun syncPositionFromAudio() {
        if (!eventBelongsToCurrentMedia() || audio.readyState <= 0) return
        val currentSeconds = audio.currentTime
        if (!currentSeconds.isFinite() || currentSeconds < 0.0) return
        val positionMs = (currentSeconds * 1_000.0).toLong().coerceAtLeast(0L)
        _position.value = _duration.value.takeIf { it > 0L }
            ?.let { positionMs.coerceAtMost(it) }
            ?: positionMs
        pendingSeekMs = _position.value
        publishMediaSessionPosition()
    }

    private fun publishDurationFromAudio() {
        if (!eventBelongsToCurrentMedia()) return
        val seconds = audio.duration
        if (seconds.isFinite() && seconds > 0.0) {
            _duration.value = (seconds * 1_000.0).toLong().coerceAtLeast(1L)
            publishMediaSessionPosition(force = true)
        }
    }

    private fun applyPendingSeek() {
        if (!eventBelongsToCurrentMedia() || audio.readyState <= 0) return
        val bounded = boundPosition(pendingSeekMs)
        runCatching { audio.currentTime = bounded / 1_000.0 }
        _position.value = bounded
        pendingSeekMs = bounded
        publishMediaSessionPosition(force = true)
    }

    private fun boundPosition(positionMs: Long): Long {
        val nonNegative = positionMs.coerceAtLeast(0L)
        return _duration.value.takeIf { it > 0L }
            ?.let { nonNegative.coerceAtMost(it) }
            ?: nonNegative
    }

    /**
     * Invalidate every asynchronous result from the previous selection. When [clearSource] is
     * true, `load()` aborts the old media request and any pending browser play promise.
     */
    private fun invalidatePlayback(clearSource: Boolean): Long {
        playbackGeneration += 1L
        resolveJob?.cancel()
        resolveJob = null
        stopPositionSync()
        playRequested = false
        _isPlaying.value = false
        if (clearSource) clearAudioSource()
        return playbackGeneration
    }

    private fun clearAudioSource() {
        loadedMediaId = null
        audio.pause()
        audio.removeAttribute("src")
        audio.load()
        revokeActiveObjectUrl()
    }

    private fun revokeActiveObjectUrl() {
        activeObjectUrl?.let(WebDownloadStorage::revokeObjectUrl)
        activeObjectUrl = null
    }

    private fun selectCurrentTrack(autoplay: Boolean, positionMs: Long = 0L) {
        val generation = invalidatePlayback(clearSource = true)
        val media = queueModel.currentItem
        pendingSeekMs = positionMs.coerceAtLeast(0L)
        publishQueue()
        if (media == null) {
            _state.value = PlayerState.IDLE
            _position.value = 0L
            _duration.value = -1L
            return
        }
        _position.value = pendingSeekMs
        _state.value = if (autoplay) PlayerState.BUFFERING else PlayerState.PAUSED
        playRequested = autoplay
        updateWebMediaSessionPlaybackState("paused")
        _playbackOccurrence.advancePlaybackOccurrence()
        if (autoplay) resolveAndLoad(media, generation, pendingSeekMs)
    }

    private fun resolveAndLoad(media: MediaInfo, generation: Long, startMs: Long) {
        resolveJob = scope.launch {
            var createdObjectUrl: String? = null
            try {
                createdObjectUrl = media.id.toLongOrNull()
                    ?.let { DownloadedSongsCache.snapshot()[it]?.uri }
                    ?.let { uri -> runCatching { WebDownloadStorage.createObjectUrl(uri) }.getOrNull() }
                val streamUrl = createdObjectUrl ?: resolver.resolve(media.id)
                if (!isPlaybackCurrent(generation, media)) {
                    createdObjectUrl?.let(WebDownloadStorage::revokeObjectUrl)
                    return@launch
                }
                if (streamUrl.isNullOrBlank()) {
                    createdObjectUrl?.let(WebDownloadStorage::revokeObjectUrl)
                    publishPlaybackError()
                    return@launch
                }

                revokeActiveObjectUrl()
                activeObjectUrl = createdObjectUrl
                loadedMediaId = media.id
                pendingSeekMs = startMs.coerceAtLeast(0L)
                audio.src = streamUrl
                audio.load()
                if (!isPlaybackCurrent(generation, media)) return@launch
                if (playRequested) requestAudioPlay(generation, media)
            } catch (cancelled: CancellationException) {
                if (createdObjectUrl != activeObjectUrl) {
                    createdObjectUrl?.let(WebDownloadStorage::revokeObjectUrl)
                }
                throw cancelled
            } catch (_: Throwable) {
                if (createdObjectUrl != activeObjectUrl) {
                    createdObjectUrl?.let(WebDownloadStorage::revokeObjectUrl)
                }
                if (isPlaybackCurrent(generation, media)) publishPlaybackError()
            }
        }
    }

    private fun isPlaybackCurrent(generation: Long, media: MediaInfo): Boolean =
        !released && generation == playbackGeneration && queueModel.currentItem?.id == media.id

    private fun requestAudioPlay(generation: Long, media: MediaInfo) {
        if (!isPlaybackCurrent(generation, media) || !playRequested) return
        _state.value = PlayerState.BUFFERING
        audio.play().then(
            onFulfilled = {
                if (isPlaybackCurrent(generation, media) && playRequested && !audio.paused) {
                    _isPlaying.value = true
                    _state.value = PlayerState.PLAYING
                    startPositionSync()
                    updateWebMediaSessionPlaybackState("playing")
                    publishMediaSessionPosition(force = true)
                }
                null
            },
            onRejected = {
                // Autoplay rejection is recoverable: keep the resolved source so the next direct
                // user gesture can call play() synchronously. Media/network failures also emit the
                // audio element's `error` event and are promoted to ERROR there.
                if (isPlaybackCurrent(generation, media) && playRequested) {
                    playRequested = false
                    _isPlaying.value = false
                    _state.value = PlayerState.PAUSED
                    stopPositionSync()
                    updateWebMediaSessionPlaybackState("paused")
                }
                null
            },
        )
    }

    override fun play() {
        if (released || playRequested || _isPlaying.value) return
        val media = queueModel.currentItem ?: return

        if (_state.value == PlayerState.ENDED) {
            pendingSeekMs = 0L
            _position.value = 0L
            _playbackOccurrence.advancePlaybackOccurrence()
            if (loadedMediaId == media.id && audio.readyState > 0) {
                runCatching { audio.currentTime = 0.0 }
            }
        }

        playRequested = true
        _state.value = PlayerState.BUFFERING
        if (loadedMediaId == media.id && audio.src.isNotBlank()) {
            requestAudioPlay(playbackGeneration, media)
        } else {
            val generation = invalidatePlayback(clearSource = true)
            playRequested = true
            _state.value = PlayerState.BUFFERING
            resolveAndLoad(media, generation, _position.value.coerceAtLeast(0L))
        }
    }

    override fun pause() {
        if (released || _state.value == PlayerState.IDLE) return
        val wasActive = playRequested || _isPlaying.value || _state.value == PlayerState.BUFFERING
        if (!wasActive) return

        syncPositionFromAudio()
        invalidatePlayback(clearSource = false)
        audio.pause()
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        updateWebMediaSessionPlaybackState("paused")
        publishMediaSessionPosition(force = true)
    }

    override fun togglePlayPause() {
        if (playRequested || _isPlaying.value || _state.value == PlayerState.BUFFERING) {
            pause()
        } else {
            play()
        }
    }

    override fun seekTo(positionMs: Long) {
        if (released || queueModel.currentItem == null) return
        val bounded = boundPosition(positionMs)
        pendingSeekMs = bounded
        _position.value = bounded
        if (eventBelongsToCurrentMedia() && audio.readyState > 0) {
            runCatching { audio.currentTime = bounded / 1_000.0 }
        }
        if (_state.value == PlayerState.ENDED && bounded < _duration.value) {
            _state.value = PlayerState.PAUSED
        }
        publishMediaSessionPosition(force = true)
    }

    override fun seekToPrevious() {
        if (released) return
        val autoplay = playRequested || _isPlaying.value || _state.value == PlayerState.BUFFERING
        val previous = queueModel.previous(repeat = false)
        if (previous.currentIndex == queueModel.currentIndex) return
        queueModel = previous
        selectCurrentTrack(autoplay = autoplay)
    }

    override fun seekToNext() {
        if (released) return
        val autoplay = playRequested || _isPlaying.value || _state.value == PlayerState.BUFFERING
        val next = queueModel.next(repeat = false)
        if (next.currentIndex != queueModel.currentIndex) {
            queueModel = next
            selectCurrentTrack(autoplay = autoplay)
        } else if (autoplay) {
            finishAtQueueEnd()
        }
    }

    override fun setQueue(items: List<MediaInfo>, startIndex: Int) {
        if (released) return
        if (items.isEmpty()) {
            clearQueue()
            return
        }
        queueModel = PlayQueue.of(items, startIndex)
        selectCurrentTrack(autoplay = true)
    }

    override fun restoreQueue(items: List<MediaInfo>, startIndex: Int, positionMs: Long) {
        if (released) return
        if (items.isEmpty()) {
            clearQueue()
            return
        }
        queueModel = PlayQueue.of(items, startIndex)
        val mediaDuration = queueModel.currentItem?.duration?.takeIf { it > 0L }
        val safePosition = mediaDuration
            ?.let { positionMs.coerceIn(0L, it) }
            ?: positionMs.coerceAtLeast(0L)
        selectCurrentTrack(autoplay = false, positionMs = safePosition)
    }

    override fun addToQueue(item: MediaInfo) {
        if (released) return
        queueModel = queueModel.addItem(item)
        publishQueue()
    }

    override fun clearQueue() {
        if (released) return
        invalidatePlayback(clearSource = true)
        queueModel = PlayQueue.empty()
        pendingSeekMs = 0L
        publishQueue()
        _position.value = 0L
        _duration.value = -1L
        _state.value = PlayerState.IDLE
        clearWebMediaSession()
    }

    override fun skipToIndex(index: Int) {
        if (released) return
        val itemIndex = queueModel.playOrder.getOrNull(index) ?: return
        if (itemIndex == queueModel.currentIndex) return
        val autoplay = playRequested || _isPlaying.value || _state.value == PlayerState.BUFFERING
        queueModel = queueModel.skipTo(itemIndex)
        selectCurrentTrack(autoplay = autoplay)
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        if (released) return
        queueModel = queueModel.setShuffle(enabled)
        publishQueue()
    }

    override fun setVolume(volume: Float) {
        if (released) return
        val safeVolume = normalizePlayerVolume(volume)
        val oldPercent = playerVolumeToPercent(_volume.value)
        val newPercent = playerVolumeToPercent(safeVolume)
        _volume.value = safeVolume
        audio.volume = safeVolume.toDouble()
        if (oldPercent != newPercent) {
            settings.setLong(SettingKeys.PLAYER_VOLUME, newPercent)
        }
    }

    private fun handleNaturalEnd() {
        stopPositionSync()
        _isPlaying.value = false
        val next = queueModel.next(repeat = false)
        if (next.currentIndex != queueModel.currentIndex) {
            queueModel = next
            selectCurrentTrack(autoplay = true)
        } else {
            finishAtQueueEnd()
        }
    }

    private fun finishAtQueueEnd() {
        syncPositionFromAudio()
        invalidatePlayback(clearSource = false)
        audio.pause()
        _isPlaying.value = false
        _position.value = _duration.value.coerceAtLeast(0L)
        pendingSeekMs = _position.value
        _state.value = PlayerState.ENDED
        updateWebMediaSessionPlaybackState("paused")
        publishMediaSessionPosition(force = true)
    }

    private fun pauseForPageExit() {
        if (released) return
        // Calling pause(), rather than only HTMLAudioElement.pause(), also cancels a URL lookup
        // that is still BUFFERING. Otherwise its late result could start audio after pagehide.
        if (playRequested || _isPlaying.value || _state.value == PlayerState.BUFFERING) pause()
    }

    override fun release() {
        if (released) return
        playbackLifecycle.dispose()
        clearWebMediaSessionHandlers()
        clearWebMediaSession()
        audioListeners.forEach { (type, listener) -> audio.removeEventListener(type, listener) }
        audioListeners.clear()

        playbackGeneration += 1L
        resolveJob?.cancel()
        resolveJob = null
        stopPositionSync()
        playRequested = false
        _isPlaying.value = false
        loadedMediaId = null
        audio.pause()
        audio.removeAttribute("src")
        audio.load()
        revokeActiveObjectUrl()
        released = true
        scope.cancel()
    }

    private fun publishMediaSessionPosition(force: Boolean = false) {
        val durationMs = _duration.value
        if (durationMs <= 0L) return
        val positionMs = _position.value.coerceIn(0L, durationMs)
        val positionSecond = positionMs / 1_000L
        if (!force && positionSecond == lastMediaSessionPositionSecond) return
        lastMediaSessionPositionSecond = positionSecond
        updateWebMediaSessionPosition(
            durationSeconds = durationMs / 1_000.0,
            positionSeconds = positionMs / 1_000.0,
        )
    }

    private companion object {
        const val POSITION_POLL_INTERVAL_MS = 100L

        val terminalOrEmptyStates = setOf(
            PlayerState.IDLE,
            PlayerState.ENDED,
            PlayerState.ERROR,
        )
    }
}

@JsFun(
    """(onPlay, onPause, onNext, onPrevious, onSeek, onRelativeSeek) => {
        if (!("mediaSession" in navigator)) return;
        const setHandler = (action, handler) => {
            try { navigator.mediaSession.setActionHandler(action, handler); } catch (_) {}
        };
        setHandler("play", () => onPlay());
        setHandler("pause", () => onPause());
        setHandler("nexttrack", () => onNext());
        setHandler("previoustrack", () => onPrevious());
        setHandler("seekto", details => onSeek(details.seekTime || 0));
        setHandler("seekbackward", details => onRelativeSeek(-(details.seekOffset || 10)));
        setHandler("seekforward", details => onRelativeSeek(details.seekOffset || 10));
    }""",
)
private external fun installWebMediaSessionHandlers(
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Double) -> Unit,
    onRelativeSeek: (Double) -> Unit,
)

@JsFun(
    """() => {
        if (!("mediaSession" in navigator)) return;
        for (const action of ["play", "pause", "nexttrack", "previoustrack", "seekto", "seekbackward", "seekforward"]) {
            try { navigator.mediaSession.setActionHandler(action, null); } catch (_) {}
        }
    }""",
)
private external fun clearWebMediaSessionHandlers()

@JsFun(
    """(title, artist, album, artworkUri) => {
        if (!("mediaSession" in navigator) || !("MediaMetadata" in globalThis)) return;
        navigator.mediaSession.metadata = new MediaMetadata({
            title,
            artist,
            album,
            artwork: artworkUri ? [{ src: artworkUri }] : [],
        });
    }""",
)
private external fun updateWebMediaSessionMetadata(
    title: String,
    artist: String,
    album: String,
    artworkUri: String,
)

@JsFun(
    """(state) => {
        if (!("mediaSession" in navigator)) return;
        try { navigator.mediaSession.playbackState = state; } catch (_) {}
    }""",
)
private external fun updateWebMediaSessionPlaybackState(state: String)

@JsFun(
    """(durationSeconds, positionSeconds) => {
        if (!("mediaSession" in navigator) || !navigator.mediaSession.setPositionState) return;
        if (!(durationSeconds > 0) || !Number.isFinite(durationSeconds) || !Number.isFinite(positionSeconds)) return;
        try {
            navigator.mediaSession.setPositionState({
                duration: durationSeconds,
                playbackRate: 1,
                position: Math.max(0, Math.min(positionSeconds, durationSeconds)),
            });
        } catch (_) {}
    }""",
)
private external fun updateWebMediaSessionPosition(
    durationSeconds: Double,
    positionSeconds: Double,
)

@JsFun(
    """() => {
        if (!("mediaSession" in navigator)) return;
        try {
            navigator.mediaSession.metadata = null;
            navigator.mediaSession.playbackState = "none";
            navigator.mediaSession.setPositionState?.();
        } catch (_) {}
    }""",
)
private external fun clearWebMediaSession()

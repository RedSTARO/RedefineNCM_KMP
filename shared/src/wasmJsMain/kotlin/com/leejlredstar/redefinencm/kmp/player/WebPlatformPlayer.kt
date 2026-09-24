@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.toProviderItemIdOrNull
import com.leejlredstar.redefinencm.kmp.download.LocalMediaAssets
import com.leejlredstar.redefinencm.kmp.transition.TransitionCapability
import com.leejlredstar.redefinencm.kmp.transition.TransitionPlan
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.WebDownloadStorage
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.math.abs

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
 *
 * A song transition plays the next track on a second element, its volume and the current one's
 * rate driven every 20 ms. At the swap the second element becomes [audio] and takes its
 * listeners; the first keeps fading out, unheard by anything else, and is emptied at the end.
 */
class WebPlatformPlayer(
    private val repo: Repository,
    private val settings: PlatformSettings,
    private val localMediaAssets: LocalMediaAssets,
    private val providers: MusicProviderRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : BasePlatformPlayer(settings.persistedPlayerVolume()) {

    /** The element the queue's current track plays on; a song transition hands it over. */
    private var audio = document.createElement("audio") as HTMLAudioElement

    private fun resolverFor(recordFailures: Boolean) = StreamUrlResolver { mediaId ->
        resolveStreamUrl(
            mediaId = mediaId,
            providers = providers,
            // The browser has no local download store to prefer.
            localAudioUri = { null },
            onlineUrl = { id, quality -> repo.getSongUrl(id, quality.name.lowercase()) },
            quality = { settings.onlinePlaybackQuality() },
            recordFailures = recordFailures,
        )
    }

    private val resolver = resolverFor(recordFailures = true)

    // The next track of a song transition is prepared before anyone is listening to it; its
    // failure must not reach the "this track cannot play" channel.
    private val quietResolver = resolverFor(recordFailures = false)

    // ── Song transitions ──

    private var armedPlan: TransitionPlan? = null
    private var preparedPlan: TransitionPlan? = null
    private var preparedDeck: HTMLAudioElement? = null
    private var prepareJob: Job? = null
    private var transitionJob: Job? = null
    private var blend: WebBlend? = null

    /** A blend under way: [deck] plays the incoming track, [ghost] the outgoing one after the swap. */
    private class WebBlend(
        val plan: TransitionPlan,
        val deck: HTMLAudioElement,
        val outgoing: HTMLAudioElement,
        val outgoingInfo: MediaInfo?,
        val incomingInfo: MediaInfo?,
    ) {
        var swapped = false
    }

    private var queueModel: PlayQueue<MediaInfo> = PlayQueue.empty()

    private var resolveJob: Job? = null
    private var positionJob: Job? = null
    private var playbackGeneration = 0L
    private var loadedMediaId: String? = null
    private var activeObjectUrl: String? = null
    private var activeArtworkObjectUrl: String? = null
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

    /** Hands every listener from [from] to [to], for the swap of a song transition. */
    private fun moveAudioListeners(from: HTMLAudioElement, to: HTMLAudioElement) {
        audioListeners.forEach { (type, listener) ->
            from.removeEventListener(type, listener)
            to.addEventListener(type, listener)
        }
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
        val snapshot = publishQueue(queueModel)
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
        revokeActiveArtworkObjectUrl()
    }

    private fun revokeActiveObjectUrl() {
        activeObjectUrl?.let(WebDownloadStorage::revokeObjectUrl)
        activeObjectUrl = null
    }

    private fun revokeActiveArtworkObjectUrl() {
        activeArtworkObjectUrl?.let(localMediaAssets::releaseArtworkUri)
        activeArtworkObjectUrl = null
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
            var createdArtworkUrl: String? = null
            try {
                DownloadedSongsCache.ensureInitialized()
                createdObjectUrl = media.id.toLongOrNull()
                    ?.let { DownloadedSongsCache.snapshot()[it]?.uri }
                    ?.let { uri -> runCatching { WebDownloadStorage.createObjectUrl(uri) }.getOrNull() }
                if (createdObjectUrl != null) {
                    createdArtworkUrl = media.id.toLongOrNull()
                        ?.let { songId ->
                            runCatching {
                                localMediaAssets.resolveArtworkUri(songId)
                            }.getOrNull()
                        }
                }
                val streamUrl = createdObjectUrl ?: resolver.resolve(media.id)
                if (!isPlaybackCurrent(generation, media)) {
                    createdObjectUrl?.let(WebDownloadStorage::revokeObjectUrl)
                    createdArtworkUrl?.let(localMediaAssets::releaseArtworkUri)
                    return@launch
                }
                if (streamUrl.isNullOrBlank()) {
                    createdObjectUrl?.let(WebDownloadStorage::revokeObjectUrl)
                    createdArtworkUrl?.let(localMediaAssets::releaseArtworkUri)
                    publishPlaybackError()
                    return@launch
                }

                revokeActiveObjectUrl()
                activeObjectUrl = createdObjectUrl
                revokeActiveArtworkObjectUrl()
                activeArtworkObjectUrl = createdArtworkUrl
                loadedMediaId = media.id
                pendingSeekMs = startMs.coerceAtLeast(0L)
                audio.src = streamUrl
                audio.load()
                updateWebMediaSessionMetadata(
                    title = media.title,
                    artist = media.artist,
                    album = media.albumTitle,
                    artworkUri = createdArtworkUrl ?: media.artworkUri,
                )
                if (!isPlaybackCurrent(generation, media)) return@launch
                if (playRequested) requestAudioPlay(generation, media)
            } catch (cancelled: CancellationException) {
                if (createdObjectUrl != activeObjectUrl) {
                    createdObjectUrl?.let(WebDownloadStorage::revokeObjectUrl)
                }
                if (createdArtworkUrl != activeArtworkObjectUrl) {
                    createdArtworkUrl?.let(localMediaAssets::releaseArtworkUri)
                }
                throw cancelled
            } catch (_: Throwable) {
                if (createdObjectUrl != activeObjectUrl) {
                    createdObjectUrl?.let(WebDownloadStorage::revokeObjectUrl)
                }
                if (createdArtworkUrl != activeArtworkObjectUrl) {
                    createdArtworkUrl?.let(localMediaAssets::releaseArtworkUri)
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
        abandonTransition()
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
        abandonTransition()
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
        abandonTransition()
        val autoplay = playRequested || _isPlaying.value || _state.value == PlayerState.BUFFERING
        val previous = queueModel.previous(repeat = false)
        if (previous.currentIndex == queueModel.currentIndex) return
        queueModel = previous
        selectCurrentTrack(autoplay = autoplay)
    }

    override fun seekToNext() {
        if (released) return
        abandonTransition()
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
        abandonTransition(dropPlan = true)
        if (items.isEmpty()) {
            clearQueue()
            return
        }
        queueModel = PlayQueue.of(items, startIndex)
        selectCurrentTrack(autoplay = true)
    }

    override fun restoreQueue(items: List<MediaInfo>, startIndex: Int, positionMs: Long) {
        if (released) return
        abandonTransition(dropPlan = true)
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
        revalidateTransition()
    }

    override fun clearQueue() {
        if (released) return
        abandonTransition(dropPlan = true)
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
        abandonTransition()
        val selected = queueModel.skipToPlayOrderPosition(index)
        if (selected.currentIndex == queueModel.currentIndex) return
        val autoplay = playRequested || _isPlaying.value || _state.value == PlayerState.BUFFERING
        queueModel = selected
        selectCurrentTrack(autoplay = autoplay)
    }

    override fun removeFromQueue(position: Int) {
        if (released) return
        val itemIndex = queueModel.playOrder.getOrNull(position) ?: return
        val remaining = queueModel.removeAtPlayOrderPosition(position)
        if (remaining.isEmpty) {
            clearQueue()
            return
        }
        if (itemIndex != queueModel.currentIndex) {
            queueModel = remaining
            publishQueue()
            revalidateTransition()
            return
        }
        abandonTransition(dropPlan = true)
        val autoplay = playRequested || _isPlaying.value || _state.value == PlayerState.BUFFERING
        queueModel = remaining
        selectCurrentTrack(autoplay = autoplay)
    }

    override fun moveInQueue(from: Int, to: Int) {
        if (released) return
        val moved = queueModel.movePlayOrderPosition(from, to)
        if (moved === queueModel) return
        queueModel = moved
        publishQueue()
        revalidateTransition()
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        if (released) return
        queueModel = queueModel.setShuffle(enabled)
        publishQueue()
        revalidateTransition()
    }

    override fun setVolume(volume: Float) {
        if (released) return
        applyVolume(volume, settings) { audio.volume = it.toDouble() }
    }

    private fun handleNaturalEnd() {
        blend?.let { active ->
            // The outgoing track ran out inside the blend: the incoming one takes over now.
            if (!active.swapped) {
                swapToIncoming(active)
                return
            }
        }
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
        abandonTransition(dropPlan = true)
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
        revokeActiveArtworkObjectUrl()
        released = true
        scope.cancel()
    }

    override val transitionCapability: TransitionCapability = TransitionCapability.TEMPO_MATCHED

    override fun armTransition(plan: TransitionPlan) {
        if (released || blend != null) return
        val next = queueModel.next(repeat = false).takeIf { it.currentIndex != queueModel.currentIndex }
        if (queueModel.currentItem?.id != plan.outgoingMediaId || next?.currentItem?.id != plan.incomingMediaId) return
        if (preparedPlan != null && preparedPlan?.incomingMediaId != plan.incomingMediaId) discardPrepared()
        armedPlan = plan
        ensureTransitionLoop()
    }

    override fun disarmTransition() {
        if (blend != null) return
        armedPlan = null
        discardPrepared()
        audio.playbackRate = 1.0
    }

    private fun ensureTransitionLoop() {
        if (transitionJob?.isActive == true) return
        transitionJob = scope.launch {
            while (isActive && !released && (armedPlan != null || blend != null)) {
                tickTransition()
                delay(TRANSITION_TICK_MS)
            }
        }
    }

    /** One step of the plan: prepare, ramp, start, drive the gains, swap and finish. */
    private fun tickTransition() {
        blend?.let { active ->
            tickBlend(active)
            return
        }
        val plan = armedPlan ?: return
        if (queueModel.currentItem?.id != plan.outgoingMediaId) return
        if (!playRequested || audio.paused || audio.readyState < 2) return
        val positionMs = audio.currentTime * 1_000.0
        val entry = if (plan.changesTempo) plan.rampStartMs else plan.startMs
        if (audio.playbackRate == 1.0 && positionMs > entry + LATE_TOLERANCE_MS) {
            // A seek landed past the plan's start; the track plays out on its own.
            armedPlan = null
            discardPrepared()
            return
        }
        if (preparedPlan != plan && positionMs >= plan.startMs - PREPARE_LEAD_MS) prepareIncoming(plan)
        if (plan.changesTempo && positionMs >= plan.rampStartMs) {
            audio.playbackRate = plan.outgoingRateAt(positionMs.toLong())
        }
        if (positionMs >= plan.startMs) startBlend(plan)
    }

    private fun prepareIncoming(plan: TransitionPlan) {
        discardPrepared()
        preparedPlan = plan
        prepareJob = scope.launch {
            val url = quietResolver.resolve(plan.incomingMediaId) ?: return@launch
            if (released || armedPlan != plan) return@launch
            val deck = document.createElement("audio") as HTMLAudioElement
            deck.preload = "auto"
            deck.autoplay = false
            deck.volume = 0.0
            deck.src = url
            deck.load()
            if (!awaitMediaEvent(deck, "loadedmetadata")) {
                releaseDeck(deck)
                return@launch
            }
            deck.currentTime = plan.incomingEntryMs / 1_000.0
            if (!awaitMediaEvent(deck, "canplay") || released || armedPlan != plan) {
                releaseDeck(deck)
                return@launch
            }
            preparedDeck = deck
        }
    }

    private fun startBlend(plan: TransitionPlan) {
        val deck = preparedDeck?.takeIf { preparedPlan == plan }
        preparedDeck = null
        preparedPlan = null
        armedPlan = null
        if (deck == null) {
            // The next track was not ready in time; the current one plays out at its own tempo.
            audio.playbackRate = 1.0
            return
        }
        deck.volume = 0.0
        val active = WebBlend(
            plan = plan,
            deck = deck,
            outgoing = audio,
            outgoingInfo = queueModel.currentItem,
            incomingInfo = queueModel.next(repeat = false).currentItem?.takeIf { it.id == plan.incomingMediaId },
        )
        blend = active
        deck.play().then(
            onFulfilled = { null },
            onRejected = {
                if (blend === active) abandonTransition()
                null
            },
        )
    }

    private fun tickBlend(active: WebBlend) {
        val plan = active.plan
        // The incoming track's own clock drives the blend: before it really starts, nothing moves.
        val elapsedMs = (active.deck.currentTime * 1_000.0 - plan.incomingEntryMs).coerceAtLeast(0.0)
        val gains = plan.gainsAt(elapsedMs.toLong())
        val userVolume = _volume.value.toDouble()
        active.outgoing.volume = (userVolume * gains.outgoing).coerceIn(0.0, 1.0)
        active.deck.volume = (userVolume * gains.incoming).coerceIn(0.0, 1.0)
        val outgoingInfo = active.outgoingInfo
        val incomingInfo = active.incomingInfo
        _transitionBlend.value = if (elapsedMs <= 0.0 || outgoingInfo == null || incomingInfo == null) {
            null
        } else {
            TransitionBlend(outgoingInfo, incomingInfo, plan, elapsedMs.toLong().coerceAtMost(plan.overlapMs))
        }
        // Keep the outgoing beat on the incoming one: nudge its rate by the phase error, up to
        // 4 %, the way a DJ rides the pitch fader. Browsers start play() tens of milliseconds late.
        if (!active.outgoing.ended && elapsedMs > 0.0) {
            val expected = plan.startMs + elapsedMs * plan.outgoingRate
            val errorMs = (active.outgoing.currentTime * 1_000.0 - expected) / plan.outgoingRate
            val correction = if (abs(errorMs) < 5.0) 0.0 else (errorMs / 1_000.0).coerceIn(-0.04, 0.04)
            active.outgoing.playbackRate = plan.outgoingRate * (1.0 - correction)
        }
        if (!active.swapped && elapsedMs >= plan.swapAfterMs) swapToIncoming(active)
        if (elapsedMs >= plan.overlapMs || active.outgoing.ended) finishBlend(active)
    }

    /** The incoming track becomes the current one, through the one queue publication path. */
    private fun swapToIncoming(active: WebBlend) {
        val advanced = queueModel.next(repeat = false)
        if (advanced.currentIndex == queueModel.currentIndex || advanced.currentItem?.id != active.plan.incomingMediaId) {
            abandonTransition()
            return
        }
        active.swapped = true
        moveAudioListeners(active.outgoing, active.deck)
        audio = active.deck
        queueModel = advanced
        loadedMediaId = active.plan.incomingMediaId
        playRequested = true
        publishQueue()
        publishDurationFromAudio()
        syncPositionFromAudio()
        _isPlaying.value = true
        _state.value = PlayerState.PLAYING
        startPositionSync()
        updateWebMediaSessionPlaybackState("playing")
        // Last, so observers reading currentMedia on this change already see the new track.
        _playbackOccurrence.advancePlaybackOccurrence()
    }

    private fun finishBlend(active: WebBlend) {
        if (!active.swapped) swapToIncoming(active)
        if (blend !== active) return
        releaseDeck(active.outgoing)
        audio.volume = _volume.value.toDouble()
        audio.playbackRate = 1.0
        blend = null
        _transitionBlend.value = null
    }

    /**
     * Ends a blend or a ramp that is under way, keeping the track the queue calls current at its
     * own tempo and volume. [dropPlan] also forgets a plan that has not started, for actions that
     * replace the current track.
     */
    private fun abandonTransition(dropPlan: Boolean = false) {
        val active = blend
        if (active != null) {
            blend = null
            _transitionBlend.value = null
            if (active.swapped) {
                releaseDeck(active.outgoing)
            } else {
                releaseDeck(active.deck)
            }
            audio.volume = _volume.value.toDouble()
        }
        audio.playbackRate = 1.0
        if (dropPlan || active != null) {
            armedPlan = null
            discardPrepared()
        }
    }

    /** After a queue change that keeps the current track: drop a plan for a pair that is gone. */
    private fun revalidateTransition() {
        val next = queueModel.next(repeat = false).takeIf { it.currentIndex != queueModel.currentIndex }?.currentItem
        val active = blend
        if (active != null && !active.swapped && active.plan.incomingMediaId != next?.id) {
            abandonTransition()
        }
        val plan = armedPlan ?: return
        if (plan.outgoingMediaId != queueModel.currentItem?.id || plan.incomingMediaId != next?.id) {
            armedPlan = null
            discardPrepared()
            if (blend == null) audio.playbackRate = 1.0
        }
    }

    private fun discardPrepared() {
        prepareJob?.cancel()
        prepareJob = null
        preparedDeck?.let(::releaseDeck)
        preparedDeck = null
        preparedPlan = null
    }

    private fun releaseDeck(deck: HTMLAudioElement) {
        if (deck === audio) return
        deck.pause()
        deck.removeAttribute("src")
        deck.load()
    }

    /** Waits for [type] on [element]; false on an `error` event instead. */
    private suspend fun awaitMediaEvent(element: HTMLAudioElement, type: String): Boolean {
        val outcome = CompletableDeferred<Boolean>()
        val onEvent: (Event) -> Unit = { outcome.complete(true) }
        val onError: (Event) -> Unit = { outcome.complete(false) }
        element.addEventListener(type, onEvent)
        element.addEventListener("error", onError)
        return try {
            outcome.await()
        } finally {
            element.removeEventListener(type, onEvent)
            element.removeEventListener("error", onError)
        }
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
        const val TRANSITION_TICK_MS = 20L

        /** How long before a blend its incoming track is loaded: a CDN round trip plus a seek. */
        const val PREPARE_LEAD_MS = 12_000L
        const val LATE_TOLERANCE_MS = 250.0

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

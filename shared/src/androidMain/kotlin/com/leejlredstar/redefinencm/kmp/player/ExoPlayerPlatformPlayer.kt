package com.leejlredstar.redefinencm.kmp.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.download.LocalMediaAssets
import com.leejlredstar.redefinencm.kmp.transition.TransitionCapability
import com.leejlredstar.redefinencm.kmp.transition.TransitionKind
import com.leejlredstar.redefinencm.kmp.transition.TransitionPlan
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.findDownloadedSongUri
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Android PlatformPlayer backed by ExoPlayer + Media3.
 *
 * Lifecycle: created as a Koin singleton in [AndroidPlatformModule]; must be constructed on the
 * main thread (ExoPlayer requirement). PlaybackService wraps [sessionPlayer] in a MediaSession
 * for OS media controls and background playback.
 *
 * URL resolution: placeholder URIs (`redefinencm://playbackPlaceHolder?id=xxx`) are intercepted
 * by [RedirectingDataSourceFactory] which calls [Repository.getSongUrl] synchronously (via
 * runBlocking) on ExoPlayer's IO thread at play time. Stream URLs are never persisted.
 *
 * There are two ExoPlayers, and one of them is active: it holds the queue, and every flow and the
 * MediaSession follow it. The other exists for song transitions. It is loaded with the same
 * playlist and play order, starts the next track under the current one, and becomes the active
 * player at the plan's swap point; the old one fades out unheard by anything else and is
 * emptied. Without transitions the second player is never built.
 */
@OptIn(UnstableApi::class)
class ExoPlayerPlatformPlayer(
    context: Context,
    private val repo: Repository,
    private val settings: PlatformSettings,
    private val localMediaAssets: LocalMediaAssets,
    private val providers: MusicProviderRegistry,
) : BasePlatformPlayer(settings.persistedPlayerVolume()) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val primary = buildDeck()
    private var secondary: ExoPlayer? = null

    /** The player that holds the queue. Read by resolvers on ExoPlayer's loading threads. */
    @Volatile private var active: ExoPlayer = primary

    private val _sessionPlayer = MutableStateFlow<Player>(primary)

    /**
     * The player the MediaSession must wrap. It changes when a song transition hands playback to
     * the other player; PlaybackService follows it with `MediaSession.setPlayer`.
     */
    val sessionPlayer: StateFlow<Player> = _sessionPlayer.asStateFlow()

    private var positionJob: Job? = null

    /**
     * Builds one player. Its resolver records a failed track in the provider registry only while
     * this player is the active one: the other player prepares the next track before anyone is
     * listening to it, and that failure is not the "this track cannot play" the screen reports.
     */
    private fun buildDeck(): ExoPlayer {
        lateinit var deck: ExoPlayer
        val resolver = StreamUrlResolver { mediaId ->
            resolveStreamUrl(
                mediaId = mediaId,
                providers = providers,
                localAudioUri = { id ->
                    findDownloadedSongUri(id)?.also { publishLocalMediaSessionArtwork(mediaId) }
                },
                onlineUrl = { id, quality -> repo.getSongUrl(id, quality.name.lowercase()) },
                quality = { settings.onlinePlaybackQuality() },
                recordFailures = deck === active,
            )
        }
        deck = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    RedirectingDataSourceFactory(DefaultDataSource.Factory(appContext), resolver),
                ),
            )
            .build()
        deck.volume = _volume.value
        deck.addListener(DeckListener(deck))
        return deck
    }

    /** Publishes what one player reports, but only while that player is the active one. */
    private inner class DeckListener(private val deck: ExoPlayer) : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            if (deck !== active) return
            _isPlaying.value = playing
            updateStateFromExo()
            if (playing) startPositionSync() else stopPositionSync()
            if (playing && (armedPlan != null || blend != null)) ensureTransitionLoop()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // The outgoing player of a blend reached the end of its track before the plan's end
            // (the container's duration was a little long): the blend is over, and the incoming
            // player takes over before this player's pause could be published as the app's.
            val running = blend ?: return
            if (deck === running.outgoing && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                finishBlend(running)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (deck !== active) return
            updateStateFromExo()
            val dur = deck.duration
            if (dur != C.TIME_UNSET) _duration.value = dur
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (deck !== active) return
            val isNewOccurrence = if (mediaItem != null) {
                val currentWindowIndex = deck.currentMediaItemIndex
                val shouldAdvance = shouldAdvanceMedia3PlaybackOccurrence(
                    reason = reason,
                    currentWindowIndex = currentWindowIndex,
                    previousWindowIndex = lastOccurrenceMediaItemIndex,
                )
                lastOccurrenceMediaItemIndex = currentWindowIndex
                shouldAdvance
            } else {
                false
            }
            // 每次切歌都完整重建列表与高亮：随机模式下 ExoPlayer 可能在不触发
            // onTimelineChanged 的情况下重排内部顺序，缓存索引会失效（原版修过的回归 bug）
            rebuildQueue()
            // occurrence 最后发布：观察方收到它时，currentMedia 已属于新播放项。
            if (isNewOccurrence) _playbackOccurrence.advancePlaybackOccurrence()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (deck !== active) return
            // 切换随机模式改变播放顺序，必须整体重建列表与高亮
            rebuildQueue()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (deck !== active) return
            rebuildQueue()
        }
    }

    private fun updateStateFromExo() {
        val player = active
        _state.value = when {
            player.isPlaying -> PlayerState.PLAYING
            player.playbackState == Player.STATE_BUFFERING -> PlayerState.BUFFERING
            player.playbackState == Player.STATE_ENDED -> PlayerState.ENDED
            player.playbackState == Player.STATE_READY -> PlayerState.PAUSED
            else -> PlayerState.IDLE
        }
    }

    private fun publishLocalMediaSessionArtwork(mediaId: String) {
        val songId = mediaId.toLongOrNull() ?: return
        scope.launch {
            val localArtworkUri = kotlinx.coroutines.withContext(Dispatchers.Default) {
                runCatching { localMediaAssets.resolveArtworkUri(songId) }.getOrNull()
            }?.takeIf(String::isNotBlank) ?: return@launch
            val player = active
            if (player.currentMediaItem?.mediaId != mediaId) return@launch
            val index = player.currentMediaItemIndex
            if (index == C.INDEX_UNSET) return@launch
            val current = player.getMediaItemAt(index)
            if (current.mediaMetadata.artworkUri?.toString() == localArtworkUri) return@launch
            val metadata = current.mediaMetadata
                .buildUpon()
                .setArtworkUri(Uri.parse(localArtworkUri))
                .build()
            player.replaceMediaItem(
                index,
                current.buildUpon().setMediaMetadata(metadata).build(),
            )
        }
    }

    /** 播放顺序 → ExoPlayer 窗口索引的映射，与 _queue/_currentIndex 同源重建。 */
    private var playOrderWindowIndices: List<Int> = emptyList()
    private var lastOccurrenceMediaItemIndex: Int = C.INDEX_UNSET

    /**
     * 依据当前 timeline（按播放顺序，含随机模式）重建可见队列、窗口顺序索引与当前高亮。
     * 三者必须来自同一次重建（从原版继承的随机模式不变量），不要拆开更新。
     */
    private fun rebuildQueue() {
        val player = active
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            playOrderWindowIndices = emptyList()
            publishQueueSnapshot(PlayerQueueSnapshot())
            _position.value = 0L
            _duration.value = -1L
            return
        }

        val shuffle = player.shuffleModeEnabled
        val items = mutableListOf<MediaInfo>()
        val indices = mutableListOf<Int>()
        var idx = timeline.getFirstWindowIndex(shuffle)
        while (idx != C.INDEX_UNSET) {
            val item = player.getMediaItemAt(idx)
            items += (item.localConfiguration?.tag as? MediaInfo) ?: item.toMediaInfo()
            indices += idx
            idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, shuffle)
        }

        playOrderWindowIndices = indices
        // 高亮位置直接由本次重建出的 indices 计算，绝不读取旧缓存
        val currentIndex = indices.indexOf(player.currentMediaItemIndex)
        publishQueueSnapshot(
            PlayerQueueSnapshot(
                items = items,
                currentIndex = currentIndex,
                currentMedia = items.getOrNull(currentIndex),
                shuffleEnabled = shuffle,
            ),
        )
        publishCurrentPositionAndDuration()
    }

    private fun publishCurrentPositionAndDuration() {
        val player = active
        _position.value = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration
        _duration.value = when {
            duration != C.TIME_UNSET -> duration
            else -> _queue.value.getOrNull(_currentIndex.value)?.duration?.takeIf { it > 0 } ?: -1L
        }
    }

    private fun startPositionSync() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                _position.value = active.currentPosition
                delay(100)
            }
        }
    }

    private fun stopPositionSync() {
        positionJob?.cancel()
        positionJob = null
    }

    // ── PlatformPlayer controls ──

    override fun play() {
        val player = active
        if (player.playbackState == Player.STATE_ENDED && player.currentMediaItem != null) {
            lastOccurrenceMediaItemIndex = player.currentMediaItemIndex
            player.seekToDefaultPosition()
            _position.value = 0L
            _playbackOccurrence.advancePlaybackOccurrence()
        }
        player.play()
    }

    override fun pause() {
        abandonTransition()
        active.pause()
    }

    override fun togglePlayPause() = if (active.isPlaying) pause() else play()

    override fun seekTo(positionMs: Long) {
        abandonTransition()
        active.seekTo(positionMs)
        _position.value = positionMs.coerceAtLeast(0L)
    }

    override fun seekToPrevious() {
        abandonTransition(dropPlan = true)
        active.seekToPreviousMediaItem()
    }

    override fun seekToNext() {
        abandonTransition(dropPlan = true)
        active.seekToNextMediaItem()
    }

    override fun skipToIndex(index: Int) {
        abandonTransition(dropPlan = true)
        // index 是播放顺序位置，需映射回 ExoPlayer 窗口索引（随机模式下二者不同）
        val windowIndex = playOrderWindowIndices.getOrNull(index) ?: index
        active.seekToDefaultPosition(windowIndex)
    }

    override fun removeFromQueue(position: Int) {
        // Same mapping as skipToIndex. The timeline callback then rebuilds the visible queue,
        // the window map and the highlight together; nothing is patched here by hand.
        val windowIndex = playOrderWindowIndices.getOrNull(position) ?: return
        if (windowIndex == active.currentMediaItemIndex) {
            abandonTransition(dropPlan = true)
            active.removeMediaItem(windowIndex)
            return
        }
        active.removeMediaItem(windowIndex)
        mirrorToIncoming { it.removeMediaItem(windowIndex) }
        revalidateTransition()
    }

    override fun moveInQueue(from: Int, to: Int) {
        // PlayQueue's rule: no reordering under shuffle, where a window move would not change
        // the order anything plays in.
        if (active.shuffleModeEnabled) return
        val fromWindow = playOrderWindowIndices.getOrNull(from) ?: return
        val toWindow = playOrderWindowIndices.getOrNull(to) ?: return
        active.moveMediaItem(fromWindow, toWindow)
        mirrorToIncoming { it.moveMediaItem(fromWindow, toWindow) }
        revalidateTransition()
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        // The two players would each draw their own random order; the plan is made again for
        // whatever comes next under the new order.
        abandonTransition(dropPlan = true)
        active.shuffleModeEnabled = enabled
    }

    override fun setVolume(volume: Float) {
        applyVolume(volume, settings) { safeVolume ->
            // During a blend the transition loop scales both players by this on its next step.
            if (blend == null) active.volume = safeVolume
        }
    }

    override fun setQueue(items: List<MediaInfo>, startIndex: Int) {
        abandonTransition(dropPlan = true)
        if (items.isEmpty()) {
            clearQueue()
            return
        }
        val player = active
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        publishImmediateQueue(items, safeIndex, 0L)
        player.setMediaItems(items.map { it.toExoMediaItem() }, safeIndex, 0L)
        lastOccurrenceMediaItemIndex = safeIndex
        _playbackOccurrence.advancePlaybackOccurrence()
        player.prepare()
        player.play()
    }

    override fun restoreQueue(items: List<MediaInfo>, startIndex: Int, positionMs: Long) {
        abandonTransition(dropPlan = true)
        if (items.isEmpty()) {
            clearQueue()
            return
        }
        val player = active
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        val safePosition = positionMs.coerceAtLeast(0L)
        stopPositionSync()
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        publishImmediateQueue(items, safeIndex, safePosition)
        player.setMediaItems(items.map { it.toExoMediaItem() }, safeIndex, safePosition)
        lastOccurrenceMediaItemIndex = safeIndex
        _playbackOccurrence.advancePlaybackOccurrence()
        player.prepare() // 只装载不播放（原版恢复时注释掉了 play()）
    }

    private fun publishImmediateQueue(items: List<MediaInfo>, startIndex: Int, positionMs: Long) {
        val safeIndex = if (items.isEmpty()) -1 else startIndex.coerceIn(0, items.lastIndex)
        playOrderWindowIndices = items.indices.toList()
        publishQueueSnapshot(
            PlayerQueueSnapshot(
                items = items,
                currentIndex = safeIndex,
                currentMedia = items.getOrNull(safeIndex),
                shuffleEnabled = _shuffleEnabled.value,
            ),
        )
        _position.value = positionMs.coerceAtLeast(0L)
        _duration.value = items.getOrNull(safeIndex)?.duration?.takeIf { it > 0 } ?: -1L
    }

    override fun addToQueue(item: MediaInfo) {
        val mediaItem = item.toExoMediaItem()
        active.addMediaItem(mediaItem)
        mirrorToIncoming { it.addMediaItem(mediaItem) }
        _queue.value = _queue.value + item
        revalidateTransition()
    }

    override fun clearQueue() {
        abandonTransition(dropPlan = true)
        stopPositionSync()
        active.clearMediaItems()
        playOrderWindowIndices = emptyList()
        lastOccurrenceMediaItemIndex = C.INDEX_UNSET
        _queue.value = emptyList()
        _currentIndex.value = -1
        _currentMedia.value = null
        _position.value = 0L
        _duration.value = -1L
        _isPlaying.value = false
        _state.value = PlayerState.IDLE
    }

    override fun release() {
        scope.cancel()
        primary.release()
        secondary?.release()
    }

    // ── Song transitions ──

    override val transitionCapability: TransitionCapability = TransitionCapability.TEMPO_MATCHED

    private var armedPlan: TransitionPlan? = null
    private var preparedPlan: TransitionPlan? = null
    private var blend: AndroidBlend? = null
    private var transitionJob: Job? = null

    /**
     * A blend under way. [incoming] plays its track from the beginning, silent until the plan's
     * entry, and was started early enough to reach the entry when [outgoing] reaches the plan's
     * start: a seek into an MP3 lands up to a frame away from the entry analysis found, which is
     * heard as a flam, while a player that plays from the start agrees with the analysis.
     */
    private class AndroidBlend(val plan: TransitionPlan, val incoming: ExoPlayer, val outgoing: ExoPlayer) {
        var swapped = false
        var lastRateChangeAt = 0L
        var outgoingRate = 1f
    }

    override fun armTransition(plan: TransitionPlan) {
        if (blend != null) return
        val player = active
        val next = nextWindowIndex(player)
        if (player.currentMediaItem?.mediaId != plan.outgoingMediaId || next == C.INDEX_UNSET ||
            player.getMediaItemAt(next).mediaId != plan.incomingMediaId
        ) return
        if (preparedPlan != null && preparedPlan != plan) discardPrepared()
        armedPlan = plan
        ensureTransitionLoop()
    }

    override fun disarmTransition() {
        if (blend != null) return
        armedPlan = null
        discardPrepared()
        setSpeed(active, 1f)
    }

    private fun ensureTransitionLoop() {
        if (transitionJob?.isActive == true) return
        transitionJob = scope.launch {
            while (isActive && (armedPlan != null || blend != null)) {
                tickTransition()
                delay(TRANSITION_TICK_MS)
            }
        }
    }

    private fun nextWindowIndex(player: ExoPlayer): Int {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return C.INDEX_UNSET
        return timeline.getNextWindowIndex(
            player.currentMediaItemIndex,
            Player.REPEAT_MODE_OFF,
            player.shuffleModeEnabled,
        )
    }

    private suspend fun tickTransition() {
        blend?.let { running ->
            tickBlend(running)
            return
        }
        val plan = armedPlan ?: return
        val player = active
        if (player.currentMediaItem?.mediaId != plan.outgoingMediaId || !player.isPlaying) return
        val position = player.currentPosition
        val rate = plan.outgoingRateAt(position)
        // The incoming player plays from the start of its track, so it starts early by its entry.
        val blendStart = plan.startMs - (plan.incomingEntryMs * rate).toLong()
        val tolerance = if (plan.kind == TransitionKind.BEAT_MATCHED) LATE_BEAT_MATCHED_MS else LATE_CROSSFADE_MS
        if (position > blendStart + tolerance) {
            // A seek, or a resume, landed past the point the blend had to start; the track plays
            // out on its own rather than with its beats off the incoming ones.
            armedPlan = null
            discardPrepared()
            setSpeed(player, 1f)
            return
        }
        if (preparedPlan != plan && position >= blendStart - PREPARE_LEAD_MS) prepareIncoming(plan)
        if (plan.changesTempo && position >= plan.rampStartMs) setSpeed(player, quantizeRate(rate))
        val untilStart = ((blendStart - position) / rate).toLong()
        if (untilStart <= TRANSITION_TICK_MS) {
            // Start on the millisecond rather than on the next tick: every millisecond late is a
            // millisecond the beats start apart.
            if (untilStart > 0) delay(untilStart)
            if (armedPlan == plan && active === player) startBlend(plan)
        }
    }

    /** Loads the other player with the same playlist and play order, at the next track's start. */
    private fun prepareIncoming(plan: TransitionPlan) {
        discardPrepared()
        val player = active
        val next = nextWindowIndex(player)
        if (next == C.INDEX_UNSET || player.getMediaItemAt(next).mediaId != plan.incomingMediaId) return
        val other = if (player === primary) secondary ?: buildDeck().also { secondary = it } else primary
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        other.volume = 0f
        other.playWhenReady = false
        setSpeed(other, 1f)
        other.setMediaItems(items, next, 0L)
        other.shuffleModeEnabled = player.shuffleModeEnabled
        if (player.shuffleModeEnabled && playOrderWindowIndices.size == items.size) {
            other.setShuffleOrder(
                ShuffleOrder.DefaultShuffleOrder(playOrderWindowIndices.toIntArray(), System.nanoTime()),
            )
        }
        other.prepare()
        preparedPlan = plan
    }

    private fun inactiveDeck(): ExoPlayer? = if (active === primary) secondary else primary

    private fun startBlend(plan: TransitionPlan) {
        armedPlan = null
        val other = inactiveDeck()?.takeIf { preparedPlan == plan && it.playbackState == Player.STATE_READY }
        preparedPlan = null
        if (other == null) {
            // The next track was not ready in time; the current one plays out at its own tempo.
            discardPrepared()
            setSpeed(active, 1f)
            return
        }
        val outgoing = active
        // Played out, the outgoing track must stop rather than roll into the next item, which the
        // incoming player is already playing.
        outgoing.pauseAtEndOfMediaItems = true
        other.volume = 0f
        other.play()
        blend = AndroidBlend(plan, other, outgoing).also { it.outgoingRate = outgoing.playbackParameters.speed }
    }

    private fun tickBlend(blend: AndroidBlend) {
        val plan = blend.plan
        val incomingPosition = blend.incoming.currentPosition
        // The incoming player's own clock drives the blend: it reaches the entry after its silent
        // pre-roll, and before that nothing of it sounds.
        val beforeEntry = incomingPosition < plan.incomingEntryMs
        val elapsedMs = (incomingPosition - plan.incomingEntryMs).coerceAtLeast(0L)
        val gains = plan.gainsAt(elapsedMs)
        val userVolume = _volume.value
        blend.outgoing.volume = userVolume * gains.outgoing
        blend.incoming.volume = if (beforeEntry) 0f else userVolume * gains.incoming
        if (beforeEntry) {
            // A long entry can start the pre-roll before the outgoing tempo ramp; keep ramping.
            val position = blend.outgoing.currentPosition
            if (plan.changesTempo && position >= plan.rampStartMs) {
                blend.outgoingRate = quantizeRate(plan.outgoingRateAt(position))
                setSpeed(blend.outgoing, blend.outgoingRate)
            }
        } else {
            keepBeatsTogether(blend, elapsedMs)
        }
        if (!blend.swapped && elapsedMs >= plan.swapAfterMs) swapToIncoming(blend)
        if (this.blend !== blend) return
        val outgoingDone = blend.outgoing.playbackState == Player.STATE_ENDED ||
            blend.outgoing.currentMediaItem?.mediaId != plan.outgoingMediaId
        if (elapsedMs >= plan.overlapMs || outgoingDone) finishBlend(blend)
    }

    /**
     * Keeps the outgoing beat on the incoming one. Both players report positions that account for
     * their output latency, so their difference is the phase error; the outgoing player's speed is
     * nudged by up to 4 %, at most every 250 ms, because each change restarts ExoPlayer's
     * time-stretcher, and the outgoing track is the one fading away.
     */
    private fun keepBeatsTogether(blend: AndroidBlend, elapsedMs: Long) {
        val plan = blend.plan
        val now = SystemClock.elapsedRealtime()
        if (now - blend.lastRateChangeAt < RATE_CHANGE_INTERVAL_MS) return
        val expected = plan.startMs + elapsedMs * plan.outgoingRate
        val errorMs = (blend.outgoing.currentPosition - expected) / plan.outgoingRate
        val target = if (abs(errorMs) < PHASE_DEADBAND_MS) {
            plan.outgoingRate
        } else {
            plan.outgoingRate * (1.0 - (errorMs / 1_000.0).coerceIn(-MAX_NUDGE, MAX_NUDGE))
        }
        val quantized = quantizeRate(target)
        if (quantized != blend.outgoingRate) {
            blend.outgoingRate = quantized
            blend.lastRateChangeAt = now
            setSpeed(blend.outgoing, quantized)
        }
    }

    /** The incoming player becomes the active one, and everything that follows the queue with it. */
    private fun swapToIncoming(blend: AndroidBlend) {
        val incoming = blend.incoming
        if (incoming.currentMediaItem?.mediaId != blend.plan.incomingMediaId) {
            abandonTransition()
            return
        }
        blend.swapped = true
        active = incoming
        lastOccurrenceMediaItemIndex = incoming.currentMediaItemIndex
        rebuildQueue()
        _isPlaying.value = incoming.isPlaying
        updateStateFromExo()
        incoming.duration.takeIf { it != C.TIME_UNSET }?.let { _duration.value = it }
        if (incoming.isPlaying) startPositionSync() else stopPositionSync()
        _sessionPlayer.value = incoming
        publishLocalMediaSessionArtwork(blend.plan.incomingMediaId)
        // Last, so observers reading currentMedia on this change already see the new track.
        _playbackOccurrence.advancePlaybackOccurrence()
    }

    private fun finishBlend(blend: AndroidBlend) {
        if (!blend.swapped) swapToIncoming(blend)
        if (this.blend !== blend) return
        this.blend = null
        emptyDeck(blend.outgoing)
        active.volume = _volume.value
        setSpeed(active, 1f)
    }

    /**
     * Ends a blend or a ramp under way, keeping the track the queue calls current at its own tempo
     * and volume. [dropPlan] also forgets a plan that has not started, for actions that replace the
     * current track.
     */
    private fun abandonTransition(dropPlan: Boolean = false) {
        val running = blend
        if (running != null) {
            blend = null
            if (running.swapped) emptyDeck(running.outgoing) else emptyDeck(running.incoming)
            active.volume = _volume.value
        }
        active.pauseAtEndOfMediaItems = false
        setSpeed(active, 1f)
        if (dropPlan || running != null) {
            armedPlan = null
            discardPrepared()
        }
    }

    /** After a queue change that keeps the current track: drop a plan for a pair that is gone. */
    private fun revalidateTransition() {
        val player = active
        val next = nextWindowIndex(player).takeIf { it != C.INDEX_UNSET }?.let { player.getMediaItemAt(it).mediaId }
        val running = blend
        if (running != null && !running.swapped && running.plan.incomingMediaId != next) abandonTransition()
        val plan = armedPlan ?: return
        if (plan.outgoingMediaId != player.currentMediaItem?.mediaId || plan.incomingMediaId != next) {
            armedPlan = null
            discardPrepared()
            if (blend == null) setSpeed(player, 1f)
        }
    }

    /** Applies a queue edit to the other player too while it holds a copy of the playlist. */
    private fun mirrorToIncoming(edit: (ExoPlayer) -> Unit) {
        val running = blend
        val other = when {
            running != null -> running.incoming.takeIf { !running.swapped }
            preparedPlan != null -> inactiveDeck()
            else -> null
        } ?: return
        if (other.mediaItemCount > 0) edit(other)
    }

    private fun discardPrepared() {
        preparedPlan = null
        val other = inactiveDeck()
        if (other != null && blend?.incoming !== other && blend?.outgoing !== other) emptyDeck(other)
    }

    private fun emptyDeck(deck: ExoPlayer) {
        if (deck === active) return
        deck.pauseAtEndOfMediaItems = false
        deck.pause()
        deck.stop()
        deck.clearMediaItems()
        deck.volume = _volume.value
        setSpeed(deck, 1f)
    }

    private fun setSpeed(player: ExoPlayer, speed: Float) {
        if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
    }

    /** Rates change in steps of 0.25 %: finer steps are inaudible and each one restarts Sonic. */
    private fun quantizeRate(rate: Double): Float = ((rate * 400.0).roundToInt() / 400.0).toFloat()

    private fun MediaInfo.toExoMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(placeholderUri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(albumTitle)
                .setArtworkUri(artworkUri.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) })
                .build()
        )
        .setTag(this)
        .build()

    private fun MediaItem.toMediaInfo(): MediaInfo {
        val meta = mediaMetadata
        return MediaInfo(
            id = mediaId,
            title = meta.title?.toString() ?: "",
            artist = meta.artist?.toString() ?: "",
            albumTitle = meta.albumTitle?.toString() ?: "",
            artworkUri = meta.artworkUri?.toString() ?: "",
            placeholderUri = localConfiguration?.uri?.toString() ?: "",
        )
    }

    private companion object {
        const val TRANSITION_TICK_MS = 20L
        const val RATE_CHANGE_INTERVAL_MS = 250L
        const val PHASE_DEADBAND_MS = 8.0
        const val MAX_NUDGE = 0.04

        /** How long before a blend its incoming track is loaded: a CDN round trip plus buffering. */
        const val PREPARE_LEAD_MS = 12_000L

        /**
         * How late a blend may still start. Starting late puts the incoming beats that far behind,
         * which the phase nudge takes a while to pull in, so a beat-matched blend tolerates little;
         * a crossfade has no beats to line up.
         */
        const val LATE_BEAT_MATCHED_MS = 40L
        const val LATE_CROSSFADE_MS = 1_000L
    }
}

/**
 * Media3 emits `PLAYLIST_CHANGED` both when a replacement queue is selected and when one is
 * appended. Queue replacement is counted synchronously by [ExoPlayerPlatformPlayer.setQueue],
 * while append must not count, so this callback counts only transport transitions.
 */
internal fun shouldAdvanceMedia3PlaybackOccurrence(
    reason: Int,
    currentWindowIndex: Int,
    previousWindowIndex: Int,
): Boolean = when (reason) {
    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> true
    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> currentWindowIndex != previousWindowIndex
    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> false
    else -> currentWindowIndex != previousWindowIndex
}

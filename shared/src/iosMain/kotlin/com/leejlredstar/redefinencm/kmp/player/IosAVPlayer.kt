@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.toProviderItemIdOrNull
import com.leejlredstar.redefinencm.kmp.download.LocalMediaAssets
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.onlinePlaybackQuality
import com.leejlredstar.redefinencm.kmp.util.startNetworkPathMonitor
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
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.dataTaskWithURL
import platform.MediaPlayer.*
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UIKit.UIImage
import platform.darwin.NSObjectProtocol
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** AVPlayer-backed iOS player with AVPlayer time, completion and system transport integration. */
class IosAVPlayer(
    private val repo: Repository,
    private val settings: PlatformSettings,
    private val localMediaAssets: LocalMediaAssets,
    private val providers: MusicProviderRegistry,
) : BasePlatformPlayer(settings.persistedPlayerVolume()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Replaced when the media server restarts: every object made with the old one is dead.
    private var avPlayer = AVPlayer().apply { volume = _volume.value }
    private val resolver = StreamUrlResolver { mediaId ->
        resolveStreamUrl(
            mediaId = mediaId,
            providers = providers,
            localAudioUri = { id ->
                DownloadedSongsCache.ensureInitialized()
                DownloadedSongsCache.snapshot()[id]?.uri
            },
            onlineUrl = { id, quality -> repo.getSongUrl(id, quality.name.lowercase()) },
            quality = { settings.onlinePlaybackQuality() },
        )
    }

    private var queueModel: PlayQueue<MediaInfo> = PlayQueue.empty()

    private var pollJob: Job? = null
    private var resolveJob: Job? = null
    private var playbackGeneration = 0L
    private val itemObservers = mutableListOf<NSObjectProtocol>()
    private val sessionObservers = mutableListOf<NSObjectProtocol>()
    private val remoteCommandTargets = mutableListOf<Pair<MPRemoteCommand, Any>>()

    /** Whether the track should be heard: set by play, cleared by a pause, an error or the end. */
    private var playRequested = false

    /** When AVPlayer was last asked to play. It reports waiting or playing shortly after. */
    private var playRequestMark: TimeMark? = null

    /** When playback last stopped without the app asking. An interruption may be reported after it. */
    private var systemPauseMark: TimeMark? = null
    private var resumeAfterInterruption = false

    /** Where the one automatic reopen after a failure happened; another needs progress past it. */
    private var reopenedAtMs: Long? = null

    /** A seek in flight: AVPlayer still reports the old time until it lands. */
    private var seekPending = false
    private var seekSequence = 0L
    private var audioSessionActive = false
    private var backgroundTask: ULong = UIBackgroundTaskInvalid

    private var artworkTask: NSURLSessionDataTask? = null
    private var artworkResolveJob: Job? = null
    private var artworkGeneration = 0L
    private var artworkMediaId: String? = null
    private var artworkRequestKey: String? = null
    private var artworkUri: String? = null
    private var nowPlayingArtwork: MPMediaItemArtwork? = null

    init {
        configureAudioSession()
        installRemoteCommands()
        observeAudioSession()
        startNetworkPathMonitor()
    }

    private val wantsPlayback: Boolean get() = playRequested || _isPlaying.value

    private fun publishQueue() {
        publishQueue(queueModel)
        updateNowPlayingInfo()
    }

    private fun beginPlaybackSession(clearItem: Boolean): Long {
        playbackGeneration += 1L
        resolveJob?.cancel()
        resolveJob = null
        stopPolling()
        avPlayer.pause()
        _isPlaying.value = false
        playRequested = false
        playRequestMark = null
        seekPending = false
        if (clearItem) {
            removeItemObservers()
            avPlayer.replaceCurrentItemWithPlayerItem(null)
        }
        return playbackGeneration
    }

    private fun isPlaybackCurrent(generation: Long): Boolean = generation == playbackGeneration

    private fun playCurrentFromQueue(autoplay: Boolean) {
        val media = queueModel.currentItem ?: run {
            clearQueue()
            return
        }
        publishQueue()
        _position.value = 0L
        reopenedAtMs = null
        resolveAndPlay(
            media = media,
            startMs = 0L,
            autoplay = autoplay,
            onPrepared = { _playbackOccurrence.advancePlaybackOccurrence() },
        )
    }

    private fun resolveAndPlay(
        media: MediaInfo,
        startMs: Long,
        autoplay: Boolean,
        onPrepared: () -> Unit = {},
    ) {
        val generation = beginPlaybackSession(clearItem = true)
        playRequested = autoplay
        // Nothing is heard while the link is looked up, and iOS suspends a background app that
        // plays nothing: the lookup for the next track runs under a background task.
        if (autoplay) holdBackgroundTask()
        _position.value = startMs.coerceAtLeast(0L)
        _state.value = PlayerState.BUFFERING
        updateNowPlayingInfo()
        onPrepared()
        resolveJob = scope.launch {
            val streamUrl = resolver.resolve(media.id)
            if (!isPlaybackCurrent(generation)) return@launch
            resolveJob = null
            val url = streamUrl?.takeIf { it.isNotBlank() }?.let(NSURL::URLWithString)
            if (url == null) {
                publishPlaybackError()
                return@launch
            }
            val playerItem = AVPlayerItem(uRL = url)
            avPlayer.replaceCurrentItemWithPlayerItem(playerItem)
            observeItem(playerItem, generation)
            if (startMs > 0L) seekPlayer(startMs)
            if (playRequested) {
                startAvPlayback()
            } else {
                _state.value = PlayerState.PAUSED
                releaseBackgroundTask()
            }
            updateNowPlayingInfo()
        }
    }

    /** Asks AVPlayer to play. PLAYING is published once audio actually moves, not before. */
    private fun startAvPlayback() {
        playRequested = true
        activateAudioSession()
        avPlayer.play()
        playRequestMark = TimeSource.Monotonic.markNow()
        publishFromPlayer()
        startPolling()
    }

    private fun seekPlayer(positionMs: Long) {
        val generation = playbackGeneration
        val sequence = ++seekSequence
        seekPending = true
        avPlayer.seekToTime(CMTimeMake(positionMs, 1000)) { _ ->
            scope.launch {
                if (isPlaybackCurrent(generation) && sequence == seekSequence) seekPending = false
            }
        }
    }

    private fun startPolling() {
        stopPolling()
        pollJob = scope.launch {
            while (isActive) {
                publishFromPlayer()
                // Waiting for audio is checked often, so the play button turns the moment it starts.
                delay(
                    if (_state.value == PlayerState.BUFFERING) BUFFERING_POLL_INTERVAL_MS
                    else POSITION_POLL_INTERVAL_MS,
                )
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * Publishes what AVPlayer is doing. Its rate stays 1 while it waits for data, so the time
     * control status decides: PLAYING only once audio moves, BUFFERING while it waits. Reporting
     * counts a play from PLAYING, and must not count a track that was never heard.
     */
    private fun publishFromPlayer() {
        val item = avPlayer.currentItem ?: return
        if (item.status == AVPlayerItemStatusFailed) {
            handlePlaybackFailure(playbackGeneration)
            return
        }
        // An item that is not ready, or a seek still in flight, reports a time that is not the
        // position the track is at.
        if (item.status == AVPlayerItemStatusReadyToPlay && !seekPending) {
            currentTimeMillis(avPlayer)?.let { _position.value = it }
        }
        durationMillis(item)?.let { _duration.value = it }
        val status = iosPlaybackStatus(
            timeControlStatus = avPlayer.timeControlStatus,
            playRequested = playRequested,
            sincePlayRequestMs = playRequestMark?.elapsedNow()?.inWholeMilliseconds,
            positionMs = _position.value,
            durationMs = _duration.value,
        )
        when (status) {
            IosPlaybackStatus.PLAYING -> {
                playRequested = true
                _isPlaying.value = true
                _state.value = PlayerState.PLAYING
                // Audible again: background audio keeps the app running from here.
                releaseBackgroundTask()
            }
            IosPlaybackStatus.BUFFERING -> {
                _isPlaying.value = false
                _state.value = PlayerState.BUFFERING
            }
            IosPlaybackStatus.PAUSED -> {
                _isPlaying.value = false
                if (_state.value != PlayerState.ERROR && _state.value != PlayerState.ENDED) {
                    _state.value = PlayerState.PAUSED
                }
            }
            // The end-of-item notification moves on.
            IosPlaybackStatus.ENDING -> Unit
            IosPlaybackStatus.STOPPED_BY_SYSTEM -> {
                // Stopped without the app asking (a route change, another app's audio). Followed
                // as a pause, so the controls say what is heard and play starts it again.
                systemPauseMark = TimeSource.Monotonic.markNow()
                pausePlayback()
                return
            }
        }
        updateNowPlayingInfo()
    }

    /**
     * The item failed: an expired link, a dropped connection, a server refusing it. While the
     * track should be playing it is looked up again, once, from where it stopped; another failure
     * before it has played on stops in ERROR, from which play() looks it up again.
     */
    private fun handlePlaybackFailure(generation: Long) {
        if (!isPlaybackCurrent(generation) || _state.value == PlayerState.ERROR) return
        val media = queueModel.currentItem ?: return
        val position = _position.value.coerceAtLeast(0L)
        // The poll may have seen the failing item stop before this was reported, and followed it
        // as a pause.
        val wanted = playRequested || pausedBySystemJustNow()
        if (wanted && iosMayReopenStream(reopenedAtMs, position)) {
            reopenedAtMs = position
            println("IosAVPlayer: the stream failed at $position ms; opening it again")
            resolveAndPlay(media, position, autoplay = true)
            return
        }
        println("IosAVPlayer: the stream failed at $position ms")
        publishPlaybackError()
    }

    private fun publishPlaybackError() {
        playRequested = false
        playRequestMark = null
        stopPolling()
        avPlayer.pause()
        _isPlaying.value = false
        _state.value = PlayerState.ERROR
        releaseBackgroundTask()
        updateNowPlayingInfo()
    }

    override fun play() {
        resumeAfterInterruption = false
        systemPauseMark = null
        val media = _currentMedia.value ?: return
        // Already on its way: playing, waiting for data, or looking up the link to play.
        if (playRequested && _state.value != PlayerState.ERROR) return
        if (resolveJob?.isActive == true) {
            // The link of a paused selection is being looked up: it plays as soon as it arrives.
            playRequested = true
            holdBackgroundTask()
            return
        }
        if (_state.value == PlayerState.ENDED) {
            _position.value = 0L
            _playbackOccurrence.advancePlaybackOccurrence()
            avPlayer.currentItem?.let { seekPlayer(0L) }
        }
        val item = avPlayer.currentItem
        if (item == null || item.status == AVPlayerItemStatusFailed || _state.value == PlayerState.ERROR) {
            // No item, or one that failed: its link may have expired while paused. A fresh one is
            // looked up and plays from where the track stopped.
            resolveAndPlay(media, _position.value.coerceAtLeast(0L), autoplay = true)
            return
        }
        startAvPlayback()
        updateNowPlayingInfo()
    }

    override fun pause() {
        resumeAfterInterruption = false
        systemPauseMark = null
        pausePlayback()
    }

    private fun pausedBySystemJustNow(): Boolean =
        systemPauseMark?.let { it.elapsedNow() < SYSTEM_PAUSE_MATCH_WINDOW } == true

    private fun pausePlayback() {
        if (_state.value == PlayerState.IDLE || _state.value == PlayerState.ENDED) return
        if (resolveJob?.isActive == true) {
            // Still looking up the link: the lookup is dropped, and play() starts it again.
            beginPlaybackSession(clearItem = true)
        } else {
            avPlayer.pause()
            playRequested = false
            playRequestMark = null
            if (avPlayer.currentItem?.status == AVPlayerItemStatusReadyToPlay && !seekPending) {
                currentTimeMillis(avPlayer)?.let { _position.value = it }
            }
        }
        _isPlaying.value = false
        if (_state.value != PlayerState.ERROR) _state.value = PlayerState.PAUSED
        stopPolling()
        releaseBackgroundTask()
        updateNowPlayingInfo()
    }

    override fun togglePlayPause() {
        if (wantsPlayback) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        markPositionDiscontinuity()
        val bounded = _duration.value.takeIf { it > 0L }
            ?.let { positionMs.coerceIn(0L, it) }
            ?: positionMs.coerceAtLeast(0L)
        _position.value = bounded
        if (_state.value == PlayerState.ENDED && (_duration.value <= 0L || bounded < _duration.value)) {
            _state.value = PlayerState.PAUSED
        }
        val item = avPlayer.currentItem
        if (
            item != null &&
            item.status != AVPlayerItemStatusFailed &&
            _state.value != PlayerState.ERROR &&
            resolveJob?.isActive != true
        ) {
            seekPlayer(bounded)
        } else {
            // No usable item yet, or a failed one: the track is opened again at the new position.
            _currentMedia.value?.let { resolveAndPlay(it, bounded, autoplay = playRequested) }
        }
        updateNowPlayingInfo()
    }

    override fun seekToPrevious() {
        val autoplay = wantsPlayback
        val next = queueModel.previous(repeat = false)
        if (next.currentIndex != queueModel.currentIndex) {
            queueModel = next
            playCurrentFromQueue(autoplay = autoplay)
        }
    }

    override fun seekToNext() {
        val autoplay = wantsPlayback
        when (val transition = iosPlaybackEndTransition(queueModel, _duration.value)) {
            is IosPlaybackEndTransition.Advance -> {
                queueModel = transition.queue
                playCurrentFromQueue(autoplay = autoplay)
            }
            is IosPlaybackEndTransition.Ended -> if (autoplay) applyEndedTransition(transition)
        }
    }

    override fun setQueue(items: List<MediaInfo>, startIndex: Int) {
        if (items.isEmpty()) {
            clearQueue()
            return
        }
        queueModel = PlayQueue.of(items, startIndex, shuffle = queueModel.shuffleEnabled)
        playCurrentFromQueue(autoplay = true)
    }

    override fun restoreQueue(items: List<MediaInfo>, startIndex: Int, positionMs: Long, shuffle: Boolean, playOrder: List<Int>?) {
        if (items.isEmpty()) {
            clearQueue()
            setShuffleEnabled(shuffle)
            return
        }
        beginPlaybackSession(clearItem = true)
        queueModel = PlayQueue.restore(items, startIndex, shuffle, playOrder)
        reopenedAtMs = null
        publishQueue()
        _position.value = positionMs.coerceAtLeast(0L)
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        updateNowPlayingInfo()
        _playbackOccurrence.advancePlaybackOccurrence()
    }

    override fun addToQueue(item: MediaInfo) {
        queueModel = queueModel.addItem(item)
        publishQueue()
    }

    override fun clearQueue() {
        beginPlaybackSession(clearItem = true)
        resumeAfterInterruption = false
        queueModel = queueModel.clear()
        publishQueue()
        _state.value = PlayerState.IDLE
        _isPlaying.value = false
        _position.value = 0L
        _duration.value = -1L
        clearNowPlayingInfo()
        releaseBackgroundTask()
        // Nothing left to play: other apps may take the audio back.
        deactivateAudioSession()
    }

    override fun skipToIndex(index: Int) {
        val selected = queueModel.skipToPlayOrderPosition(index)
        if (selected.currentIndex == queueModel.currentIndex) return
        val autoplay = wantsPlayback
        queueModel = selected
        selectCurrentFromQueue(autoplay)
    }

    override fun removeFromQueue(position: Int) {
        val itemIndex = queueModel.playOrder.getOrNull(position) ?: return
        val remaining = queueModel.removeAtPlayOrderPosition(position)
        if (remaining.isEmpty) {
            clearQueue()
            return
        }
        if (itemIndex != queueModel.currentIndex) {
            queueModel = remaining
            publishQueue()
            return
        }
        val autoplay = wantsPlayback
        queueModel = remaining
        selectCurrentFromQueue(autoplay)
    }

    override fun moveInQueue(from: Int, to: Int) {
        val moved = queueModel.movePlayOrderPosition(from, to)
        if (moved === queueModel) return
        queueModel = moved
        publishQueue()
    }

    /** Makes the queue's current track the one loaded: playing it, or paused at its start. */
    private fun selectCurrentFromQueue(autoplay: Boolean) {
        if (autoplay) {
            playCurrentFromQueue(autoplay = true)
        } else {
            publishQueue()
            beginPlaybackSession(clearItem = true)
            reopenedAtMs = null
            _position.value = 0L
            _state.value = PlayerState.PAUSED
            releaseBackgroundTask()
            updateNowPlayingInfo()
            _playbackOccurrence.advancePlaybackOccurrence()
        }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        queueModel = queueModel.setShuffle(enabled)
        publishQueue()
    }

    override fun setVolume(volume: Float) {
        applyVolume(volume, settings) { avPlayer.volume = it }
    }

    override fun release() {
        beginPlaybackSession(clearItem = true)
        removeRemoteCommands()
        sessionObservers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        sessionObservers.clear()
        releaseBackgroundTask()
        deactivateAudioSession()
        clearNowPlayingInfo()
        scope.cancel()
    }

    private fun observeItem(item: AVPlayerItem, generation: Long) {
        removeItemObservers()
        val center = NSNotificationCenter.defaultCenter
        itemObservers += center.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            scope.launch {
                if (!isPlaybackCurrent(generation)) return@launch
                when (val transition = iosPlaybackEndTransition(queueModel, _duration.value)) {
                    is IosPlaybackEndTransition.Advance -> {
                        queueModel = transition.queue
                        playCurrentFromQueue(autoplay = true)
                    }
                    is IosPlaybackEndTransition.Ended -> applyEndedTransition(transition)
                }
            }
        }
        // A failure after playback began (the connection dropped mid-track). A failure to load is
        // seen by the poll through the item's status.
        itemObservers += center.addObserverForName(
            name = AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            scope.launch { handlePlaybackFailure(generation) }
        }
    }

    private fun removeItemObservers() {
        itemObservers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        itemObservers.clear()
    }

    /**
     * The category is set at launch, but the session is activated only when playback starts:
     * activating a playback session stops other apps' audio, and opening the app to browse must not.
     */
    private fun configureAudioSession() {
        val session = AVAudioSession.sharedInstance()
        val categoryConfigured = runCatching {
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        }.getOrElse { error ->
            reportAudioSessionFailure("category setup threw: ${error.message}")
            return
        }
        if (!categoryConfigured) reportAudioSessionFailure("category setup returned false")
    }

    private fun reportAudioSessionFailure(detail: String) {
        _state.value = PlayerState.ERROR
        println("AVAudioSession setup failed: $detail")
    }

    private fun activateAudioSession() {
        // Asked every time playback starts: recognition and interruptions deactivate the session
        // behind the player's back. A failure is only logged: AVPlayer activates it itself.
        val activated = runCatching { AVAudioSession.sharedInstance().setActive(true, error = null) }
            .getOrElse { error ->
                println("AVAudioSession activation threw: ${error.message}")
                false
            }
        if (activated) audioSessionActive = true else println("AVAudioSession activation returned false")
    }

    /** Gives the audio back: an app this one interrupted may resume. */
    private fun deactivateAudioSession() {
        if (!audioSessionActive) return
        audioSessionActive = false
        runCatching {
            AVAudioSession.sharedInstance().setActive(
                false,
                withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                error = null,
            )
        }.onSuccess { deactivated ->
            if (!deactivated) println("AVAudioSession deactivation returned false")
        }.onFailure { error ->
            println("AVAudioSession deactivation failed: ${error.message}")
        }
    }

    private fun observeAudioSession() {
        val center = NSNotificationCenter.defaultCenter
        val session = AVAudioSession.sharedInstance()
        sessionObservers += center.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue,
        ) { notification -> notification?.let(::handleInterruption) }
        sessionObservers += center.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue,
        ) { notification -> notification?.let(::handleRouteChange) }
        sessionObservers += center.addObserverForName(
            name = AVAudioSessionMediaServicesWereResetNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> handleMediaServicesReset() }
    }

    /**
     * A call, an alarm or Siri took the audio. Playback that was going on when it began is resumed
     * when it ends, if the system says it may be; a play or pause in between replaces that.
     */
    private fun handleInterruption(notification: NSNotification) {
        when (notification.unsignedValue(AVAudioSessionInterruptionTypeKey)) {
            AVAudioSessionInterruptionTypeBegan -> {
                // The system deactivated the session; the poll may already have followed the pause.
                audioSessionActive = false
                val wasPlaying = playRequested || pausedBySystemJustNow()
                if (playRequested || _isPlaying.value) pausePlayback()
                resumeAfterInterruption = wasPlaying
            }
            AVAudioSessionInterruptionTypeEnded -> {
                val options = notification.unsignedValue(AVAudioSessionInterruptionOptionKey) ?: 0uL
                val resume = resumeAfterInterruption &&
                    options and AVAudioSessionInterruptionOptionShouldResume != 0uL
                resumeAfterInterruption = false
                if (resume) play()
            }
        }
    }

    /** Headphones unplugged or a Bluetooth device gone: playback stops rather than moving to the speaker. */
    private fun handleRouteChange(notification: NSNotification) {
        if (
            notification.unsignedValue(AVAudioSessionRouteChangeReasonKey) ==
            AVAudioSessionRouteChangeReasonOldDeviceUnavailable &&
            wantsPlayback
        ) {
            pause()
        }
    }

    /**
     * The media server restarted, and every object made with the old one is dead. A new player
     * takes over, and the current track is opened again where it was.
     */
    private fun handleMediaServicesReset() {
        val media = queueModel.currentItem
        val wasPlaying = wantsPlayback
        val position = _position.value.coerceAtLeast(0L)
        val state = _state.value
        beginPlaybackSession(clearItem = true)
        avPlayer = AVPlayer().apply { volume = _volume.value }
        audioSessionActive = false
        configureAudioSession()
        when {
            media == null -> Unit
            wasPlaying -> resolveAndPlay(media, position, autoplay = true)
            state != PlayerState.IDLE && state != PlayerState.ENDED && state != PlayerState.ERROR ->
                _state.value = PlayerState.PAUSED
        }
        updateNowPlayingInfo()
    }

    private fun holdBackgroundTask() {
        if (backgroundTask != UIBackgroundTaskInvalid) return
        backgroundTask = UIApplication.sharedApplication.beginBackgroundTaskWithName(
            "RedefineNCM playback",
        ) { releaseBackgroundTask() }
    }

    private fun releaseBackgroundTask() {
        val task = backgroundTask
        if (task == UIBackgroundTaskInvalid) return
        backgroundTask = UIBackgroundTaskInvalid
        UIApplication.sharedApplication.endBackgroundTask(task)
    }

    private fun installRemoteCommands() {
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        registerRemoteCommand(commands.playCommand) { play() }
        registerRemoteCommand(commands.pauseCommand) { pause() }
        // Headset and keyboard play/pause buttons send the toggle, not play or pause.
        registerRemoteCommand(commands.togglePlayPauseCommand) { togglePlayPause() }
        registerRemoteCommand(commands.nextTrackCommand) { seekToNext() }
        registerRemoteCommand(commands.previousTrackCommand) { seekToPrevious() }
        val seekTarget = commands.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val seekEvent = event as? MPChangePlaybackPositionCommandEvent
                ?: return@addTargetWithHandler MPRemoteCommandHandlerStatusCommandFailed
            scope.launch { seekTo((seekEvent.positionTime * 1000.0).toLong()) }
            MPRemoteCommandHandlerStatusSuccess
        }
        remoteCommandTargets += commands.changePlaybackPositionCommand to seekTarget
    }

    private fun registerRemoteCommand(command: MPRemoteCommand, action: () -> Unit) {
        command.enabled = true
        val target = command.addTargetWithHandler {
            scope.launch { action() }
            MPRemoteCommandHandlerStatusSuccess
        }
        remoteCommandTargets += command to target
    }

    private fun removeRemoteCommands() {
        remoteCommandTargets.forEach { (command, target) -> command.removeTarget(target) }
        remoteCommandTargets.clear()
    }

    private fun applyEndedTransition(transition: IosPlaybackEndTransition.Ended) {
        // Manual "next" can reach the queue tail while URL resolution is still in flight.
        // Invalidate that generation before publishing ENDED or its late result can restart audio.
        beginPlaybackSession(clearItem = true)
        resumeAfterInterruption = false
        _isPlaying.value = transition.isPlaying
        _position.value = transition.positionMs
        _state.value = transition.state
        updateNowPlayingInfo()
        releaseBackgroundTask()
        deactivateAudioSession()
    }

    private fun updateNowPlayingInfo() {
        val media = _currentMedia.value ?: return
        ensureNowPlayingArtwork(media)
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to media.title,
            MPMediaItemPropertyArtist to media.artist,
            MPMediaItemPropertyAlbumTitle to media.albumTitle,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to (_position.value / 1000.0),
            MPNowPlayingInfoPropertyPlaybackRate to if (_isPlaying.value) 1.0 else 0.0,
        )
        _duration.value.takeIf { it > 0L }?.let { duration ->
            info[MPMediaItemPropertyPlaybackDuration] = duration / 1000.0
        }
        if (artworkMediaId == media.id) {
            nowPlayingArtwork?.let { info[MPMediaItemPropertyArtwork] = it }
        }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }

    private fun ensureNowPlayingArtwork(media: MediaInfo) {
        val remoteUri = media.artworkUri.trim().takeIf { it.isNotEmpty() }
        if (artworkMediaId == media.id && artworkRequestKey == remoteUri) return

        artworkResolveJob?.cancel()
        artworkResolveJob = null
        artworkTask?.cancel()
        artworkTask = null
        artworkGeneration += 1L
        val generation = artworkGeneration
        artworkMediaId = media.id
        artworkRequestKey = remoteUri
        artworkUri = null
        nowPlayingArtwork = null
        artworkResolveJob = scope.launch {
            DownloadedSongsCache.ensureInitialized()
            val localUri = media.id.toLongOrNull()
                ?.takeIf { DownloadedSongsCache.isDownloaded(it) }
                ?.let { songId ->
                    runCatching { localMediaAssets.resolveArtworkUri(songId) }.getOrNull()
                }
            val requestedUri = localUri?.takeIf(String::isNotBlank) ?: remoteUri
            if (
                generation != artworkGeneration ||
                _currentMedia.value?.id != media.id
            ) {
                localUri?.let(localMediaAssets::releaseArtworkUri)
                return@launch
            }
            artworkResolveJob = null
            artworkUri = requestedUri
            val url = requestedUri?.let(NSURL::URLWithString) ?: return@launch
            if (url.fileURL) {
                val path = url.path ?: return@launch
                val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return@launch
                applyNowPlayingArtwork(data, generation, media.id, requestedUri)
            } else {
                artworkTask = NSURLSession.sharedSession.dataTaskWithURL(url) { data, _, error ->
                    if (error != null || data == null) return@dataTaskWithURL
                    scope.launch {
                        applyNowPlayingArtwork(data, generation, media.id, requestedUri)
                    }
                }.also { it.resume() }
            }
        }
    }

    private fun applyNowPlayingArtwork(
        data: NSData,
        generation: Long,
        mediaId: String,
        requestedUri: String,
    ) {
        val image = UIImage.imageWithData(data) ?: return
        if (
            generation != artworkGeneration ||
            _currentMedia.value?.id != mediaId ||
            artworkUri != requestedUri
        ) return
        nowPlayingArtwork = MPMediaItemArtwork(image)
        artworkTask = null
        updateNowPlayingInfo()
    }

    private fun clearNowPlayingInfo() {
        artworkGeneration += 1L
        artworkResolveJob?.cancel()
        artworkResolveJob = null
        artworkTask?.cancel()
        artworkTask = null
        artworkMediaId = null
        artworkRequestKey = null
        artworkUri = null
        nowPlayingArtwork = null
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    private companion object {
        const val POSITION_POLL_INTERVAL_MS = 200L
        const val BUFFERING_POLL_INTERVAL_MS = 50L

        /** How long after the poll followed a stop its cause (an interruption, a failure) may arrive. */
        val SYSTEM_PAUSE_MATCH_WINDOW = 1_500.milliseconds
    }
}

internal sealed interface IosPlaybackEndTransition<out T> {
    data class Advance<T>(val queue: PlayQueue<T>) : IosPlaybackEndTransition<T>

    data class Ended(
        val state: PlayerState = PlayerState.ENDED,
        val isPlaying: Boolean = false,
        val positionMs: Long,
    ) : IosPlaybackEndTransition<Nothing>
}

internal fun <T> iosPlaybackEndTransition(
    queue: PlayQueue<T>,
    durationMs: Long,
): IosPlaybackEndTransition<T> {
    val next = queue.next(repeat = false)
    return if (next.currentIndex != queue.currentIndex) {
        IosPlaybackEndTransition.Advance(next)
    } else {
        IosPlaybackEndTransition.Ended(positionMs = durationMs.coerceAtLeast(0L))
    }
}

internal enum class IosPlaybackStatus { PLAYING, BUFFERING, PAUSED, ENDING, STOPPED_BY_SYSTEM }

/** How long AVPlayer may take to report waiting or playing after it was asked to play. */
internal const val IOS_PLAY_START_GRACE_MS = 1_000L

/** A stop this close to the end is the end of the track, which its own notification handles. */
internal const val IOS_END_TOLERANCE_MS = 1_000L

/** Progress past the last automatic reopen after which a new failure may reopen again. */
internal const val IOS_REOPEN_PROGRESS_MS = 10_000L

/**
 * What the player is doing, from AVPlayer's [timeControlStatus] (its rate stays 1 while it waits
 * for data, so the rate cannot tell playing from waiting) and whether playback was asked for.
 */
internal fun iosPlaybackStatus(
    timeControlStatus: Long,
    playRequested: Boolean,
    sincePlayRequestMs: Long?,
    positionMs: Long,
    durationMs: Long,
): IosPlaybackStatus = when {
    timeControlStatus == AVPlayerTimeControlStatusPlaying -> IosPlaybackStatus.PLAYING
    !playRequested -> IosPlaybackStatus.PAUSED
    timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> IosPlaybackStatus.BUFFERING
    sincePlayRequestMs != null && sincePlayRequestMs < IOS_PLAY_START_GRACE_MS -> IosPlaybackStatus.BUFFERING
    durationMs > 0L && positionMs >= durationMs - IOS_END_TOLERANCE_MS -> IosPlaybackStatus.ENDING
    else -> IosPlaybackStatus.STOPPED_BY_SYSTEM
}

/**
 * Whether a failed stream may be opened again automatically: once per selection, and again only
 * after it played on [IOS_REOPEN_PROGRESS_MS] past the last reopen. A link that fails at once stops.
 */
internal fun iosMayReopenStream(reopenedAtMs: Long?, positionMs: Long): Boolean =
    reopenedAtMs == null || positionMs >= reopenedAtMs + IOS_REOPEN_PROGRESS_MS

private fun NSNotification.unsignedValue(key: String?): ULong? =
    when (val value = userInfo?.get(key)) {
        is NSNumber -> value.unsignedLongValue
        is ULong -> value
        is Number -> value.toLong().toULong()
        else -> null
    }

private fun currentTimeMillis(player: AVPlayer): Long? {
    val seconds = CMTimeGetSeconds(player.currentTime())
    return seconds.takeIf { it.isFinite() && it >= 0.0 }?.let { (it * 1000.0).toLong() }
}

private fun durationMillis(item: AVPlayerItem): Long? {
    val seconds = CMTimeGetSeconds(item.duration)
    return seconds.takeIf { it.isFinite() && it > 0.0 }?.let { (it * 1000.0).toLong() }
}

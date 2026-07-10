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
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.dataTaskWithURL
import platform.MediaPlayer.*
import platform.UIKit.UIImage
import platform.darwin.NSObjectProtocol

/** AVPlayer-backed iOS player with AVPlayer time, completion and system transport integration. */
class IosAVPlayer(
    private val repo: Repository,
    private val settings: PlatformSettings,
) : PlatformPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val avPlayer = AVPlayer()
    private val resolver = StreamUrlResolver { mediaId ->
        val id = mediaId.toLong()
        DownloadedSongsCache.snapshot()[id]?.uri?.let { return@StreamUrlResolver it }
        val qualityName = settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.EXHIGH.name)
        val quality = runCatching { SoundQuality.valueOf(qualityName) }
            .getOrDefault(SoundQuality.EXHIGH)
        repo.getSongUrl(id, quality.name.lowercase())
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
    private val _currentIndex = MutableStateFlow(-1)
    override val currentIndex: StateFlow<Int> = _currentIndex
    private val _shuffleEnabled = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled
    private val _queueSnapshot = MutableStateFlow(PlayerQueueSnapshot())
    override val queueSnapshot: StateFlow<PlayerQueueSnapshot> = _queueSnapshot
    private val _volume = MutableStateFlow(
        playerVolumeFromPercent(
            settings.getLong(SettingKeys.PLAYER_VOLUME, DEFAULT_PLAYER_VOLUME_PERCENT),
        ),
    )
    override val volume: StateFlow<Float> = _volume

    private var pollJob: Job? = null
    private var resolveJob: Job? = null
    private var playbackGeneration = 0L
    private var playbackEndObserver: NSObjectProtocol? = null
    private val remoteCommandTargets = mutableListOf<Pair<MPRemoteCommand, Any>>()
    private var artworkTask: NSURLSessionDataTask? = null
    private var artworkGeneration = 0L
    private var artworkMediaId: String? = null
    private var artworkUri: String? = null
    private var nowPlayingArtwork: MPMediaItemArtwork? = null

    init {
        avPlayer.volume = _volume.value
        configureAudioSession()
        installRemoteCommands()
    }

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
        updateNowPlayingInfo()
    }

    private fun beginPlaybackSession(clearItem: Boolean): Long {
        playbackGeneration += 1L
        resolveJob?.cancel()
        resolveJob = null
        stopPolling()
        avPlayer.pause()
        _isPlaying.value = false
        if (clearItem) {
            removePlaybackEndObserver()
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
        _position.value = 0L
        publishQueue()
        resolveAndPlay(media, startMs = 0L, autoplay = autoplay)
    }

    private fun resolveAndPlay(media: MediaInfo, startMs: Long, autoplay: Boolean) {
        val generation = beginPlaybackSession(clearItem = true)
        _position.value = startMs.coerceAtLeast(0L)
        _state.value = PlayerState.BUFFERING
        updateNowPlayingInfo()
        resolveJob = scope.launch {
            val streamUrl = resolver.resolve(media.id)
            if (!isPlaybackCurrent(generation)) return@launch
            if (streamUrl.isNullOrBlank()) {
                _state.value = PlayerState.ERROR
                return@launch
            }
            val url = NSURL.URLWithString(streamUrl) ?: run {
                _state.value = PlayerState.ERROR
                return@launch
            }
            val playerItem = AVPlayerItem(uRL = url)
            avPlayer.replaceCurrentItemWithPlayerItem(playerItem)
            observePlaybackEnd(playerItem, generation)
            if (startMs > 0L) avPlayer.seekToTime(CMTimeMake(startMs, 1000))
            if (!isPlaybackCurrent(generation)) return@launch
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
            updateNowPlayingInfo()
        }
    }

    private fun startPolling() {
        stopPolling()
        pollJob = scope.launch {
            while (isActive) {
                val item = avPlayer.currentItem
                if (item != null) {
                    currentTimeMillis(avPlayer)?.let { _position.value = it }
                    durationMillis(item)?.let { _duration.value = it }
                    _isPlaying.value = avPlayer.rate > 0.0f
                    _state.value = when {
                        item.status == AVPlayerItemStatusFailed -> PlayerState.ERROR
                        avPlayer.rate > 0.0f -> PlayerState.PLAYING
                        item.status == AVPlayerItemStatusReadyToPlay -> PlayerState.PAUSED
                        else -> PlayerState.BUFFERING
                    }
                    updateNowPlayingInfo()
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun play() {
        if (_isPlaying.value || _state.value == PlayerState.BUFFERING) return
        val media = _currentMedia.value ?: return
        if (_state.value == PlayerState.ENDED) {
            _position.value = 0L
            avPlayer.currentItem?.let { avPlayer.seekToTime(CMTimeMake(0L, 1000)) }
        }
        if (avPlayer.currentItem == null) {
            resolveAndPlay(media, _position.value.coerceAtLeast(0L), autoplay = true)
            return
        }
        avPlayer.play()
        _isPlaying.value = true
        _state.value = PlayerState.PLAYING
        startPolling()
        updateNowPlayingInfo()
    }

    override fun pause() {
        if (_state.value == PlayerState.IDLE) return
        if (_state.value == PlayerState.BUFFERING) {
            beginPlaybackSession(clearItem = true)
        } else {
            avPlayer.pause()
            currentTimeMillis(avPlayer)?.let { _position.value = it }
        }
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        stopPolling()
        updateNowPlayingInfo()
    }

    override fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        val bounded = _duration.value.takeIf { it > 0L }
            ?.let { positionMs.coerceIn(0L, it) }
            ?: positionMs.coerceAtLeast(0L)
        _position.value = bounded
        if (avPlayer.currentItem != null) {
            avPlayer.seekToTime(CMTimeMake(bounded, 1000))
        } else {
            _currentMedia.value?.let { resolveAndPlay(it, bounded, autoplay = _isPlaying.value) }
        }
        updateNowPlayingInfo()
    }

    override fun seekToPrevious() {
        val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
        val next = queueModel.previous(repeat = false)
        if (next.currentIndex != queueModel.currentIndex) {
            queueModel = next
            playCurrentFromQueue(autoplay = autoplay)
        }
    }

    override fun seekToNext() {
        val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
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
        queueModel = PlayQueue.of(items, startIndex)
        playCurrentFromQueue(autoplay = true)
    }

    override fun restoreQueue(items: List<MediaInfo>, startIndex: Int, positionMs: Long) {
        if (items.isEmpty()) {
            clearQueue()
            return
        }
        beginPlaybackSession(clearItem = true)
        queueModel = PlayQueue.of(items, startIndex, shuffle = queueModel.shuffleEnabled)
        publishQueue()
        _position.value = positionMs.coerceAtLeast(0L)
        _isPlaying.value = false
        _state.value = PlayerState.PAUSED
        updateNowPlayingInfo()
    }

    override fun addToQueue(item: MediaInfo) {
        queueModel = queueModel.addItem(item)
        publishQueue()
    }

    override fun clearQueue() {
        beginPlaybackSession(clearItem = true)
        queueModel = PlayQueue.empty()
        publishQueue()
        _state.value = PlayerState.IDLE
        _isPlaying.value = false
        _position.value = 0L
        _duration.value = -1L
        clearNowPlayingInfo()
    }

    override fun skipToIndex(index: Int) {
        val itemIndex = queueModel.playOrder.getOrNull(index) ?: index
        if (itemIndex !in queueModel.items.indices) return
        val autoplay = _isPlaying.value || _state.value == PlayerState.BUFFERING
        queueModel = queueModel.skipTo(itemIndex)
        publishQueue()
        if (autoplay) {
            playCurrentFromQueue(autoplay = true)
        } else {
            beginPlaybackSession(clearItem = true)
            _position.value = 0L
            _state.value = PlayerState.PAUSED
            updateNowPlayingInfo()
        }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        queueModel = queueModel.setShuffle(enabled)
        publishQueue()
    }

    override fun setVolume(volume: Float) {
        val safeVolume = normalizePlayerVolume(volume)
        val oldPercent = playerVolumeToPercent(_volume.value)
        val newPercent = playerVolumeToPercent(safeVolume)
        _volume.value = safeVolume
        avPlayer.volume = safeVolume
        if (newPercent != oldPercent) settings.setLong(SettingKeys.PLAYER_VOLUME, newPercent)
    }

    override fun release() {
        beginPlaybackSession(clearItem = true)
        removeRemoteCommands()
        runCatching { AVAudioSession.sharedInstance().setActive(false, error = null) }
            .onSuccess { deactivated ->
                if (!deactivated) println("AVAudioSession deactivation returned false")
            }
            .onFailure { error ->
                println("AVAudioSession deactivation failed: ${error.message}")
            }
        clearNowPlayingInfo()
        scope.cancel()
    }

    private fun observePlaybackEnd(item: AVPlayerItem, generation: Long) {
        removePlaybackEndObserver()
        playbackEndObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) {
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
    }

    private fun removePlaybackEndObserver() {
        playbackEndObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        playbackEndObserver = null
    }

    private fun configureAudioSession() {
        val session = AVAudioSession.sharedInstance()
        val categoryConfigured = runCatching {
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        }.getOrElse { error ->
            reportAudioSessionFailure("category setup threw: ${error.message}")
            return
        }
        if (!categoryConfigured) {
            reportAudioSessionFailure("category setup returned false")
            return
        }

        val activated = runCatching { session.setActive(true, error = null) }
            .getOrElse { error ->
                reportAudioSessionFailure("activation threw: ${error.message}")
                return
            }
        if (!activated) reportAudioSessionFailure("activation returned false")
    }

    private fun reportAudioSessionFailure(detail: String) {
        _state.value = PlayerState.ERROR
        println("AVAudioSession setup failed: $detail")
    }

    private fun installRemoteCommands() {
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        registerRemoteCommand(commands.playCommand) { play() }
        registerRemoteCommand(commands.pauseCommand) { pause() }
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
        _isPlaying.value = transition.isPlaying
        _position.value = transition.positionMs
        _state.value = transition.state
        updateNowPlayingInfo()
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
        val requestedUri = media.artworkUri.trim().takeIf { it.isNotEmpty() }
        if (artworkMediaId == media.id && artworkUri == requestedUri) return

        artworkTask?.cancel()
        artworkTask = null
        artworkGeneration += 1L
        val generation = artworkGeneration
        artworkMediaId = media.id
        artworkUri = requestedUri
        nowPlayingArtwork = null
        val url = requestedUri?.let(NSURL::URLWithString) ?: return

        artworkTask = NSURLSession.sharedSession.dataTaskWithURL(url) { data, _, error ->
            if (error != null || data == null) return@dataTaskWithURL
            val image = UIImage.imageWithData(data) ?: return@dataTaskWithURL
            scope.launch {
                if (generation != artworkGeneration ||
                    _currentMedia.value?.id != media.id ||
                    artworkUri != requestedUri
                ) return@launch
                nowPlayingArtwork = MPMediaItemArtwork(image)
                artworkTask = null
                updateNowPlayingInfo()
            }
        }.also { it.resume() }
    }

    private fun clearNowPlayingInfo() {
        artworkGeneration += 1L
        artworkTask?.cancel()
        artworkTask = null
        artworkMediaId = null
        artworkUri = null
        nowPlayingArtwork = null
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    private companion object {
        const val POSITION_POLL_INTERVAL_MS = 200L
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

private fun currentTimeMillis(player: AVPlayer): Long? {
    val seconds = CMTimeGetSeconds(player.currentTime())
    return seconds.takeIf { it.isFinite() && it >= 0.0 }?.let { (it * 1000.0).toLong() }
}

private fun durationMillis(item: AVPlayerItem): Long? {
    val seconds = CMTimeGetSeconds(item.duration)
    return seconds.takeIf { it.isFinite() && it > 0.0 }?.let { (it * 1000.0).toLong() }
}

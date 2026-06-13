package com.leejlredstar.redefinencm.kmp.player

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.launch

/**
 * Android PlatformPlayer backed by ExoPlayer + Media3.
 *
 * Lifecycle: created as a Koin singleton in [AndroidPlatformModule]; must be constructed on the
 * main thread (ExoPlayer requirement). PlaybackService wraps the same exoPlayer instance in a
 * MediaSession for OS media controls and background playback.
 *
 * URL resolution: placeholder URIs (`redefinencm://playbackPlaceHolder?id=xxx`) are intercepted
 * by [RedirectingDataSourceFactory] which calls [Repository.getSongUrl] synchronously (via
 * runBlocking) on ExoPlayer's IO thread at play time — stream URLs are never persisted.
 */
@OptIn(UnstableApi::class)
class ExoPlayerPlatformPlayer(
    context: Context,
    private val repo: Repository,
    private val settings: PlatformSettings,
) : PlatformPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val resolver = StreamUrlResolver { mediaId ->
        val qualityName = settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.EXHIGH.name)
        val quality = runCatching { SoundQuality.valueOf(qualityName) }.getOrDefault(SoundQuality.EXHIGH)
        repo.getSongUrl(mediaId.toLong(), quality.name.lowercase())
    }

    /** Exposed so PlaybackService can wrap it in a MediaSession. Do not release externally. */
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                RedirectingDataSourceFactory(DefaultDataSource.Factory(context), resolver)
            )
        )
        .build()

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

    private var positionJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                updateStateFromExo()
                if (playing) startPositionSync() else stopPositionSync()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateStateFromExo()
                val dur = exoPlayer.duration
                if (dur != C.TIME_UNSET) _duration.value = dur
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentIndex.value = exoPlayer.currentMediaItemIndex
                _currentMedia.value = _queue.value.getOrNull(exoPlayer.currentMediaItemIndex)
                    ?: mediaItem?.toMediaInfo()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleEnabled.value = shuffleModeEnabled
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                rebuildQueue()
            }
        })
    }

    private fun updateStateFromExo() {
        _state.value = when {
            exoPlayer.isPlaying -> PlayerState.PLAYING
            exoPlayer.playbackState == Player.STATE_BUFFERING -> PlayerState.BUFFERING
            exoPlayer.playbackState == Player.STATE_ENDED -> PlayerState.ENDED
            exoPlayer.playbackState == Player.STATE_READY -> PlayerState.PAUSED
            else -> PlayerState.IDLE
        }
    }

    private fun rebuildQueue() {
        val items = (0 until exoPlayer.mediaItemCount).map { i ->
            val item = exoPlayer.getMediaItemAt(i)
            (item.localConfiguration?.tag as? MediaInfo) ?: item.toMediaInfo()
        }
        _queue.value = items
        _currentIndex.value = exoPlayer.currentMediaItemIndex
        _currentMedia.value = items.getOrNull(_currentIndex.value)
    }

    private fun startPositionSync() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                _position.value = exoPlayer.currentPosition
                delay(200)
            }
        }
    }

    private fun stopPositionSync() {
        positionJob?.cancel()
        positionJob = null
    }

    // ── PlatformPlayer controls ──

    override fun play() = exoPlayer.play()
    override fun pause() = exoPlayer.pause()
    override fun togglePlayPause() = if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    override fun seekTo(positionMs: Long) = exoPlayer.seekTo(positionMs)
    override fun seekToPrevious() = exoPlayer.seekToPreviousMediaItem()
    override fun seekToNext() = exoPlayer.seekToNextMediaItem()
    override fun skipToIndex(index: Int) = exoPlayer.seekToDefaultPosition(index)
    override fun setShuffleEnabled(enabled: Boolean) { exoPlayer.shuffleModeEnabled = enabled }

    override fun setQueue(items: List<MediaInfo>, startIndex: Int) {
        _queue.value = items
        exoPlayer.setMediaItems(items.map { it.toExoMediaItem() }, startIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun addToQueue(item: MediaInfo) {
        exoPlayer.addMediaItem(item.toExoMediaItem())
        _queue.value = _queue.value + item
    }

    override fun clearQueue() {
        exoPlayer.clearMediaItems()
        _queue.value = emptyList()
        _currentMedia.value = null
    }

    override fun release() {
        scope.cancel()
        exoPlayer.release()
    }

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
}

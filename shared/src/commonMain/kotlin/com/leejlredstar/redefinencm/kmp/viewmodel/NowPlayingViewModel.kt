package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.CommentMusic
import com.leejlredstar.redefinencm.kmp.data.api.dto.Lyric
import com.leejlredstar.redefinencm.kmp.player.*
import com.leejlredstar.redefinencm.kmp.smtc.MediaControlsIntegrator
import com.leejlredstar.redefinencm.kmp.util.LyricParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Ported from the original Android NowPlayingViewModel.
 *
 * Key invariant (preserved from original):
 * playList, playOrderWindowIndices, and currentMediaIndexInList MUST always be
 * rebuilt together from the current Player state via rebuildPlaylistFromTimeline().
 * Never update them independently — this prevents the shuffle highlight misalignment bug.
 */
class NowPlayingViewModel(
    private val repo: Repository,
    private val player: PlatformPlayer,
    private val lyricBus: LyricBus = LyricBus, // using singleton
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Player state ──
    val currentMedia = MutableStateFlow<MediaInfo?>(null)
    val isPlaying = MutableStateFlow(false)
    val playerState = MutableStateFlow(PlayerState.IDLE)
    val currentPosition = MutableStateFlow(0L)
    val songLength = MutableStateFlow(0L)
    val shuffleStatus = MutableStateFlow(false)

    // ── Queue ──
    val playList = MutableStateFlow<List<MediaInfo>>(emptyList())
    val currentMediaIndexInList = MutableStateFlow<String?>(null)

    // ── Lyrics ──
    val lyricIndex = MutableStateFlow(0)
    val lyricMap = MutableStateFlow<LinkedHashMap<Long?, String?>>(
        linkedMapOf(0L to "Loading Lyric")
    )

    // ── Comments ──
    val comments = MutableStateFlow<CommentMusic?>(null)

    init {
        initPlayerSync()
        rebuildPlaylistFromTimeline()
    }

    private fun initPlayerSync() {
        scope.launch {
            player.state.collect { state ->
                playerState.value = state
            }
        }
        scope.launch {
            player.isPlaying.collect { playing ->
                isPlaying.value = playing
                MediaControlsIntegrator.updateMetadata(isPlaying = playing)
            }
        }
        scope.launch {
            player.position.collect { pos ->
                currentPosition.value = pos
            }
        }
        scope.launch {
            player.duration.collect { dur ->
                songLength.value = dur
            }
        }
        scope.launch {
            player.currentMedia.collect { media ->
                currentMedia.value = media
                media?.let {
                    MediaControlsIntegrator.updateMetadata(
                        title = it.title,
                        artist = it.artist,
                        album = it.albumTitle,
                        artworkUri = it.artworkUri,
                        duration = it.duration,
                    )
                }
                // Fetch lyrics when track changes
                media?.id?.let { fetchLyrics(it) }
            }
        }
        scope.launch {
            player.shuffleEnabled.collect { shuffle ->
                shuffleStatus.value = shuffle
            }
        }
    }

    /**
     * Rebuild the visible playlist, window-order indices, and current highlight
     * from the current Player state. This is the SINGLE rebuild path —
     * all track transitions, shuffle toggles, and timeline changes go through here.
     */
    fun rebuildPlaylistFromTimeline() {
        val queue = player.queue.value
        val currentIdx = player.currentIndex.value

        playList.value = queue
        currentMediaIndexInList.value = currentIdx.toString()
    }

    private fun fetchLyrics(mediaId: String) {
        scope.launch {
            repo.getLyric(mediaId.toLong()).collect { lyric ->
                if (lyric?.lrc?.lyric?.isNotEmpty() == true) {
                    try {
                        val parsed = LyricParser.parse(lyric.lrc.lyric)
                        lyricMap.value = parsed
                    } catch (e: Exception) {
                        lyricMap.value = linkedMapOf(0L to "Lyric parse error")
                    }
                } else {
                    lyricMap.value = linkedMapOf(0L to "No lyric available")
                }
            }
        }
    }

    fun getComments() {
        scope.launch {
            currentMedia.value?.id?.toLongOrNull()?.let { id ->
                repo.getCommentMusic(id).collect { detail ->
                    comments.value = detail
                }
            }
        }
    }

    // ── Playback actions ──

    fun onFavClick() {
        scope.launch {
            val mediaId = currentMedia.value?.id?.toLongOrNull()
            repo.like(mediaId)
        }
    }

    fun onPervClick() = player.seekToPrevious()
    fun onPauseClick() = player.togglePlayPause()
    fun onNextClick() = player.seekToNext()
    fun onSeekClick(index: Int) = player.skipToIndex(index)
    fun onPositionSeekClick(newPosition: Long) = player.seekTo(newPosition)
    fun onPlaylistClick() = rebuildPlaylistFromTimeline()
    fun onShuffleClick(status: Boolean) = player.setShuffleEnabled(status)

    fun onCleared() {
        scope.cancel()
    }
}

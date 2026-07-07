package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.CommentMusic
import com.leejlredstar.redefinencm.kmp.data.api.dto.Lyric
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
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
    val rawLyric = MutableStateFlow("") // raw LRC text for external lyric renderers
    val rawWordLyric = MutableStateFlow("") // raw YRC text for word-level external lyric renderers
    val wordLyricLines = MutableStateFlow<List<LyricParser.WordLine>>(emptyList())
    val lyricMediaId = MutableStateFlow<String?>(null)

    // ── Comments ──
    val comments = MutableStateFlow<CommentMusic?>(null)

    init {
        initPlayerSync()
        initLyricSync()
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
                MediaControlsIntegrator.updateMetadata(position = pos)
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
        // 队列/当前索引变化（切歌、洗牌、换单）时实时重建可见列表与高亮，
        // 与原版 onMediaItemTransition/onTimelineChanged → rebuildPlaylistFromTimeline 对齐
        scope.launch {
            player.queue.collect { rebuildPlaylistFromTimeline() }
        }
        scope.launch {
            player.currentIndex.collect { rebuildPlaylistFromTimeline() }
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

    private fun initLyricSync() {
        scope.launch {
            combine(currentPosition, lyricMap) { pos, map ->
                computeLyricIndex(pos, map)
            }.distinctUntilChanged().collect { idx ->
                lyricIndex.value = idx.coerceAtLeast(0)
                val map = lyricMap.value
                val media = currentMedia.value
                val values = map.values.toList()
                LyricNotificationController.updateLyric(
                    title = media?.title,
                    artist = media?.artist,
                    currentLyric = values.getOrNull(idx),
                    nextLyric = values.getOrNull(idx + 1),
                    artworkUri = media?.artworkUri,
                    isPlaying = isPlaying.value,
                )
            }
        }
    }

    private fun computeLyricIndex(positionMs: Long, map: LinkedHashMap<Long?, String?>): Int {
        var index = -1
        var currentIndex = 0
        for ((time, _) in map) {
            if (time != null && positionMs >= time) {
                index = currentIndex
            } else {
                break
            }
            currentIndex++
        }
        return index
    }

    private var lyricFetchJob: Job? = null

    private fun fetchLyrics(mediaId: String) {
        lyricFetchJob?.cancel()
        resetLyricsForMedia(mediaId)
        // 网络必须离开 Main：桌面端 Main=Swing EDT，AMLL 软件渲染期间 EDT 饱和会把
        // 运行其上的 Ktor 连接协程饿到超时（实测 /lyric 连环 ConnectTimeout 的根因）
        lyricFetchJob = scope.launch(Dispatchers.Default) {
            val id = mediaId.toLongOrNull() ?: return@launch
            var settled = false
            // 网络瞬断（连接超时）时 safeApiCall 返回 null，缓存又没有 → flow 一个值都不发，
            // rawLyric 会永远停在空串（AMLL/歌词页黑屏）。这里对"无任何响应"重试几次。
            repeat(4) { attempt ->
                repo.getLyric(id).collect { lyric ->
                    val lrcText = lyric?.lrc?.lyric?.takeIf { it.isNotBlank() }
                    val yrcText = lyric?.yrc?.lyric?.takeIf { it.isNotBlank() }
                    if (lrcText != null || yrcText != null) {
                        val wordLines = yrcText
                            ?.let { runCatching { LyricParser.parseYrc(it) }.getOrDefault(emptyList()) }
                            .orEmpty()
                        applyLyricsForMedia(mediaId) {
                            rawWordLyric.value = if (wordLines.isNotEmpty()) yrcText.orEmpty() else ""
                            wordLyricLines.value = wordLines
                            rawLyric.value = lrcText ?: LyricParser.toLrcText(wordLines)
                            lyricMap.value = parseLineLyrics(lrcText, wordLines)
                        }
                        settled = true
                    } else if (lyric != null) {
                        // 服务器有响应但确实没有歌词 —— 不再重试
                        applyLyricsForMedia(mediaId) {
                            rawLyric.value = ""
                            rawWordLyric.value = ""
                            wordLyricLines.value = emptyList()
                            lyricMap.value = linkedMapOf(0L to "No lyric available")
                        }
                        settled = true
                    }
                }
                if (settled) return@launch
                if (attempt < 3) delay(2_000)
            }
            applyLyricsForMedia(mediaId) {
                rawLyric.value = ""
                rawWordLyric.value = ""
                wordLyricLines.value = emptyList()
                lyricMap.value = linkedMapOf(0L to "歌词加载失败")
            }
        }
    }

    private fun resetLyricsForMedia(mediaId: String) {
        lyricMediaId.value = mediaId
        lyricIndex.value = 0
        rawLyric.value = ""
        rawWordLyric.value = ""
        wordLyricLines.value = emptyList()
        lyricMap.value = linkedMapOf(0L to "Loading Lyric")
    }

    private inline fun applyLyricsForMedia(mediaId: String, block: () -> Unit) {
        if (lyricMediaId.value == mediaId && currentMedia.value?.id == mediaId) {
            block()
        }
    }

    private fun parseLineLyrics(
        lrcText: String?,
        wordLines: List<LyricParser.WordLine>,
    ): LinkedHashMap<Long?, String?> {
        if (lrcText == null) return LyricParser.toLineLyricMap(wordLines)
        return runCatching { LyricParser.parse(lrcText) }
            .getOrElse {
                wordLines
                    .takeIf { it.isNotEmpty() }
                    ?.let { LyricParser.toLineLyricMap(it) }
                    ?: linkedMapOf(0L to "Lyric parse error")
            }
    }

    fun getComments() {
        scope.launch(Dispatchers.Default) {
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

    fun onLyricLineClick(mediaId: String?, newPosition: Long) {
        val currentId = currentMedia.value?.id ?: return
        if (mediaId != null && mediaId != currentId) return
        if (mediaId != null && lyricMediaId.value != mediaId) return

        val duration = songLength.value
        val safePosition = if (duration > 0) {
            // 旧歌的点击事件可能在切歌动画/WebView 重绘期间迟到；超出当前歌时长的 seek 丢弃。
            if (newPosition > duration + 2_000L) return
            newPosition.coerceIn(0L, duration)
        } else {
            newPosition.coerceAtLeast(0L)
        }
        player.seekTo(safePosition)
    }
    fun onPlaylistClick() = rebuildPlaylistFromTimeline()
    fun onShuffleClick(status: Boolean) = player.setShuffleEnabled(status)

    fun onCleared() {
        scope.cancel()
    }
}

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

sealed interface LyricUiState {
    data object Idle : LyricUiState
    data object Loading : LyricUiState
    data object Empty : LyricUiState
    data class Content(val lineCount: Int) : LyricUiState
    data class Error(val message: String) : LyricUiState
}

/**
 * Ported from the original Android NowPlayingViewModel.
 *
 * Key invariant (preserved from original):
 * The visible queue and current highlight MUST always be rebuilt together from the current
 * Player state via rebuildPlaylistFromTimeline().
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
    private val _queueSnapshot = MutableStateFlow(PlayerQueueSnapshot())
    val queueSnapshot: StateFlow<PlayerQueueSnapshot> = _queueSnapshot.asStateFlow()

    // ── Lyrics ──
    val lyricIndex = MutableStateFlow(0)
    val lyricMap = MutableStateFlow<LinkedHashMap<Long?, String?>>(linkedMapOf())
    val rawLyric = MutableStateFlow("") // raw LRC text for external lyric renderers
    val rawWordLyric = MutableStateFlow("") // raw YRC text for word-level external lyric renderers
    val rawTranslatedLyric = MutableStateFlow("")
    val rawRomanLyric = MutableStateFlow("")
    val wordLyricLines = MutableStateFlow<List<LyricParser.WordLine>>(emptyList())
    val lyricMediaId = MutableStateFlow<String?>(null)
    val lyricUiState = MutableStateFlow<LyricUiState>(LyricUiState.Idle)
    val lyricLoadError = MutableStateFlow<String?>(null)

    // ── Comments ──
    val comments = MutableStateFlow<CommentMusic?>(null)
    val commentsLoading = MutableStateFlow(false)
    val commentsLoadError = MutableStateFlow<String?>(null)
    val commentsFromCache = MutableStateFlow(false)

    init {
        initPlayerSync()
        initLyricSync()
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
                MediaControlsIntegrator.updateMetadata(duration = dur)
            }
        }
        scope.launch {
            player.currentMedia.collect { media ->
                currentMedia.value = media
                commentsFetchJob?.cancel()
                comments.value = null
                commentsLoading.value = false
                commentsLoadError.value = null
                commentsFromCache.value = false
                if (media != null) {
                    MediaControlsIntegrator.updateMetadata(
                        title = media.title,
                        artist = media.artist,
                        album = media.albumTitle,
                        artworkUri = media.artworkUri,
                        duration = media.duration,
                    )
                    fetchLyrics(media.id)
                } else {
                    lyricFetchJob?.cancel()
                    clearLyrics()
                    MediaControlsIntegrator.clear()
                    LyricNotificationController.clearFocus()
                }
            }
        }
        // Queue/order/index/current media are published by each player as one immutable snapshot.
        // This is the only input to visible queue state, so shuffle transitions cannot combine a
        // new order with the previous index.
        scope.launch {
            player.queueSnapshot.collect { snapshot ->
                rebuildPlaylistFromTimeline(snapshot)
            }
        }
    }

    /**
     * Rebuild the visible playlist, window-order indices, and current highlight
     * from the current Player state. This is the SINGLE rebuild path —
     * all track transitions, shuffle toggles, and timeline changes go through here.
     */
    fun rebuildPlaylistFromTimeline(snapshot: PlayerQueueSnapshot = player.queueSnapshot.value) {
        _queueSnapshot.value = snapshot
        shuffleStatus.value = snapshot.shuffleEnabled
    }

    private fun initLyricSync() {
        scope.launch {
            val playbackProgress = combine(currentPosition, songLength) { position, duration ->
                position to duration
            }
            combine(
                playbackProgress,
                lyricMap,
                currentMedia,
                isPlaying,
                lyricMediaId,
            ) { progress, map, media, playing, loadedLyricMediaId ->
                val (position, duration) = progress
                val lyricsBelongToMedia = media != null && media.id == loadedLyricMediaId
                var index = -1
                var currentLyric: String? = null
                var nextLyric: String? = null
                if (lyricsBelongToMedia) {
                    var candidateIndex = 0
                    val iterator = map.entries.iterator()
                    while (iterator.hasNext()) {
                        val (time, lyric) = iterator.next()
                        if (time != null && position >= time) {
                            index = candidateIndex
                            currentLyric = lyric
                            candidateIndex += 1
                        } else {
                            if (index < 0) {
                                index = 0
                                currentLyric = lyric
                                nextLyric = if (iterator.hasNext()) iterator.next().value else null
                            } else {
                                nextLyric = lyric
                            }
                            break
                        }
                    }
                }
                LyricNotificationPayload(
                    index = index.coerceAtLeast(0),
                    media = media,
                    currentLyric = currentLyric,
                    nextLyric = nextLyric,
                    isPlaying = playing,
                    positionMs = (position.coerceAtLeast(0L) / 1_000L) * 1_000L,
                    durationMs = duration,
                )
            }.distinctUntilChanged().collect { payload ->
                lyricIndex.value = payload.index
                val media = payload.media
                if (media == null) {
                    LyricNotificationController.clearFocus()
                } else {
                    LyricNotificationController.updateLyric(
                        title = media.title,
                        artist = media.artist,
                        currentLyric = payload.currentLyric,
                        nextLyric = payload.nextLyric,
                        artworkUri = media.artworkUri,
                        isPlaying = payload.isPlaying,
                        positionMs = payload.positionMs,
                        durationMs = payload.durationMs,
                    )
                }
            }
        }
    }

    private var lyricFetchJob: Job? = null
    private var commentsFetchJob: Job? = null

    private fun fetchLyrics(mediaId: String) {
        lyricFetchJob?.cancel()
        resetLyricsForMedia(mediaId)
        // 网络必须离开 Main：桌面端 Main=Swing EDT，AMLL 软件渲染期间 EDT 饱和会把
        // 运行其上的 Ktor 连接协程饿到超时（实测 /lyric 连环 ConnectTimeout 的根因）
        lyricFetchJob = scope.launch(Dispatchers.Default) {
            val id = mediaId.toLongOrNull()
            if (id == null) {
                applyLyricsForMedia(mediaId) {
                    val message = "歌曲标识无效，无法加载歌词"
                    lyricLoadError.value = message
                    lyricUiState.value = LyricUiState.Error(message)
                }
                return@launch
            }
            var settled = false
            var lastFailure: Exception? = null
            // 网络瞬断（连接超时）时 safeApiCall 返回 null，缓存又没有 → flow 一个值都不发，
            // rawLyric 会永远停在空串（AMLL/歌词页黑屏）。这里对"无任何响应"重试几次。
            repeat(4) { attempt ->
                try {
                    repo.getLyric(id).collect { lyric ->
                        val lrcText = lyric?.lrc?.lyric?.takeIf { it.isNotBlank() }
                        val yrcText = lyric?.yrc?.lyric?.takeIf { it.isNotBlank() }
                        val translatedText = lyric?.tlyric?.lyric?.takeIf { it.isNotBlank() }
                        val romanText = lyric?.romalrc?.lyric?.takeIf { it.isNotBlank() }
                        if (lrcText != null || yrcText != null) {
                            val wordLines = yrcText
                                ?.let { runCatching { LyricParser.parseYrc(it) }.getOrDefault(emptyList()) }
                                .orEmpty()
                            val plainLyricText = lrcText ?: LyricParser.toLrcText(wordLines)
                            val displayLyricMap = if (wordLines.isNotEmpty()) {
                                LyricParser.toLineLyricMap(wordLines)
                            } else {
                                parseLineLyrics(lrcText, wordLines)
                            }
                            if (displayLyricMap.isEmpty()) {
                                applyLyricsForMedia(mediaId) {
                                    val message = "歌词解析失败"
                                    lyricLoadError.value = message
                                    rawLyric.value = ""
                                    rawWordLyric.value = ""
                                    rawTranslatedLyric.value = ""
                                    rawRomanLyric.value = ""
                                    wordLyricLines.value = emptyList()
                                    lyricMap.value = linkedMapOf()
                                    lyricUiState.value = LyricUiState.Error(message)
                                }
                            } else {
                                applyLyricsForMedia(mediaId) {
                                    lyricLoadError.value = null
                                    rawWordLyric.value = if (wordLines.isNotEmpty()) yrcText.orEmpty() else ""
                                    wordLyricLines.value = wordLines
                                    rawTranslatedLyric.value = translatedText.orEmpty()
                                    rawRomanLyric.value = romanText.orEmpty()
                                    rawLyric.value = plainLyricText
                                    lyricMap.value = displayLyricMap
                                    lyricUiState.value = LyricUiState.Content(displayLyricMap.size)
                                }
                            }
                            settled = true
                        } else if (lyric != null) {
                            // 服务器有响应但确实没有歌词 —— 不再重试
                            applyLyricsForMedia(mediaId) {
                                lyricLoadError.value = null
                                rawLyric.value = ""
                                rawWordLyric.value = ""
                                rawTranslatedLyric.value = ""
                                rawRomanLyric.value = ""
                                wordLyricLines.value = emptyList()
                                lyricMap.value = linkedMapOf()
                                lyricUiState.value = LyricUiState.Empty
                            }
                            settled = true
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (failure: Exception) {
                    lastFailure = failure
                }
                if (settled) return@launch
                if (attempt < 3) delay(2_000)
            }
            applyLyricsForMedia(mediaId) {
                val message = lastFailure?.message ?: "歌词请求失败"
                lyricLoadError.value = message
                rawLyric.value = ""
                rawWordLyric.value = ""
                rawTranslatedLyric.value = ""
                rawRomanLyric.value = ""
                wordLyricLines.value = emptyList()
                lyricMap.value = linkedMapOf()
                lyricUiState.value = LyricUiState.Error(message)
            }
        }
    }

    private fun resetLyricsForMedia(mediaId: String) {
        lyricMediaId.value = mediaId
        lyricIndex.value = 0
        rawLyric.value = ""
        rawWordLyric.value = ""
        rawTranslatedLyric.value = ""
        rawRomanLyric.value = ""
        wordLyricLines.value = emptyList()
        lyricLoadError.value = null
        lyricMap.value = linkedMapOf()
        lyricUiState.value = LyricUiState.Loading
    }

    private fun clearLyrics() {
        lyricMediaId.value = null
        lyricIndex.value = 0
        rawLyric.value = ""
        rawWordLyric.value = ""
        rawTranslatedLyric.value = ""
        rawRomanLyric.value = ""
        wordLyricLines.value = emptyList()
        lyricLoadError.value = null
        lyricMap.value = linkedMapOf()
        lyricUiState.value = LyricUiState.Idle
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
                    ?: linkedMapOf()
            }
    }

    fun retryLyrics() {
        currentMedia.value?.id?.let(::fetchLyrics)
    }

    fun getComments() {
        commentsFetchJob?.cancel()
        val mediaId = currentMedia.value?.id ?: return
        val id = mediaId.toLongOrNull() ?: return
        commentsLoading.value = true
        commentsLoadError.value = null
        commentsFetchJob = scope.launch(Dispatchers.Default) {
            var emitted = false
            try {
                repo.getCommentMusic(id).collect { emission ->
                    if (currentMedia.value?.id == mediaId) {
                        emitted = true
                        comments.value = emission.value
                        commentsFromCache.value = emission.isFromCache
                    }
                }
                if (
                    !emitted &&
                    currentMedia.value?.id == mediaId &&
                    currentCoroutineContext()[Job] == commentsFetchJob
                ) {
                    commentsLoadError.value = "评论加载失败，请检查网络后重试"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (failure: Exception) {
                if (
                    currentMedia.value?.id == mediaId &&
                    currentCoroutineContext()[Job] == commentsFetchJob
                ) {
                    commentsLoadError.value = failure.message ?: "评论加载失败"
                }
            } finally {
                if (
                    currentMedia.value?.id == mediaId &&
                    currentCoroutineContext()[Job] == commentsFetchJob
                ) {
                    commentsLoading.value = false
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
        commentsFetchJob?.cancel()
        scope.cancel()
    }

    private data class LyricNotificationPayload(
        val index: Int,
        val media: MediaInfo?,
        val currentLyric: String?,
        val nextLyric: String?,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
    )
}

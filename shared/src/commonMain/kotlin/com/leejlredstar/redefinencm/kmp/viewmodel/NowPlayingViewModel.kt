package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.SongWikiSummary
import com.leejlredstar.redefinencm.kmp.data.api.dto.CommentMusic
import com.leejlredstar.redefinencm.kmp.download.LocalMediaAssets
import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel
import com.leejlredstar.redefinencm.kmp.lyric.LyricQuery
import com.leejlredstar.redefinencm.kmp.lyric.LyricResolution
import com.leejlredstar.redefinencm.kmp.lyric.LyricResolver
import com.leejlredstar.redefinencm.kmp.lyric.LyricSource
import com.leejlredstar.redefinencm.kmp.lyric.LyricSourceMode
import com.leejlredstar.redefinencm.kmp.lyric.LyricSourceModeGate
import com.leejlredstar.redefinencm.kmp.lyric.supportsDynamicNowPlayingCover
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.player.*
import com.leejlredstar.redefinencm.kmp.smtc.MediaControlsIntegrator
import com.leejlredstar.redefinencm.kmp.util.LyricParser
import com.leejlredstar.redefinencm.kmp.util.DownloadScanResult
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface LyricUiState {
    data object Idle : LyricUiState
    data object Loading : LyricUiState
    data class Empty(
        val capabilityLevel: LyricCapabilityLevel? = null,
    ) : LyricUiState
    data class Content(
        val lineCount: Int,
        val capabilityLevel: LyricCapabilityLevel,
    ) : LyricUiState
    data class Error(val message: String) : LyricUiState
}

val LyricUiState.lyricCapabilityLevel: LyricCapabilityLevel?
    get() = when (this) {
        is LyricUiState.Content -> capabilityLevel
        is LyricUiState.Empty -> capabilityLevel
        is LyricUiState.Idle,
        is LyricUiState.Loading,
        is LyricUiState.Error,
        -> null
    }

sealed interface SongWikiUiState {
    data object Idle : SongWikiUiState
    data class Loading(val mediaId: String) : SongWikiUiState
    data class Content(val mediaId: String, val summary: SongWikiSummary) : SongWikiUiState
    data class Empty(val mediaId: String) : SongWikiUiState
    data class Error(val mediaId: String, val message: String) : SongWikiUiState
}

data class FavoriteUiState(
    val mediaId: String? = null,
    val isLiked: Boolean = false,
)

data class DynamicCoverUiState(
    val mediaId: String? = null,
    val url: String? = null,
) {
    fun urlFor(mediaId: String?): String? = url?.takeIf { this.mediaId == mediaId }
}

internal data class LocalArtworkResolutionState(
    val mediaId: String? = null,
    val pending: Boolean = false,
    val active: Boolean = false,
)

internal fun localArtworkAllowsDynamicCover(
    mediaId: String?,
    state: LocalArtworkResolutionState,
): Boolean =
    mediaId != null &&
        (state.mediaId != mediaId || (!state.pending && !state.active))

internal fun completeLocalArtworkResolutionState(
    state: LocalArtworkResolutionState,
    mediaId: String,
    active: Boolean,
): LocalArtworkResolutionState =
    if (state.mediaId == mediaId && state.pending) {
        LocalArtworkResolutionState(
            mediaId = mediaId,
            pending = false,
            active = active,
        )
    } else {
        state
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
    private val mainViewModel: MainViewModel,
    private val settings: PlatformSettings,
    private val lyricResolver: LyricResolver,
    private val localMediaAssets: LocalMediaAssets,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Player state ──
    val currentMedia = MutableStateFlow(player.currentMedia.value)
    val playerStatusRestoreState: StateFlow<PlayerStatusRestoreState> =
        mainViewModel.playerStatusRestoreState
    val isPlaying = MutableStateFlow(player.isPlaying.value)
    val playerState = MutableStateFlow(player.state.value)
    val currentPosition = MutableStateFlow(player.position.value)
    val songLength = MutableStateFlow(player.duration.value)
    val shuffleStatus = MutableStateFlow(player.shuffleEnabled.value)

    // ── Queue ──
    private val _queueSnapshot = MutableStateFlow(player.queueSnapshot.value)
    val queueSnapshot: StateFlow<PlayerQueueSnapshot> = _queueSnapshot.asStateFlow()

    // ── Lyrics ──
    val lyricIndex = MutableStateFlow(0)
    val lyricMap = MutableStateFlow<LinkedHashMap<Long?, String?>>(linkedMapOf())
    val rawLyric = MutableStateFlow("") // raw LRC text for external lyric renderers
    val rawWordLyric = MutableStateFlow("") // raw YRC text for word-level external lyric renderers
    val rawTtmlLyric = MutableStateFlow("")
    val rawTranslatedLyric = MutableStateFlow("")
    val rawRomanLyric = MutableStateFlow("")
    val wordLyricLines = MutableStateFlow<List<LyricParser.WordLine>>(emptyList())
    val untimedLyricLines = MutableStateFlow<List<String>>(emptyList())
    val activeLyricSource = MutableStateFlow<LyricSource?>(null)
    val activeLyricEndpoint = MutableStateFlow("")
    private val lyricSourceModeGate = LyricSourceModeGate()
    val lyricSourceMode: StateFlow<LyricSourceMode> = lyricSourceModeGate.mode
    val showTranslatedLyric = MutableStateFlow(false)
    val showRomanLyric = MutableStateFlow(false)
    val lyricMediaId = MutableStateFlow(player.currentMedia.value?.id)
    val lyricUiState = MutableStateFlow<LyricUiState>(
        if (player.currentMedia.value == null) LyricUiState.Idle else LyricUiState.Loading,
    )
    val lyricLoadError = MutableStateFlow<String?>(null)

    // ── Comments ──
    val comments = MutableStateFlow<CommentMusic?>(null)
    val commentsLoading = MutableStateFlow(false)
    val commentsLoadError = MutableStateFlow<String?>(null)
    val commentsFromCache = MutableStateFlow(false)

    // ── Song wiki ──
    val songWikiUiState = MutableStateFlow<SongWikiUiState>(SongWikiUiState.Idle)

    // ── Favorite ──
    val favoriteUiState = MutableStateFlow(FavoriteUiState())

    // ── Artwork ──
    val useDynamicCover = MutableStateFlow(false)
    val dynamicCoverUiState = MutableStateFlow(DynamicCoverUiState())
    private val _localArtworkActive = MutableStateFlow(false)
    val localArtworkActive: StateFlow<Boolean> = _localArtworkActive.asStateFlow()
    private val localArtworkResolution = MutableStateFlow(LocalArtworkResolutionState())
    private val _remoteArtworkUri = MutableStateFlow("")
    val remoteArtworkUri: StateFlow<String> = _remoteArtworkUri.asStateFlow()
    private var activeLocalArtworkUri: String? = null

    init {
        initLyricPreferences()
        initPlayerSync()
        initLyricSync()
        initFavoriteSync()
        initDynamicCoverPreference()
        initDynamicCoverSync()
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
            player.currentMedia.collectLatest { media ->
                // Advance before the first suspension: an A → B → A switch must reject the
                // original A request even if its cancellation is delivered late.
                val lyricGeneration = beginLyricRequest()
                releaseActiveLocalArtwork()
                if (media == null) {
                    currentMedia.value = null
                    clearLocalArtworkResolution()
                } else {
                    beginLocalArtworkResolution(media.id)
                    // Dynamic-cover synchronization consumes this ViewModel flow. Publishing the
                    // media only after its pending gate prevents it from observing the new song
                    // without the matching local-artwork resolution state.
                    currentMedia.value = media
                }
                _remoteArtworkUri.value = media?.artworkUri.orEmpty()
                songWikiFetchJob?.cancel()
                songWikiFetchJob = null
                songWikiRequestGeneration += 1
                songWikiUiState.value = SongWikiUiState.Idle
                commentsFetchJob?.cancel()
                comments.value = null
                commentsLoading.value = false
                commentsLoadError.value = null
                commentsFromCache.value = false
                if (media != null) {
                    preparePendingLyricsForMedia(media)
                    val songId = media.id.toLongOrNull()?.takeIf { it > 0L }
                    val localAudioAvailable = songId != null && withContext(Dispatchers.Default) {
                        when (DownloadedSongsCache.ensureInitialized()) {
                            is DownloadScanResult.Success ->
                                DownloadedSongsCache.isDownloaded(songId)
                            is DownloadScanResult.Failure -> false
                        }
                    }
                    fetchLyrics(
                        media = media,
                        preferLocal = localAudioAvailable,
                        requestGeneration = lyricGeneration,
                    )
                    var unresolvedLocalArtwork: String? = null
                    if (localAudioAvailable) {
                        unresolvedLocalArtwork = withContext(Dispatchers.Default) {
                            runCatching {
                                localMediaAssets.resolveArtworkUri(checkNotNull(songId))
                            }.getOrNull()
                        }
                    }
                    try {
                        currentCoroutineContext().ensureActive()
                    } catch (cancelled: CancellationException) {
                        unresolvedLocalArtwork?.let(localMediaAssets::releaseArtworkUri)
                        throw cancelled
                    }
                    val resolvedArtwork = unresolvedLocalArtwork?.takeIf(String::isNotBlank)
                    if (!completeLocalArtworkResolution(media.id, resolvedArtwork != null)) {
                        resolvedArtwork?.let(localMediaAssets::releaseArtworkUri)
                        return@collectLatest
                    }
                    val effectiveMedia = if (resolvedArtwork == null) {
                        media
                    } else {
                        activeLocalArtworkUri = resolvedArtwork
                        media.copy(artworkUri = resolvedArtwork)
                    }
                    currentMedia.value = effectiveMedia
                    MediaControlsIntegrator.updateMetadata(
                        title = effectiveMedia.title,
                        artist = effectiveMedia.artist,
                        album = effectiveMedia.albumTitle,
                        artworkUri = effectiveMedia.artworkUri,
                        duration = effectiveMedia.duration,
                    )
                } else {
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
                scheduleNextLyricPrefetch(snapshot)
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

    private fun initFavoriteSync() {
        scope.launch {
            combine(player.currentMedia, mainViewModel.uid) { media, uid -> media?.id to uid }
                .distinctUntilChanged()
                .collectLatest { (mediaId, uid) ->
                    favoriteActionJob?.cancel()
                    val requestGeneration = ++favoriteStatusGeneration
                    val songId = mediaId?.toLongOrNull()?.takeIf { it > 0 }
                    if (songId == null || uid <= 0) {
                        favoriteUiState.value = FavoriteUiState()
                        return@collectLatest
                    }

                    favoriteUiState.value = FavoriteUiState(mediaId = mediaId)
                    val isLiked = withContext(Dispatchers.Default) {
                        repo.isSongLiked(songId)
                    }
                    if (
                        requestGeneration == favoriteStatusGeneration &&
                        player.currentMedia.value?.id == mediaId &&
                        mainViewModel.uid.value == uid
                    ) {
                        favoriteUiState.value = FavoriteUiState(
                            mediaId = mediaId,
                            isLiked = isLiked == true,
                        )
                    }
                }
        }
    }

    private fun initDynamicCoverPreference() {
        scope.launch {
            try {
                settings.awaitLoaded()
                setUseDynamicCover(
                    settings.getBooleanAsync(SettingKeys.USE_DYNAMIC_COVER, false),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                setUseDynamicCover(false)
            }
        }
    }

    private fun initLyricPreferences() {
        scope.launch {
            try {
                settings.awaitLoaded()
                val storedMode = LyricSourceMode.fromStoredWireValue(
                    settings.getStringAsync(
                        SettingKeys.LYRIC_SOURCE_MODE,
                        LyricSourceMode.DEFAULT.wireValue,
                    ),
                )
                val showTranslation = settings.getBooleanAsync(
                    SettingKeys.SHOW_TRANSLATED_LYRIC,
                    false,
                )
                val showRoman = settings.getBooleanAsync(
                    SettingKeys.SHOW_ROMAN_LYRIC,
                    false,
                )
                showTranslatedLyric.value = showTranslation
                showRomanLyric.value = showRoman
                lyricSourceModeGate.completeInitialLoad(storedMode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                showTranslatedLyric.value = false
                showRomanLyric.value = false
                // The persisted privacy choice is unknown, so do not send a song ID to a
                // third-party source. Backend-only keeps lyrics functional while failing closed.
                lyricSourceModeGate.failInitialLoad()
            }
        }
    }

    private fun initDynamicCoverSync() {
        scope.launch {
            combine(
                currentMedia,
                mainViewModel.uid,
                useDynamicCover,
                localArtworkResolution,
            ) { media, uid, enabled, localArtworkState ->
                DynamicCoverRequest(
                    mediaId = media?.id,
                    uid = uid,
                    enabled = enabled,
                    localArtworkAllowsDynamicCover = localArtworkAllowsDynamicCover(
                        media?.id,
                        localArtworkState,
                    ),
                )
            }
                .distinctUntilChanged()
                .collectLatest { request ->
                    val mediaId = request.mediaId
                    val uid = request.uid
                    val enabled = request.enabled
                    dynamicCoverUiState.value = DynamicCoverUiState(mediaId = mediaId)
                    val songId = mediaId?.toLongOrNull()?.takeIf { it > 0 }
                    if (
                        !enabled ||
                        !request.localArtworkAllowsDynamicCover ||
                        uid <= 0 ||
                        songId == null
                    ) return@collectLatest

                    val url = withContext(Dispatchers.Default) {
                        repo.getSongDynamicCoverUrl(songId)
                    }
                    if (
                        useDynamicCover.value &&
                        localArtworkAllowsDynamicCover(
                            mediaId,
                            localArtworkResolution.value,
                        ) &&
                        mainViewModel.uid.value == uid &&
                        currentMedia.value?.id == mediaId &&
                        player.currentMedia.value?.id == mediaId
                    ) {
                        dynamicCoverUiState.value = DynamicCoverUiState(
                            mediaId = mediaId,
                            url = url,
                        )
                    }
                }
        }
    }

    fun setUseDynamicCover(enabled: Boolean) {
        useDynamicCover.value = enabled && supportsDynamicNowPlayingCover
    }

    private var lyricFetchJob: Job? = null
    private var lyricPrefetchJob: Job? = null
    private val lyricRequestGeneration = MutableStateFlow(0L)
    private var commentsFetchJob: Job? = null
    private var songWikiFetchJob: Job? = null
    private var songWikiRequestGeneration = 0L
    private var favoriteActionJob: Job? = null
    private var favoriteStatusGeneration = 0L

    private fun preparePendingLyricsForMedia(media: MediaInfo) {
        val songId = media.id.toLongOrNull()
        val mode = lyricSourceModeGate.currentModeOrNull()
        val preferLocal = songId != null && DownloadedSongsCache.isDownloaded(songId)
        val query = songId?.let {
            LyricQuery(
                songId = it,
                title = media.title,
                artist = media.artist,
                album = media.albumTitle,
                durationMs = media.duration,
            )
        }
        val cachedResolution = if (query != null && mode != null) {
            lyricResolver.cachedResolution(query, mode, preferLocal)
        } else {
            null
        }
        prepareLyricsForMedia(media.id, cachedResolution)
    }

    private fun fetchLyrics(
        media: MediaInfo,
        preferLocal: Boolean = DownloadedSongsCache.isDownloaded(media.id.toLongOrNull() ?: -1L),
        requestGeneration: Long = beginLyricRequest(),
    ) {
        val mediaId = media.id
        if (
            requestGeneration != lyricRequestGeneration.value ||
            currentMedia.value?.id != mediaId
        ) {
            return
        }
        val songId = mediaId.toLongOrNull()
        val query = songId?.let {
            LyricQuery(
                songId = it,
                title = media.title,
                artist = media.artist,
                album = media.albumTitle,
                durationMs = media.duration,
            )
        }
        val readyMode = lyricSourceModeGate.currentModeOrNull()
        val initialCachedResolution = if (query != null && readyMode != null) {
            lyricResolver.cachedResolution(query, readyMode, preferLocal)
        } else {
            null
        }
        prepareLyricsForMedia(mediaId, initialCachedResolution)
        // 网络必须离开 Main：桌面端 Main=Swing EDT，AMLL 软件渲染期间 EDT 饱和会把
        // 运行其上的 Ktor 连接协程饿到超时（实测 /lyric 连环 ConnectTimeout 的根因）
        lyricFetchJob = scope.launch(Dispatchers.Default) {
            val mode = lyricSourceModeGate.awaitMode()
            if (songId == null) {
                applyLyricsForMedia(mediaId, requestGeneration) {
                    applyLyricError("歌曲标识无效，无法加载歌词")
                }
                return@launch
            }
            val resolvedQuery = checkNotNull(query)
            var displayedResolution = initialCachedResolution
            val cachedAfterModeLoad = lyricResolver.cachedResolution(resolvedQuery, mode, preferLocal)
            if (cachedAfterModeLoad != null && cachedAfterModeLoad != displayedResolution) {
                applyLyricsForMedia(mediaId, requestGeneration) {
                    applyLyricResolution(cachedAfterModeLoad)
                }
                displayedResolution = cachedAfterModeLoad
            }
            try {
                lyricResolver.resolve(resolvedQuery, mode, preferLocal).collect { resolution ->
                    if (resolution != displayedResolution) {
                        applyLyricsForMedia(mediaId, requestGeneration) {
                            applyLyricResolution(resolution)
                        }
                        displayedResolution = resolution
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                applyLyricsForMedia(mediaId, requestGeneration) {
                    applyLyricError("歌词请求失败")
                }
            }
        }
    }

    private fun beginLyricRequest(): Long {
        lyricFetchJob?.cancel()
        return lyricRequestGeneration.updateAndGet { it + 1L }
    }

    private fun prepareLyricsForMedia(
        mediaId: String,
        cachedResolution: LyricResolution?,
    ) {
        lyricMediaId.value = mediaId
        lyricIndex.value = 0
        if (cachedResolution == null) {
            clearLyricPayload()
            lyricLoadError.value = null
            lyricUiState.value = LyricUiState.Loading
        } else {
            applyLyricResolution(cachedResolution)
        }
    }

    private fun applyLyricResolution(resolution: LyricResolution) {
        when (resolution) {
            is LyricResolution.Found -> {
                val document = resolution.document
                val displayLyricMap = LyricParser.toLineLyricMap(document.lines)
                if (displayLyricMap.isEmpty()) {
                    applyLyricError("歌词解析失败")
                } else {
                    lyricLoadError.value = null
                    rawTtmlLyric.value = document.rawTtml
                    rawWordLyric.value = document.rawWordLyric
                    wordLyricLines.value = document.lines
                    untimedLyricLines.value = emptyList()
                    rawTranslatedLyric.value = document.rawTranslatedLyric
                    rawRomanLyric.value = document.rawRomanLyric
                    rawLyric.value = document.rawLineLyric
                    activeLyricSource.value = document.source
                    activeLyricEndpoint.value = document.endpoint
                    lyricMap.value = displayLyricMap
                    lyricUiState.value = LyricUiState.Content(
                        lineCount = displayLyricMap.size,
                        capabilityLevel = document.capabilityLevel,
                    )
                }
            }
            is LyricResolution.Untimed -> {
                val document = resolution.document
                clearLyricPayload()
                lyricLoadError.value = null
                rawLyric.value = document.rawLineLyric
                rawWordLyric.value = document.rawWordLyric
                rawTranslatedLyric.value = document.rawTranslatedLyric
                rawRomanLyric.value = document.rawRomanLyric
                untimedLyricLines.value = document.untimedLines
                activeLyricSource.value = document.source
                activeLyricEndpoint.value = document.endpoint
                lyricUiState.value = LyricUiState.Content(
                    lineCount = document.untimedLines.size,
                    capabilityLevel = LyricCapabilityLevel.UNSYNCED,
                )
            }
            LyricResolution.Empty -> {
                clearLyricPayload()
                lyricLoadError.value = null
                lyricUiState.value = LyricUiState.Empty()
            }
            is LyricResolution.Error ->
                applyLyricError(resolution.message.ifBlank { "歌词请求失败" })
        }
    }

    private fun scheduleNextLyricPrefetch(
        snapshot: PlayerQueueSnapshot = player.queueSnapshot.value,
    ) {
        lyricPrefetchJob?.cancel()
        val candidate = nextLyricPrefetchCandidate(snapshot)
        if (candidate == null) {
            lyricPrefetchJob = null
            return
        }
        lyricPrefetchJob = scope.launch(Dispatchers.Default) {
            val mode = lyricSourceModeGate.awaitMode()
            val localLibraryReady =
                DownloadedSongsCache.ensureInitialized() is DownloadScanResult.Success
            val songId = candidate.id.toLongOrNull()?.takeIf { it > 0L } ?: return@launch
            val preferLocal = localLibraryReady && DownloadedSongsCache.isDownloaded(songId)
            val query = LyricQuery(
                songId = songId,
                title = candidate.title,
                artist = candidate.artist,
                album = candidate.albumTitle,
                durationMs = candidate.duration,
            )
            if (lyricResolver.cachedResolution(query, mode, preferLocal) != null) return@launch
            try {
                lyricResolver.resolveLatest(
                    query = query,
                    mode = mode,
                    preferLocal = preferLocal,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Prefetch is opportunistic; foreground loading retains its normal error state.
            }
        }
    }

    private fun clearLyrics() {
        lyricMediaId.value = null
        lyricIndex.value = 0
        rawLyric.value = ""
        rawWordLyric.value = ""
        rawTtmlLyric.value = ""
        rawTranslatedLyric.value = ""
        rawRomanLyric.value = ""
        wordLyricLines.value = emptyList()
        untimedLyricLines.value = emptyList()
        activeLyricSource.value = null
        activeLyricEndpoint.value = ""
        lyricLoadError.value = null
        lyricMap.value = linkedMapOf()
        lyricUiState.value = LyricUiState.Idle
    }

    private fun clearLyricPayload() {
        rawLyric.value = ""
        rawWordLyric.value = ""
        rawTtmlLyric.value = ""
        rawTranslatedLyric.value = ""
        rawRomanLyric.value = ""
        wordLyricLines.value = emptyList()
        untimedLyricLines.value = emptyList()
        activeLyricSource.value = null
        activeLyricEndpoint.value = ""
        lyricMap.value = linkedMapOf()
    }

    private fun applyLyricError(message: String) {
        clearLyricPayload()
        lyricLoadError.value = message
        lyricUiState.value = LyricUiState.Error(message)
    }

    private inline fun applyLyricsForMedia(
        mediaId: String,
        requestGeneration: Long,
        block: () -> Unit,
    ) {
        if (
            requestGeneration == lyricRequestGeneration.value &&
            lyricMediaId.value == mediaId &&
            currentMedia.value?.id == mediaId
        ) {
            block()
        }
    }

    fun retryLyrics() {
        currentMedia.value?.let(::fetchLyrics)
    }

    fun setLyricSourceMode(mode: LyricSourceMode) {
        if (!lyricSourceModeGate.update(mode)) return
        currentMedia.value?.let(::fetchLyrics)
        scheduleNextLyricPrefetch()
    }

    fun setLyricDisplayOptions(
        showTranslation: Boolean,
        showRomanization: Boolean,
    ) {
        showTranslatedLyric.value = showTranslation
        showRomanLyric.value = showRomanization
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

    fun getSongWikiSummary() {
        val mediaId = currentMedia.value?.id
        if (mediaId == null) {
            songWikiFetchJob?.cancel()
            songWikiRequestGeneration += 1
            songWikiUiState.value = SongWikiUiState.Error(
                mediaId = "",
                message = "当前没有正在播放的歌曲",
            )
            return
        }
        when (val state = songWikiUiState.value) {
            is SongWikiUiState.Loading -> if (state.mediaId == mediaId) return
            is SongWikiUiState.Content -> if (state.mediaId == mediaId) return
            is SongWikiUiState.Empty -> if (state.mediaId == mediaId) return
            else -> Unit
        }
        songWikiFetchJob?.cancel()
        val requestGeneration = ++songWikiRequestGeneration
        val id = mediaId.toLongOrNull()?.takeIf { it > 0 }
        if (id == null) {
            songWikiUiState.value = SongWikiUiState.Error(
                mediaId = mediaId,
                message = "歌曲标识无效，无法加载音乐百科",
            )
            return
        }

        songWikiUiState.value = SongWikiUiState.Loading(mediaId)
        songWikiFetchJob = scope.launch(Dispatchers.Default) {
            try {
                val summary = repo.getSongWikiSummary(id)
                if (
                    currentMedia.value?.id != mediaId ||
                    requestGeneration != songWikiRequestGeneration
                ) return@launch
                songWikiUiState.value = when {
                    summary == null -> SongWikiUiState.Error(
                        mediaId = mediaId,
                        message = "音乐百科加载失败，请检查网络后重试",
                    )
                    summary.sections.isEmpty() -> SongWikiUiState.Empty(mediaId)
                    else -> SongWikiUiState.Content(mediaId, summary)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (failure: Exception) {
                if (
                    currentMedia.value?.id == mediaId &&
                    requestGeneration == songWikiRequestGeneration
                ) {
                    songWikiUiState.value = SongWikiUiState.Error(
                        mediaId = mediaId,
                        message = failure.message ?: "音乐百科加载失败",
                    )
                }
            }
        }
    }

    // ── Playback actions ──

    fun onFavClick() {
        val uid = mainViewModel.uid.value.takeIf { it > 0 } ?: return
        val mediaId = player.currentMedia.value?.id ?: return
        val songId = mediaId.toLongOrNull()?.takeIf { it > 0 } ?: return
        if (favoriteUiState.value.let { it.mediaId == mediaId && it.isLiked }) return
        if (favoriteActionJob?.isActive == true) return

        favoriteActionJob = scope.launch {
            val liked = withContext(Dispatchers.Default) { repo.like(songId) } != null
            if (
                liked &&
                player.currentMedia.value?.id == mediaId &&
                mainViewModel.uid.value == uid
            ) {
                favoriteStatusGeneration += 1
                favoriteUiState.value = FavoriteUiState(
                    mediaId = mediaId,
                    isLiked = true,
                )
            }
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
        songWikiFetchJob?.cancel()
        favoriteActionJob?.cancel()
        releaseActiveLocalArtwork()
        scope.cancel()
    }

    private fun releaseActiveLocalArtwork() {
        activeLocalArtworkUri?.let(localMediaAssets::releaseArtworkUri)
        activeLocalArtworkUri = null
    }

    private fun beginLocalArtworkResolution(mediaId: String) {
        localArtworkResolution.value = LocalArtworkResolutionState(
            mediaId = mediaId,
            pending = true,
        )
        _localArtworkActive.value = false
    }

    private fun clearLocalArtworkResolution() {
        localArtworkResolution.value = LocalArtworkResolutionState()
        _localArtworkActive.value = false
    }

    private fun completeLocalArtworkResolution(
        mediaId: String,
        active: Boolean,
    ): Boolean {
        val current = localArtworkResolution.value
        val completed = completeLocalArtworkResolutionState(current, mediaId, active)
        if (completed === current) return false
        localArtworkResolution.value = completed
        _localArtworkActive.value = completed.active
        return true
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

    private data class DynamicCoverRequest(
        val mediaId: String?,
        val uid: Long,
        val enabled: Boolean,
        val localArtworkAllowsDynamicCover: Boolean,
    )
}

internal fun nextLyricPrefetchCandidate(snapshot: PlayerQueueSnapshot): MediaInfo? {
    if (snapshot.currentIndex !in snapshot.items.indices) return null
    val candidate = snapshot.items.getOrNull(snapshot.currentIndex + 1) ?: return null
    return candidate.takeUnless { it.id == snapshot.currentMedia?.id }
}

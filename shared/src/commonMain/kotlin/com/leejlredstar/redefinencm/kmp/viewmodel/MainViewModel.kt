package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.PersistedMediaItem
import com.leejlredstar.redefinencm.kmp.data.PlayerStatus
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlaybackAccountVerificationEvent
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingCoordinator
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingState
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.forCredential
import com.leejlredstar.redefinencm.kmp.player.playbackCredentialKey
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.cookieFingerprint
import com.leejlredstar.redefinencm.kmp.util.currentReleaseVersion
import com.leejlredstar.redefinencm.kmp.util.fetchLatestReleaseTag
import com.leejlredstar.redefinencm.kmp.util.isNewerReleaseVersion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun accountCookieFingerprint(cookie: String): Long = cookieFingerprint(cookie)

internal fun isCachedAccountIdentityValid(
    cookie: String,
    uid: Long,
    fingerprint: Long,
): Boolean =
    cookie.isNotBlank() && uid != 0L && fingerprint == accountCookieFingerprint(cookie)

internal fun shouldApplyPlaybackVerification(
    currentUid: Long,
    currentCredentialKey: Long?,
    lastAppliedGeneration: Long?,
    event: PlaybackAccountVerificationEvent,
): Boolean = currentUid == event.uid &&
    currentCredentialKey == event.credentialKey &&
    (lastAppliedGeneration ?: Long.MIN_VALUE) < event.reportingGeneration

/**
 * Ported from the original Android MainViewModel.
 * KMP-compatible: uses PlatformPlayer instead of MediaController,
 * PlatformSettings instead of DataStoreManager, kotlinx.serialization instead of Gson.
 */
class MainViewModel(
    private val repo: Repository,
    private val settings: PlatformSettings,
    private val player: PlatformPlayer,
    private val downloadManager: SongDownloadManager,
    private val playbackReportingCoordinator: PlaybackReportingCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val accountCredentialKey = MutableStateFlow<Long?>(null)
    private val lastAppliedPlaybackVerification = mutableMapOf<Long, Long>()
    private var lastSavedPlayerStatus: PlayerStatus? = null
    private val playerStatusSaveMutex = Mutex()
    val playerStatusSaveError = MutableStateFlow<String?>(null)
    val playerStatusLoadError = MutableStateFlow<String?>(null)

    // ── User ──
    private val _uid = MutableStateFlow(0L)
    val uid: StateFlow<Long> = _uid.asStateFlow()

    val userDetail = MutableStateFlow<UserDetail?>(null)
    val userLevel = MutableStateFlow<UserLevelResponse?>(null)
    val userPlaylists = MutableStateFlow<List<UserPlaylistEach>>(emptyList())
    val userPlaylistsLoaded = MutableStateFlow(false)
    val accountLoading = MutableStateFlow(false)
    val accountLoadError = MutableStateFlow<String?>(null)
    val userDetailLoadError = MutableStateFlow<String?>(null)
    val userLevelLoadError = MutableStateFlow<String?>(null)
    val userPlaylistsLoadError = MutableStateFlow<String?>(null)
    val userDetailFromCache = MutableStateFlow(false)
    val userLevelFromCache = MutableStateFlow(false)
    val userPlaylistsFromCache = MutableStateFlow(false)
    val playbackReportingState: StateFlow<PlaybackReportingState> = combine(
        playbackReportingCoordinator.reportingState,
        accountCredentialKey,
    ) { state, credentialKey ->
        state.forCredential(credentialKey)
    }.stateIn(scope, SharingStarted.Eagerly, PlaybackReportingState())
    private val accountGeneration = MutableStateFlow(0L)
    private var accountJob: Job? = null

    // ── Intelligence playback ──
    val intelligenceLoadingPlaylistId = MutableStateFlow<Long?>(null)
    val intelligenceError = MutableStateFlow<String?>(null)
    private val intelligenceGeneration = MutableStateFlow(0L)
    private var intelligenceJob: Job? = null

    // ── Playlist ──
    val playlistDetail = MutableStateFlow<PlaylistDetail?>(null)
    val playlistSongs = MutableStateFlow<PlaylistTrackAll?>(null)
    val playlistLoading = MutableStateFlow(false)
    val playlistLoadError = MutableStateFlow<String?>(null)
    val playlistDetailLoadError = MutableStateFlow<String?>(null)
    val playlistDetailFromCache = MutableStateFlow(false)
    val playlistSongsFromCache = MutableStateFlow(false)
    private val playlistGeneration = MutableStateFlow(0L)
    private val activePlaylistId = MutableStateFlow<Long?>(null)
    private var playlistJob: Job? = null

    // ── Recommend ──
    val recommendResource = MutableStateFlow<RecommendResource?>(null)
    val recommendSongs = MutableStateFlow<RecommendSongs?>(null)
    val recommendResourceLoadError = MutableStateFlow<String?>(null)
    val recommendSongsLoadError = MutableStateFlow<String?>(null)
    val recommendResourceFromCache = MutableStateFlow(false)
    val recommendSongsFromCache = MutableStateFlow(false)

    // ── Search ──
    val searchResults = MutableStateFlow<List<SongDetailSongs>>(emptyList())
    val searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchLoading = MutableStateFlow(false)
    val searchSubmittedQuery = MutableStateFlow<String?>(null)
    val searchError = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    // ── Update check（原版 SplashActivity.checkAppUpdate）──
    val updateMessage = MutableStateFlow<String?>(null)

    init {
        loadAccount(clearPersistedAccount = false)
        restorePlayerStatus()
        downloadManager.syncWithLocalLibrary()
        initPlayerStatusAutosave()
        observePlaybackVerification()
        checkAppUpdate()
    }

    private fun observePlaybackVerification() {
        scope.launch {
            playbackReportingCoordinator.verificationEvents.collect { event ->
                if (
                    !shouldApplyPlaybackVerification(
                        currentUid = _uid.value,
                        currentCredentialKey = accountCredentialKey.value,
                        lastAppliedGeneration = lastAppliedPlaybackVerification[event.credentialKey],
                        event = event,
                    )
                ) {
                    return@collect
                }
                lastAppliedPlaybackVerification[event.credentialKey] = event.reportingGeneration
                event.userLevel?.let { refreshedLevel ->
                    userLevel.value = refreshedLevel
                    userLevelFromCache.value = false
                    userLevelLoadError.value = null
                }
            }
        }
    }

    /** checkUpdate 设置开启时，比较 GitHub 最新 release tag 与本地版本，不同则提示。 */
    private fun checkAppUpdate() {
        scope.launch(Dispatchers.Default) {
            settings.awaitLoaded()
            if (!settings.getBooleanAsync(SettingKeys.CHECK_UPDATE, false)) return@launch
            // 完整构建版本带提交 hash（v0.0.1.412ae548）；更新检查只比较发布基线 tag。
            val current = currentReleaseVersion()
            val latest = fetchLatestReleaseTag() ?: return@launch
            if (isNewerReleaseVersion(latest, current)) {
                updateMessage.value = "发现新版本：$latest"
            }
        }
    }

    fun consumeUpdateMessage() {
        updateMessage.value = null
    }

    // ── 播放状态持久化（原版 savePlayerStatus / restorePlayerStatus）──

    fun savePlayerStatus() {
        val status = buildPlayerStatus() ?: return
        scope.launch(Dispatchers.Default) { savePlayerStatus(status) }
    }

    private fun buildPlayerStatus(): PlayerStatus? {
        val queueSnapshot = player.queueSnapshot.value
        if (queueSnapshot.items.isEmpty()) return null
        return PlayerStatus(
            playlist = queueSnapshot.items.map {
                PersistedMediaItem(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    albumTitle = it.albumTitle,
                    artworkUri = it.artworkUri,
                    duration = it.duration,
                    sourceId = it.sourceId,
                )
            },
            index = queueSnapshot.currentIndex.coerceAtLeast(0),
            position = player.position.value,
            isPlaying = player.isPlaying.value,
            isShuffling = queueSnapshot.shuffleEnabled,
        )
    }

    private suspend fun savePlayerStatus(status: PlayerStatus) {
        playerStatusSaveMutex.withLock {
            if (status == lastSavedPlayerStatus) return
            repo.savePlayerStatus(status)
                .onSuccess {
                    lastSavedPlayerStatus = status
                    playerStatusSaveError.value = null
                }
                .onFailure { failure ->
                    playerStatusSaveError.value = failure.message ?: "播放状态保存失败"
                }
        }
    }

    private suspend fun saveCurrentPlayerStatus() {
        savePlayerStatus(buildPlayerStatus() ?: return)
    }

    private fun initPlayerStatusAutosave() {
        scope.launch(Dispatchers.Default) {
            combine(player.queueSnapshot, player.isPlaying) { _, _ -> Unit }
                .drop(1)
                .collect { saveCurrentPlayerStatus() }
        }
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                // Position-only changes are checkpointed periodically. Queue, track, shuffle and
                // play/pause changes are persisted immediately by the flow above.
                delay(PLAYER_POSITION_CHECKPOINT_MS)
                saveCurrentPlayerStatus()
            }
        }
    }

    fun restorePlayerStatus() {
        // DB 读取 + 整条播放列表 JSON 反序列化放后台，避免启动时阻塞主线程（Android 主线程/桌面 EDT）；
        // 但 player 操作（ExoPlayer 要求主线程）切回 Main 执行。
        scope.launch(Dispatchers.Default) {
            val status = repo.getPlayerStatus()
                .onFailure { failure ->
                    playerStatusLoadError.value = failure.message ?: "播放状态读取失败"
                }
                .getOrNull()
                ?: return@launch
            playerStatusLoadError.value = null
            if (status.playlist.isEmpty()) return@launch
            lastSavedPlayerStatus = status
            val items = status.playlist.map {
                MediaInfo(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    albumTitle = it.albumTitle,
                    artworkUri = it.artworkUri,
                    placeholderUri = "redefinencm://playbackPlaceHolder?id=${it.id}",
                    duration = it.duration,
                    sourceId = it.sourceId,
                )
            }
            withContext(Dispatchers.Main) {
                // 播放器里已有队列时不覆盖（例如服务先于 UI 恢复了状态）
                if (player.queueSnapshot.value.items.isNotEmpty()) return@withContext
                player.restoreQueue(items, status.index.coerceIn(0, items.lastIndex), status.position)
                player.setShuffleEnabled(status.isShuffling)
                // 原版恢复时不自动播放（play() 被注释掉），此处保持一致
            }
        }
    }

    private suspend fun resolveUid(ignorePersistedUid: Boolean, cookie: String): Long {
        if (!ignorePersistedUid) {
            val cachedUid = settings.getLongAsync(SettingKeys.UID, 0L)
            val cachedFingerprint = settings.getLongAsync(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
            if (isCachedAccountIdentityValid(cookie, cachedUid, cachedFingerprint)) {
                return cachedUid
            }
        }
        return repo.getUserAccount()?.account?.id ?: 0L
    }

    /** Login/account changes invalidate all work started for the previous credential. */
    private fun loadAccount(clearPersistedAccount: Boolean) {
        cancelIntelligenceRequest()
        val generation = accountGeneration.updateAndGet { it + 1L }
        accountJob?.cancel()
        if (clearPersistedAccount) accountCredentialKey.value = null
        accountLoading.value = true
        accountLoadError.value = null
        userDetailLoadError.value = null
        userLevelLoadError.value = null
        userPlaylistsLoadError.value = null
        recommendResourceLoadError.value = null
        recommendSongsLoadError.value = null
        if (clearPersistedAccount) {
            _uid.value = 0L
            userDetail.value = null
            userLevel.value = null
            userPlaylists.value = emptyList()
            userPlaylistsLoaded.value = false
            recommendResource.value = null
            recommendSongs.value = null
            userDetailFromCache.value = false
            userLevelFromCache.value = false
            userPlaylistsFromCache.value = false
            recommendResourceFromCache.value = false
            recommendSongsFromCache.value = false
        }
        accountJob = scope.launch(Dispatchers.Default) {
            try {
                settings.awaitLoaded()
                val cookie = settings.getStringAsync(SettingKeys.COOKIE, "")
                val credentialKey = playbackCredentialKey(HttpClientFactory.cleanCookie(cookie))
                if (accountGeneration.value != generation) return@launch
                accountCredentialKey.value = credentialKey
                if (clearPersistedAccount) {
                    settings.setLong(SettingKeys.UID, 0L)
                    settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
                    settings.flush()
                    repo.clearAccountScopedCaches().getOrThrow()
                }

                val resolvedUid = resolveUid(
                    ignorePersistedUid = clearPersistedAccount,
                    cookie = cookie,
                )
                ensureActive()
                if (accountGeneration.value != generation) return@launch
                if (resolvedUid == 0L) {
                    accountCredentialKey.value = null
                    settings.setLong(SettingKeys.UID, 0L)
                    settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
                    settings.flush()
                    return@launch
                }
                settings.setLong(SettingKeys.UID, resolvedUid)
                settings.setLong(
                    SettingKeys.UID_COOKIE_FINGERPRINT,
                    accountCookieFingerprint(cookie),
                )
                settings.flush()
                ensureActive()
                if (accountGeneration.value != generation) return@launch
                _uid.value = resolvedUid

                coroutineScope {
                    launch {
                        var emitted = false
                        repo.getUserDetail(resolvedUid).collect { emission ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                val detail = emission.value
                                emitted = true
                                userDetail.value = detail
                                userDetailFromCache.value = emission.isFromCache
                                userDetailLoadError.value = null
                            }
                        }
                        if (
                            !emitted &&
                            accountGeneration.value == generation &&
                            _uid.value == resolvedUid
                        ) {
                            userDetailLoadError.value = "用户资料加载失败，请检查网络后重试"
                        }
                    }
                    launch {
                        var emitted = false
                        repo.getUserLevel(resolvedUid).collect { emission ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                emitted = true
                                userLevel.value = emission.value
                                userLevelFromCache.value = emission.isFromCache
                                userLevelLoadError.value = null
                            }
                        }
                        if (
                            !emitted &&
                            accountGeneration.value == generation &&
                            _uid.value == resolvedUid
                        ) {
                            userLevelLoadError.value = "用户等级信息加载失败，请检查网络后重试"
                        }
                    }
                    launch {
                        var emitted = false
                        repo.getUserPlaylist(resolvedUid).collect { emission ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                emitted = true
                                userPlaylists.value = emission.value.playlist
                                userPlaylistsLoaded.value = true
                                userPlaylistsFromCache.value = emission.isFromCache
                                userPlaylistsLoadError.value = null
                            }
                        }
                        if (
                            !emitted &&
                            accountGeneration.value == generation &&
                            _uid.value == resolvedUid
                        ) {
                            userPlaylistsLoadError.value = "歌单加载失败，请检查网络后重试"
                        }
                    }
                    launch {
                        var emitted = false
                        repo.getRecommendResource(
                            uid = resolvedUid,
                            readCache = !clearPersistedAccount,
                        ).collect { emission ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                emitted = true
                                recommendResource.value = emission.value
                                recommendResourceFromCache.value = emission.isFromCache
                                recommendResourceLoadError.value = null
                            }
                        }
                        if (
                            !emitted &&
                            accountGeneration.value == generation &&
                            _uid.value == resolvedUid
                        ) {
                            recommendResourceLoadError.value = "推荐歌单加载失败，请检查网络后重试"
                        }
                    }
                    launch {
                        var emitted = false
                        repo.getRecommendSongs(
                            uid = resolvedUid,
                            readCache = !clearPersistedAccount,
                        ).collect { emission ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                emitted = true
                                recommendSongs.value = emission.value
                                recommendSongsFromCache.value = emission.isFromCache
                                recommendSongsLoadError.value = null
                            }
                        }
                        if (
                            !emitted &&
                            accountGeneration.value == generation &&
                            _uid.value == resolvedUid
                        ) {
                            recommendSongsLoadError.value = "每日推荐加载失败，请检查网络后重试"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (failure: Exception) {
                if (accountGeneration.value == generation) {
                    accountCredentialKey.value = null
                    _uid.value = 0L
                    userDetail.value = null
                    userLevel.value = null
                    userPlaylists.value = emptyList()
                    userPlaylistsLoaded.value = false
                    userLevelLoadError.value = null
                    recommendResource.value = null
                    recommendSongs.value = null
                    userDetailFromCache.value = false
                    userLevelFromCache.value = false
                    userPlaylistsFromCache.value = false
                    recommendResourceFromCache.value = false
                    recommendSongsFromCache.value = false
                    settings.setLong(SettingKeys.UID, 0L)
                    settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
                    runCatching { settings.flush() }
                    accountLoadError.value = failure.message ?: "账号数据加载失败"
                }
            } finally {
                if (accountGeneration.value == generation) {
                    accountLoading.value = false
                }
            }
        }
    }

    /** 登录、退出或换号后调用。 */
    fun refreshAccount() {
        loadAccount(clearPersistedAccount = true)
    }

    /** Retry account-scoped cache/network reads without clearing the persisted identity. */
    fun retryAccountData() {
        loadAccount(clearPersistedAccount = false)
    }

    fun startIntelligenceMode(playlistId: Long) {
        val generation = intelligenceGeneration.updateAndGet { it + 1L }
        intelligenceJob?.cancel()
        intelligenceJob = null
        intelligenceLoadingPlaylistId.value = null
        intelligenceError.value = null

        val requestUid = _uid.value
        if (playlistId <= 0L) {
            intelligenceError.value = "喜欢歌单 ID 无效"
            return
        }
        if (requestUid <= 0L) {
            intelligenceError.value = "请先登录后再使用心动模式"
            return
        }

        intelligenceLoadingPlaylistId.value = playlistId
        intelligenceJob = scope.launch(Dispatchers.Default) {
            try {
                val seed = selectIntelligenceSeed(repo.getLikedSongIds(requestUid))
                    ?: error("无法获取喜欢的音乐，请检查网络、登录状态或歌单内容")
                ensureActive()
                if (!isCurrentIntelligenceRequest(generation, requestUid)) return@launch

                val response = repo.getIntelligenceList(
                    id = seed,
                    pid = playlistId,
                ) ?: error("心动模式列表获取失败，请检查网络后重试")
                val queue = buildIntelligenceQueue(
                    songInfos = response.data.orEmpty().map { it.songInfo },
                    playlistId = playlistId,
                )
                if (queue.isEmpty()) error("心动模式返回的歌曲列表为空")
                ensureActive()
                if (!isCurrentIntelligenceRequest(generation, requestUid)) return@launch

                withContext(Dispatchers.Main) {
                    if (isCurrentIntelligenceRequest(generation, requestUid)) {
                        replaceQueueWithIntelligenceList(player, queue)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrentIntelligenceRequest(generation, requestUid)) {
                    intelligenceError.value = failure.message ?: "心动模式启动失败"
                }
            } finally {
                if (isCurrentIntelligenceRequest(generation, requestUid)) {
                    intelligenceLoadingPlaylistId.value = null
                    if (currentCoroutineContext()[Job] == intelligenceJob) {
                        intelligenceJob = null
                    }
                }
            }
        }
    }

    private fun isCurrentIntelligenceRequest(
        generation: Long,
        uid: Long,
    ): Boolean = intelligenceGeneration.value == generation && _uid.value == uid

    private fun cancelIntelligenceRequest() {
        intelligenceGeneration.updateAndGet { it + 1L }
        intelligenceJob?.cancel()
        intelligenceJob = null
        intelligenceLoadingPlaylistId.value = null
        intelligenceError.value = null
    }

    fun fetchPlaylistDetail(songlistID: Long) {
        val generation = playlistGeneration.updateAndGet { it + 1L }
        playlistJob?.cancel()
        activePlaylistId.value = songlistID
        playlistDetail.value = null
        playlistSongs.value = null
        playlistLoading.value = true
        playlistLoadError.value = null
        playlistDetailLoadError.value = null
        playlistDetailFromCache.value = false
        playlistSongsFromCache.value = false
        playlistJob = scope.launch(Dispatchers.Default) {
            var detailEmitted = false
            var tracksEmitted = false
            try {
                coroutineScope {
                    launch {
                        repo.getPlaylistDetail(songlistID).collect { emission ->
                            if (
                                playlistGeneration.value == generation &&
                                activePlaylistId.value == songlistID
                            ) {
                                detailEmitted = true
                                playlistDetail.value = emission.value
                                playlistDetailFromCache.value = emission.isFromCache
                            }
                        }
                    }
                    launch {
                        repo.getPlaylistTrackAll(songlistID).collect { emission ->
                            if (
                                playlistGeneration.value == generation &&
                                activePlaylistId.value == songlistID
                            ) {
                                tracksEmitted = true
                                playlistSongs.value = emission.value
                                playlistSongsFromCache.value = emission.isFromCache
                            }
                        }
                    }
                }
                if (
                    !detailEmitted &&
                    playlistGeneration.value == generation &&
                    activePlaylistId.value == songlistID
                ) {
                    playlistDetailLoadError.value = "歌单资料加载失败，可重试以恢复封面与简介"
                }
                if (
                    !tracksEmitted &&
                    playlistGeneration.value == generation &&
                    activePlaylistId.value == songlistID
                ) {
                    playlistLoadError.value = "歌曲列表加载失败，请检查网络后重试"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (failure: Exception) {
                if (playlistGeneration.value == generation) {
                    playlistLoadError.value = failure.message ?: "歌单加载失败"
                }
            } finally {
                if (playlistGeneration.value == generation) {
                    playlistLoading.value = false
                }
            }
        }
    }

    // ── Download（应用内下载队列；不再使用系统 DownloadManager）──

    fun onDownloadPlaylistClick(songlistID: Long) {
        val currentSongs = if (activePlaylistId.value == songlistID) {
            playlistSongs.value?.songs.orEmpty()
        } else {
            emptyList()
        }
        if (currentSongs.isNotEmpty()) {
            downloadManager.enqueueSongs(currentSongs, songlistID)
        } else {
            downloadManager.enqueuePlaylist(songlistID)
        }
    }

    /** 上报歌单播放次数（原版播放歌单时调用）。 */
    fun updatePlaylistPlaycount(songlistID: Long) {
        scope.launch(Dispatchers.Default) { repo.updatePlaylistPlaycount(songlistID) }
    }

    // ── Search ──

    fun search(keyword: String) {
        val query = keyword.trim()
        searchJob?.cancel()
        if (query.isEmpty()) {
            searchResults.value = emptyList()
            searchLoading.value = false
            searchSubmittedQuery.value = null
            searchError.value = null
            return
        }
        searchSuggestions.value = emptyList()
        searchSubmittedQuery.value = query
        searchError.value = null
        searchJob = scope.launch(Dispatchers.Default) {
            searchLoading.value = true
            try {
                val response = repo.search(query)
                searchResults.value = response?.result?.songs ?: emptyList()
                if (response == null) {
                    searchError.value = "搜索失败，请检查网络后重试"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (currentCoroutineContext()[Job] == searchJob) {
                    searchResults.value = emptyList()
                    searchError.value = "搜索失败，请检查网络后重试"
                }
            } finally {
                if (currentCoroutineContext()[Job] == searchJob) {
                    searchLoading.value = false
                }
            }
        }
    }

    fun fetchSearchSuggestions(keyword: String) {
        val query = keyword.trim()
        suggestionJob?.cancel()
        if (query.isEmpty()) {
            searchSuggestions.value = emptyList()
            return
        }
        suggestionJob = scope.launch(Dispatchers.Default) {
            val response = repo.searchSuggest(query)
            searchSuggestions.value =
                response?.result?.allMatch?.map { it.keyword } ?: emptyList()
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        suggestionJob?.cancel()
        searchResults.value = emptyList()
        searchSuggestions.value = emptyList()
        searchLoading.value = false
        searchSubmittedQuery.value = null
        searchError.value = null
    }

    fun onCleared() {
        scope.cancel()
    }

    private companion object {
        const val PLAYER_POSITION_CHECKPOINT_MS = 30_000L
    }
}

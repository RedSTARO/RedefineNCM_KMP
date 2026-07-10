package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.PersistedMediaItem
import com.leejlredstar.redefinencm.kmp.data.PlayerStatus
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.currentReleaseVersion
import com.leejlredstar.redefinencm.kmp.util.fetchLatestReleaseTag
import com.leejlredstar.redefinencm.kmp.util.isNewerReleaseVersion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun accountCookieFingerprint(cookie: String): Long {
    var hash = -3750763034362895579L // FNV-1a 64-bit offset basis represented as a signed Long.
    cookie.forEach { character ->
        hash = hash xor character.code.toLong()
        hash *= 1099511628211L
    }
    return hash
}

internal fun isCachedAccountIdentityValid(
    cookie: String,
    uid: Long,
    fingerprint: Long,
): Boolean =
    cookie.isNotBlank() && uid != 0L && fingerprint == accountCookieFingerprint(cookie)

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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastSavedPlayerStatus: PlayerStatus? = null
    private val playerStatusSaveMutex = Mutex()
    val playerStatusSaveError = MutableStateFlow<String?>(null)
    val playerStatusLoadError = MutableStateFlow<String?>(null)

    // ── User ──
    private val _uid = MutableStateFlow(0L)
    val uid: StateFlow<Long> = _uid.asStateFlow()

    val userDetail = MutableStateFlow<UserDetail?>(null)
    val userPlaylists = MutableStateFlow<List<UserPlaylistEach>>(emptyList())
    val accountLoadError = MutableStateFlow<String?>(null)
    private val accountGeneration = MutableStateFlow(0L)
    private var accountJob: Job? = null

    // ── Playlist ──
    val playlistDetail = MutableStateFlow<PlaylistDetail?>(null)
    val playlistSongs = MutableStateFlow<PlaylistTrackAll?>(null)
    val playlistLoadError = MutableStateFlow<String?>(null)
    private val playlistGeneration = MutableStateFlow(0L)
    private val activePlaylistId = MutableStateFlow<Long?>(null)
    private var playlistJob: Job? = null

    // ── Recommend ──
    val recommendResource = MutableStateFlow<RecommendResource?>(null)
    val recommendSongs = MutableStateFlow<RecommendSongs?>(null)

    // ── Search ──
    val searchResults = MutableStateFlow<List<SongDetailSongs>>(emptyList())
    val searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchLoading = MutableStateFlow(false)
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    // ── Update check（原版 SplashActivity.checkAppUpdate）──
    val updateMessage = MutableStateFlow<String?>(null)

    init {
        loadAccount(clearPersistedAccount = false)
        restorePlayerStatus()
        downloadManager.syncWithLocalLibrary()
        initPlayerStatusAutosave()
        checkAppUpdate()
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
        val generation = accountGeneration.updateAndGet { it + 1L }
        accountJob?.cancel()
        accountLoadError.value = null
        if (clearPersistedAccount) {
            _uid.value = 0L
            userDetail.value = null
            userPlaylists.value = emptyList()
            recommendResource.value = null
            recommendSongs.value = null
        }
        accountJob = scope.launch(Dispatchers.Default) {
            try {
                settings.awaitLoaded()
                val cookie = settings.getStringAsync(SettingKeys.COOKIE, "")
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
                        repo.getUserDetail(resolvedUid).collect { detail ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                userDetail.value = detail
                            }
                        }
                    }
                    launch {
                        repo.getUserPlaylist(resolvedUid).collect { detail ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                userPlaylists.value = detail?.playlist.orEmpty()
                            }
                        }
                    }
                    launch {
                        repo.getRecommendResource(
                            uid = resolvedUid,
                            readCache = !clearPersistedAccount,
                        ).collect { detail ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                recommendResource.value = detail
                            }
                        }
                    }
                    launch {
                        repo.getRecommendSongs(
                            uid = resolvedUid,
                            readCache = !clearPersistedAccount,
                        ).collect { detail ->
                            if (accountGeneration.value == generation && _uid.value == resolvedUid) {
                                recommendSongs.value = detail
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (failure: Exception) {
                if (accountGeneration.value == generation) {
                    _uid.value = 0L
                    userDetail.value = null
                    userPlaylists.value = emptyList()
                    recommendResource.value = null
                    recommendSongs.value = null
                    settings.setLong(SettingKeys.UID, 0L)
                    settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
                    runCatching { settings.flush() }
                    accountLoadError.value = failure.message ?: "账号数据加载失败"
                }
            }
        }
    }

    /** 登录、退出或换号后调用。 */
    fun refreshAccount() {
        loadAccount(clearPersistedAccount = true)
    }

    fun fetchPlaylistDetail(songlistID: Long) {
        val generation = playlistGeneration.updateAndGet { it + 1L }
        playlistJob?.cancel()
        activePlaylistId.value = songlistID
        playlistDetail.value = null
        playlistSongs.value = null
        playlistLoadError.value = null
        playlistJob = scope.launch(Dispatchers.Default) {
            try {
                coroutineScope {
                    launch {
                        repo.getPlaylistDetail(songlistID).collect { detail ->
                            if (
                                playlistGeneration.value == generation &&
                                activePlaylistId.value == songlistID
                            ) {
                                playlistDetail.value = detail
                            }
                        }
                    }
                    launch {
                        repo.getPlaylistTrackAll(songlistID).collect { detail ->
                            if (
                                playlistGeneration.value == generation &&
                                activePlaylistId.value == songlistID
                            ) {
                                playlistSongs.value = detail
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (failure: Exception) {
                if (playlistGeneration.value == generation) {
                    playlistLoadError.value = failure.message ?: "歌单加载失败"
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
            return
        }
        searchSuggestions.value = emptyList()
        searchJob = scope.launch(Dispatchers.Default) {
            searchLoading.value = true
            try {
                val response = repo.search(query)
                searchResults.value = response?.result?.songs ?: emptyList()
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
    }

    fun onCleared() {
        scope.cancel()
    }

    private companion object {
        const val PLAYER_POSITION_CHECKPOINT_MS = 30_000L
    }
}

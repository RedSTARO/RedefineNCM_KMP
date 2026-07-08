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
import com.leejlredstar.redefinencm.kmp.util.currentAppVersion
import com.leejlredstar.redefinencm.kmp.util.fetchLatestReleaseTag
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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

    // ── User ──
    private val _uid = MutableStateFlow(0L)
    val uid: StateFlow<Long> = _uid.asStateFlow()

    val userDetail = MutableStateFlow<UserDetail?>(null)
    val userPlaylists = MutableStateFlow<List<UserPlaylistEach>>(emptyList())

    // ── Playlist ──
    val playlistDetail = MutableStateFlow<PlaylistDetail?>(null)
    val playlistSongs = MutableStateFlow<PlaylistTrackAll?>(null)

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
        scope.launch(Dispatchers.Default) {
            // 先解析 UID（原版 fetchUID 为阻塞式，保证后续用户请求携带有效 uid）
            fetchUID()
            fetchUserData()
            fetchUserPlaylists()
        }
        fetchRecommend()
        restorePlayerStatus()
        initPlayerStatusAutosave()
        checkAppUpdate()
    }

    /** checkUpdate 设置开启时，比较 GitHub 最新 release tag 与本地版本，不同则提示。 */
    private fun checkAppUpdate() {
        scope.launch(Dispatchers.Default) {
            if (!settings.getBooleanAsync(SettingKeys.CHECK_UPDATE, false)) return@launch
            // 归一化后比较：GitHub tag 常带 "v" 前缀（v1.2.3），本地 versionName 不带（1.2.3），
            // 直接字符串比较会永远判为"有新版本"。
            val current = currentAppVersion()?.normalizeVersion() ?: return@launch
            val latest = fetchLatestReleaseTag() ?: return@launch
            if (latest.normalizeVersion().isNotEmpty() && latest.normalizeVersion() != current) {
                updateMessage.value = "发现新版本：$latest"
            }
        }
    }

    private fun String.normalizeVersion(): String = trim().removePrefix("v").removePrefix("V").trim()

    fun consumeUpdateMessage() {
        updateMessage.value = null
    }

    // ── 播放状态持久化（原版 savePlayerStatus / restorePlayerStatus）──

    fun savePlayerStatus() {
        val status = buildPlayerStatus() ?: return
        scope.launch(Dispatchers.Default) { savePlayerStatus(status) }
    }

    private fun buildPlayerStatus(): PlayerStatus? {
        val queue = player.queue.value
        if (queue.isEmpty()) return null
        return PlayerStatus(
            playlist = queue.map {
                PersistedMediaItem(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    albumTitle = it.albumTitle,
                    artworkUri = it.artworkUri,
                    duration = it.duration,
                )
            },
            index = player.currentIndex.value.coerceAtLeast(0),
            position = player.position.value,
            isPlaying = player.isPlaying.value,
            isShuffling = player.shuffleEnabled.value,
        )
    }

    private suspend fun savePlayerStatus(status: PlayerStatus) {
        if (status == lastSavedPlayerStatus) return
        repo.savePlayerStatus(status)
        lastSavedPlayerStatus = status
    }

    private suspend fun saveCurrentPlayerStatus() {
        savePlayerStatus(buildPlayerStatus() ?: return)
    }

    private fun initPlayerStatusAutosave() {
        scope.launch(Dispatchers.Default) {
            combine(player.queue, player.currentIndex, player.shuffleEnabled) { _, _, _ -> Unit }
                .drop(1)
                .collect { saveCurrentPlayerStatus() }
        }
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(2_000)
                saveCurrentPlayerStatus()
            }
        }
    }

    fun restorePlayerStatus() {
        // DB 读取 + 整条播放列表 JSON 反序列化放后台，避免启动时阻塞主线程（Android 主线程/桌面 EDT）；
        // 但 player 操作（ExoPlayer 要求主线程）切回 Main 执行。
        scope.launch(Dispatchers.Default) {
            val status = repo.getPlayerStatus() ?: return@launch
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
                if (player.queue.value.isNotEmpty()) return@withContext
                player.restoreQueue(items, status.index.coerceIn(0, items.lastIndex), status.position)
                player.setShuffleEnabled(status.isShuffling)
                // 原版恢复时不自动播放（play() 被注释掉），此处保持一致
            }
        }
    }

    private suspend fun fetchUID() {
        val cached = settings.getLongAsync(SettingKeys.UID, 0L)
        if (cached != 0L) {
            _uid.value = cached
            return
        }
        // 无缓存时走 /user/account 解析（原版 retrofit.userAccount().account.id）
        val accountId = repo.getUserAccount()?.account?.id ?: 0L
        if (accountId != 0L) {
            _uid.value = accountId
            settings.setLong(SettingKeys.UID, accountId)
        }
    }

    /** 登录/换号后调用：清掉缓存 UID 并重新拉取用户数据。 */
    fun refreshAccount() {
        settings.setLong(SettingKeys.UID, 0L)
        _uid.value = 0L
        scope.launch(Dispatchers.Default) {
            fetchUID()
            fetchUserData()
            fetchUserPlaylists()
        }
    }

    fun fetchUserData() {
        scope.launch(Dispatchers.Default) {
            val uid = _uid.value
            if (uid == 0L) {
                userDetail.value = null
                return@launch
            }
            repo.getUserDetail(uid).collect { detail ->
                userDetail.value = detail
            }
        }
    }

    fun fetchUserPlaylists() {
        scope.launch(Dispatchers.Default) {
            val uid = _uid.value
            if (uid == 0L) {
                userPlaylists.value = emptyList()
                return@launch
            }
            repo.getUserPlaylist(uid).collect { detail ->
                userPlaylists.value = detail?.playlist ?: emptyList()
            }
        }
    }

    fun fetchPlaylistDetail(songlistID: Long) {
        scope.launch(Dispatchers.Default) {
            repo.getPlaylistDetail(songlistID).collect { detail ->
                playlistDetail.value = detail
            }
        }
        fetchPlaylistTrackAll(songlistID)
    }

    fun fetchPlaylistTrackAll(songlistID: Long) {
        scope.launch(Dispatchers.Default) {
            repo.getPlaylistTrackAll(songlistID).collect { detail ->
                playlistSongs.value = detail
            }
        }
    }

    fun fetchRecommend() {
        scope.launch(Dispatchers.Default) {
            repo.getRecommendResource().collect { detail ->
                recommendResource.value = detail
            }
        }
        scope.launch(Dispatchers.Default) {
            repo.getRecommendSongs().collect { detail ->
                recommendSongs.value = detail
            }
        }
    }

    // ── Download（应用内下载队列；不再使用系统 DownloadManager）──

    fun onDownloadPlaylistClick(songlistID: Long) {
        val currentSongs = playlistSongs.value?.songs.orEmpty()
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
}

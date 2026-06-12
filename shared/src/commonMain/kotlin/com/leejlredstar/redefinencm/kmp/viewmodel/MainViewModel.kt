package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import com.leejlredstar.redefinencm.kmp.data.api.safeApiCall
import com.leejlredstar.redefinencm.kmp.player.LyricBus
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlayerState
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    init {
        fetchUID()
        fetchUserData()
        fetchUserPlaylists()
        fetchRecommend()
    }

    private fun fetchUID() {
        scope.launch {
            val value = settings.getLongAsync(SettingKeys.UID, 0L)
            if (value != 0L) {
                _uid.value = value
            } else {
                // Fetch from API
                val account = safeApiCall {
                    // Account fetch is handled via the API
                    // In the original, this was done through RetrofitInstance
                    // For KMP, we access via Repository
                    null // Handled by the platform-specific init
                }
            }
        }
    }

    fun fetchUserData() {
        scope.launch {
            repo.getUserDetail(_uid.value).collect { detail ->
                userDetail.value = detail
            }
        }
    }

    fun fetchUserPlaylists() {
        scope.launch {
            repo.getUserPlaylist(_uid.value).collect { detail ->
                userPlaylists.value = detail?.playlist ?: emptyList()
            }
        }
    }

    fun fetchPlaylistDetail(songlistID: Long) {
        scope.launch {
            repo.getPlaylistDetail(songlistID).collect { detail ->
                playlistDetail.value = detail
            }
        }
        fetchPlaylistTrackAll(songlistID)
    }

    fun fetchPlaylistTrackAll(songlistID: Long) {
        scope.launch {
            repo.getPlaylistTrackAll(songlistID).collect { detail ->
                playlistSongs.value = detail
            }
        }
    }

    fun fetchRecommend() {
        scope.launch {
            repo.getRecommendResource().collect { detail ->
                recommendResource.value = detail
            }
        }
        scope.launch {
            repo.getRecommendSongs().collect { detail ->
                recommendSongs.value = detail
            }
        }
    }

    // ── Search ──

    fun search(keyword: String) {
        val query = keyword.trim()
        if (query.isEmpty()) {
            searchResults.value = emptyList()
            return
        }
        searchSuggestions.value = emptyList()
        scope.launch {
            searchLoading.value = true
            val response = repo.search(query)
            searchResults.value = response?.result?.songs ?: emptyList()
            searchLoading.value = false
        }
    }

    fun fetchSearchSuggestions(keyword: String) {
        val query = keyword.trim()
        if (query.isEmpty()) {
            searchSuggestions.value = emptyList()
            return
        }
        scope.launch {
            val response = repo.searchSuggest(query)
            searchSuggestions.value =
                response?.result?.allMatch?.map { it.keyword } ?: emptyList()
        }
    }

    fun clearSearch() {
        searchResults.value = emptyList()
        searchSuggestions.value = emptyList()
        searchLoading.value = false
    }

    fun onCleared() {
        scope.cancel()
    }
}

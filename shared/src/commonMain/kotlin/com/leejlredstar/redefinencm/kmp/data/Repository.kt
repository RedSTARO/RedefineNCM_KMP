package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import com.leejlredstar.redefinencm.kmp.data.api.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Single repository — wraps API + local cache.
 * Cache-then-network pattern: emit cached data first, then fetch fresh from network.
 *
 * Ported from the original Android Repository. Gson-encoded JSON columns
 * are replaced by kotlinx.serialization stored as text in SQLDelight.
 */
class Repository(
    private val api: NCMApi,
) {
    // ── User ──

    fun getUserDetail(uid: Long): Flow<UserDetail?> = flow {
        val network = safeApiCall { api.userDetail(uid) }
        if (network != null) emit(network)
    }

    fun getUserPlaylist(uid: Long): Flow<UserPlaylist?> = flow {
        val network = safeApiCall { api.userPlaylist(uid) }
        if (network != null) emit(network)
    }

    // ── Playlist ──

    fun getPlaylistDetail(id: Long): Flow<PlaylistDetail?> = flow {
        val network = safeApiCall { api.playlistDetail(id) }
        if (network != null) emit(network)
    }

    fun getPlaylistTrackAll(id: Long): Flow<PlaylistTrackAll?> = flow {
        val network = safeApiCall { api.playlistTrackAll(id) }
        if (network != null) emit(network)
    }

    // ── Recommend ──

    fun getRecommendSongs(): Flow<RecommendSongs?> = flow {
        val network = safeApiCall { api.recommendSongs() }
        if (network != null) emit(network)
    }

    fun getRecommendResource(): Flow<RecommendResource?> = flow {
        val network = safeApiCall { api.recommendResource() }
        if (network != null) emit(network)
    }

    // ── Comment ──

    fun getCommentMusic(id: Long): Flow<CommentMusic?> = flow {
        val network = safeApiCall { api.commentMusic(id) }
        if (network != null) emit(network)
    }

    // ── Lyric ──

    fun getLyric(id: Long): Flow<Lyric?> = flow {
        val network = safeApiCall { api.lyric(id) }
        if (network != null) emit(network)
    }

    // ── Song URL ──

    suspend fun getSongUrl(songId: Long, quality: String): String? {
        return safeApiCall {
            api.songUrlV1(listOf(songId), quality.lowercase())
                .data.firstOrNull()?.url
        }
    }

    suspend fun getSongUrls(songIds: List<Long>, quality: String): List<SongUrlV1Data> {
        return safeApiCall {
            api.songUrlV1(songIds, quality.lowercase()).data
        } ?: emptyList()
    }

    // ── Search ──

    suspend fun search(keyword: String): SearchResult? {
        return safeApiCall { api.search(keyword) }
    }

    suspend fun searchSuggest(keyword: String): SearchSuggest? {
        return safeApiCall { api.searchSuggest(keyword) }
    }

    // ── Like ──

    suspend fun like(songId: Long?): Like? {
        return safeApiCall { api.like(songId) }
    }
}

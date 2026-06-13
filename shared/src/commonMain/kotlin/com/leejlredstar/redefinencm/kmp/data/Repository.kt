package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import com.leejlredstar.redefinencm.kmp.data.api.safeApiCall
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class Repository(
    private val api: NCMApi,
    private val db: AppDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── User ──

    fun getUserDetail(uid: Long): Flow<UserDetail?> = flow {
        db.cachedUserDetailQueries.selectByUid(uid).executeAsOneOrNull()
            ?.let { emit(json.decodeFromString<UserDetail>(it)) }
        safeApiCall { api.userDetail(uid) }?.let { network ->
            db.cachedUserDetailQueries.upsert(uid, json.encodeToString(network))
            emit(network)
        }
    }

    fun getUserPlaylist(uid: Long): Flow<UserPlaylist?> = flow {
        db.cachedUserPlaylistQueries.selectByUid(uid).executeAsOneOrNull()
            ?.let { emit(json.decodeFromString<UserPlaylist>(it)) }
        safeApiCall { api.userPlaylist(uid) }?.let { network ->
            db.cachedUserPlaylistQueries.upsert(uid, json.encodeToString(network))
            emit(network)
        }
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
        db.cachedRecommendSongsQueries.select().executeAsOneOrNull()
            ?.let { emit(json.decodeFromString<RecommendSongs>(it)) }
        safeApiCall { api.recommendSongs() }?.let { network ->
            db.cachedRecommendSongsQueries.upsert(json.encodeToString(network))
            emit(network)
        }
    }

    fun getRecommendResource(): Flow<RecommendResource?> = flow {
        db.cachedRecommendResourceQueries.select().executeAsOneOrNull()
            ?.let { emit(json.decodeFromString<RecommendResource>(it)) }
        safeApiCall { api.recommendResource() }?.let { network ->
            db.cachedRecommendResourceQueries.upsert(json.encodeToString(network))
            emit(network)
        }
    }

    // ── Comment ──

    fun getCommentMusic(id: Long): Flow<CommentMusic?> = flow {
        val network = safeApiCall { api.commentMusic(id) }
        if (network != null) emit(network)
    }

    // ── Lyric ──

    fun getLyric(id: Long): Flow<Lyric?> = flow {
        db.cachedLyricQueries.selectBySongId(id).executeAsOneOrNull()
            ?.let { emit(json.decodeFromString<Lyric>(it)) }
        safeApiCall { api.lyric(id) }?.let { network ->
            db.cachedLyricQueries.upsert(id, json.encodeToString(network))
            emit(network)
        }
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

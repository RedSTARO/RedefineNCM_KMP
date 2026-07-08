package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import com.leejlredstar.redefinencm.kmp.data.api.safeApiCall
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

enum class LyricCacheStatus {
    Saved,
    NoLyric,
    Failed,
}

class Repository(
    private val api: NCMApi,
    private val db: AppDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── User ──

    /** 网络获取当前登录账号（用于首次解析 UID，原版 fetchUID 的 userAccount 调用）。 */
    suspend fun getUserAccount(): UserAccount? = safeApiCall { api.userAccount() }

    fun getUserDetail(uid: Long): Flow<UserDetail?> = flow {
        runCatching {
            db.cachedUserDetailQueries.selectByUid(uid).executeAsOneOrNull()
                ?.let { json.decodeFromString<UserDetail>(it) }
        }.getOrNull()?.let { emit(it) }
        safeApiCall { api.userDetail(uid) }?.let { network ->
            runCatching { db.cachedUserDetailQueries.upsert(uid, json.encodeToString(network)) }
            emit(network)
        }
    }

    fun getUserPlaylist(uid: Long): Flow<UserPlaylist?> = flow {
        runCatching {
            db.cachedUserPlaylistQueries.selectByUid(uid).executeAsOneOrNull()
                ?.let { json.decodeFromString<UserPlaylist>(it) }
        }.getOrNull()?.let { emit(it) }
        safeApiCall { api.userPlaylist(uid) }?.let { network ->
            runCatching { db.cachedUserPlaylistQueries.upsert(uid, json.encodeToString(network)) }
            emit(network)
        }
    }

    // ── Playlist ──

    fun getPlaylistDetail(id: Long): Flow<PlaylistDetail?> = flow {
        runCatching {
            db.cachedPlaylistDetailQueries.selectById(id).executeAsOneOrNull()
                ?.let { json.decodeFromString<PlaylistDetail>(it) }
        }.getOrNull()?.let { emit(it) }
        safeApiCall { api.playlistDetail(id) }?.let { network ->
            runCatching { db.cachedPlaylistDetailQueries.upsert(id, json.encodeToString(network)) }
            emit(network)
        }
    }

    fun getPlaylistTrackAll(id: Long): Flow<PlaylistTrackAll?> = flow {
        runCatching {
            db.cachedPlaylistTrackAllQueries.selectById(id).executeAsOneOrNull()
                ?.let { json.decodeFromString<PlaylistTrackAll>(it) }
        }.getOrNull()?.let { emit(it) }
        safeApiCall { api.playlistTrackAll(id) }?.let { network ->
            runCatching { db.cachedPlaylistTrackAllQueries.upsert(id, json.encodeToString(network)) }
            emit(network)
        }
    }

    /** 一次性网络获取歌单全部曲目（批量下载用，不走缓存流）。 */
    suspend fun getPlaylistTrackAllOnce(id: Long): PlaylistTrackAll? =
        safeApiCall { api.playlistTrackAll(id) }

    /** 上报歌单播放次数（原版播放歌单时调用）。 */
    suspend fun updatePlaylistPlaycount(id: Long) {
        safeApiCall { api.playlistUpdatePlaycount(id) }
    }

    // ── Recommend ──

    fun getRecommendSongs(): Flow<RecommendSongs?> = flow {
        runCatching {
            db.cachedRecommendSongsQueries.select().executeAsOneOrNull()
                ?.let { json.decodeFromString<RecommendSongs>(it) }
        }.getOrNull()?.let { emit(it) }
        safeApiCall { api.recommendSongs() }?.let { network ->
            runCatching { db.cachedRecommendSongsQueries.upsert(json.encodeToString(network)) }
            emit(network)
        }
    }

    fun getRecommendResource(): Flow<RecommendResource?> = flow {
        runCatching {
            db.cachedRecommendResourceQueries.select().executeAsOneOrNull()
                ?.let { json.decodeFromString<RecommendResource>(it) }
        }.getOrNull()?.let { emit(it) }
        safeApiCall { api.recommendResource() }?.let { network ->
            runCatching { db.cachedRecommendResourceQueries.upsert(json.encodeToString(network)) }
            emit(network)
        }
    }

    // ── Comment ──

    fun getCommentMusic(id: Long): Flow<CommentMusic?> = flow {
        runCatching {
            db.cachedCommentMusicQueries.selectBySongId(id).executeAsOneOrNull()
                ?.let { json.decodeFromString<CommentMusic>(it) }
        }.getOrNull()?.let { emit(it) }
        safeApiCall { api.commentMusic(id) }?.let { network ->
            runCatching { db.cachedCommentMusicQueries.upsert(id, json.encodeToString(network)) }
            emit(network)
        }
    }

    // ── Lyric ──

    fun getLyric(id: Long): Flow<Lyric?> = flow {
        cachedLyric(id)?.let { emit(it) }
        fetchAndCacheLyric(id)?.let { emit(it) }
    }

    suspend fun cacheLyric(id: Long): LyricCacheStatus {
        cachedLyric(id)?.takeIf { it.hasAnyLyricText() }?.let { return LyricCacheStatus.Saved }
        val network = fetchAndCacheLyric(id) ?: return LyricCacheStatus.Failed
        return if (network.hasAnyLyricText()) LyricCacheStatus.Saved else LyricCacheStatus.NoLyric
    }

    private fun cachedLyric(id: Long): Lyric? = runCatching {
        db.cachedLyricQueries.selectBySongId(id).executeAsOneOrNull()
            ?.let { json.decodeFromString<Lyric>(it) }
    }.getOrNull()

    private suspend fun fetchAndCacheLyric(id: Long): Lyric? {
        // /lyric/new 返回 yrc (逐字歌词); /lyric 不返回 yrc。优先使用 /lyric/new
        val networkNew = safeApiCall { api.lyricNew(id) }
        if (networkNew != null && networkNew.hasAnyLyricText()) {
            runCatching { db.cachedLyricQueries.upsert(id, json.encodeToString(networkNew)) }
            return networkNew
        }
        val fallback = safeApiCall { api.lyric(id) } ?: return null
        runCatching { db.cachedLyricQueries.upsert(id, json.encodeToString(fallback)) }
        return fallback
    }

    private fun Lyric.hasAnyLyricText(): Boolean =
        lrc?.lyric?.isNotBlank() == true ||
            yrc?.lyric?.isNotBlank() == true ||
            tlyric?.lyric?.isNotBlank() == true ||
            romalrc?.lyric?.isNotBlank() == true

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

    suspend fun getSongDetails(songIds: List<Long>): List<SongDetailSongs> {
        if (songIds.isEmpty()) return emptyList()
        return songIds.chunked(200)
            .flatMap { chunk ->
                safeApiCall { api.songDetail(chunk).songs } ?: emptyList()
            }
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

    // ── Player status（播放状态持久化，对应原版 Room playerStatus 表）──

    suspend fun getPlayerStatus(): PlayerStatus? = runCatching {
        db.playerStatusQueries.select().executeAsOneOrNull()
            ?.let { json.decodeFromString<PlayerStatus>(it) }
    }.getOrNull()

    suspend fun savePlayerStatus(status: PlayerStatus) {
        runCatching { db.playerStatusQueries.upsert(json.encodeToString(status)) }
    }
}

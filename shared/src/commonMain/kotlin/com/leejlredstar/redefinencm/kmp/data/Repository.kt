package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import com.leejlredstar.redefinencm.kmp.data.api.safeApiCall
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class LyricCacheStatus {
    Saved,
    NoLyric,
    Failed,
}

enum class CacheThenNetworkSource {
    CACHE,
    NETWORK,
}

data class CacheThenNetworkData<out T>(
    val value: T,
    val source: CacheThenNetworkSource,
) {
    val isFromCache: Boolean
        get() = source == CacheThenNetworkSource.CACHE
}

internal fun <T> cacheThenNetworkFlow(
    readCache: () -> T?,
    fetchNetwork: suspend () -> T?,
    writeCache: (T) -> Unit,
): Flow<CacheThenNetworkData<T>> = flow {
    readCache()?.let { cached ->
        emit(CacheThenNetworkData(cached, CacheThenNetworkSource.CACHE))
    }
    fetchNetwork()?.let { network ->
        writeCache(network)
        emit(CacheThenNetworkData(network, CacheThenNetworkSource.NETWORK))
    }
}

class Repository(
    private val api: NCMApi,
    private val db: AppDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private companion object {
        const val API_SUCCESS_CODE = 200
    }

    // ── User ──

    /** 网络获取当前登录账号（用于首次解析 UID，原版 fetchUID 的 userAccount 调用）。 */
    suspend fun getUserAccount(): UserAccount? =
        safeApiCall { api.userAccount() }?.takeIf { it.code == API_SUCCESS_CODE }

    fun getUserDetail(uid: Long): Flow<CacheThenNetworkData<UserDetail>> = cacheThenNetworkFlow(
        readCache = {
            runCatching {
                db.cachedUserDetailQueries.selectByUid(uid).executeAsOneOrNull()
                    ?.let { json.decodeFromString<UserDetail>(it) }
            }.getOrNull()?.takeIf { it.code == API_SUCCESS_CODE }
        },
        fetchNetwork = {
            safeApiCall { api.userDetail(uid) }
                ?.takeIf { it.code == API_SUCCESS_CODE }
        },
        writeCache = { network ->
            db.cachedUserDetailQueries.upsert(uid, json.encodeToString(network))
        },
    )

    fun getUserLevel(uid: Long): Flow<CacheThenNetworkData<UserLevelResponse>> {
        require(uid > 0) { "uid must be positive" }
        return cacheThenNetworkFlow(
            readCache = {
                runCatching {
                    db.cachedUserLevelQueries.selectByUid(uid).executeAsOneOrNull()
                        ?.let { json.decodeFromString<UserLevelResponse>(it) }
                }.getOrNull()?.takeIf { response ->
                    response.code == API_SUCCESS_CODE && response.data?.userId == uid
                }
            },
            fetchNetwork = {
                safeApiCall { api.userLevel() }
                    ?.takeIf { response ->
                        response.code == API_SUCCESS_CODE && response.data?.userId == uid
                    }
            },
            writeCache = { network ->
                db.cachedUserLevelQueries.upsert(uid, json.encodeToString(network))
            },
        )
    }

    fun getUserPlaylist(uid: Long): Flow<CacheThenNetworkData<UserPlaylist>> = cacheThenNetworkFlow(
        readCache = {
            runCatching {
                db.cachedUserPlaylistQueries.selectByUid(uid).executeAsOneOrNull()
                    ?.let { json.decodeFromString<UserPlaylist>(it) }
            }.getOrNull()?.takeIf { it.code == API_SUCCESS_CODE }
        },
        fetchNetwork = {
            safeApiCall { api.userPlaylist(uid) }
                ?.takeIf { it.code == API_SUCCESS_CODE }
        },
        writeCache = { network ->
            db.cachedUserPlaylistQueries.upsert(uid, json.encodeToString(network))
        },
    )

    // ── Playlist ──

    fun getPlaylistDetail(id: Long): Flow<CacheThenNetworkData<PlaylistDetail>> = cacheThenNetworkFlow(
        readCache = {
            runCatching {
                db.cachedPlaylistDetailQueries.selectById(id).executeAsOneOrNull()
                    ?.let { json.decodeFromString<PlaylistDetail>(it) }
            }.getOrNull()?.takeIf { it.code == API_SUCCESS_CODE }
        },
        fetchNetwork = {
            safeApiCall { api.playlistDetail(id) }
                ?.takeIf { it.code == API_SUCCESS_CODE }
        },
        writeCache = { network ->
            db.cachedPlaylistDetailQueries.upsert(id, json.encodeToString(network))
        },
    )

    fun getPlaylistTrackAll(id: Long): Flow<CacheThenNetworkData<PlaylistTrackAll>> = cacheThenNetworkFlow(
        readCache = {
            runCatching {
                db.cachedPlaylistTrackAllQueries.selectById(id).executeAsOneOrNull()
                    ?.let { json.decodeFromString<PlaylistTrackAll>(it) }
            }.getOrNull()?.takeIf { it.code == API_SUCCESS_CODE }
        },
        fetchNetwork = {
            safeApiCall { api.playlistTrackAll(id) }
                ?.takeIf { it.code == API_SUCCESS_CODE }
        },
        writeCache = { network ->
            db.cachedPlaylistTrackAllQueries.upsert(id, json.encodeToString(network))
        },
    )

    /** 一次性网络获取歌单全部曲目（批量下载用，不走缓存流）。 */
    suspend fun getPlaylistTrackAllOnce(id: Long): PlaylistTrackAll? =
        safeApiCall { api.playlistTrackAll(id) }?.takeIf { it.code == API_SUCCESS_CODE }

    /** 上报歌单播放次数（原版播放歌单时调用）。 */
    suspend fun updatePlaylistPlaycount(id: Long): Boolean =
        safeApiCall { api.playlistUpdatePlaycount(id) }?.code == API_SUCCESS_CODE

    // ── Recommend ──

    fun getRecommendSongs(
        uid: Long,
        readCache: Boolean = true,
    ): Flow<CacheThenNetworkData<RecommendSongs>> = cacheThenNetworkFlow(
        readCache = {
            if (!readCache) {
                null
            } else {
                runCatching {
                    db.cachedRecommendSongsQueries.select().executeAsOneOrNull()
                        ?.let { json.decodeFromString<CachedRecommendSongsEntry>(it) }
                }.getOrNull()
                    ?.takeIf { it.uid == uid }
                    ?.value
                    ?.takeIf { it.code == API_SUCCESS_CODE }
            }
        },
        fetchNetwork = {
            safeApiCall { api.recommendSongs() }
                ?.takeIf { it.code == API_SUCCESS_CODE }
        },
        writeCache = { network ->
            db.cachedRecommendSongsQueries.upsert(
                json.encodeToString(CachedRecommendSongsEntry(uid, network)),
            )
        },
    )

    fun getRecommendResource(
        uid: Long,
        readCache: Boolean = true,
    ): Flow<CacheThenNetworkData<RecommendResource>> = cacheThenNetworkFlow(
        readCache = {
            if (!readCache) {
                null
            } else {
                runCatching {
                    db.cachedRecommendResourceQueries.select().executeAsOneOrNull()
                        ?.let { json.decodeFromString<CachedRecommendResourceEntry>(it) }
                }.getOrNull()
                    ?.takeIf { it.uid == uid }
                    ?.value
                    ?.takeIf { it.code == API_SUCCESS_CODE }
            }
        },
        fetchNetwork = {
            safeApiCall { api.recommendResource() }
                ?.takeIf { it.code == API_SUCCESS_CODE }
        },
        writeCache = { network ->
            db.cachedRecommendResourceQueries.upsert(
                json.encodeToString(CachedRecommendResourceEntry(uid, network)),
            )
        },
    )

    /** Account-scoped singleton caches must not survive a cookie/account switch. */
    fun clearAccountScopedCaches(): Result<Unit> = runCatching {
        db.transaction {
            db.cachedRecommendSongsQueries.deleteAll()
            db.cachedRecommendResourceQueries.deleteAll()
        }
    }

    // ── Comment ──

    fun getCommentMusic(id: Long): Flow<CacheThenNetworkData<CommentMusic>> = cacheThenNetworkFlow(
        readCache = {
            runCatching {
                db.cachedCommentMusicQueries.selectBySongId(id).executeAsOneOrNull()
                    ?.let { json.decodeFromString<CommentMusic>(it) }
            }.getOrNull()?.takeIf { it.code == API_SUCCESS_CODE }
        },
        fetchNetwork = {
            safeApiCall { api.commentMusic(id) }
                ?.takeIf { it.code == API_SUCCESS_CODE }
        },
        writeCache = { network ->
            db.cachedCommentMusicQueries.upsert(id, json.encodeToString(network))
        },
    )

    // ── Lyric ──

    fun getLyric(id: Long): Flow<Lyric?> = flow {
        cachedLyric(id)?.let { emit(it) }
        fetchAndCacheLyric(id)?.let { emit(it) }
    }

    suspend fun cacheLyric(id: Long): LyricCacheStatus {
        return try {
            cachedLyric(id)?.takeIf { it.hasAnyLyricText() }?.let { return LyricCacheStatus.Saved }
            val network = fetchAndCacheLyric(id) ?: return LyricCacheStatus.Failed
            if (network.hasAnyLyricText()) LyricCacheStatus.Saved else LyricCacheStatus.NoLyric
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Callers receive Failed instead of a false Saved result when SQLDelight rejects the write.
            LyricCacheStatus.Failed
        }
    }

    private fun cachedLyric(id: Long): Lyric? = runCatching {
        db.cachedLyricQueries.selectBySongId(id).executeAsOneOrNull()
            ?.let { json.decodeFromString<Lyric>(it) }
            ?.takeIf { it.code == API_SUCCESS_CODE }
    }.getOrNull()

    private suspend fun fetchAndCacheLyric(id: Long): Lyric? {
        // /lyric/new 返回 yrc (逐字歌词); /lyric 不返回 yrc。优先使用 /lyric/new
        val networkNew = safeApiCall { api.lyricNew(id) }
            ?.takeIf { it.code == API_SUCCESS_CODE }
        if (networkNew != null && networkNew.hasAnyLyricText()) {
            db.cachedLyricQueries.upsert(id, json.encodeToString(networkNew))
            return networkNew
        }
        val fallback = safeApiCall { api.lyric(id) }
            ?.takeIf { it.code == API_SUCCESS_CODE }
            ?: return null
        db.cachedLyricQueries.upsert(id, json.encodeToString(fallback))
        return fallback
    }

    private fun Lyric.hasAnyLyricText(): Boolean =
        lrc?.lyric?.isNotBlank() == true ||
            yrc?.lyric?.isNotBlank() == true ||
            tlyric?.lyric?.isNotBlank() == true ||
            romalrc?.lyric?.isNotBlank() == true

    // ── Song URL ──

    suspend fun getSongUrl(songId: Long, quality: String): String? {
        return safeApiCall { api.songUrlV1(listOf(songId), quality.lowercase()) }
            ?.takeIf { it.code == API_SUCCESS_CODE }
            ?.data
            ?.firstOrNull()
            ?.url
            ?.takeIf(String::isNotBlank)
    }

    suspend fun getSongUrls(songIds: List<Long>, quality: String): List<SongUrlV1Data> {
        return safeApiCall { api.songUrlV1(songIds, quality.lowercase()) }
            ?.takeIf { it.code == API_SUCCESS_CODE }
            ?.data
            ?: emptyList()
    }

    suspend fun getSongDetails(songIds: List<Long>): List<SongDetailSongs> {
        if (songIds.isEmpty()) return emptyList()
        return songIds.chunked(200)
            .flatMap { chunk ->
                safeApiCall { api.songDetail(chunk) }
                    ?.takeIf { it.code == API_SUCCESS_CODE }
                    ?.songs
                    ?: emptyList()
            }
    }

    suspend fun audioMatch(durationSeconds: Int, audioFingerprint: String): AudioMatch? =
        safeApiCall { api.audioMatch(durationSeconds, audioFingerprint) }
            ?.takeIf { it.code == API_SUCCESS_CODE }

    // ── Playback reporting ──

    suspend fun scrobbleV1(
        id: Long,
        timeSeconds: Long,
        sourceId: String? = null,
        source: String? = null,
        name: String? = null,
        artist: String? = null,
        bitrate: Int? = null,
        level: String? = null,
        totalSeconds: Long? = null,
        credentialCookie: String? = null,
    ): Boolean = safeApiCall {
        api.scrobbleV1(
            id = id,
            timeSeconds = timeSeconds,
            sourceId = sourceId,
            source = source,
            name = name,
            artist = artist,
            bitrate = bitrate,
            level = level,
            totalSeconds = totalSeconds,
            credentialCookie = credentialCookie,
        )
    }?.code == API_SUCCESS_CODE

    suspend fun submitPlayState(
        id: Long,
        sessionId: String,
        progressSeconds: Long,
        playMode: String,
        type: String = "song",
        credentialCookie: String? = null,
    ): Boolean = safeApiCall {
        api.submitPlayState(
            id = id,
            sessionId = sessionId,
            progressSeconds = progressSeconds,
            playMode = playMode,
            type = type,
            credentialCookie = credentialCookie,
        )
    }?.code == API_SUCCESS_CODE

    // ── Search ──

    suspend fun search(keyword: String): SearchResult? {
        return safeApiCall { api.search(keyword) }?.takeIf { it.code == API_SUCCESS_CODE }
    }

    suspend fun searchSuggest(keyword: String): SearchSuggest? {
        return safeApiCall { api.searchSuggest(keyword) }?.takeIf { it.code == API_SUCCESS_CODE }
    }

    // ── Like ──

    suspend fun like(songId: Long?): Like? {
        return safeApiCall { api.like(songId) }?.takeIf { it.code == API_SUCCESS_CODE }
    }

    // ── Player status（播放状态持久化，对应原版 Room playerStatus 表）──

    suspend fun getPlayerStatus(): Result<PlayerStatus?> = runCatching {
        db.playerStatusQueries.select().executeAsOneOrNull()
            ?.let { json.decodeFromString<PlayerStatus>(it) }
    }

    suspend fun savePlayerStatus(status: PlayerStatus): Result<Unit> = runCatching {
        db.playerStatusQueries.upsert(json.encodeToString(status))
    }
}

@Serializable
private data class CachedRecommendSongsEntry(
    val uid: Long,
    val value: RecommendSongs,
)

@Serializable
private data class CachedRecommendResourceEntry(
    val uid: Long,
    val value: RecommendResource,
)

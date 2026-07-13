package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.NcmHttpResponse
import com.leejlredstar.redefinencm.kmp.data.api.NcmResponseBodyKind
import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import com.leejlredstar.redefinencm.kmp.data.api.safeApiCall
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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
    private val relayCapability = MutableStateFlow(EndpointCapability.UNKNOWN)
    private val relayCapabilityMutex = Mutex()
    private val userLevelRequestMutex = Mutex()
    private var nextUserLevelRequestGeneration = 0L
    private val latestCommittedUserLevelRequestByUid = mutableMapOf<Long, Long>()

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
        return flow {
            userLevelRequestMutex.lock()
            try {
                readCachedUserLevel(uid)?.let { cached ->
                    // Keep the cache read and emission ordered before a concurrent newer refresh.
                    emit(CacheThenNetworkData(cached, CacheThenNetworkSource.CACHE))
                }
            } finally {
                userLevelRequestMutex.unlock()
            }
            val requestGeneration = beginUserLevelRequest()
            val network = fetchUserLevel(uid) ?: return@flow
            if (writeUserLevelIfCurrent(uid, requestGeneration, network)) {
                emit(CacheThenNetworkData(network, CacheThenNetworkSource.NETWORK))
            } else {
                userLevelRequestMutex.lock()
                try {
                    readCachedUserLevel(uid)?.let { committed ->
                        // A newer network refresh won the generation race. Surface that committed
                        // value instead of ending this account-level flow without any result.
                        emit(CacheThenNetworkData(committed, CacheThenNetworkSource.NETWORK))
                    }
                } finally {
                    userLevelRequestMutex.unlock()
                }
            }
        }
    }

    private fun readCachedUserLevel(uid: Long): UserLevelResponse? = runCatching {
        db.cachedUserLevelQueries.selectByUid(uid).executeAsOneOrNull()
            ?.let { json.decodeFromString<UserLevelResponse>(it) }
    }.getOrNull()?.takeIf { response ->
        response.code == API_SUCCESS_CODE && response.data?.userId == uid
    }

    private suspend fun fetchUserLevel(
        uid: Long,
        credentialCookie: String? = null,
    ): UserLevelResponse? = safeApiCall { api.userLevel(credentialCookie) }
        ?.takeIf { response ->
            response.code == API_SUCCESS_CODE && response.data?.userId == uid
        }

    suspend fun refreshUserLevel(
        uid: Long,
        credentialCookie: String? = null,
    ): UserLevelResponse? {
        require(uid > 0) { "uid must be positive" }
        val requestGeneration = beginUserLevelRequest()
        val network = fetchUserLevel(uid, credentialCookie) ?: return null
        return network.takeIf {
            writeUserLevelIfCurrent(uid, requestGeneration, network)
        }
    }

    private suspend fun beginUserLevelRequest(): Long = userLevelRequestMutex.withLock {
        ++nextUserLevelRequestGeneration
    }

    private suspend fun writeUserLevelIfCurrent(
        uid: Long,
        requestGeneration: Long,
        response: UserLevelResponse,
    ): Boolean = userLevelRequestMutex.withLock {
        val latestCommitted = latestCommittedUserLevelRequestByUid[uid] ?: Long.MIN_VALUE
        if (requestGeneration < latestCommitted) return@withLock false
        db.cachedUserLevelQueries.upsert(uid, json.encodeToString(response))
        latestCommittedUserLevelRequestByUid[uid] = requestGeneration
        true
    }

    suspend fun getUserRecord(
        uid: Long,
        type: Int = 1,
        credentialCookie: String? = null,
    ): UserRecordResponse? {
        require(uid > 0) { "uid must be positive" }
        require(type == 0 || type == 1) { "type must be 0 or 1" }
        return safeApiCall { api.userRecord(uid, type, credentialCookie) }
            ?.takeIf { it.code == API_SUCCESS_CODE }
    }

    suspend fun getRecentSongs(
        limit: Int = 100,
        credentialCookie: String? = null,
    ): RecentSongsResponse? {
        require(limit > 0) { "limit must be positive" }
        return safeApiCall { api.recentSongs(limit, credentialCookie) }
            ?.takeIf { it.code == API_SUCCESS_CODE }
    }

    suspend fun getPlaybackAccountSnapshot(
        uid: Long,
        recentLimit: Int = 100,
        credentialCookie: String? = null,
    ): PlaybackAccountSnapshot = coroutineScope {
        require(uid > 0) { "uid must be positive" }
        require(recentLimit > 0) { "recentLimit must be positive" }
        val level = async { refreshUserLevel(uid, credentialCookie) }
        val record = async { getUserRecord(uid, type = 1, credentialCookie) }
        val recent = async { getRecentSongs(recentLimit, credentialCookie) }
        PlaybackAccountSnapshot(
            uid = uid,
            userLevel = level.await(),
            weeklyRecord = record.await(),
            recentSongs = recent.await(),
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

    suspend fun getIntelligenceList(
        id: Long,
        pid: Long,
        sid: Long? = null,
    ): IntelligenceListResponse? {
        require(id > 0) { "id must be positive" }
        require(pid > 0) { "pid must be positive" }
        require(sid == null || sid > 0) { "sid must be positive when provided" }
        return safeApiCall { api.intelligenceList(id, pid, sid) }
            ?.takeIf { it.code == API_SUCCESS_CODE }
    }

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

    suspend fun submitPlaybackStart(
        id: Long,
        sourceId: String? = null,
        credentialCookie: String? = null,
    ): PlaybackReportResult {
        if (id <= 0L) {
            return PlaybackReportResult.Rejected(
                endpoint = PlaybackReportEndpoint.WEBLOG_STARTPLAY,
                details = PlaybackReportDetails(message = "songId must be positive"),
            )
        }
        val cleanedCredential = HttpClientFactory.cleanCookie(credentialCookie.orEmpty())
        if (cleanedCredential.isEmpty()) {
            return PlaybackReportResult.Rejected(
                endpoint = PlaybackReportEndpoint.WEBLOG_STARTPLAY,
                details = PlaybackReportDetails(message = "credential cookie is required"),
            )
        }
        val resolvedSourceId = sourceId
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: id
        val response = safeApiCall {
            api.submitPlaybackStart(
                id = id,
                sourceId = resolvedSourceId,
                credentialCookie = weblogCredentialCookie(cleanedCredential),
            )
        } ?: return PlaybackReportResult.TransportFailure(
            endpoint = PlaybackReportEndpoint.WEBLOG_STARTPLAY,
            reason = PlaybackReportFailureReason.REQUEST_FAILED,
        )
        return response.toWeblogStartResult()
    }

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
    ): PlaybackReportResult {
        val response = safeApiCall {
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
        } ?: return PlaybackReportResult.TransportFailure(
            endpoint = PlaybackReportEndpoint.SCROBBLE_V1,
            reason = PlaybackReportFailureReason.REQUEST_FAILED,
        )
        return response.toScrobbleResult()
    }

    suspend fun submitPlayState(
        id: Long,
        sessionId: String,
        progressSeconds: Long,
        playMode: String,
        type: String = "song",
        credentialCookie: String? = null,
    ): PlaybackReportResult {
        if (relayCapability.value == EndpointCapability.UNSUPPORTED) {
            return PlaybackReportResult.Unsupported(PlaybackReportEndpoint.RELAY)
        }
        val requestRelay: suspend () -> PlaybackReportResult = requestRelay@{
            val response = safeApiCall {
                api.submitPlayState(
                    id = id,
                    sessionId = sessionId,
                    progressSeconds = progressSeconds,
                    playMode = playMode,
                    type = type,
                    credentialCookie = credentialCookie,
                )
            } ?: return@requestRelay PlaybackReportResult.TransportFailure(
                endpoint = PlaybackReportEndpoint.RELAY,
                reason = PlaybackReportFailureReason.REQUEST_FAILED,
            )
            if (response.isHtmlNotFound) {
                relayCapability.value = EndpointCapability.UNSUPPORTED
                PlaybackReportResult.Unsupported(
                    endpoint = PlaybackReportEndpoint.RELAY,
                    htmlResponse = response.bodyKind == NcmResponseBodyKind.HTML,
                )
            } else {
                relayCapability.value = EndpointCapability.AVAILABLE
                response.toRelayResult()
            }
        }
        return if (relayCapability.value == EndpointCapability.UNKNOWN) {
            relayCapabilityMutex.withLock {
                if (relayCapability.value == EndpointCapability.UNSUPPORTED) {
                    PlaybackReportResult.Unsupported(PlaybackReportEndpoint.RELAY)
                } else {
                    requestRelay()
                }
            }
        } else {
            requestRelay()
        }
    }

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

    suspend fun getLikedSongIds(uid: Long): List<Long> {
        require(uid > 0) { "uid must be positive" }
        return safeApiCall { api.likelist(uid) }
            ?.takeIf { it.code == API_SUCCESS_CODE }
            ?.ids
            ?.asSequence()
            ?.filter { it > 0 }
            ?.distinct()
            ?.toList()
            .orEmpty()
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

private enum class EndpointCapability {
    UNKNOWN,
    AVAILABLE,
    UNSUPPORTED,
}

private const val MAX_REPORT_TEXT_LENGTH = 1_024
private const val MAX_REPORT_DETAILS_LENGTH = 4_096

internal fun weblogCredentialCookie(rawCredential: String): String {
    val cleaned = HttpClientFactory.cleanCookie(rawCredential)
    require(cleaned.isNotEmpty()) { "credential cookie must not be empty" }
    var osWritten = false
    val parts = cleaned.split(';').mapNotNull { rawPart ->
        val part = rawPart.trim()
        val name = part.substringBefore('=', missingDelimiterValue = "").trim()
        if (!name.equals("os", ignoreCase = true)) {
            part.takeIf(String::isNotEmpty)
        } else if (!osWritten) {
            osWritten = true
            "os=osx"
        } else {
            null
        }
    }.toMutableList()
    if (!osWritten) parts += "os=osx"
    return parts.joinToString("; ")
}

private fun NcmHttpResponse<WeblogResponse>.toWeblogStartResult(): PlaybackReportResult {
    if (isHtmlNotFound) {
        return PlaybackReportResult.Unsupported(
            endpoint = PlaybackReportEndpoint.WEBLOG_STARTPLAY,
            htmlResponse = bodyKind == NcmResponseBodyKind.HTML,
        )
    }
    val response = body
    if (response == null) return invalidResponseResult(PlaybackReportEndpoint.WEBLOG_STARTPLAY)
    val details = reportDetails(
        data = response.data,
        message = response.msg ?: response.message,
        details = response.details,
    )
    return if (statusCode in 200..299 && response.code == 200) {
        PlaybackReportResult.Accepted(
            endpoint = PlaybackReportEndpoint.WEBLOG_STARTPLAY,
            httpStatus = statusCode,
            serverCode = response.code,
            details = details,
        )
    } else {
        PlaybackReportResult.Rejected(
            endpoint = PlaybackReportEndpoint.WEBLOG_STARTPLAY,
            httpStatus = statusCode,
            serverCode = response.code,
            details = details,
        )
    }
}

private fun NcmHttpResponse<ScrobbleV1Response>.toScrobbleResult(): PlaybackReportResult {
    if (isHtmlNotFound) {
        return PlaybackReportResult.Unsupported(
            endpoint = PlaybackReportEndpoint.SCROBBLE_V1,
            htmlResponse = bodyKind == NcmResponseBodyKind.HTML,
        )
    }
    val response = body
    if (response == null) return invalidResponseResult(PlaybackReportEndpoint.SCROBBLE_V1)
    val details = reportDetails(response.data, response.msg, response.details)
    return if (statusCode in 200..299 && response.code == 200) {
        PlaybackReportResult.Accepted(
            endpoint = PlaybackReportEndpoint.SCROBBLE_V1,
            httpStatus = statusCode,
            serverCode = response.code,
            details = details,
        )
    } else {
        PlaybackReportResult.Rejected(
            endpoint = PlaybackReportEndpoint.SCROBBLE_V1,
            httpStatus = statusCode,
            serverCode = response.code,
            details = details,
        )
    }
}

private fun NcmHttpResponse<PlayStateSubmitResponse>.toRelayResult(): PlaybackReportResult {
    if (isHtmlNotFound) {
        return PlaybackReportResult.Unsupported(
            endpoint = PlaybackReportEndpoint.RELAY,
            htmlResponse = bodyKind == NcmResponseBodyKind.HTML,
        )
    }
    val response = body
    if (response == null) return invalidResponseResult(PlaybackReportEndpoint.RELAY)
    val details = reportDetails(response.data, response.msg ?: response.message, null)
    return if (statusCode in 200..299 && response.code == 200) {
        PlaybackReportResult.Accepted(
            endpoint = PlaybackReportEndpoint.RELAY,
            httpStatus = statusCode,
            serverCode = response.code,
            details = details,
        )
    } else {
        PlaybackReportResult.Rejected(
            endpoint = PlaybackReportEndpoint.RELAY,
            httpStatus = statusCode,
            serverCode = response.code,
            details = details,
        )
    }
}

private fun NcmHttpResponse<*>.invalidResponseResult(
    endpoint: PlaybackReportEndpoint,
): PlaybackReportResult = if (statusCode in 200..299) {
    PlaybackReportResult.TransportFailure(
        endpoint = endpoint,
        httpStatus = statusCode,
        reason = PlaybackReportFailureReason.INVALID_RESPONSE,
    )
} else {
    PlaybackReportResult.Rejected(
        endpoint = endpoint,
        httpStatus = statusCode,
    )
}

private fun reportDetails(
    data: Any?,
    message: String?,
    details: JsonElement?,
): PlaybackReportDetails = PlaybackReportDetails(
    data = data?.toString()?.take(MAX_REPORT_TEXT_LENGTH),
    message = message?.trim()?.take(MAX_REPORT_TEXT_LENGTH),
    serverDetails = details?.toString()?.take(MAX_REPORT_DETAILS_LENGTH),
)

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

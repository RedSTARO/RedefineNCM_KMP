package com.leejlredstar.redefinencm.kmp.data.api

import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val playSessionIdPattern = Regex("^[A-Z0-9]{12}$")
private val responseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

enum class NcmResponseBodyKind {
    JSON,
    HTML,
    EMPTY,
    MALFORMED_JSON,
    OTHER,
}

data class NcmHttpResponse<out T>(
    val statusCode: Int,
    val body: T?,
    val bodyKind: NcmResponseBodyKind,
    val contentType: String?,
) {
    val isHtmlNotFound: Boolean
        get() = statusCode == HttpStatusCode.NotFound.value && bodyKind == NcmResponseBodyKind.HTML
}

private suspend inline fun <reified T> HttpResponse.toNcmHttpResponse(): NcmHttpResponse<T> {
    val responseText = bodyAsText()
    val trimmed = responseText.trimStart()
    val responseContentType = contentType()?.toString()
    val looksLikeHtml = responseContentType?.startsWith("text/html", ignoreCase = true) == true ||
        trimmed.startsWith("<!doctype html", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true)
    if (responseText.isBlank()) {
        return NcmHttpResponse(status.value, null, NcmResponseBodyKind.EMPTY, responseContentType)
    }
    if (looksLikeHtml) {
        return NcmHttpResponse(status.value, null, NcmResponseBodyKind.HTML, responseContentType)
    }

    val decoded = runCatching { responseJson.decodeFromString<T>(responseText) }.getOrNull()
    val kind = when {
        decoded != null -> NcmResponseBodyKind.JSON
        responseContentType?.startsWith("application/json", ignoreCase = true) == true ||
            trimmed.startsWith("{") || trimmed.startsWith("[") -> NcmResponseBodyKind.MALFORMED_JSON
        else -> NcmResponseBodyKind.OTHER
    }
    return NcmHttpResponse(status.value, decoded, kind, responseContentType)
}

@Serializable
private data class WeblogRequest(val data: WeblogRequestData)

@Serializable
private data class WeblogRequestData(val logs: String)

internal fun startPlaybackWeblogLogs(songId: Long, sourceId: Long): String {
    require(songId > 0) { "songId must be positive" }
    require(sourceId > 0) { "sourceId must be positive" }
    return buildJsonArray {
        add(
            buildJsonObject {
                put("action", "startplay")
                put(
                    "json",
                    buildJsonObject {
                        put("id", songId)
                        put("type", "song")
                        put("mainsite", "1")
                        put("mainsiteWeb", "1")
                        put("content", "id=$sourceId")
                    },
                )
            },
        )
    }.toString()
}

internal fun intelligenceListQueryParameters(
    id: Long,
    pid: Long,
    sid: Long?,
): List<Pair<String, String>> {
    require(id > 0) { "id must be positive" }
    require(pid > 0) { "pid must be positive" }
    require(sid == null || sid > 0) { "sid must be positive when provided" }

    return buildList {
        add("id" to id.toString())
        add("pid" to pid.toString())
        sid?.let { add("sid" to it.toString()) }
    }
}

internal fun scrobbleV1QueryParameters(
    id: Long,
    timeSeconds: Long,
    sourceId: String?,
    source: String?,
    name: String?,
    artist: String?,
    bitrate: Int?,
    level: String?,
    totalSeconds: Long?,
): List<Pair<String, String>> {
    require(id > 0) { "id must be positive" }
    require(timeSeconds > 0) { "timeSeconds must be positive" }

    return buildList {
        add("id" to id.toString())
        add("time" to timeSeconds.toString())
        sourceId?.takeIf(String::isNotBlank)?.let { add("sourceid" to it) }
        source?.takeIf(String::isNotBlank)?.let { add("source" to it) }
        name?.takeIf(String::isNotBlank)?.let { add("name" to it) }
        artist?.takeIf(String::isNotBlank)?.let { add("artist" to it) }
        bitrate?.takeIf { it > 0 }?.let { add("bitrate" to it.toString()) }
        level?.takeIf(String::isNotBlank)?.let { add("level" to it) }
        totalSeconds?.takeIf { it > 0 }?.let { add("total" to it.toString()) }
    }
}

internal fun submitPlayStateQueryParameters(
    id: Long,
    sessionId: String,
    progressSeconds: Long,
    playMode: String,
    type: String,
): List<Pair<String, String>> {
    require(id > 0) { "id must be positive" }
    require(playSessionIdPattern.matches(sessionId)) {
        "sessionId must contain exactly 12 uppercase letters or digits"
    }
    require(progressSeconds >= 0) { "progressSeconds must not be negative" }
    require(playMode.isNotBlank()) { "playMode must not be blank" }
    require(type.isNotBlank()) { "type must not be blank" }

    return listOf(
        "id" to id.toString(),
        "sessionId" to sessionId,
        "progress" to progressSeconds.toString(),
        "playMode" to playMode,
        "type" to type,
    )
}

private fun HttpRequestBuilder.appendQueryParameters(parameters: List<Pair<String, String>>) {
    parameters.forEach { (name, value) -> parameter(name, value) }
}

private fun HttpRequestBuilder.appendCredentialCookie(credentialCookie: String?) {
    credentialCookie?.takeIf(String::isNotBlank)?.let {
        attributes.put(NcmCredentialCookieAttribute, it)
    }
}

/**
 * NeteaseCloudMusicApi service interface implemented via Ktor.
 * Ported from the original Retrofit NCMApi with the same endpoint structure.
 */
class NCMApi(private val client: HttpClient) {

    // ── Account ──

    suspend fun userAccount(): UserAccount =
        client.get("/user/account").body()

    suspend fun userDetail(uid: Long): UserDetail =
        client.get("/user/detail") { parameter("uid", uid) }.body()

    suspend fun userLevel(credentialCookie: String? = null): UserLevelResponse =
        client.get("/user/level") { appendCredentialCookie(credentialCookie) }.body()

    suspend fun userRecord(
        uid: Long,
        type: Int = 1,
        credentialCookie: String? = null,
    ): UserRecordResponse = client.get("/user/record") {
        require(uid > 0) { "uid must be positive" }
        require(type == 0 || type == 1) { "type must be 0 or 1" }
        parameter("uid", uid)
        parameter("type", type)
        appendCredentialCookie(credentialCookie)
    }.body()

    suspend fun recentSongs(
        limit: Int = 100,
        credentialCookie: String? = null,
    ): RecentSongsResponse = client.get("/record/recent/song") {
        require(limit > 0) { "limit must be positive" }
        parameter("limit", limit)
        appendCredentialCookie(credentialCookie)
    }.body()

    // ── Login ──

    suspend fun loginStatus(cookie: String): LoginStatus =
        client.post("/login/status") {
            parameter("cookie", cookie)
        }.body()

    suspend fun loginQrKey(): LoginQrKey =
        client.post("/login/qr/key").body()

    suspend fun loginQrCreate(key: String, qrimg: Boolean): LoginQrCreate =
        client.post("/login/qr/create") {
            parameter("key", key)
            parameter("qrimg", qrimg)
        }.body()

    suspend fun loginQrCheck(key: String): LoginQrCheck =
        client.post("/login/qr/check") {
            parameter("key", key)
        }.body()

    suspend fun dailysignin(type: Int): Dailysignin =
        client.get("/daily_signin") { parameter("type", type) }.body()

    // ── Playlist ──

    suspend fun userPlaylist(uid: Long): UserPlaylist =
        client.get("/user/playlist") { parameter("uid", uid) }.body()

    suspend fun playlistTrackAll(id: Long): PlaylistTrackAll =
        client.get("/playlist/track/all") { parameter("id", id) }.body()

    suspend fun playlistDetail(id: Long): PlaylistDetail =
        client.get("/playlist/detail") { parameter("id", id) }.body()

    suspend fun playlistUpdatePlaycount(id: Long): PlaylistUpdatePlayCount =
        client.get("/playlist/update/playcount") { parameter("id", id) }.body()

    suspend fun intelligenceList(
        id: Long,
        pid: Long,
        sid: Long? = null,
    ): IntelligenceListResponse = client.get("/playmode/intelligence/list") {
        appendQueryParameters(intelligenceListQueryParameters(id, pid, sid))
    }.body()

    // ── Song ──

    suspend fun songUrlV1(id: List<Long>, level: String): SongUrlV1 =
        client.get("/song/url/v1") {
            parameter("id", id.joinToString(","))
            parameter("level", level)
        }.body()

    suspend fun songDetail(ids: List<Long>): SongDetail =
        client.get("/song/detail") {
            parameter("ids", ids.joinToString(","))
        }.body()

    suspend fun audioMatch(durationSeconds: Int, audioFingerprint: String): AudioMatch =
        client.post("/audio/match") {
            parameter("duration", durationSeconds)
            parameter("audioFP", audioFingerprint)
        }.body()

    // ── Playback reporting ──

    suspend fun submitPlaybackStart(
        id: Long,
        sourceId: Long,
        credentialCookie: String,
    ): NcmHttpResponse<WeblogResponse> = client.post("/weblog") {
        appendCredentialCookie(credentialCookie)
        contentType(ContentType.Application.Json)
        setBody(WeblogRequest(WeblogRequestData(startPlaybackWeblogLogs(id, sourceId))))
    }.toNcmHttpResponse()

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
    ): NcmHttpResponse<ScrobbleV1Response> = client.get("/scrobble/v1") {
        appendQueryParameters(
            scrobbleV1QueryParameters(
                id = id,
                timeSeconds = timeSeconds,
                sourceId = sourceId,
                source = source,
                name = name,
                artist = artist,
                bitrate = bitrate,
                level = level,
                totalSeconds = totalSeconds,
            ),
        )
        appendCredentialCookie(credentialCookie)
    }.toNcmHttpResponse()

    suspend fun submitPlayState(
        id: Long,
        sessionId: String,
        progressSeconds: Long,
        playMode: String,
        type: String = "song",
        credentialCookie: String? = null,
    ): NcmHttpResponse<PlayStateSubmitResponse> = client.get("/relay/play/state/submit") {
        appendQueryParameters(
            submitPlayStateQueryParameters(
                id = id,
                sessionId = sessionId,
                progressSeconds = progressSeconds,
                playMode = playMode,
                type = type,
            ),
        )
        appendCredentialCookie(credentialCookie)
    }.toNcmHttpResponse()

    // ── Search ──

    suspend fun search(keywords: String, limit: Int = 30): SearchResult =
        client.get("/cloudsearch") {
            parameter("keywords", keywords)
            parameter("limit", limit)
        }.body()

    suspend fun searchSuggest(keywords: String, type: String = "mobile"): SearchSuggest =
        client.get("/search/suggest") {
            parameter("keywords", keywords)
            parameter("type", type)
        }.body()

    // ── Recommend ──

    suspend fun recommendSongs(): RecommendSongs =
        client.get("/recommend/songs").body()

    suspend fun recommendResource(): RecommendResource =
        client.get("/recommend/resource").body()

    // ── Lyric ──

    suspend fun lyric(id: Long): Lyric =
        client.get("/lyric") { parameter("id", id) }.body()

    suspend fun lyricNew(id: Long): Lyric =
        client.get("/lyric/new") { parameter("id", id) }.body()

    // ── Like ──

    suspend fun like(id: Long?): Like =
        client.post("/like") { id?.let { parameter("id", it) } }.body()

    suspend fun likelist(uid: Long): LikeList =
        client.post("/likelist") { parameter("uid", uid) }.body()

    // ── Comment ──

    suspend fun commentMusic(id: Long): CommentMusic =
        client.get("/comment/music") { parameter("id", id) }.body()

    // ── Version ──

    suspend fun innerVersion(url: String): InnerVersion =
        client.get(url).body()
}

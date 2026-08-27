package com.leejlredstar.redefinencm.kmp.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for a self-hosted `Rain120/qq-music-api` instance.
 *
 * QQ Music has no public API, so this talks to a backend the user runs, the same arrangement as
 * the NetEase side. The account cookie is held by the app and sent as a `Cookie` header on every
 * request, so one backend can serve several clients and so the Android build can sign in without
 * reaching the backend's own config file.
 *
 * `Rain120/qq-music-api` does not read that header yet: it takes its credential only from
 * `config/user-info`, and `/user/setCookie` answers 403 by design. The header is sent regardless
 * because it is the standard mechanism and costs nothing, but until the backend honours it, QQ
 * requests are effectively anonymous whatever the app has stored.
 *
 * Two shapes of that backend are load-bearing here. Its routes declare path parameters but every
 * controller reads `ctx.query`, so requests must pass query strings — `/getSearchByKey/周杰伦`
 * answers `400 search key is null`. And nearly every response is wrapped in a `response` envelope,
 * with `/getMusicPlay` the exception.
 */
class QQMusicApi(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
    private val cookie: suspend () -> String = { "" },
) {
    suspend fun search(keyword: String, limit: Int, page: Int = 1): QQSearchData? =
        request<QQEnvelope<QQSearchData>>("getSearchByKey") {
            parameter("key", keyword)
            parameter("limit", limit)
            parameter("page", page)
        }?.response

    suspend fun lyric(songMid: String): QQLyric? =
        request<QQEnvelope<QQLyric>>("getLyric") {
            parameter("songmid", songMid)
        }?.response?.takeIf { it.code == QQ_SUCCESS_CODE }

    suspend fun playlistDetail(dissId: String): QQPlaylistDetail? =
        request<QQEnvelope<QQPlaylistDetail>>("getSongListDetail") {
            parameter("disstid", dissId)
        }?.response

    /**
     * Resolves a stream URL at one quality tier.
     *
     * Returns null rather than throwing when the tier is unavailable: signed out, or without an
     * entitled account, the backend answers with an empty `purl` and an `暂无播放链接` error for
     * anything above `128`, and for VIP tracks at every tier.
     */
    suspend fun songUrl(songMid: String, quality: String): String? =
        request<QQPlayUrlResponse>("getMusicPlay") {
            parameter("songmid", songMid)
            parameter("quality", quality)
        }?.data?.playUrl?.get(songMid)?.url?.takeIf(String::isNotBlank)

    private suspend inline fun <reified T> request(
        path: String,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): T? = runCatching {
        val root = baseUrl().trimEnd('/')
        if (root.isEmpty()) return null
        val account = cookie().trim()
        val response: HttpResponse = client.get("$root/$path") {
            // Credentials travel per request rather than being installed into the backend, so one
            // backend can serve several clients and so the Android build can sign in at all.
            if (account.isNotEmpty()) header(HttpHeaders.Cookie, account)
            block()
        }
        // The backend answers 400 with a JSON body for bad input; decoding that as the success
        // shape would yield an empty result that reads like "no such song".
        if (response.status.value !in 200..299) return null
        qqJson.decodeFromString<T>(response.bodyAsText())
    }.getOrNull()

    private companion object {
        const val QQ_SUCCESS_CODE = 0
    }
}

private val qqJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** Almost every route wraps its payload in this envelope; `/getMusicPlay` does not. */
@Serializable
data class QQEnvelope<T>(val response: T? = null)

@Serializable
data class QQSearchData(val data: QQSearchPayload? = null)

@Serializable
data class QQSearchPayload(val song: QQSearchSongList? = null)

@Serializable
data class QQSearchSongList(val list: List<QQSearchSong> = emptyList())

@Serializable
data class QQSearchSong(
    val songmid: String = "",
    val songname: String = "",
    val albummid: String = "",
    val albumname: String = "",
    /** Seconds, unlike NetEase's milliseconds. */
    val interval: Long = 0,
    val singer: List<QQSinger> = emptyList(),
    // Non-zero byte sizes mark which tiers exist for this track at all. They say nothing about
    // whether the configured account may reach them.
    val size128: Long = 0,
    val size320: Long = 0,
    val sizeflac: Long = 0,
    val sizeape: Long = 0,
)

@Serializable
data class QQSinger(
    val mid: String = "",
    val name: String = "",
)

@Serializable
data class QQLyric(
    val code: Int = 0,
    val lyric: String = "",
    /** Translated lines, in the same LRC shape as [lyric]. Empty when none exist. */
    val trans: String = "",
)

@Serializable
data class QQPlaylistDetail(val cdlist: List<QQPlaylistEntry> = emptyList())

@Serializable
data class QQPlaylistEntry(
    val disstid: String = "",
    val dissname: String = "",
    val logo: String = "",
    val desc: String = "",
    val songnum: Int = 0,
    val songlist: List<QQPlaylistSong> = emptyList(),
)

/**
 * Playlist rows disagree with search rows: `mid`/`name` here against `songmid`/`songname` there,
 * with the album nested rather than flattened into `albummid`/`albumname`.
 */
@Serializable
data class QQPlaylistSong(
    val mid: String = "",
    val name: String = "",
    val interval: Long = 0,
    val singer: List<QQSinger> = emptyList(),
    val album: QQAlbum? = null,
)

@Serializable
data class QQAlbum(
    val mid: String = "",
    val name: String = "",
)

@Serializable
data class QQPlayUrlResponse(val data: QQPlayUrlData? = null)

@Serializable
data class QQPlayUrlData(
    @SerialName("playUrl") val playUrl: Map<String, QQPlayUrlEntry> = emptyMap(),
)

@Serializable
data class QQPlayUrlEntry(val url: String = "")

/**
 * QQ serves cover art off a predictable path keyed by album mid, so playlist and search rows do
 * not carry an artwork URL of their own.
 */
internal fun qqAlbumArtworkUrl(albumMid: String, size: Int = 300): String =
    if (albumMid.isBlank()) {
        ""
    } else {
        "https://y.gtimg.cn/music/photo_new/T002R${size}x${size}M000$albumMid.jpg"
    }

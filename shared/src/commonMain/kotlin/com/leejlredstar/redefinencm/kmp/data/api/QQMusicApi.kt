package com.leejlredstar.redefinencm.kmp.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client for a self-hosted `L-1124/QQMusicApi` web gateway (the project's `web/` FastAPI app).
 *
 * QQ Music has no public API, so this talks to a backend the user runs, the same arrangement as
 * the NetEase side. The account travels as a `Cookie` header on every request, in the names the
 * gateway reads (`musicid=…; musickey=…; …`, see `QQCredential`): a caller's cookie takes
 * precedence over any account installed in the gateway, so one gateway can serve several clients
 * and the Android build can sign in without reaching the gateway's config file.
 *
 * Every route answers `{code, msg, data}` with `code == 0` on success; a route that needs an
 * account answers `401 {"code": -1}` without one. Enumerated query parameters are integers:
 * `search_type=0` is songs, `file_type` is a row of the gateway's file-type table.
 */
class QQMusicApi(
    private val client: HttpClient,
    private val baseUrl: suspend () -> String,
    private val cookie: suspend () -> String = { "" },
    /**
     * Called when the gateway refuses (401) a request that carried an account, with the account it
     * carried. True means the request is sent once more with the account stored by then — see
     * `QQCredentialRenewer.renewAfterRejection`. The renewal's own requests never come back here.
     */
    private val onRejected: (suspend (rejectedCredential: String) -> Boolean)? = null,
) {
    /** One page of song hits; null when the gateway could not be reached or errored. */
    suspend fun search(keyword: String, num: Int, page: Int = 1): QQSearchData? =
        fetchData<QQSearchData>("search/search_by_type") {
            parameter("keyword", keyword)
            parameter("search_type", SearchTypeSong)
            parameter("num", num)
            parameter("page", page)
            parameter("highlight", false)
        }

    /** One page of a playlist; the gateway pages at `num` a call and flags `hasmore`. */
    suspend fun playlistDetail(id: Long, num: Int, page: Int = 1): QQSonglistDetail? =
        fetchData<QQSonglistDetail>("songlist/$id/detail") {
            parameter("num", num)
            parameter("page", page)
            parameter("tag", false)
            parameter("userinfo", true)
        }

    suspend fun lyric(mid: String): QQLyric? =
        fetchData<QQLyric>("song/$mid/lyric") {
            parameter("trans", true)
            parameter("roma", true)
        }

    /**
     * A playable URL at one file type, or null when QQ will not serve it.
     *
     * The gateway returns the CDN path (`purl`) and leaves the host to the caller. Of the hosts QQ
     * lists, only `dl.stream.qqmusic.qq.com` answers these paths with audio, so that one is fixed
     * here. An empty `purl` — the gateway reports it beside a non-zero `result` — means the track
     * is not available to the configured account at that tier.
     */
    suspend fun songUrl(mid: String, fileType: Int): String? = songUrlAnswer(mid, fileType)?.url

    /**
     * The gateway's answer at one file type: null when it could not be reached or errored, and an
     * answer without a [QQStreamAnswer.url] when QQ serves no copy at that tier. The two are kept
     * apart so a dead gateway is not reported as a track nobody may play.
     */
    suspend fun songUrlAnswer(mid: String, fileType: Int): QQStreamAnswer? {
        val urls = fetchData<QQSongUrls>("song/$mid/url") {
            parameter("file_type", fileType)
        } ?: return null
        val item = urls.data.firstOrNull { it.mid == mid }
        return QQStreamAnswer(
            url = item?.purl?.takeIf(String::isNotBlank)?.let { StreamHost + it },
            result = item?.result ?: 0,
        )
    }

    /**
     * Whether a gateway answers at [address], saved or not, by asking for the API description
     * FastAPI serves. It touches no QQ upstream, so a check costs the gateway nothing.
     */
    suspend fun ping(address: String): Boolean {
        val root = address.trim().trimEnd('/')
        if (root.isEmpty()) return false
        return try {
            client.get("$root/openapi.json").status.value in 200..299
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    /** The public header of an account's profile — its name and avatar — by encrypted UIN. */
    suspend fun userHomepage(encryptUin: String): QQUserHomepage? =
        fetchData<QQUserHomepage>("user/$encryptUin/homepage")

    // The login routes obtain a new account, so a refusal there is never a reason to renew the old
    // one; the renewal routes are what a refusal elsewhere calls, so they must not call back.

    /** Issues a login QR code for [loginType], `qq` or `wx`. */
    suspend fun qrCodeStart(loginType: String): QQQrCode? =
        fetchData<QQQrCode>("login/qrcode/$loginType", renewOnRejection = false)

    suspend fun qrCodeStatus(loginType: String, identifier: String): QQQrStatus? =
        fetchData<QQQrStatus>("login/qrcode/$loginType/status", renewOnRejection = false) {
            parameter("identifier", identifier)
        }

    /** Whether the account sent as the cookie has expired; null without one, or on failure. */
    suspend fun credentialExpired(): Boolean? =
        fetchEnvelope<Boolean>("login/check_expired", renewOnRejection = false)?.data

    /** Renews the account sent as the cookie; the answer is the account to store from now on. */
    suspend fun refreshCredential(): QQGatewayCredential? =
        fetchData<QQGatewayCredential>("login/refresh_credential", renewOnRejection = false)

    /** Asks QQ to text a login code; the answer's `event` is 0 sent, 1 captcha wanted, 2 too frequent. */
    suspend fun phoneSendCode(phone: Long, countryCode: Int = 86): QQPhoneAuthCode? =
        fetchData<QQPhoneAuthCode>("login/phone/authcode", renewOnRejection = false) {
            parameter("phone", phone)
            parameter("country_code", countryCode)
        }

    /** Exchanges a texted code for the account; null for a wrong or stale code. */
    suspend fun phoneAuthorize(phone: Long, code: String): QQGatewayCredential? =
        fetchData<QQGatewayCredential>("login/phone/authorize", renewOnRejection = false) {
            parameter("phone", phone)
            parameter("auth_code", code)
        }

    private suspend inline fun <reified T> fetchData(
        path: String,
        renewOnRejection: Boolean = true,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): T? = fetchEnvelope<T>(path, renewOnRejection, block)?.data

    /**
     * The decoded envelope of a successful call, or null for a transport failure, a non-2xx
     * status or a non-zero `code`. The three collapse together because no caller here can do
     * anything different with them; `search` tells an empty result from a null one, which is the
     * distinction that matters.
     */
    private suspend inline fun <reified T> fetchEnvelope(
        path: String,
        renewOnRejection: Boolean = true,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): QQApiResponse<T>? {
        val body = fetchBody(path, renewOnRejection, block) ?: return null
        return try {
            ApiJson.decodeFromString<QQApiResponse<T>>(body).takeIf { it.code == SuccessCode }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The body of a 2xx answer, or null for a transport failure or any other status. A 401 to a
     * request that carried an account goes to [onRejected] first, and the request is sent once
     * more if that renewed the account.
     */
    private suspend fun fetchBody(
        path: String,
        renewOnRejection: Boolean,
        block: HttpRequestBuilder.() -> Unit,
    ): String? {
        val root = baseUrl().trimEnd('/')
        if (root.isEmpty()) return null
        return try {
            val account = cookie().trim()
            var response = send("$root/$path", account, block)
            if (
                response.status == HttpStatusCode.Unauthorized &&
                account.isNotEmpty() &&
                renewOnRejection &&
                onRejected?.invoke(account) == true
            ) {
                response = send("$root/$path", cookie().trim(), block)
            }
            // The gateway answers 4xx with a JSON body; decoding that as the success shape would
            // yield an empty result that reads like "no such song".
            if (response.status.value !in 200..299) null else response.bodyAsText()
        } catch (cancelled: CancellationException) {
            // A cancelled caller is not a dead gateway: swallowing this turned switching QR
            // methods mid-poll into a "后端无响应" banner on the next method's page.
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun send(
        url: String,
        account: String,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse = client.get(url) {
        // Credentials travel per request rather than being installed into the gateway, so one
        // gateway can serve several clients and the Android build can sign in at all.
        if (account.isNotEmpty()) header(HttpHeaders.Cookie, account)
        block()
    }

    companion object {
        const val SuccessCode = 0
        private const val SearchTypeSong = 0

        /** The one CDN host that serves the gateway's `purl` paths. */
        const val StreamHost = "https://dl.stream.qqmusic.qq.com/"
    }
}

/** Every route's envelope. */
@Serializable
data class QQApiResponse<T>(
    val code: Int = -1,
    val msg: String = "",
    val data: T? = null,
)

@Serializable
data class QQSearchData(
    @SerialName("total_num") val totalNum: Int = 0,
    val nextpage: Int = 0,
    val song: List<QQSong> = emptyList(),
)

/** A song row, the same shape in search results and playlist pages. */
@Serializable
data class QQSong(
    val id: Long = 0,
    val mid: String = "",
    val name: String = "",
    val title: String = "",
    val singer: List<QQSinger> = emptyList(),
    val album: QQAlbum? = null,
    val file: QQSongFile? = null,
    val pay: QQSongPay? = null,
    /** Seconds, unlike NetEase's milliseconds. */
    val interval: Long = 0,
)

@Serializable
data class QQSinger(
    val id: Long = 0,
    val mid: String = "",
    val name: String = "",
)

@Serializable
data class QQAlbum(
    val id: Long = 0,
    val mid: String = "",
    val name: String = "",
    /** The cover's own id, present on rows whose [mid] is empty (singles, indie uploads). */
    val pmid: String = "",
)

/** Non-zero sizes mark which encodings exist; they say nothing about which the account may fetch. */
@Serializable
data class QQSongFile(
    @SerialName("media_mid") val mediaMid: String = "",
    @SerialName("size_128mp3") val size128: Long = 0,
    @SerialName("size_320mp3") val size320: Long = 0,
    @SerialName("size_flac") val sizeFlac: Long = 0,
)

@Serializable
data class QQSongPay(
    @SerialName("pay_play") val payPlay: Int = 0,
    @SerialName("pay_month") val payMonth: Int = 0,
)

@Serializable
data class QQSonglistDetail(
    val info: QQSonglistInfo? = null,
    val songs: List<QQSong> = emptyList(),
    val total: Int = 0,
    val hasmore: Int = 0,
)

@Serializable
data class QQSonglistInfo(
    val id: Long = 0,
    val title: String = "",
    val picurl: String = "",
    val desc: String = "",
    val songnum: Int = 0,
)

/** `lyric` is LRC; `trans` and `roma` are LRC-shaped too, and empty when absent. */
@Serializable
data class QQLyric(
    val lyric: String = "",
    val trans: String = "",
    val roma: String = "",
)

@Serializable
data class QQSongUrls(
    val expiration: Long = 0,
    val data: List<QQSongUrlItem> = emptyList(),
)

@Serializable
data class QQSongUrlItem(
    val mid: String = "",
    val purl: String = "",
    val result: Int = 0,
)

@Serializable
data class QQUserHomepage(
    @SerialName("base_info") val baseInfo: QQUserBaseInfo? = null,
)

@Serializable
data class QQUserBaseInfo(
    @SerialName("encrypted_uin") val encryptedUin: String = "",
    val name: String = "",
    val avatar: String = "",
)

/** See [QQMusicApi.songUrlAnswer]; [result] is QQ's own code beside an empty path, 0 otherwise. */
data class QQStreamAnswer(
    val url: String?,
    val result: Int,
)

@Serializable
data class QQQrCode(
    val identifier: String = "",
    val mimetype: String = "",
    /** The PNG, base64. */
    val data: String = "",
)

@Serializable
data class QQQrStatus(
    val event: Int = -1,
    val done: Boolean = false,
    val credential: QQGatewayCredential? = null,
)

@Serializable
data class QQPhoneAuthCode(
    val event: Int = -1,
    val info: String? = null,
)

/** The gateway's `Credential` model, as it appears in QR-login and renewal answers. */
@Serializable
data class QQGatewayCredential(
    val musicid: Long = 0,
    val musickey: String = "",
    val openid: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("expired_at") val expiredAt: Long = 0,
    val unionid: String = "",
    @SerialName("str_musicid") val strMusicid: String = "",
    @SerialName("refresh_key") val refreshKey: String = "",
    val encryptUin: String = "",
    val loginType: Int = 0,
)

/**
 * QQ serves cover art off a predictable path keyed by the album's mid, or by the cover's own
 * `pmid` on rows that have no album of their own.
 */
internal fun qqAlbumArtworkUrl(album: QQAlbum?, size: Int = 300): String {
    val key = album?.mid?.takeIf(String::isNotBlank)
        ?: album?.pmid?.takeIf(String::isNotBlank)
        ?: return ""
    return "https://y.gtimg.cn/music/photo_new/T002R${size}x${size}M000$key.jpg"
}

package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Maps only the service routes used by NCMApi, without performing I/O.
 *
 * Protocol reference: NeteaseCloudMusicApiEnhanced/api-enhanced at
 * `4a7d557326d16d2f1e5172ee69c9471eb3a50173`, `module/<route_underscores>.js`.
 * The special public route `daily_signin` retains its underscore. Unspecified crypto is EAPI
 * because that revision's `util/config.json` has `APP_CONF.encrypt = true`.
 *
 * A supplied query value remains a JSON string, as in the Express query object. A numeric
 * JavaScript `query.value || default` fallback becomes a JSON number instead. In particular,
 * the nonempty string `"0"` must not be mistaken for a missing value. Transport-level options
 * (credential, realIP, timestamp) are not copied into the endpoint's data.
 */
internal object NcmEndpointMapper {
    fun map(
        route: String,
        query: Map<String, String>,
        jsonBody: JsonElement? = null,
    ): NcmEndpointCall = when (val normalizedRoute = route.trim('/')) {
        "user/account" -> request("/api/nuser/account/get", crypto = NcmEndpointCrypto.WEAPI)
        "user/detail" -> request(
            "/api/v1/user/detail/${query.required("uid")}",
            crypto = NcmEndpointCrypto.WEAPI,
            responseKind = NcmEndpointResponseKind.USER_DETAIL,
        )
        "user/level" -> request("/api/user/level", crypto = NcmEndpointCrypto.WEAPI)
        "user/record" -> request("/api/v1/play/record", NcmEndpointCrypto.WEAPI) {
            copyQuery(query, "uid")
            defaultQuery(query, "type", 0)
        }
        "record/recent/song" -> request("/api/play-record/song/list", NcmEndpointCrypto.WEAPI) {
            defaultQuery(query, "limit", 100)
        }
        "login/status" -> request(
            "/api/w/nuser/account/get",
            crypto = NcmEndpointCrypto.WEAPI,
            responseKind = NcmEndpointResponseKind.LOGIN_STATUS,
        )
        "login/qr/key" -> request(
            "/api/login/qrcode/unikey",
            responseKind = NcmEndpointResponseKind.LOGIN_QR_KEY,
        ) {
            put("type", 3)
        }
        "login/qr/create" -> NcmEndpointCall(
            uri = "",
            data = buildJsonObject {
                copyQuery(query, "key")
                // The upstream tests truthiness, not whether the query string equals "true".
                put("qrimg", !query["qrimg"].isNullOrEmpty())
                put("platform", query["platform"].orEmpty().ifEmpty { "pc" })
            },
            action = NcmEndpointAction.QR_CREATE,
        )
        "login/qr/check" -> request(
            "/api/login/qrcode/client/login",
            responseKind = NcmEndpointResponseKind.LOGIN_QR_CHECK,
        ) {
            copyQuery(query, "key")
            put("type", 3)
        }
        "daily_signin" -> request("/api/point/dailyTask") {
            defaultQuery(query, "type", 0)
        }
        "vip/growthpoint/getall" -> request(
            "/api/vipnewcenter/app/level/task/reward/getall",
            crypto = NcmEndpointCrypto.XEAPI,
        )
        "user/playlist" -> request("/api/user/playlist", NcmEndpointCrypto.WEAPI) {
            copyQuery(query, "uid")
            defaultQuery(query, "limit", 30)
            defaultQuery(query, "offset", 0)
            put("includeVideo", true)
        }
        "playlist/track/all" -> NcmEndpointCall(
            uri = "",
            data = buildJsonObject {
                playlistDetailData(query)
                put("limit", parseIntegerOrDefault(query["limit"], 1000))
                put("offset", parseIntegerOrDefault(query["offset"], 0))
            },
            action = NcmEndpointAction.PLAYLIST_TRACK_ALL,
        )
        "playlist/detail" -> request("/api/v6/playlist/detail") {
            playlistDetailData(query)
        }
        "playlist/update/playcount" -> request("/api/playlist/update/playcount") {
            copyQuery(query, "id")
        }
        "playmode/intelligence/list" -> request("/api/playmode/intelligence/list") {
            copyQuery(query, "id", "songId")
            put("type", "fromPlayOne")
            copyQuery(query, "pid", "playlistId")
            (query["sid"]?.takeIf(String::isNotEmpty) ?: query["id"])?.let {
                put("startMusicId", it)
            }
            defaultQuery(query, "count", 1)
        }
        "song/url/v1" -> NcmEndpointCall(
            uri = "/api/song/enhance/player/url/v1",
            data = buildJsonObject {
                put("ids", "[${query.required("id")}]")
                copyQuery(query, "level")
                put("encodeType", if (query["level"] == "vivid") "mp3" else "flac")
                if (query["level"] == "sky") {
                    put("immerseType", query["immerseType"].orEmpty().ifEmpty { "c51" })
                }
            },
            crypto = NcmEndpointCrypto.XEAPI,
            cookieOverrides = if (query["level"] == "vivid") {
                mapOf("os" to "android", "appver" to "9.5.61")
            } else {
                emptyMap()
            },
        )
        "song/detail" -> request("/api/v3/song/detail", NcmEndpointCrypto.WEAPI) {
            val ids = query.required("ids").split(Regex("\\s*,\\s*"))
            put("c", ids.joinToString(separator = ",", prefix = "[", postfix = "]") { "{\"id\":$it}" })
        }
        "song/wiki/summary" -> request("/api/song/play/about/block/page") {
            copyQuery(query, "id", "songId")
        }
        "song/dynamic/cover" -> request("/api/songplay/dynamic-cover") {
            copyQuery(query, "id", "songId")
        }
        "audio/match" -> NcmEndpointCall(
            uri = "/api/music/audio/match",
            data = buildJsonObject {
                put("sessionId", "0123456789abcdef")
                put("algorithmCode", "shazam_v2")
                copyQuery(query, "duration")
                copyQuery(query, "audioFP", "rawdata")
                put("times", 1)
                put("decrypt", 1)
            },
            crypto = NcmEndpointCrypto.API,
            domain = "https://interface.music.163.com",
            responseKind = NcmEndpointResponseKind.AUDIO_MATCH,
            action = NcmEndpointAction.AUDIO_MATCH,
        )
        "weblog" -> NcmEndpointCall(
            uri = "/api/feedback/weblog",
            data = weblogData(jsonBody),
            crypto = NcmEndpointCrypto.WEAPI,
            action = NcmEndpointAction.WEBLOG,
        )
        "scrobble/v1" -> NcmEndpointCall(
            uri = "",
            data = buildJsonObject {
                scrobbleParameters.forEach { copyQuery(query, it) }
            },
            cookieOverrides = mapOf("os" to "pc"),
            action = NcmEndpointAction.SCROBBLE_V1,
        )
        "relay/play/state/submit" -> request("/api/relay/play/state/submit", NcmEndpointCrypto.WEAPI) {
            require(query["id"]?.isNotEmpty() == true) { "id is required" }
            val submission = buildJsonObject {
                put("resource", buildJsonObject {
                    put("id", query.required("id"))
                    put("type", query["type"] ?: "song")
                })
                put("progress", numberOrZero(query["progress"]))
                put("sessionId", query["sessionId"].orEmpty().ifEmpty { newSessionId() })
                put("playMode", query["playMode"] ?: "list_loop")
            }
            put("playStateSubmitReq", submission.toString())
        }
        "cloudsearch" -> request("/api/cloudsearch/pc") {
            copyQuery(query, "keywords", "s")
            defaultQuery(query, "type", 1)
            defaultQuery(query, "limit", 30)
            defaultQuery(query, "offset", 0)
            put("total", true)
        }
        "artist/detail" -> request("/api/artist/head/info/get") {
            copyQuery(query, "id")
        }
        "artist/top/song" -> request("/api/artist/top/song", NcmEndpointCrypto.WEAPI) {
            copyQuery(query, "id")
        }
        "artist/album" -> request("/api/artist/albums/${query.required("id")}", NcmEndpointCrypto.WEAPI) {
            defaultQuery(query, "limit", 30)
            defaultQuery(query, "offset", 0)
            put("total", true)
        }
        "album" -> request("/api/v1/album/${query.required("id")}", crypto = NcmEndpointCrypto.WEAPI)
        "search/hot/detail" -> request("/api/hotsearchlist/get", crypto = NcmEndpointCrypto.WEAPI)
        "search/suggest" -> request(
            "/api/search/suggest/${if (query["type"] == "mobile") "keyword" else "web"}",
            NcmEndpointCrypto.WEAPI,
        ) {
            put("s", query["keywords"].orEmpty())
        }
        "recommend/songs" -> request("/api/v3/discovery/recommend/songs", NcmEndpointCrypto.WEAPI) {
            copyQuery(query, "afresh")
        }
        "recommend/resource" -> request(
            "/api/v1/discovery/recommend/resource",
            crypto = NcmEndpointCrypto.WEAPI,
        )
        "lyric" -> request("/api/song/lyric") {
            copyQuery(query, "id")
            put("tv", -1)
            put("lv", -1)
            put("rv", -1)
            put("kv", -1)
            put("_nmclfl", 1)
        }
        "lyric/new" -> request("/api/song/lyric/v1") {
            copyQuery(query, "id")
            put("cp", false)
            put("tv", 0)
            put("lv", 0)
            put("rv", 0)
            put("kv", 0)
            put("yv", 0)
            put("ytv", 0)
            put("yrv", 0)
        }
        "like" -> request("/api/radio/like", NcmEndpointCrypto.WEAPI) {
            put("alg", "itembased")
            copyQuery(query, "id", "trackId")
            put("like", query["like"] != "false")
            put("time", "3")
        }
        "likelist" -> request("/api/song/like/get") {
            copyQuery(query, "uid")
        }
        "song/like/check" -> request("/api/song/like/check") {
            copyQuery(query, "ids", "trackIds")
        }
        "comment/music" -> request(
            "/api/v1/resource/comments/R_SO_4_${query.required("id")}",
            NcmEndpointCrypto.WEAPI,
        ) {
            commentData(query)
        }
        "comment/hot" -> request(
            "/api/v1/resource/hotcomments/${resourceType(query.required("type"))}${query.required("id")}",
            NcmEndpointCrypto.WEAPI,
        ) {
            commentData(query)
        }
        "inner/version" -> NcmEndpointCall(
            uri = "",
            data = buildJsonObject { },
            action = NcmEndpointAction.LOCAL_VERSION,
        )
        else -> throw IllegalArgumentException("Unsupported NCM route: $normalizedRoute")
    }

    fun shape(
        kind: NcmEndpointResponseKind,
        body: JsonElement,
        cookies: List<String> = emptyList(),
    ): JsonElement = shapeNcmEndpointResponse(kind, body, cookies)

    private fun request(
        uri: String,
        crypto: NcmEndpointCrypto = NcmEndpointCrypto.EAPI,
        responseKind: NcmEndpointResponseKind = NcmEndpointResponseKind.IDENTITY,
        data: JsonObjectBuilder.() -> Unit = {},
    ): NcmEndpointCall = NcmEndpointCall(
        uri = uri,
        data = buildJsonObject(data),
        crypto = crypto,
        responseKind = responseKind,
    )

    private fun JsonObjectBuilder.copyQuery(
        query: Map<String, String>,
        source: String,
        target: String = source,
    ) {
        query[source]?.let { put(target, it) }
    }

    private fun JsonObjectBuilder.defaultQuery(query: Map<String, String>, name: String, fallback: Int) {
        val value = query[name]
        put(name, if (value.isNullOrEmpty()) JsonPrimitive(fallback) else JsonPrimitive(value))
    }

    private fun JsonObjectBuilder.playlistDetailData(query: Map<String, String>) {
        copyQuery(query, "id")
        put("n", 100000)
        defaultQuery(query, "s", 8)
    }

    private fun JsonObjectBuilder.commentData(query: Map<String, String>) {
        copyQuery(query, "id", "rid")
        defaultQuery(query, "limit", 20)
        defaultQuery(query, "offset", 0)
        put("beforeTime", query["before"]?.takeIf(String::isNotEmpty)?.let(::JsonPrimitive) ?: JsonPrimitive(0))
    }

    private fun Map<String, String>.required(name: String): String =
        requireNotNull(this[name]) { "$name is required" }

    private fun parseIntegerOrDefault(value: String?, fallback: Long): Long {
        val trimmed = value?.trimStart().orEmpty()
        val parsed = if (trimmed.matches(Regex("^[+-]?0[xX].*"))) {
            val sign = if (trimmed.startsWith('-')) -1 else 1
            val digits = trimmed.removePrefix("+").removePrefix("-").drop(2)
            Regex("^[0-9a-fA-F]+").find(digits)?.value?.toLongOrNull(16)?.times(sign)
        } else {
            Regex("^[+-]?\\d+").find(trimmed)?.value?.toLongOrNull()
        }
        return parsed?.takeUnless { it == 0L } ?: fallback
    }

    private fun numberOrZero(value: String?): JsonElement {
        val text = value?.trim().orEmpty()
        text.toLongOrNull()?.let { return JsonPrimitive(it) }
        val number = text.toDoubleOrNull() ?: return JsonPrimitive(0)
        return when {
            number.isNaN() -> JsonPrimitive(0)
            number.isFinite() -> JsonPrimitive(number)
            else -> JsonNull
        }
    }

    private fun newSessionId(): String = buildString(12) {
        repeat(12) { append(sessionCharacters[Random.nextInt(sessionCharacters.length)]) }
    }

    private fun resourceType(value: String): String = when (value) {
        "0" -> "R_SO_4_"
        "1" -> "R_MV_5_"
        "2" -> "A_PL_0_"
        "3" -> "R_AL_3_"
        "4" -> "A_DJ_1_"
        "5" -> "R_VI_62_"
        "6" -> "A_EV_2_"
        "7" -> "A_DR_14_"
        else -> throw IllegalArgumentException("Unsupported comment resource type: $value")
    }

    private fun weblogData(jsonBody: JsonElement?): JsonObject {
        val data = (jsonBody as? JsonObject)?.get("data")
        require(data == null || data == JsonNull || data is JsonObject) { "weblog data must be an object" }
        return data as? JsonObject ?: buildJsonObject { }
    }

    private const val sessionCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val scrobbleParameters = listOf(
        "id", "time", "total", "sourceid", "sourceId", "source", "name", "artist", "bitrate", "level", "vip",
    )
}

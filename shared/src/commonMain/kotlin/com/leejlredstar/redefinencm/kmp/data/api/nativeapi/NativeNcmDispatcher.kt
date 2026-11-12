package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.NcmCrypto
import io.ktor.http.HttpMethod
import io.ktor.http.formUrlEncode
import io.ktor.util.date.getTimeMillis
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import qrcode.QRCode
import kotlin.io.encoding.Base64

/** Dispatches local/composite service operations without ever starting a local HTTP server. */
internal class NativeNcmDispatcher(
    private val protocol: NcmProtocolClient,
    private val wire: NcmWire,
    crypto: NcmCrypto,
    private val onClose: () -> Unit = {},
    private val clock: () -> Long = ::getTimeMillis,
) {
    private val reporting = NcmNcblBuilder(crypto)

    suspend fun execute(
        route: String,
        query: Map<String, String>,
        jsonBody: JsonElement? = null,
        credential: String? = null,
    ): NcmProtocolResponse {
        val call = NcmEndpointMapper.map(route, query, jsonBody)
        val account = credential ?: query["cookie"] ?: protocol.credential()
        val response = when (call.action) {
            NcmEndpointAction.QR_CREATE -> createQr(call)
            NcmEndpointAction.PLAYLIST_TRACK_ALL -> playlistTracks(call, account)
            NcmEndpointAction.AUDIO_MATCH -> audioMatch(call)
            NcmEndpointAction.LOCAL_VERSION -> NcmProtocolResponse(200, buildJsonObject {
                put("version", "native-4a7d557")
            })
            NcmEndpointAction.SCROBBLE_V1 -> scrobble(call.data, account)
            NcmEndpointAction.REQUEST, NcmEndpointAction.WEBLOG -> protocol.execute(call, account)
        }
        val status = if (call.responseKind in setOf(NcmEndpointResponseKind.LOGIN_QR_KEY,
                NcmEndpointResponseKind.LOGIN_QR_CHECK, NcmEndpointResponseKind.AUDIO_MATCH)) 200 else response.status
        return NcmProtocolResponse(status, NcmEndpointMapper.shape(call.responseKind, response.body, response.cookies), response.cookies)
    }

    private fun createQr(call: NcmEndpointCall): NcmProtocolResponse {
        val key = call.data["key"]!!.jsonPrimitive.content
        require(key.isNotBlank() && key.length <= 2048) { "Invalid QR login key" }
        val url = "https://music.163.com/login?codekey=${uriComponent(key)}"
        val image = if (call.data["qrimg"]?.jsonPrimitive?.booleanOrNull == true) {
            "data:image/png;base64," + Base64.Default.encode(QRCode.ofSquares().withSize(6).build(url).renderToBytes())
        } else ""
        return NcmProtocolResponse(200, buildJsonObject {
            put("code", 200)
            put("data", buildJsonObject { put("qrurl", url); put("qrimg", image) })
        })
    }

    private suspend fun playlistTracks(call: NcmEndpointCall, account: String): NcmProtocolResponse {
        val data = call.data
        val detail = protocol.execute(NcmEndpointCall("/api/v6/playlist/detail", JsonObject(data.filterKeys {
            it != "limit" && it != "offset"
        })), account)
        if (detail.body.jsonObject["code"]?.jsonPrimitive?.intOrNull != 200) return detail
        val ids = detail.body.jsonObject["playlist"]?.jsonObject?.get("trackIds")?.jsonArray
            ?: throw IllegalStateException("NetEase playlist did not contain track IDs")
        val offset = data["offset"]?.jsonPrimitive?.intOrNull ?: 0
        val limit = data["limit"]?.jsonPrimitive?.intOrNull ?: 1000
        require(offset >= 0 && limit in 1..100000) { "Invalid playlist pagination" }
        val selected = ids.drop(offset).take(limit)
        if (selected.isEmpty()) return NcmProtocolResponse(200, buildJsonObject {
            put("code", 200); put("songs", JsonArray(emptyList())); put("privileges", JsonArray(emptyList()))
        })
        val c = buildJsonArray {
            selected.forEach { item -> add(buildJsonObject { put("id", item.jsonObject.getValue("id")) }) }
        }.toString()
        return protocol.execute(NcmEndpointCall("/api/v3/song/detail", buildJsonObject { put("c", c) }), account)
    }

    private suspend fun audioMatch(call: NcmEndpointCall): NcmProtocolResponse {
        // This is a credential-free special GET, not the regular encrypted API POST.
        val url = "${call.domain}${call.uri}?" + call.data.map { (key, value) -> key to jsString(value) }.formUrlEncode()
        val response = wire.request(url, HttpMethod.Get)
        return NcmProtocolResponse(response.status, ApiJson.parseToJsonElement(response.body.decodeToString()))
    }

    private suspend fun scrobble(data: JsonObject, cookie: String): NcmProtocolResponse {
        if (ncmCookieMap(cookie)["MUSIC_U"].isNullOrBlank()) return reportingError(401, "Missing NetEase session")
        val songId = data["id"]?.jsonPrimitive?.longOrNull ?: return reportingError(400, "Invalid song ID")
        val played = data["time"]?.jsonPrimitive?.longOrNull ?: return reportingError(400, "Invalid listening time")
        if (songId <= 0 || played <= 0) return reportingError(400, "Invalid listening parameters")
        val params = NcmNcblParams(
            songId = songId, playedSeconds = played,
            totalSeconds = data["total"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: played,
            sourceId = data["sourceid"]?.jsonPrimitive?.content.orEmpty(),
            source = data["source"]?.jsonPrimitive?.content ?: "list",
            name = data["name"]?.jsonPrimitive?.content.orEmpty(),
            artist = data["artist"]?.jsonPrimitive?.content.orEmpty(),
            bitrate = data["bitrate"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: 320,
            level = data["level"]?.jsonPrimitive?.content ?: "exhigh",
            vip = data["vip"]?.jsonPrimitive?.content == "true",
        )
        val prepared = reporting.prepare(params, cookie, clock())
        val plv = reporting.upload(prepared, prepared.plvRecord)
        val plvResponse = wire.request(plv.url, headers = plv.headers, body = plv.body)
        val plvBody = parseUploadResult(plvResponse)
        if (!uploadAccepted(plvBody, plv.fileName)) return reportingError(
            plvBody["code"]?.jsonPrimitive?.intOrNull?.takeUnless { it == 200 } ?: -1,
            "PLV upload rejected", plvBody,
        )
        // No retry: PLV can have succeeded even if the following request fails or is cancelled.
        val pld = reporting.upload(prepared, prepared.pldRecord)
        val pldResponse = wire.request(pld.url, headers = pld.headers, body = pld.body)
        val pldBody = parseUploadResult(pldResponse)
        if (!uploadAccepted(pldBody, pld.fileName)) return reportingError(
            pldBody["code"]?.jsonPrimitive?.intOrNull?.takeUnless { it == 200 } ?: -1,
            "PLV accepted but PLD rejected", buildJsonObject { put("plv", plvBody); put("pld", pldBody) },
        )
        return NcmProtocolResponse(200, buildJsonObject {
            put("code", 200)
            put("details", buildJsonObject {
                put("plv", buildJsonObject { put("fileName", plv.fileName); put("payloadSize", plv.payloadSize) })
                put("pld", buildJsonObject { put("fileName", pld.fileName); put("payloadSize", pld.payloadSize) })
            })
        })
    }

    private fun parseUploadResult(response: NcmWireResponse): JsonObject =
        runCatching { ApiJson.parseToJsonElement(response.body.decodeToString()).jsonObject }.getOrElse {
            buildJsonObject { put("code", response.status.takeUnless { it in 200..299 } ?: -1) }
        }

    private fun uploadAccepted(body: JsonObject, fileName: String): Boolean =
        body["code"]?.jsonPrimitive?.intOrNull == 200 &&
            ((body["data"] as? JsonObject)?.get("successfiles") as? JsonArray)?.any {
                (it as? JsonPrimitive)?.content == fileName
            } == true

    private fun reportingError(code: Int, message: String, details: JsonElement? = null) =
        NcmProtocolResponse(if (code == 401 || code == 400) code else 200, buildJsonObject {
            put("code", code); put("msg", message)
            if (details != null) put("details", details)
        })

    fun close() = onClose()
}

package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.NcmCrypto
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.NcmProtocolCrypto
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.XeapiPublicKeyState
import io.ktor.http.HttpMethod
import io.ktor.http.formUrlEncode
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

internal class NcmProtocolResponse(
    val status: Int,
    val body: JsonElement,
    val cookies: List<String> = emptyList(),
)

/**
 * A focused native port of api-enhanced util/request.js, pinned to 4a7d557326d16d2f1e5172ee69c9471eb3a50173.
 * Only the protocols selected by this app's endpoint allowlist are supported. Account writes and
 * playback reports are never retried here; key bootstrap is read-only and single-flight.
 */
internal class NcmProtocolClient(
    private val wire: NcmWire,
    private val store: NcmSessionStore,
    private val crypto: NcmCrypto,
    private val clock: () -> Long = ::getTimeMillis,
) {
    private val envelope = NcmProtocolCrypto(crypto)
    private val bootstrapLock = Mutex()
    private val anonymousLock = Mutex()
    private val stateLock = Mutex()
    private var device = ""
    private val processWnmCid = crypto.secureRandom(6).joinToString("") {
        ('a'.code + (it.toInt() and 255) % 26).toChar().toString()
    } + ".${clock()}.01.0"
    private var publicKey: XeapiPublicKeyState? = null
    private var anonymousCookie: String? = null
    // Session keys and NMTID belong to a credential, not to the last request to finish.
    private val sessions = linkedMapOf<String, Session>()
    private class Session(var id: String = "", var key: ByteArray? = null, var nmtid: String = "", var nmtidProbesLeft: Int = 3)

    suspend fun credential(): String = store.credential()

    suspend fun execute(call: NcmEndpointCall, credential: String): NcmProtocolResponse {
        val deviceId = deviceId()
        val account = ncmCookieMap(credential)
        val anonymous = if (account["MUSIC_U"].isNullOrEmpty() && account["MUSIC_A"].isNullOrEmpty()) {
            ncmCookieMap(ensureAnonymous())
        } else emptyMap()
        return executePrepared(call, anonymous + account + call.cookieOverrides, deviceId, credential)
    }

    private suspend fun deviceId(): String = bootstrapLock.withLock {
        if (device.isEmpty()) {
            device = store.read(NCM_DEVICE_ID_KEY).takeIf { it.matches(Regex("[0-9A-F]{52}")) }
                ?: hex(crypto.secureRandom(26)).uppercase().also { store.write(NCM_DEVICE_ID_KEY, it) }
        }
        device
    }

    private suspend fun ensureAnonymous(): String = anonymousLock.withLock {
        anonymousCookie?.let { return@withLock it }
        val saved = store.read(NCM_ANONYMOUS_KEY)
        if (!ncmCookieMap(saved)["MUSIC_A"].isNullOrBlank()) {
            anonymousCookie = saved
            return@withLock saved
        }
        val id = deviceId()
        val xorKey = "3go8&\$8*3*3h0k(2)2"
        val xored = ByteArray(id.length) { index -> (id[index].code xor xorKey[index % xorKey.length].code).toByte() }
        val digest = Base64.Default.encode(crypto.md5(xored))
        val username = Base64.Default.encode("$id $digest".encodeToByteArray())
        // The current upstream anonymous route itself uses XEAPI. Fetch the key first, not
        // after anonymous login as the old server's first-run ordering did.
        val result = executePrepared(
            NcmEndpointCall("/api/register/anonimous", buildJsonObject { put("username", username) }, NcmEndpointCrypto.XEAPI),
            emptyMap(), id, "",
        )
        val cookie = result.cookies.joinToString(";")
        val code = result.body.jsonObject["code"]?.jsonPrimitive?.intOrNull
        require(code == 200 && !ncmCookieMap(cookie)["MUSIC_A"].isNullOrBlank()) {
            "NetEase anonymous registration failed (HTTP ${result.status}, code $code, cookie count ${result.cookies.size})"
        }
        store.write(NCM_ANONYMOUS_KEY, cookie)
        anonymousCookie = cookie
        cookie
    }

    private suspend fun xeapiKey(deviceId: String): XeapiPublicKeyState = bootstrapLock.withLock {
        publicKey?.let { return@withLock it }
        val savedJson = try {
            ApiJson.parseToJsonElement(store.read(NCM_XEAPI_KEY)).jsonObject
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val saved = savedJson?.let { runCatching { parseKey(it) }.getOrNull() }
        val savedAt = savedJson?.get("nativeFetchedAt")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        // Always revalidate once per process. A verified, recently cached key is the only fallback;
        // missing/old metadata must not make a rotated key a permanent offline default.
        val nonce = crypto.secureRandom(16).joinToString("") { ((it.toInt() and 0xff) % 10).toString() }
        val timestamp = clock()
        val form = linkedMapOf(
            "appVersion" to "9.5.61", "currentKeyVersion" to (saved?.version ?: ""),
            "deviceId" to deviceId, "nonce" to nonce, "os" to "android", "requestType" to "active",
            "signature" to envelope.xeapiSign(timestamp, nonce), "t1" to "", "t2" to "",
            "timestamp" to timestamp.toString(), "uid" to "",
        )
        val fresh = try {
            val response = wire.request(
                "$API_DOMAIN/api/bsr/sk/get", headers = mapOf(
                    "User-Agent" to ANDROID_UA,
                    "Cookie" to ncmCookieHeader(mapOf("deviceId" to deviceId)),
                    "Content-Type" to FORM_CONTENT_TYPE,
                ), body = encodeForm(form),
            )
            val root = ApiJson.parseToJsonElement(response.body.decodeToString()).jsonObject
            require(response.status in 200..299 && root["code"]?.jsonPrimitive?.intOrNull == 200) { "NetEase key request failed" }
            val data = root["data"]!!.jsonObject
            val responseTimestamp = data["timestamp"]!!.jsonPrimitive.content.toLong()
            require(envelope.xeapiSign(responseTimestamp, nonce) == data["signature"]?.jsonPrimitive?.content) {
                "NetEase key response signature mismatch"
            }
            val decrypted = envelope.decryptXeapiPublicKey(data["encryptedData"]!!.jsonPrimitive.content)
            val rawKey = ApiJson.parseToJsonElement(decrypted.decodeToString()).jsonObject
            val keyJson = if (rawKey["sk"]?.jsonPrimitive?.content.isNullOrBlank() && saved != null) {
                JsonObject(rawKey + ("sk" to JsonPrimitive(saved.sk)))
            } else rawKey
            parseKey(keyJson).also {
                store.write(NCM_XEAPI_KEY, JsonObject(keyJson + ("nativeFetchedAt" to JsonPrimitive(clock()))).toString())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            saved?.takeIf { savedAt > 0 && clock() - savedAt in 0..86_400_000L } ?: throw failure
        }
        publicKey = fresh
        fresh
    }

    private fun parseKey(json: JsonObject): XeapiPublicKeyState {
        val raw = json["publicKey"]!!.jsonPrimitive.content
        require(Base64.Default.decode(raw).size == 32) { "Invalid NetEase X25519 key" }
        val version = json["version"]!!.jsonPrimitive.content
        val sk = json["sk"]!!.jsonPrimitive.content
        require(version.isNotBlank() && sk.isNotBlank()) { "Incomplete NetEase key state" }
        return XeapiPublicKeyState(raw, version, sk)
    }

    private suspend fun executePrepared(
        call: NcmEndpointCall,
        originalCookie: Map<String, String>,
        deviceId: String,
        credential: String,
    ): NcmProtocolResponse {
        require(call.crypto in setOf(NcmEndpointCrypto.EAPI, NcmEndpointCrypto.WEAPI, NcmEndpointCrypto.XEAPI, NcmEndpointCrypto.API)) {
            "Protocol is not used by the native NetEase endpoint allowlist"
        }
        require(call.uri.startsWith("/api/")) { "Invalid NetEase API path" }
        val scope = hex(crypto.md5(credential.encodeToByteArray()))
        val session = stateLock.withLock {
            sessions.getOrPut(scope) { Session() }.let { target ->
                val snapshot = Session(target.id, target.key?.copyOf(), target.nmtid, target.nmtidProbesLeft)
                // Reserve one probe before I/O so a burst cannot create unbounded extra probes.
                if (call.crypto == NcmEndpointCrypto.EAPI && originalCookie["NMTID"].isNullOrBlank() &&
                    target.nmtid.isBlank() && target.nmtidProbesLeft > 0) target.nmtidProbesLeft--
                snapshot
            }
        }
        val now = clock()
        val cookie = processCookies(originalCookie, deviceId, session, call.crypto, now).toMutableMap()
        val data = call.data.toMutableMap().apply { put("e_r", JsonPrimitive(false)) }
        val headers = linkedMapOf("Content-Type" to FORM_CONTENT_TYPE)
        val url: String
        val form: Map<String, String>
        when (call.crypto) {
            NcmEndpointCrypto.WEAPI -> {
                data["csrf_token"] = JsonPrimitive(cookie["__csrf"].orEmpty())
                headers["Referer"] = "https://music.163.com"
                headers["User-Agent"] = WEB_UA
                headers["Cookie"] = ncmCookieHeader(cookie)
                val encrypted = envelope.weapi(JsonObject(data).toString().encodeToByteArray())
                form = linkedMapOf("params" to encrypted.params, "encSecKey" to encrypted.encSecKey)
                url = "${call.domain ?: WEB_DOMAIN}/weapi/${call.uri.removePrefix("/api/")}"
            }
            NcmEndpointCrypto.XEAPI -> {
                val state = xeapiKey(deviceId)
                val appver = if (cookie["os"] == "android") cookie["appver"].orEmpty() else "9.1.65"
                val osver = if (cookie["os"] == "android") cookie["osver"].orEmpty() else "16"
                val buildver = cookie["buildver"] ?: (now / 1000).toString()
                cookie += mapOf("os" to "android", "osver" to osver, "appver" to appver, "buildver" to buildver,
                    "sDeviceId" to (cookie["sDeviceId"] ?: cookie.getValue("deviceId")))
                headers += mapOf(
                    "User-Agent" to ANDROID_UA, "X-Client-Enc-State" to "ENCRYPTED", "x-aeapi" to "true",
                    "x-deviceid" to cookie.getValue("deviceId"), "x-os" to "android", "x-osver" to osver,
                    "x-appver" to appver, "x-sdeviceid" to cookie.getValue("sDeviceId"), "x-buildver" to buildver,
                    "Cookie" to ncmCookieHeader(cookie),
                )
                cookie["MUSIC_U"]?.let { headers["x-music-u"] = it }
                val plaintext = NcmProtocolCrypto.buildXeapiPlaintext(call.uri, data.mapValues { jsString(it.value) })
                val encrypted = envelope.xeapi(plaintext, state, sessionKey = session.key, sessionId = session.id)
                form = linkedMapOf("C" to encrypted.C, "S" to encrypted.S, "R" to encrypted.R)
                url = "${call.domain ?: XEAPI_DOMAIN}/xeapi/${call.uri.removePrefix("/api/")}"
            }
            else -> {
                val requestId = "${now}_${(crypto.secureRandom(2).let { ((it[0].toInt() and 255) * 256 + (it[1].toInt() and 255)) } % 1000).toString().padStart(4, '0')}"
                val header = linkedMapOf(
                    "osver" to cookie.getValue("osver"), "deviceId" to cookie.getValue("deviceId"),
                    "os" to cookie.getValue("os"), "appver" to cookie.getValue("appver"),
                    "versioncode" to (cookie["versioncode"] ?: "140"), "mobilename" to cookie["mobilename"].orEmpty(),
                    "buildver" to (cookie["buildver"] ?: (now / 1000).toString()), "resolution" to (cookie["resolution"] ?: "1920x1080"),
                    "__csrf" to cookie["__csrf"].orEmpty(), "channel" to cookie.getValue("channel"), "requestId" to requestId,
                )
                listOf("MUSIC_U", "MUSIC_A", "NMTID").forEach { name -> cookie[name]?.let { header[name] = it } }
                // Upstream header cookies URI-encode values; this is distinct from the raw account
                // Cookie sent by WEAPI/XEAPI. Encoding must not alter the stored credential.
                headers["Cookie"] = header.entries.joinToString("; ") { (k, v) -> "${uriComponent(k)}=${uriComponent(v)}" }
                headers["User-Agent"] = if (cookie["os"] == "osx") WEB_UA else IPHONE_UA
                if (call.crypto == NcmEndpointCrypto.EAPI) {
                    data["header"] = JsonObject(header.mapValues { JsonPrimitive(it.value) })
                    val encrypted = envelope.eapi(call.uri, JsonObject(data).toString().encodeToByteArray())
                    form = linkedMapOf("params" to encrypted.params)
                    url = "${call.domain ?: EAPI_DOMAIN}/eapi/${call.uri.removePrefix("/api/")}"
                } else {
                    form = data.mapValues { jsString(it.value) }
                    url = "${call.domain ?: API_DOMAIN}${call.uri}"
                }
            }
        }
        val response = wire.request(url, headers = headers, body = encodeForm(form))
        // Ciphertext is arbitrary binary and can start with '{' or '['. Only a complete valid
        // JSON document may bypass XEAPI response decryption; sniffing its first byte is unsafe.
        val isPlainJson = runCatching {
            ApiJson.parseToJsonElement(response.body.decodeToString(throwOnInvalidSequence = true)) is JsonObject
        }.getOrDefault(false)
        val plaintext = if (call.crypto == NcmEndpointCrypto.XEAPI && !isPlainJson) {
            envelope.decryptXeapiResponse(response.body)
        } else response.body
        val decoded = if (plaintext.size >= 2 && plaintext[0] == 0x1f.toByte() && plaintext[1] == 0x8b.toByte()) {
            NcmCompression.gunzip(plaintext)
        } else plaintext
        val body = runCatching { ApiJson.parseToJsonElement(decoded.decodeToString()) }.getOrElse {
            // Do not include raw response text (including HTML) in a throwable or application log.
            throw IllegalStateException("Invalid NetEase protocol response (${response.status})")
        }
        val cookies = response.cookies.map { it.replace(Regex("(?i);?\\s*Domain=[^;]*"), "") }
        stateLock.withLock {
            val target = sessions.getOrPut(scope) { Session() }
            val nmtid = cookies.firstNotNullOfOrNull { ncmCookieMap(it)["NMTID"] }
            if (nmtid != null) target.nmtid = nmtid
            val id = response.header("x-encr-ssid")
            val key = response.header("x-encr-sskey")
            val keyBytes = key?.encodeToByteArray()
            if (!id.isNullOrBlank() && keyBytes != null && keyBytes.size in setOf(16, 24, 32)) {
                target.id = id
                target.key = keyBytes
            }
            while (sessions.size > 4) sessions.remove(sessions.keys.first())
        }
        val code = body.jsonObject["code"]?.jsonPrimitive?.intOrNull?.takeUnless { it == 0 } ?: response.status
        val status = if (code in setOf(201, 302, 400, 502, 800, 801, 802, 803)) 200 else code.takeIf { it in 101..599 } ?: 400
        return NcmProtocolResponse(status, body, cookies)
    }

    private fun processCookies(original: Map<String, String>, deviceId: String, session: Session, protocol: NcmEndpointCrypto, now: Long): Map<String, String> {
        val os = original["os"]?.takeIf(String::isNotBlank) ?: "pc"
        val osDefaults = when (os) {
            "android" -> listOf("14", "8.20.20.231215173437", "xiaomi")
            "osx" -> listOf("15.5", "3.1.10.5100", "netease")
            "linux" -> listOf("Deepin 20.9", "1.2.1.0428", "netease")
            "iPhone OS", "iphone" -> listOf("16.2", "9.0.90", "distribution")
            else -> listOf("Microsoft-Windows-10-Professional-build-19045-64bit", "3.1.17.204416", "netease")
        }
        val nuid = original["_ntes_nuid"]?.takeIf(String::isNotBlank) ?: hex(crypto.secureRandom(32))
        return (linkedMapOf(
            "__remember_me" to "true", "ntes_kaola_ad" to "1", "_ntes_nuid" to nuid,
            "_ntes_nnid" to "$nuid,$now", "WNMCID" to processWnmCid, "WEVNSM" to "1.0.0",
            "osver" to osDefaults[0], "deviceId" to deviceId, "os" to os, "channel" to osDefaults[2], "appver" to osDefaults[1],
        ) + original.filterValues(String::isNotEmpty)).toMutableMap().apply {
            when {
                !original["NMTID"].isNullOrBlank() -> put("NMTID", original.getValue("NMTID"))
                session.nmtid.isNotBlank() -> put("NMTID", session.nmtid)
                protocol != NcmEndpointCrypto.EAPI || session.nmtidProbesLeft <= 0 ->
                    put("NMTID", "00O${hex(crypto.secureRandom(19))}")
                else -> remove("NMTID")
            }
        }
    }
}

internal fun jsString(element: JsonElement): String = if (element is JsonPrimitive) element.content else element.toString()
internal fun encodeForm(values: Map<String, String>): ByteArray = values.toList().formUrlEncode().encodeToByteArray()
internal fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
internal fun uriComponent(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val b = byte.toInt() and 255
        val c = b.toChar()
        if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-_.!~*'()") append(c)
        else append('%').append(b.toString(16).uppercase().padStart(2, '0'))
    }
}
internal const val API_DOMAIN = "https://interface.music.163.com"
internal const val EAPI_DOMAIN = "https://interfacepc.music.163.com"
internal const val XEAPI_DOMAIN = "https://interface3.music.163.com"
internal const val WEB_DOMAIN = "https://music.163.com"
internal const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded;charset=utf-8"
internal const val ANDROID_UA = "NeteaseMusic/9.5.61.260802021928(9005061);Dalvik/2.1.0 (Linux; U; Android 12; HBN-AL00 Build/cd737a2.0)"
private const val WEB_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val IPHONE_UA = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"

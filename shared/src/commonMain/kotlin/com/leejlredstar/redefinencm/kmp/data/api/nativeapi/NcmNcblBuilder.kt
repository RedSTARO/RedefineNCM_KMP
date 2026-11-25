package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.NcmCrypto
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.hexToBytes
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.toHex
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Native NCBL v3 framing, not a cryptography implementation or an HTTP client.
 *
 * Port of util/ncbl.js and module/scrobble_v1.js at 4a7d557326d16d2f1e5172ee69c9471eb3a50173.
 * Upstream attributes this code to folltoshe/netease-report-listen-song (AGPLv3).
 * Adapted to Kotlin and modified for this app on 2026-11-13; distributed as AGPL-3.0-only.
 * Attribution and license text: THIRD_PARTY_NOTICES.md and LICENSES/AGPL-3.0-only.txt.
 * Uses gzip, the original Node version's fallback; zstd is not required by this wire format.
 * ChaCha20/RSA/randomness are delegated to the platform's mature [NcmCrypto] provider. Although
 * this legacy protocol resets ChaCha20 at every frame, that behavior must not be "fixed" locally:
 * it is the service's wire format, not a construction to reuse for any other purpose.
 *
 * This builder never reads stored credentials, sends a request, logs, retries, or catches a
 * cancellation. The caller supplies its already-bound credential snapshot explicitly.
 */
class NcmNcblBuilder(private val crypto: NcmCrypto) {
    fun prepare(params: NcmNcblParams, cookie: String, nowMillis: Long): NcmNcblPrepared {
        require(nowMillis >= 0) { "Invalid scrobble timestamp" }
        val ctx = context(parseCookie(cookie), nowMillis)
        require(ctx.auth.token.isNotEmpty()) { "Missing scrobble authentication token" }
        val header = cookieHeader(ctx)
        return NcmNcblPrepared(
            context = ctx,
            metaJson = metaJson(ctx),
            cookieHeader = header,
            plvRecord = NcmNcblRecords.record(nowMillis / 1000, "_plv", NcmNcblRecords.plv(ctx, params, nowMillis)),
            pldRecord = NcmNcblRecords.record(nowMillis / 1000, "_pld", NcmNcblRecords.pld(ctx, params, nowMillis)),
        )
    }

    /** Build one upload only. The caller owns PLV-before-PLD and at-most-once dispatch. */
    fun upload(prepared: NcmNcblPrepared, record: String): NcmNcblUpload {
        require(record == prepared.plvRecord || record == prepared.pldRecord) { "Unknown scrobble record" }
        val payload = encrypt(prepared.metaJson.encodeToByteArray(), record.encodeToByteArray())
        val multipart = multipart(payload)
        val version = prepared.context.app.version
        requireHeaderValue(version)
        return NcmNcblUpload(
            url = UPLOAD_URL,
            headers = linkedMapOf(
                "Content-Type" to "multipart/form-data; boundary=${multipart.boundary}",
                "Referer" to "https://music.163.com/di",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/$version",
                "Accept-Encoding" to "gzip,deflate",
                "Accept-Language" to "zh-CN,zh;q=0.8",
                "Cookie" to prepared.cookieHeader,
            ),
            body = multipart.body,
            fileName = multipart.fileName,
            payloadSize = payload.size,
        )
    }

    fun encrypt(meta: ByteArray, body: ByteArray, options: NcmNcblOptions = NcmNcblOptions()): ByteArray {
        require(body.size <= MAX_UPLOAD_BYTES) { "NCBL body exceeds upload limit" }
        return encryptCompressed(meta, NcmCompression.gzip(body), options)
    }

    /** Separate from gzip so raw, immutable upstream frame vectors survive platform gzip headers. */
    internal fun encryptCompressed(meta: ByteArray, compressed: ByteArray, options: NcmNcblOptions): ByteArray {
        require(options.maxFrame in 1..0xffff) { "Invalid NCBL frame size" }
        require(meta.size <= 0xffff - HEADER_FIXED_LENGTH - META_HEADER_LENGTH) { "NCBL metadata exceeds header limit" }
        val keyA = (options.keyA ?: crypto.secureRandom(32)).copyOf()
        require(keyA.size == 32) { "Invalid NCBL key length" }
        if ((keyA[0].toInt() and 0xff) >= 0xa3) keyA[0] = 0xa2.toByte()
        val keyB = crypto.rsaModPow(keyA, byteArrayOf(1, 0, 1), RSA_MODULUS.copyOf(), 32)
        require(keyB.size == 32) { "Invalid NCBL wrapped key length" }
        val uuid = (options.uuid ?: randomUuid()).copyOf()
        require(uuid.size == 16) { "Invalid NCBL UUID length" }
        val nonce = uuid.copyOfRange(0, 12)
        val counter = readUInt32Le(uuid, 12) shr 2
        val baseSeq = options.baseSeq ?: readUInt16Le(crypto.secureRandom(2)).toUInt()
        val headerLength = HEADER_FIXED_LENGTH + META_HEADER_LENGTH + meta.size
        val frameCount = maxOf(1, (compressed.size.toLong() + options.maxFrame - 1) / options.maxFrame).toInt()
        val trailingLength = compressed.size.toLong() + frameCount * FRAME_HEADER_LENGTH.toLong()
        val size = headerLength + trailingLength
        require(size <= MAX_UPLOAD_BYTES) { "NCBL payload exceeds upload limit" }
        val result = ByteArray(size.toInt())
        byteArrayOf(0x4e, 0x43, 0x42, 0x4c).copyInto(result)
        writeUInt32Le(result, 4, 3u)
        writeUInt16Le(result, 8, headerLength)
        uuid.copyInto(result, 10)
        keyB.copyInto(result, 26)
        writeUInt32Le(result, 58, baseSeq)
        writeUInt32Le(result, 62, baseSeq + frameCount.toUInt() - 1u)
        writeUInt32Le(result, 66, trailingLength.toUInt())
        writeUInt16Le(result, 70, 0x4343)
        writeUInt16Le(result, 72, meta.size)
        val metaCipher = crypto.chacha20(meta, keyB, nonce, counter)
        require(metaCipher.size == meta.size) { "Invalid NCBL metadata cipher length" }
        metaCipher.copyInto(result, 74)
        var offset = headerLength
        repeat(frameCount) { frame ->
            val from = frame * options.maxFrame
            val length = minOf(options.maxFrame, compressed.size - from)
            val cipher = crypto.chacha20(compressed.copyOfRange(from, from + length), keyA, nonce, counter)
            require(cipher.size == length) { "Invalid NCBL frame cipher length" }
            writeUInt16Le(result, offset, length)
            writeUInt32Le(result, offset + 2, baseSeq + frame.toUInt())
            cipher.copyInto(result, offset + FRAME_HEADER_LENGTH)
            offset += FRAME_HEADER_LENGTH + cipher.size
        }
        return result
    }

    /** Multipart framing matches upstream, including the inner multipart/form-data content type. */
    fun multipart(
        payload: ByteArray,
        boundary: String = randomUuid().toHex(),
        fileName: String = "op_${randomBelow(90000) + 10000}_0_${randomBelow(0xffff_ffffL) + 1}",
    ): NcmNcblMultipart {
        require(boundary.matches(Regex("[A-Za-z0-9_-]{1,70}"))) { "Invalid NCBL multipart boundary" }
        require(fileName.matches(Regex("op_[0-9]{5}_0_[1-9][0-9]{0,9}"))) { "Invalid NCBL multipart file name" }
        val prefix = ("--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
            "Content-Type: multipart/form-data\r\n\r\n").encodeToByteArray()
        val suffix = "\r\n--$boundary--\r\n".encodeToByteArray()
        val size = prefix.size.toLong() + payload.size + suffix.size
        require(size <= MAX_UPLOAD_BYTES) { "NCBL multipart exceeds upload limit" }
        val body = ByteArray(size.toInt())
        prefix.copyInto(body)
        payload.copyInto(body, prefix.size)
        suffix.copyInto(body, prefix.size + payload.size)
        return NcmNcblMultipart(boundary, fileName, body)
    }

    /** No URL decoding: '+', '%' and '=' are literal credential bytes, not a form value. */
    fun parseCookie(cookie: String): Map<String, String> = buildMap {
        cookie.split(';').forEach { part ->
            val index = part.indexOf('=')
            if (index > 0) {
                val name = part.substring(0, index).trim()
                if (name.isNotEmpty()) put(name, part.substring(index + 1).trim())
            }
        }
    }

    fun context(cookie: Map<String, String>, nowMillis: Long): NcmNcblContext {
        fun value(name: String, fallback: String = "") = cookie[name].orEmpty().ifEmpty { fallback }
        return NcmNcblContext(
            app = NcmNcblApp(
                id = value("appid"),
                nsm = value("WEVNSM", "1.0.0"),
                cid = value("WNMCID").ifEmpty { "${crypto.secureRandom(3).toHex()}.$nowMillis.01.0" },
                channel = value("channel", "netease"),
                version = value("appver", "3.1.35"),
                versionCode = value("versioncode", "205293"),
                buildCode = value("buildver"),
            ),
            device = NcmNcblDevice(
                id = value("deviceId", value("sDeviceId")),
                ti = value("NMTID"),
                sign = value("clientSign"),
                model = value("mode", value("mobilename")),
                nnid = value("_ntes_nnid", ","),
                nuid = value("_ntes_nuid"),
                csrf = value("__csrf"),
                // scrobble_v1 overrides only its local context; never mutate the stored Cookie.
                systemType = "pc",
                systemVersion = value("osver", "Microsoft-Windows-10-Professional-build-19045-64bit"),
            ),
            auth = NcmNcblAuth(
                id = value("uid"),
                token = value("MUSIC_U"),
                sessionId = value("JSESSIONID-WYYY"),
                vipType = value("vipType"),
            ),
            startTime = nowMillis,
            processId = (randomBelow(90000) + 10000).toInt(),
        )
    }

    fun cookieHeader(ctx: NcmNcblContext): String {
        val fields = contextFields(ctx)
        fields.forEach { (_, value) ->
            requireHeaderValue(value)
            require(';' !in value) { "Invalid NCBL Cookie value" }
        }
        val parts = fields.map { (key, value) -> "$key=$value" }.toMutableList()
        parts.add(6, "__remember_me=true")
        return parts.joinToString("; ")
    }

    fun metaJson(ctx: NcmNcblContext): String = buildJsonObject {
        contextFields(ctx).forEach { (key, value) -> put(key, value) }
    }.toString()

    private fun contextFields(ctx: NcmNcblContext) = listOf(
        "JSESSIONID-WYYY" to ctx.auth.sessionId,
        "MUSIC_U" to ctx.auth.token,
        "NMTID" to ctx.device.ti,
        "WEVNSM" to ctx.app.nsm,
        "WNMCID" to ctx.app.cid,
        "__csrf" to ctx.device.csrf,
        "_iuqxldmzr_" to "33",
        "_ntes_nnid" to ctx.device.nnid,
        "_ntes_nuid" to ctx.device.nuid,
        "appver" to "${ctx.app.version}.${ctx.app.versionCode}",
        "channel" to ctx.app.channel,
        "clientSign" to ctx.device.sign,
        "deviceId" to ctx.device.id,
        "mode" to ctx.device.model,
        "ntes_kaola_ad" to "1",
        "os" to ctx.device.systemType,
        "osver" to ctx.device.systemVersion,
    )

    private fun randomUuid(): ByteArray = crypto.secureRandom(16).also { uuid ->
        require(uuid.size == 16) { "Invalid NCBL random UUID length" }
        uuid[6] = ((uuid[6].toInt() and 0x0f) or 0x40).toByte()
        uuid[8] = ((uuid[8].toInt() and 0x3f) or 0x80).toByte()
    }

    private fun randomBelow(bound: Long): Long {
        val limit = 0x1_0000_0000L - 0x1_0000_0000L % bound
        while (true) {
            val value = readUInt32Le(crypto.secureRandom(4), 0).toLong()
            if (value < limit) return value % bound
        }
    }

    private fun requireHeaderValue(value: String) {
        require(value.none { it.code < 0x20 || it.code == 0x7f }) { "Invalid NCBL header value" }
    }

    private fun readUInt16Le(bytes: ByteArray): Int {
        require(bytes.size == 2) { "Invalid NCBL sequence randomness length" }
        return (bytes[0].toInt() and 0xff) or ((bytes[1].toInt() and 0xff) shl 8)
    }

    private fun readUInt32Le(bytes: ByteArray, offset: Int): UInt =
        (bytes[offset].toUInt() and 0xffu) or
            ((bytes[offset + 1].toUInt() and 0xffu) shl 8) or
            ((bytes[offset + 2].toUInt() and 0xffu) shl 16) or
            ((bytes[offset + 3].toUInt() and 0xffu) shl 24)

    private fun writeUInt16Le(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeUInt32Le(bytes: ByteArray, offset: Int, value: UInt) {
        repeat(4) { index -> bytes[offset + index] = (value shr (index * 8)).toByte() }
    }

    companion object {
        const val UPLOAD_URL = "https://clientlog3.music.163.com/api/clientlog/encrypt/upload?multiupload=true"
        const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024
        private const val HEADER_FIXED_LENGTH = 70
        private const val META_HEADER_LENGTH = 4
        private const val FRAME_HEADER_LENGTH = 6
        private val RSA_MODULUS = "fd90bd466ff9bc8a3fec2fbcf263b90d5c564879fa5d7aab89b31c1d5cb4139d"
            .hexToBytes()
    }
}

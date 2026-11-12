package com.leejlredstar.redefinencm.kmp.data.api.nativecrypto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/** Wire formats ported from api-enhanced util/crypto.js at 4a7d557326d16d2f1e5172ee69c9471eb3a50173. */
class NcmProtocolCrypto(private val crypto: NcmCrypto = platformNcmCrypto()) {
    /** [jsonUtf8] is the exact JSON.stringify-compatible request document; it is not reserialized. */
    fun weapi(jsonUtf8: ByteArray): WeapiEnvelope {
        val secret = randomBase62(16)
        val first = Base64.Default.encode(crypto.aesCbcEncrypt(jsonUtf8, WEAPI_KEY, WEAPI_IV))
        val params = Base64.Default.encode(crypto.aesCbcEncrypt(first.encodeToByteArray(), secret, WEAPI_IV))
        // node-forge's NONE left-pads the reversed 16-byte secret to the RSA modulus width.
        val block = ByteArray(128)
        secret.reversedArray().copyInto(block, block.size - secret.size)
        return WeapiEnvelope(params, crypto.rsaRawEncrypt(block, WEAPI_PUBLIC_KEY).toHex())
    }

    fun eapi(path: String, jsonUtf8: ByteArray): EapiEnvelope {
        val pathBytes = path.encodeToByteArray()
        val digest = crypto.md5("nobody".encodeToByteArray() + pathBytes + "use".encodeToByteArray() +
            jsonUtf8 + "md5forencrypt".encodeToByteArray()).toHex().encodeToByteArray()
        val framed = pathBytes + EAPI_DELIMITER + jsonUtf8 + EAPI_DELIMITER + digest
        return EapiEnvelope(crypto.aesEcbEncrypt(framed, EAPI_KEY).toHex(uppercase = true))
    }

    /**
     * [plaintextUtf8] is [buildXeapiPlaintext]'s UTF-8 JSON bytes, not the endpoint's JSON body.
     * [sessionKey] is the UTF-8 byte representation of upstream options.sessionKey when supplied.
     * C is unpadded base64url; S and R are standard padded base64. No compression occurs here.
     */
    fun xeapi(
        plaintextUtf8: ByteArray,
        state: XeapiPublicKeyState,
        os: String = "android",
        sessionKey: ByteArray? = null,
        sessionId: String = "",
    ): XeapiEnvelope {
        val peerKey = Base64.Default.decode(state.publicKey)
        require(peerKey.size == 32) { "Invalid xeapi peer public key" }
        require(state.version.isNotEmpty()) { "Missing xeapi key version" }
        val dynamicKey = sessionKey?.copyOf() ?: randomBytes(16)
        checkAesKey(dynamicKey)
        val inner = crypto.aesEcbEncrypt(plaintextUtf8, XEAPI_STATIC_KEY)
        val c = crypto.aesEcbEncrypt(midTransform(inner), dynamicKey)
        val ephemeralPrivate = randomBytes(32)
        val ephemeralPublic = crypto.x25519PublicKey(ephemeralPrivate)
        val sharedSecret = crypto.x25519SharedSecret(ephemeralPrivate, peerKey)
        val aesKey = deriveXeapiAesKey(sharedSecret, ephemeralPublic)
        val nonce = randomBytes(12)
        val sPlaintext = "${Base64.Default.encode(dynamicKey)}|$os|${state.sk}".encodeToByteArray()
        val s = ephemeralPublic + nonce + crypto.aesGcmEncrypt(sPlaintext, aesKey, nonce)
        val r = crypto.aesEcbEncrypt(
            "${state.version}|${if (sessionKey != null) sessionId else ""}".encodeToByteArray(),
            XEAPI_STATIC_KEY,
        )
        return XeapiEnvelope(
            C = Base64.UrlSafe.encode(c).trimEnd('='),
            S = Base64.Default.encode(s),
            R = Base64.Default.encode(r),
        )
    }

    fun xeapiSign(timestamp: Long, nonce: String): String = Base64.Default.encode(
        // The upstream HMAC key is the literal base64-looking string, NOT its decoded bytes.
        crypto.hmacSha256("$timestamp$nonce".encodeToByteArray(), XEAPI_SIGN_KEY),
    )

    /** Returns the decrypted registration JSON bytes. Merge/validate sk in the transport layer. */
    fun decryptXeapiPublicKey(encryptedBase64: String): ByteArray =
        crypto.aesEcbDecrypt(Base64.Default.decode(encryptedBase64), XEAPI_STATIC_KEY)

    /** Returns plaintext bytes, possibly gzip. Callers own decompression and JSON parsing. */
    fun decryptEapiResponse(ciphertext: ByteArray): ByteArray = crypto.aesEcbDecrypt(ciphertext, EAPI_KEY)

    /** xeapi response encryption uses eapiKey, NOT the request's random dynamicKey. */
    fun decryptXeapiResponse(ciphertext: ByteArray): ByteArray = decryptEapiResponse(ciphertext)

    private fun deriveXeapiAesKey(sharedSecret: ByteArray, ephemeralPublic: ByteArray): ByteArray {
        // This is upstream's two HMAC calls (HKDF extract and the first expand block), not a new KDF.
        val prk = crypto.hmacSha256(if (sharedSecret.isEmpty()) ByteArray(32) else sharedSecret, ByteArray(32))
        return crypto.hmacSha256(ephemeralPublic + byteArrayOf(1), prk).copyOf(16)
    }

    private fun midTransform(ciphertext: ByteArray): ByteArray {
        val mask = randomBytes(16)
        val xored = ByteArray(ciphertext.size) { index ->
            (ciphertext[index].toInt() xor mask[index and 15].toInt()).toByte()
        }
        val rotation = if (xored.isEmpty()) 0 else (mask[0].toInt() and 15) % xored.size
        return mask + xored.copyOfRange(rotation, xored.size) + xored.copyOfRange(0, rotation)
    }

    private fun randomBase62(size: Int): ByteArray {
        val result = ByteArray(size)
        var written = 0
        // Secure rejection sampling replaces upstream's Math.random; every generated key is still
        // a protocol-compatible 16-character base62 string, without modulo bias or weak randomness.
        while (written < size) {
            for (byte in randomBytes(size - written)) {
                val value = byte.toInt() and 255
                if (value < 248) result[written++] = BASE62[value % BASE62.length].code.toByte()
            }
        }
        return result
    }

    private fun randomBytes(size: Int): ByteArray = crypto.secureRandom(size).also {
        check(it.size == size) { "Cryptographic random source returned an incorrect byte count" }
    }

    companion object {
        /**
         * Mirrors upstream URLSearchParams over already String-coerced [data], retaining insertion
         * order, excluding e_r from the body and appending e_r=true to the raw URI query. [uri]
         * must be the already-encoded request URI (as supplied by the native transport).
         */
        fun buildXeapiPlaintext(
            uri: String,
            data: Map<String, String>?,
            method: String = "POST",
            contentType: String = "application/x-www-form-urlencoded;charset=utf-8",
        ): ByteArray {
            val activeContentType = contentType.ifEmpty { "application/x-www-form-urlencoded;charset=utf-8" }
            val mediaType = activeContentType.substringBefore(';').lowercase()
            val activeMethod = method.ifEmpty { "POST" }.uppercase()
            val query = uri.substringBefore('#').substringAfter('?', "")
            val body = data?.entries?.filter { it.key != "e_r" }?.joinToString("&") {
                "${formEncode(it.key)}=${formEncode(it.value)}"
            }.orEmpty()
            return buildJsonObject {
                if (mediaType != "application/x-www-form-urlencoded") put("contentType", activeContentType)
                if (activeMethod != "POST") put("method", activeMethod)
                // Upstream inserts queryString before content/body when the URI had a query.
                if (query.isNotEmpty()) put("queryString", "$query&e_r=true")
                if (body.isNotEmpty()) {
                    if (mediaType == "application/x-www-form-urlencoded") put("content", body)
                    else put("body", Base64.Default.encode(body.encodeToByteArray()))
                }
                if (query.isEmpty()) put("queryString", "e_r=true")
            }.toString().encodeToByteArray()
        }

        private fun formEncode(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val unsigned = byte.toInt() and 255
                when {
                    unsigned in 65..90 || unsigned in 97..122 || unsigned in 48..57 ||
                        unsigned == 42 || unsigned == 45 || unsigned == 46 || unsigned == 95 -> append(unsigned.toChar())
                    unsigned == 32 -> append('+')
                    else -> {
                        append('%')
                        append(HEX_UPPER[unsigned ushr 4])
                        append(HEX_UPPER[unsigned and 15])
                    }
                }
            }
        }

        private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private const val HEX_UPPER = "0123456789ABCDEF"
        private val WEAPI_IV = "0102030405060708".encodeToByteArray()
        private val WEAPI_KEY = "0CoJUm6Qyw8W8jud".encodeToByteArray()
        private val WEAPI_PUBLIC_KEY = Base64.Default.decode(
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6s" +
                "XqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgT" +
                "z+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB",
        )
        private val EAPI_KEY = "e82ckenh8dichen8".encodeToByteArray()
        private val EAPI_DELIMITER = "-36cd479b6b5-".encodeToByteArray()
        private val XEAPI_STATIC_KEY = "ab1d5a430f6bb04a3f01e81ddd72bd916d5ce591248ac128714806d7f8fb1b84".hexToBytes()
        private val XEAPI_SIGN_KEY =
            "mUHCwVNWJbunMqAHf5MImuirT6plvs6VSFW62MGHstFQxhBGdEoIhLItH3djc4+FB/OKty3+lL2rGeoFBpVe5g==".encodeToByteArray()
    }
}

data class WeapiEnvelope(val params: String, val encSecKey: String)
data class EapiEnvelope(val params: String)
data class XeapiEnvelope(val C: String, val S: String, val R: String)
data class XeapiPublicKeyState(val publicKey: String, val version: String, val sk: String = "")

internal fun ByteArray.toHex(uppercase: Boolean = false): String {
    val alphabet = if (uppercase) "0123456789ABCDEF" else "0123456789abcdef"
    return buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 255
            append(alphabet[value ushr 4])
            append(alphabet[value and 15])
        }
    }
}

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Invalid hexadecimal length" }
    return ByteArray(length / 2) { index ->
        val high = this[index * 2].digitToIntOrNull(16) ?: error("Invalid hexadecimal digit")
        val low = this[index * 2 + 1].digitToIntOrNull(16) ?: error("Invalid hexadecimal digit")
        ((high shl 4) or low).toByte()
    }
}

package com.leejlredstar.redefinencm.kmp.data.api.nativecrypto

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Expected bytes are generated independently with Node's built-in crypto by generate-vectors.cjs.
 * The xeapi C/S/R, HMAC and response fixtures are also checked against the pinned upstream JS in
 * that script. The fake random source is ONLY in these tests; every cipher is the real provider.
 */
class NcmProtocolCryptoTest {
    private val native = platformNcmCrypto()
    private val json = """{"id":347230,"text":"网易云 🎵","empty":"","nested":{"a":true}}""".encodeToByteArray()
    private val path = "/api/song/enhance/player/url/v1"
    private val state = XeapiPublicKeyState("3p7bfXt9wbTTW2HC7OQ1Nz+DQ8hbeGdNrfx+FG+IK08=", "3", "server-token|test")
    private val xeapiPlaintext = """{"queryString":"existing=a%20b&e_r=true","content":"ids=%5B347230%5D&level=lossless&keyword=%E7%BD%91%E6%98%93%E4%BA%91+%2B%2F%7E*"}""".encodeToByteArray()

    @Test
    fun weapiFixedBase62SecretMatchesNodeAesAndRawRsa() {
        // Bytes 0..15 become a..p under secure base62 sampling. No Math.random in production.
        val random = SequenceRandomCrypto(native)
        val actual = NcmProtocolCrypto(random).weapi(json)
        assertEquals(16, random.consumed)
        assertEquals(
            "UBTfBjrxUGbzMmwkgyz8QSa2gG7FsNzziOUouWpF1PWEp93CnfYhJGC5Bem+vtRvEadSoPkdNCsSuKgTU54rcSgkLsJ+VBLpoDux82xWUAeHMAIPsR30N5QmrZhUsWNeJRj7YF9ee4bDpT1eGRqy5Q==",
            actual.params,
        )
        assertEquals(
            "d15a1683c992095d0c234c19966605c5c5964911268bbeda8cb8d08d834913e59d53b32358903a121b5fca784c1f5ae44951fd02524df58ecc98e52cc7cf8689b42c2e93ddf05b0592512d87f5960467e2f086c018849d76014d323500e30f13ef4cafbb0cf5a66731a3f1776c75ca35d0062dac70a3e33245afabcf47938487",
            actual.encSecKey,
        )
        assertEquals(256, actual.encSecKey.length)
    }

    @Test
    fun eapiDigestFramingAndUppercaseCiphertextMatchNode() {
        val actual = NcmProtocolCrypto(native).eapi(path, json)
        assertEquals(
            "FA90B329E9614F79E79598F37DC2EDB487F00D1BC4C9B24CD57E6C318B9073569338432CD7D98D1A3626E997A2C53121D0925231A9C84B3D1DB28405312D13700F598643C7E4CD0915B13E1F269F11ED92516A6F1013E262AA3578CB9F9AC840C7D1166E7EE5CEB4BE24712F29FA9B94C148275189953E59863888849E16EFC4342F637ADAB264C981BA4E91182F32B179EC6403DCAD852D2E80E6EAC2B0C2C4",
            actual.params,
        )
        val frame = native.aesEcbDecrypt(actual.params.hexToBytes(), "e82ckenh8dichen8".encodeToByteArray())
        val digest = native.md5("nobody".encodeToByteArray() + path.encodeToByteArray() + "use".encodeToByteArray() + json + "md5forencrypt".encodeToByteArray()).toHex()
        assertContentEquals(
            path.encodeToByteArray() + "-36cd479b6b5-".encodeToByteArray() + json + "-36cd479b6b5-$digest".encodeToByteArray(),
            frame,
        )
        assertEquals(
            "4DC723619A991588865191FD2F319BAD89A665F3FF2B75B10678911D4E9910E4690E5F68E0095E53773148F1312770C2786A0951D5B32887C8AE362CEF057841F3A83A42DA35956C149F335E924490D4",
            NcmProtocolCrypto(native).eapi("/api/test", "{}".encodeToByteArray()).params,
        )
    }

    @Test
    fun xeapiFullEnvelopeMatchesPinnedUpstreamWithFixedRandomBytes() {
        // Precisely: dynamic key 00..0f, mask 10..1f, ephemeral seed 20..3f, GCM IV 40..4b.
        val random = SequenceRandomCrypto(native)
        val actual = NcmProtocolCrypto(random).xeapi(xeapiPlaintext, state)
        assertEquals(76, random.consumed)
        assertEquals(
            "B_7vdOHVA26QDu4RjpSSk8jQ_tfM-lp7eQbtYFLz2nshsrCjAeMzkMkox_IGbG3YSTzIISQ5taJRB9fd1et0FcjP_7RC3_0zewQwcNnk_uCbubfV6rbGsTG_bXnZlaZYSCV4qqk4uE7RMSorxFFiN3Pg3hdnGXwJDW1V-zLj7nKxUnGBCbL7BB_c-8tb6E9CxxtpGNPlcDxsILwc_2TAGZVPZPLk6G6e7oLSAhZoSJk",
            actual.C,
        )
        assertEquals(
            "NYBy1jZYgNGu6jKa35EhODhR7SGijjt16WXQ0s0WYlRAQUJDREVGR0hJSkuBKAz1MsO+QnwiJ05KzGgmwsM5mTXQCaoEueaUYhLtw+qXHB5Y8GJlgRLQogd7Gm11fvCajKwFRpt5+PLLCkTKrZE=",
            actual.S,
        )
        assertEquals("nS2NW7bNgpyhghYVlw9EyQ==", actual.R)
        assertTrue('=' !in actual.C && '+' !in actual.C && '/' !in actual.C)
        verifyS(actual.S, "AAECAwQFBgcICQoLDA0ODw==|android|server-token|test")
    }

    @Test
    fun xeapiSessionEnvelopeMatchesPinnedUpstreamAndConsumesNoDynamicKeyRandom() {
        // With session: mask 00..0f, ephemeral seed 10..2f, GCM IV 30..3b.
        val random = SequenceRandomCrypto(native)
        val actual = NcmProtocolCrypto(random).xeapi(
            xeapiPlaintext, state, os = "pc", sessionKey = "0123456789abcdef".encodeToByteArray(), sessionId = "session-123",
        )
        assertEquals(60, random.consumed)
        assertEquals(
            "oHmZ8OK_vhb5lZPphKRJtyG7HfVMjkSTdWHyQaS5sJ8kVweWS3RKQE8XSShrfzg1e59eX-LQ_5zxYtPb1KEfiYniyL9_ojutsy8uDqn7zt0NOr6KO0QHZPAX_va-AMhFqIlfh1vG8s8S1FI2sGicfZ_e8I5nvd8ai5QkwdRM5FphEMFVwH59VtuTTtW36FOk1i8XWQZIkv0sC5yJq1nWtjdyIuBhqSTFkc2cJ-oWPtQ",
            actual.C,
        )
        assertEquals(
            "2J47rXlDfb7Z+ENBgwT0YP8Fx/6B/kqVd6gEy5Nn/2YwMTIzNDU2Nzg5OjtmTEZmuUpA6DowqInhjS+ahyAedzN1EtVHdhnF51jTTEbojopTI8TD5ouAKioetqEDeNHxatpa6tQK3o51",
            actual.S,
        )
        assertEquals("hsYqRGmVMdYKP1AlyxS4kQ==", actual.R)
        verifyS(actual.S, "MDEyMzQ1Njc4OWFiY2RlZg==|pc|server-token|test")
    }

    @Test
    fun xeapiSignUsesLiteralStringKeyNotBase64DecodedKey() {
        assertEquals(
            "aioMQyzaoEVwdnsOld3vNY7lwh+LNRK1Hbzl7f7vziw=",
            NcmProtocolCrypto(native).xeapiSign(1_700_000_000_123, "nonce-123"),
        )
    }

    @Test
    fun registrationAndResponsesDecryptToExactBytesWithoutDecompression() {
        val protocol = NcmProtocolCrypto(native)
        assertEquals(
            """{"publicKey":"3p7bfXt9wbTTW2HC7OQ1Nz+DQ8hbeGdNrfx+FG+IK08=","version":"3","sk":"server-token|test"}""",
            protocol.decryptXeapiPublicKey("Zl6uYwh9eqd1eYl5OpQdls7Z8dU55lGeZb79cEp44uavWK3PYj6f8JyFf5T8wMYVhGr+DBkcJE1zvsUt2OupzhC8UVYehGiv7ZII82fx9tydM47lQBa2EvgBSXwp6pflSvYoCXZf/TLXdgvhvj4mMw==").decodeToString(),
        )
        val plain = """{"code":200,"message":"ok"}""".encodeToByteArray()
        val ciphertext = "4dc12a14eca8f30f33123e619c7b9c893b1ae1dd553e6d9cc22204a313ce169d".hexToBytes()
        assertContentEquals(plain, protocol.decryptEapiResponse(ciphertext))
        assertContentEquals(plain, protocol.decryptXeapiResponse(ciphertext))
        val compressedCiphertext = "52da83621fa2e4b846413e043a2baf55800378406ce4cc7db0675186b127d87c823e87a09cfdf1bfb5e1e2aff77674af".hexToBytes()
        assertContentEquals(
            "1f8b080000000000000aab564ace4f4955b2323230d051ca4d2d2e4e4c4f55b252cacf56aa050014e2cf951b000000".hexToBytes(),
            protocol.decryptXeapiResponse(compressedCiphertext),
        )
    }

    @Test
    fun base62RejectsBiasedTailAndProviderLengthErrorsAreNotIgnored() {
        val random = SequenceRandomCrypto(native, initialOffset = 248)
        // First 8 bytes ff-tail rejected, then bytes 00..0f give same secret as first test.
        val actual = NcmProtocolCrypto(random).weapi(json)
        assertEquals(24, random.consumed)
        assertTrue(actual.params.startsWith("UBTfBjrxUGbzMmwkgyz8QSa2"))
        val broken = object : NcmCrypto by native {
            override fun secureRandom(size: Int): ByteArray = ByteArray(size + 1)
        }
        assertFailsWith<IllegalStateException> { NcmProtocolCrypto(broken).weapi(json) }
    }

    @Test
    fun invalidKeyMaterialFailsRatherThanSubstitutingFakeCrypto() {
        val protocol = NcmProtocolCrypto(native)
        assertFailsWith<IllegalArgumentException> { protocol.xeapi(xeapiPlaintext, state.copy(publicKey = "AA==")) }
        assertFailsWith<IllegalArgumentException> { protocol.xeapi(xeapiPlaintext, state, sessionKey = ByteArray(15)) }
        assertFails { protocol.decryptXeapiResponse(ByteArray(16)) }
    }

    private fun verifyS(encodedS: String, expected: String) {
        val raw = Base64.Default.decode(encodedS)
        val publicKey = raw.copyOfRange(0, 32)
        val shared = native.x25519SharedSecret(
            "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb".hexToBytes(), publicKey,
        )
        val prk = native.hmacSha256(shared, ByteArray(32))
        val key = native.hmacSha256(publicKey + byteArrayOf(1), prk).copyOf(16)
        assertEquals(expected, native.aesGcmDecrypt(raw.copyOfRange(44, raw.size), key, raw.copyOfRange(32, 44)).decodeToString())
    }
}

/** Deterministic stream across calls, never used by production; all other methods delegate. */
private class SequenceRandomCrypto(private val native: NcmCrypto, private val initialOffset: Int = 0) : NcmCrypto by native {
    var consumed: Int = 0
        private set

    override fun secureRandom(size: Int): ByteArray = ByteArray(size) { (initialOffset + consumed++).toByte() }
}

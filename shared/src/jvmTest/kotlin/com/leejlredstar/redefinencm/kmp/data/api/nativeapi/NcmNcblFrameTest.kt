package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.NcmCrypto
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.platformNcmCrypto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NcmNcblFrameTest {
    private val crypto = platformNcmCrypto()
    private val builder = NcmNcblBuilder(crypto)

    @Test
    fun rawNcblVectorMatchesPinnedNodeWithGzipFallbackAndBuiltInCrypto() {
        // Generated from unmodified upstream util/ncbl.js in an offline VM. Its zlib import exposed
        // only gzipSync, axios threw on every call, and Node's built-in ChaCha20 independently
        // verified the first frame. Fixed raw gzip keeps the OS byte/header differences out.
        val originalKey = KEY.hexBytes()
        val uuid = UUID.hexBytes()
        val options = NcmNcblOptions(maxFrame = 13, keyA = originalKey, uuid = uuid, baseSeq = 0x1234u)
        val actual = builder.encryptCompressed(META.encodeToByteArray(), GZIP.hexBytes(), options)
        assertContentEquals(NCBL.hexBytes(), actual)
        assertContentEquals(KEY.hexBytes(), originalKey, "Key clamp must not mutate caller-owned test inputs")
        assertContentEquals(UUID.hexBytes(), uuid)
        assertEquals(3u, actual.uint32(4))
        assertEquals(107, actual.uint16(8))
        assertEquals(0x1234u, actual.uint32(58))
        assertEquals(0x1238u, actual.uint32(62))
        assertEquals(85u, actual.uint32(66))
        assertEquals(0x4343, actual.uint16(70))
        assertEquals(33, actual.uint16(72))
        assertContentEquals(WRAPPED_KEY.hexBytes(), actual.copyOfRange(26, 58))
    }

    @Test
    fun platformGzipIsUsedAndAllFramesDecodeToTheOriginalRecord() {
        val body = ("binary\u0000 record\u0001" + "repeat".repeat(300)).encodeToByteArray()
        val payload = builder.encrypt(
            META.encodeToByteArray(),
            body,
            NcmNcblOptions(maxFrame = 7, keyA = KEY.hexBytes(), uuid = UUID.hexBytes(), baseSeq = 1234u),
        )
        assertContentEquals(META.encodeToByteArray(), decryptMeta(payload))
        val compressed = decryptFrames(payload)
        assertEquals(0x1f, compressed[0].toInt() and 0xff)
        assertEquals(0x8b, compressed[1].toInt() and 0xff)
        assertContentEquals(body, NcmCompression.gunzip(compressed, body.size))
    }

    @Test
    fun emptyBodyStillHasOneGzipFrameAndExplicitZeroSequenceIsNotRandomized() {
        val payload = builder.encrypt(
            byteArrayOf(),
            byteArrayOf(),
            NcmNcblOptions(keyA = KEY.hexBytes(), uuid = UUID.hexBytes(), baseSeq = 0u),
        )
        assertEquals(74, payload.uint16(8))
        assertEquals(0u, payload.uint32(58))
        assertEquals(0u, payload.uint32(62))
        assertEquals(0, payload.uint16(72))
        assertContentEquals(byteArrayOf(), NcmCompression.gunzip(decryptFrames(payload), 0))
    }

    @Test
    fun emptyCompressedInputStillGetsOneZeroLengthFrame() {
        val payload = builder.encryptCompressed(
            byteArrayOf(),
            byteArrayOf(),
            NcmNcblOptions(keyA = KEY.hexBytes(), uuid = UUID.hexBytes(), baseSeq = 65535u),
        )
        assertEquals(80, payload.size)
        assertEquals(6u, payload.uint32(66))
        assertEquals(0, payload.uint16(74))
        assertEquals(65535u, payload.uint32(76))
        assertEquals(65535u, payload.uint32(62))
    }

    @Test
    fun frameSequencesWrapAsUnsigned32BitAndResetCipherCounterForEverySlice() {
        val compressed = ByteArray(128) { it.toByte() }
        val payload = builder.encryptCompressed(
            byteArrayOf(),
            compressed,
            NcmNcblOptions(maxFrame = 64, keyA = KEY.hexBytes(), uuid = UUID.hexBytes(), baseSeq = UInt.MAX_VALUE),
        )
        assertEquals(UInt.MAX_VALUE, payload.uint32(58))
        assertEquals(0u, payload.uint32(62))
        assertEquals(UInt.MAX_VALUE, payload.uint32(76))
        assertEquals(0u, payload.uint32(146))
        assertContentEquals(compressed, decryptFrames(payload))
        val firstCipher = payload.copyOfRange(80, 144)
        val secondCipher = payload.copyOfRange(150, 214)
        for (index in 0 until 64) {
            assertEquals(
                (compressed[index].toInt() xor firstCipher[index].toInt()) and 0xff,
                (compressed[index + 64].toInt() xor secondCipher[index].toInt()) and 0xff,
                "Upstream starts every frame with the same ChaCha20 key stream",
            )
        }
    }

    @Test
    fun defaultRandomUuidHasRfc4122VersionAndVariantBits() {
        val payload = builder.encrypt("{}".encodeToByteArray(), "fixture".encodeToByteArray())
        assertEquals(0x40, payload[10 + 6].toInt() and 0xf0)
        assertEquals(0x80, payload[10 + 8].toInt() and 0xc0)
        assertTrue(payload.uint32(58) <= 65535u)
    }

    @Test
    fun rejectsInvalidFrameInputsBeforeCallingCrypto() {
        val failCrypto = object : NcmCrypto by crypto {
            override fun secureRandom(size: Int): ByteArray = error("Invalid framing must fail before randomness")
        }
        val failBuilder = NcmNcblBuilder(failCrypto)
        listOf(0, -1, 65536).forEach { maxFrame ->
            assertFailsWith<IllegalArgumentException> {
                failBuilder.encryptCompressed(byteArrayOf(), byteArrayOf(), NcmNcblOptions(maxFrame = maxFrame))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            failBuilder.encryptCompressed(ByteArray(65462), byteArrayOf(), NcmNcblOptions())
        }
        assertFailsWith<IllegalArgumentException> {
            failBuilder.encryptCompressed(byteArrayOf(), byteArrayOf(), NcmNcblOptions(keyA = ByteArray(31)))
        }
        assertFailsWith<IllegalArgumentException> {
            builder.encryptCompressed(byteArrayOf(), byteArrayOf(), NcmNcblOptions(keyA = ByteArray(32), uuid = ByteArray(15)))
        }
    }

    @Test
    fun maximumHeaderFitsAndOversizeBodyOrMultipartIsRejected() {
        val payload = builder.encryptCompressed(
            ByteArray(65461),
            byteArrayOf(),
            NcmNcblOptions(keyA = KEY.hexBytes(), uuid = UUID.hexBytes(), baseSeq = 1u),
        )
        assertEquals(65535, payload.uint16(8))
        assertFailsWith<IllegalArgumentException> {
            builder.encrypt(byteArrayOf(), ByteArray(NcmNcblBuilder.MAX_UPLOAD_BYTES + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            builder.multipart(ByteArray(NcmNcblBuilder.MAX_UPLOAD_BYTES), "fixture", "op_12345_0_1")
        }
    }

    @Test
    fun uploadBuildsExactHostHeadersAndBinaryMultipartWithoutNetwork() {
        val prepared = builder.prepare(NcmNcblParams(42, 30), "MUSIC_U=fake+/%==; WNMCID=fixture", 1700000000123)
        val upload = builder.upload(prepared, prepared.plvRecord)
        assertEquals(NcmNcblBuilder.UPLOAD_URL, upload.url)
        assertEquals("https://music.163.com/di", upload.headers["Referer"])
        assertEquals("gzip,deflate", upload.headers["Accept-Encoding"])
        assertEquals("zh-CN,zh;q=0.8", upload.headers["Accept-Language"])
        assertEquals(prepared.cookieHeader, upload.headers["Cookie"])
        assertTrue(upload.headers.getValue("User-Agent").endsWith("NeteaseMusicDesktop/3.1.35"))
        assertTrue(upload.fileName.matches(Regex("op_[0-9]{5}_0_[1-9][0-9]{0,9}")))
        val boundary = upload.headers.getValue("Content-Type").substringAfter("boundary=")
        val prefix = ("--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"${upload.fileName}\"\r\n" +
            "Content-Type: multipart/form-data\r\n\r\n").encodeToByteArray()
        val footer = "\r\n--$boundary--\r\n".encodeToByteArray()
        assertContentEquals(prefix, upload.body.copyOfRange(0, prefix.size))
        assertContentEquals(footer, upload.body.copyOfRange(upload.body.size - footer.size, upload.body.size))
        assertEquals(upload.body.size - prefix.size - footer.size, upload.payloadSize)
        assertEquals("NCBL", upload.body.copyOfRange(prefix.size, prefix.size + 4).decodeToString())
        assertFalse(upload.toString().contains("fake+/%=="))
        assertFailsWith<IllegalArgumentException> { builder.upload(prepared, "not a prepared record") }
    }

    private fun decryptMeta(payload: ByteArray): ByteArray {
        val uuid = payload.copyOfRange(10, 26)
        return crypto.chacha20(
            payload.copyOfRange(74, payload.uint16(8)),
            payload.copyOfRange(26, 58),
            uuid.copyOfRange(0, 12),
            uuid.uint32(12) shr 2,
        )
    }

    private fun decryptFrames(payload: ByteArray): ByteArray {
        val uuid = payload.copyOfRange(10, 26)
        val keyA = KEY.hexBytes().also { it[0] = 0xa2.toByte() }
        val output = mutableListOf<ByteArray>()
        var offset = payload.uint16(8)
        var expectedSequence = payload.uint32(58)
        while (offset < payload.size) {
            val size = payload.uint16(offset)
            assertEquals(expectedSequence++, payload.uint32(offset + 2))
            output += crypto.chacha20(
                payload.copyOfRange(offset + 6, offset + 6 + size),
                keyA,
                uuid.copyOfRange(0, 12),
                uuid.uint32(12) shr 2,
            )
            offset += 6 + size
        }
        assertEquals(payload.size, offset)
        assertEquals(payload.uint32(62), expectedSequence - 1u)
        return output.fold(byteArrayOf()) { all, frame -> all + frame }
    }

    private fun String.hexBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.uint16(offset: Int) =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
    private fun ByteArray.uint32(offset: Int): UInt =
        (this[offset].toUInt() and 0xffu) or ((this[offset + 1].toUInt() and 0xffu) shl 8) or
            ((this[offset + 2].toUInt() and 0xffu) shl 16) or ((this[offset + 3].toUInt() and 0xffu) shl 24)

    companion object {
        private const val META = "{\"MUSIC_U\":\"fake+/%==\",\"os\":\"pc\"}"
        private const val KEY = "ff0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        private const val UUID = "000102030405460788090a0bf0ffffff"
        private const val GZIP = "1f8b080000000000000a3334378002c6f8829c32c66aa5cc14252b251323251da592ca8254252ba5e2fcbc74a55a00084bd71029000000"
        private const val WRAPPED_KEY = "c21630a828082641cd04e82bb86d1af821637cb9901731be17a2384c157894da"
        private const val NCBL = "4e43424c030000006b00000102030405460788090a0bf0ffffff" +
            "c21630a828082641cd04e82bb86d1af821637cb9901731be17a2384c157894da" +
            "3412000038120000550000004343210076a1764a539337b34aaa666c5de17925164d9613d0aa31fd0c9acc9fdfb1e559c7" +
            "0d00341200005a225b04139bfcb5f8d13b27c60d0035120000c5ab95fc9107ce73927ec407d4" +
            "0d00361200006e8c40273686592732595c36da0d0037120000e04bafb8673ea6b5f090df03d803003812000045a953"
    }
}

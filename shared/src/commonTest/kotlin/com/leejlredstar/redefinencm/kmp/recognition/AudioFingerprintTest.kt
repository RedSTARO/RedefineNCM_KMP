package com.leejlredstar.redefinencm.kmp.recognition

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AudioFingerprintTest {
    @Test
    fun rejectsAnythingOtherThanExactlyThreeSeconds() {
        assertFailsWith<IllegalArgumentException> {
            AudioFingerprint.generate(FloatArray(23_999))
        }
        assertFailsWith<IllegalArgumentException> {
            AudioFingerprint.generate(FloatArray(24_001))
        }
    }

    @Test
    fun fixedPcmProducesReferencePeakPositions() {
        val peaks = FingerprintExtractor.extract(fixedPcm())

        assertEquals(
            listOf(
                25 to 2,
                25 to 11,
                25 to 25,
                25 to 39,
                25 to 52,
                25 to 66,
                25 to 89,
                25 to 103,
                25 to 112,
                25 to 126,
            ),
            peaks.map { it.frequencyBin to it.timeFrame },
        )
        assertTrue(abs(peaks.first().amplitude - 3.0450659f) < 0.00001f)
    }

    @Test
    fun completeFingerprintIsDeterministic() {
        val samples = fixedPcm()

        val first = AudioFingerprint.generate(samples)
        val second = AudioFingerprint.generate(samples.copyOf())

        assertEquals(first, second)
        assertEquals(0, first.length % 4)
        assertTrue(first.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' })
    }

    @Test
    fun rawContainerUsesExpectedLittleEndianLayout() {
        val peaks = listOf(
            FingerprintPeak(frequencyBin = 25, timeFrame = 2, amplitude = 3.5f),
            FingerprintPeak(frequencyBin = 403, timeFrame = 7, amplitude = 1.25f),
        )

        val raw = AudioFingerprintCodec.buildRawFingerprint(
            durationSeconds = 3f,
            peaks = peaks,
        )

        assertEquals(79 + peaks.size * 12, raw.size)
        var offset = 0
        val firstVersionLength = raw.readUInt32LittleEndian(offset)
        offset += 4
        assertEquals(23, firstVersionLength)
        assertEquals("hyai_1.2.0_client_1.0.0", raw.decodeToString(offset, offset + 23))
        offset += 23
        assertContentEquals(ByteArray(8), raw.copyOfRange(offset, offset + 8))
        offset += 8
        assertEquals(3f.toBits(), raw.readUInt32LittleEndian(offset))
        offset += 4
        assertEquals("FPVER", raw.decodeToString(offset, offset + 5))
        offset += 5
        assertEquals(23, raw.readUInt32LittleEndian(offset))
        offset += 4
        assertEquals("hyai_1.2.0_client_1.0.0", raw.decodeToString(offset, offset + 23))
        offset += 23
        assertEquals("Peak", raw.decodeToString(offset, offset + 4))
        offset += 4
        assertEquals(2, raw.readUInt32LittleEndian(offset))
        offset += 4
        assertEquals(25, raw.readUInt32LittleEndian(offset))
        assertEquals(2, raw.readUInt32LittleEndian(offset + 4))
        assertEquals(3.5f.toBits(), raw.readUInt32LittleEndian(offset + 8))
        offset += 12
        assertEquals(403, raw.readUInt32LittleEndian(offset))
        assertEquals(7, raw.readUInt32LittleEndian(offset + 4))
        assertEquals(1.25f.toBits(), raw.readUInt32LittleEndian(offset + 8))
    }

    @Test
    fun storedZlibContainerRoundTripsAcrossBlockBoundary() {
        val source = ByteArray(70_000) { index -> (index * 37).toByte() }

        val compressed = AudioFingerprintCodec.zlibStore(source)

        assertContentEquals(source, inflateStoredZlib(compressed))
    }

    @Test
    fun aes128MatchesNistEcbVector() {
        val key = hexToBytes("000102030405060708090a0b0c0d0e0f")
        val plaintext = hexToBytes("00112233445566778899aabbccddeeff")
        val expectedFirstBlock = hexToBytes("69c4e0d86a7b0430d8cdb78070b4c55a")

        val encrypted = AudioFingerprintCodec.aes128EcbPkcs7Encrypt(plaintext, key)

        // PKCS#7 adds another block; the first block is the standard AES-128 ECB vector.
        assertEquals(32, encrypted.size)
        assertContentEquals(expectedFirstBlock, encrypted.copyOfRange(0, 16))
    }

    @Test
    fun base64MatchesRfc4648Vectors() {
        assertEquals("", AudioFingerprintCodec.encodeBase64(byteArrayOf()))
        assertEquals("Zg==", AudioFingerprintCodec.encodeBase64("f".encodeToByteArray()))
        assertEquals("Zm8=", AudioFingerprintCodec.encodeBase64("fo".encodeToByteArray()))
        assertEquals("Zm9v", AudioFingerprintCodec.encodeBase64("foo".encodeToByteArray()))
        assertEquals("Zm9vYmFy", AudioFingerprintCodec.encodeBase64("foobar".encodeToByteArray()))
    }

    private fun fixedPcm(): FloatArray =
        FloatArray(24_000) { index ->
            (((index * 73) % 65_536) - 32_768) / 32_768f
        }

    private fun inflateStoredZlib(input: ByteArray): ByteArray {
        assertTrue(input.size >= 11)
        val compressionMethodAndFlags = input[0].toInt() and 0xFF
        val additionalFlags = input[1].toInt() and 0xFF
        assertEquals(8, compressionMethodAndFlags and 0x0F)
        assertEquals(0, ((compressionMethodAndFlags shl 8) or additionalFlags) % 31)

        val output = ArrayList<Byte>()
        var offset = 2
        var isFinal = false
        while (!isFinal) {
            val header = input[offset++].toInt() and 0xFF
            isFinal = header and 1 == 1
            assertEquals(0, (header ushr 1) and 0x03)
            val length =
                (input[offset].toInt() and 0xFF) or
                    ((input[offset + 1].toInt() and 0xFF) shl 8)
            val invertedLength =
                (input[offset + 2].toInt() and 0xFF) or
                    ((input[offset + 3].toInt() and 0xFF) shl 8)
            offset += 4
            assertEquals(length xor 0xFFFF, invertedLength)
            repeat(length) { output += input[offset++] }
        }

        assertEquals(input.size - 4, offset)
        val expectedAdler =
            ((input[offset].toInt() and 0xFF) shl 24) or
                ((input[offset + 1].toInt() and 0xFF) shl 16) or
                ((input[offset + 2].toInt() and 0xFF) shl 8) or
                (input[offset + 3].toInt() and 0xFF)
        val result = output.toByteArray()
        assertEquals(expectedAdler, adler32(result))
        return result
    }

    private fun adler32(input: ByteArray): Int {
        var first = 1
        var second = 0
        for (byte in input) {
            first = (first + (byte.toInt() and 0xFF)) % 65_521
            second = (second + first) % 65_521
        }
        return (second shl 16) or first
    }

    private fun ByteArray.readUInt32LittleEndian(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun hexToBytes(value: String): ByteArray {
        require(value.length % 2 == 0)
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}

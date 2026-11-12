package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.getPlatform
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/** Native contract runs on iOS as well as JVM/Android; Web must explicitly reject native gzip. */
class NcmCompressionContractTest {
    @Test
    fun binaryRoundTripOrExplicitWebUnsupported() {
        if (!getPlatform().canSendCookieHeader) {
            assertFailsWith<UnsupportedOperationException> { NcmCompression.gzip(byteArrayOf()) }
            assertFailsWith<UnsupportedOperationException> { NcmCompression.gunzip(byteArrayOf()) }
            return
        }
        listOf(byteArrayOf(), ByteArray(32769) { it.toByte() }).forEach { bytes ->
            assertContentEquals(bytes, NcmCompression.gunzip(NcmCompression.gzip(bytes), bytes.size))
        }
    }

    @Test
    fun byteBudgetIncludesEveryConcatenatedMember() {
        if (!getPlatform().canSendCookieHeader) return
        val first = ByteArray(8193) { 65 }
        val second = ByteArray(8193) { 66 }
        val concatenated = NcmCompression.gzip(first) + NcmCompression.gzip(second)
        assertContentEquals(first + second, NcmCompression.gunzip(concatenated, first.size + second.size))
        assertFailsWith<NcmCompressionException> { NcmCompression.gunzip(concatenated, first.size) }
        assertFailsWith<NcmCompressionException> { NcmCompression.gunzip(concatenated, 0) }
        assertFailsWith<IllegalArgumentException> { NcmCompression.gunzip(concatenated, -1) }
        assertContentEquals(byteArrayOf(), NcmCompression.gunzip(NcmCompression.gzip(byteArrayOf()), 0))
    }

    @Test
    fun nativeDecodersAcceptTheSameRawNodeGzipVector() {
        if (!getPlatform().canSendCookieHeader) return
        val gzip = "1f8b080000000000000a3334378002c6f8829c32c66aa5cc14252b251323251da592ca8254252ba5e2fcbc74a55a00084bd71029000000"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val body = "1700000000\u0001_plv\u0001{\"id\":\"42\",\"type\":\"song\"}".encodeToByteArray()
        assertContentEquals(body, NcmCompression.gunzip(gzip, body.size))
        assertFailsWith<NcmCompressionException> { NcmCompression.gunzip(gzip, body.size - 1) }
    }

    @Test
    fun corruptOrTruncatedInputNeverReturnsPartialOutput() {
        if (!getPlatform().canSendCookieHeader) return
        val gzip = NcmCompression.gzip(ByteArray(32769) { it.toByte() })
        val corrupt = gzip.copyOf().also { it[it.size - 8] = (it[it.size - 8].toInt() xor 1).toByte() }
        listOf(byteArrayOf(), byteArrayOf(1, 2, 3), gzip.copyOf(gzip.size - 1), corrupt).forEach { bytes ->
            assertFailsWith<NcmCompressionException> { NcmCompression.gunzip(bytes) }
        }
    }
}

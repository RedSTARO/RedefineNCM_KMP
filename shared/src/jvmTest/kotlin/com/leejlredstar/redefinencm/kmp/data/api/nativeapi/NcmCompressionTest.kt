package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class NcmCompressionTest {
    @Test
    fun roundTripsEmptyBinaryAndMultiBufferData() {
        listOf(
            byteArrayOf(),
            byteArrayOf(0, 1, 2, -1, 13, 10),
            ByteArray(200_000) { (it * 31).toByte() },
        ).forEach { input ->
            assertContentEquals(input, NcmCompression.gunzip(NcmCompression.gzip(input)))
        }
    }

    @Test
    fun gunzipMatchesRawNodeBuiltInGzipVector() {
        // Node v26.2.0 zlib.gzipSync, emitted offline with no service or installed npm dependencies.
        val gzip = "1f8b080000000000000a3334378002c6f8829c32c66aa5cc14252b251323251da592ca8254252ba5e2fcbc74a55a00084bd71029000000"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertContentEquals(
            "1700000000\u0001_plv\u0001{\"id\":\"42\",\"type\":\"song\"}".encodeToByteArray(),
            NcmCompression.gunzip(gzip),
        )
    }

    @Test
    fun rejectsOutputBeyondByteBudgetButAcceptsExactLimit() {
        val input = ByteArray(1_000_000) { 65 }
        val gzip = NcmCompression.gzip(input)
        assertFailsWith<NcmCompressionException> { NcmCompression.gunzip(gzip, 1024) }
        assertFailsWith<NcmCompressionException> { NcmCompression.gunzip(gzip, input.size - 1) }
        assertContentEquals(input, NcmCompression.gunzip(gzip, input.size))
        assertFailsWith<NcmCompressionException> { NcmCompression.gunzip(gzip, 0) }
        assertContentEquals(byteArrayOf(), NcmCompression.gunzip(NcmCompression.gzip(byteArrayOf()), 0))
        assertFailsWith<IllegalArgumentException> { NcmCompression.gunzip(gzip, -1) }
    }

    @Test
    fun concatenatedMembersShareOneBudget() {
        val first = ByteArray(6000) { 65 }
        val second = ByteArray(6000) { 66 }
        val gzip = NcmCompression.gzip(first) + NcmCompression.gzip(second)
        assertContentEquals(first + second, NcmCompression.gunzip(gzip, 12000))
        assertFailsWith<NcmCompressionException> { NcmCompression.gunzip(gzip, 6000) }
    }

    @Test
    fun defaultBudgetRejectsACompressionBombWithoutTrustingTrailerSize() {
        val gzip = NcmCompression.gzip(ByteArray(16 * 1024 * 1024 + 1))
        assertFailsWith<NcmCompressionException> { NcmCompression.gunzip(gzip) }
    }

    @Test
    fun rejectsCorruptTruncatedAndNonGzipResponses() {
        val gzip = NcmCompression.gzip("binary fixture".encodeToByteArray())
        val corruptCrc = gzip.copyOf().also { it[it.size - 8] = (it[it.size - 8].toInt() xor 1).toByte() }
        listOf(
            byteArrayOf(),
            "not gzip".encodeToByteArray(),
            gzip.copyOf(gzip.size - 1),
            gzip.copyOf(gzip.size / 2),
            corruptCrc,
        ).forEach { invalid ->
            assertFailsWith<NcmCompressionException> { NcmCompression.gunzip(invalid) }
        }
    }
}

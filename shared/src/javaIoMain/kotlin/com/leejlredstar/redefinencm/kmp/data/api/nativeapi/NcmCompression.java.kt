package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

actual object NcmCompression {
    actual fun gzip(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(data) }
        return output.toByteArray()
    }

    actual fun gunzip(data: ByteArray, maxBytes: Int): ByteArray {
        require(maxBytes >= 0) { "Invalid gzip output limit" }
        val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_SIZE))
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            GZIPInputStream(ByteArrayInputStream(data), BUFFER_SIZE).use { input ->
                while (true) {
                    val remaining = maxBytes - output.size()
                    // One extra byte distinguishes an exact-limit result from a compressed bomb.
                    // Avoid remaining + 1 overflowing when the caller supplied Int.MAX_VALUE.
                    val wanted = if (remaining >= buffer.size) buffer.size else remaining + 1
                    val count = input.read(buffer, 0, wanted)
                    if (count == -1) break
                    if (count > remaining) throw NcmCompressionException("Gzip output exceeds byte limit")
                    output.write(buffer, 0, count)
                }
            }
        } catch (failure: IOException) {
            throw NcmCompressionException("Invalid gzip data", failure)
        }
        return output.toByteArray()
    }

    private const val BUFFER_SIZE = 8192
}

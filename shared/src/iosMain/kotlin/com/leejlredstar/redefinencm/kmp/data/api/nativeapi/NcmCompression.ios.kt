@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.memset
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.inflateReset2
import platform.zlib.z_stream

actual object NcmCompression {
    actual fun gzip(data: ByteArray): ByteArray = transform(data, compress = true, maxBytes = Int.MAX_VALUE)

    actual fun gunzip(data: ByteArray, maxBytes: Int): ByteArray {
        require(maxBytes >= 0) { "Invalid gzip output limit" }
        if (data.isEmpty()) throw NcmCompressionException("Invalid gzip data")
        return transform(data, compress = false, maxBytes = maxBytes)
    }

    private fun transform(data: ByteArray, compress: Boolean, maxBytes: Int): ByteArray = memScoped {
        val stream = alloc<z_stream>()
        memset(stream.ptr, 0, sizeOf<z_stream>().convert())
        val initialized = if (compress) {
            deflateInit2(stream.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, GZIP_WINDOW_BITS, 8, Z_DEFAULT_STRATEGY)
        } else {
            inflateInit2(stream.ptr, GZIP_WINDOW_BITS)
        }
        if (initialized != Z_OK) throw NcmCompressionException("Cannot initialize gzip stream")
        val output = NcmCompressionOutput(maxBytes)
        val buffer = ByteArray(BUFFER_SIZE)
        // addressOf(0) is undefined on an empty ByteArray, even when avail_in is zero.
        val input = if (data.isEmpty()) ByteArray(1) else data
        try {
            input.usePinned { inputPin ->
                buffer.usePinned { outputPin ->
                    stream.next_in = inputPin.addressOf(0).reinterpret()
                    stream.avail_in = data.size.toUInt()
                    while (true) {
                        val remaining = maxBytes - output.size
                        val wanted = if (remaining >= buffer.size) buffer.size else remaining + 1
                        stream.next_out = outputPin.addressOf(0).reinterpret()
                        stream.avail_out = wanted.toUInt()
                        val before = stream.avail_in
                        val status = if (compress) deflate(stream.ptr, Z_FINISH) else inflate(stream.ptr, Z_NO_FLUSH)
                        val produced = wanted - stream.avail_out.toInt()
                        output.append(buffer, produced)
                        if (status == Z_STREAM_END) {
                            if (compress || stream.avail_in == 0u) break
                            // A response can contain several gzip members. All share one byte budget.
                            val next = stream.next_in
                            val available = stream.avail_in
                            if (inflateReset2(stream.ptr, GZIP_WINDOW_BITS) != Z_OK) {
                                throw NcmCompressionException("Cannot reset gzip stream")
                            }
                            stream.next_in = next
                            stream.avail_in = available
                        } else if (status != Z_OK || (before == stream.avail_in && produced == 0)) {
                            throw NcmCompressionException("Invalid gzip data")
                        }
                    }
                }
            }
            output.bytes()
        } finally {
            if (compress) deflateEnd(stream.ptr) else inflateEnd(stream.ptr)
        }
    }

    private const val BUFFER_SIZE = 8192
    private const val GZIP_WINDOW_BITS = 15 + 16
}

/** Retain only bounded output; never preallocate from the gzip trailer's attacker-controlled size. */
private class NcmCompressionOutput(private val maxBytes: Int) {
    private val chunks = mutableListOf<ByteArray>()
    var size: Int = 0
        private set

    fun append(buffer: ByteArray, count: Int) {
        if (count > maxBytes - size) throw NcmCompressionException("Gzip output exceeds byte limit")
        if (count == 0) return
        chunks += buffer.copyOf(count)
        size += count
    }

    fun bytes(): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }
}

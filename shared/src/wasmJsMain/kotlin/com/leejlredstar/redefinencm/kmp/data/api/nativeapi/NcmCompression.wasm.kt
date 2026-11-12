package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

/** The browser keeps the HTTP server transport; native NCBL is intentionally unavailable there. */
actual object NcmCompression {
    actual fun gzip(data: ByteArray): ByteArray = unsupported()

    actual fun gunzip(data: ByteArray, maxBytes: Int): ByteArray = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("Native NCM compression is unavailable on Web")
}

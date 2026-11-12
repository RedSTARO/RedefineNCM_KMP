package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

/** Invalid/truncated compressed input, or output exceeding the caller's byte budget. */
class NcmCompressionException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/**
 * Native protocol compression. NCBL deliberately uses the upstream Node fallback, gzip, not zstd.
 *
 * [gunzip] counts actual inflated bytes, including every concatenated gzip member. It never trusts
 * the gzip ISIZE field and throws before retaining a byte beyond [maxBytes]. Corrupt input is not
 * returned partially. Web uses the server API and does not invoke this native implementation.
 */
expect object NcmCompression {
    fun gzip(data: ByteArray): ByteArray
    fun gunzip(data: ByteArray, maxBytes: Int = 16 * 1024 * 1024): ByteArray
}

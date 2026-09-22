package com.leejlredstar.redefinencm.kmp.data.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Decodes an image sent as base64, with or without a `data:image/png;base64,` prefix and with the
 * padding some backends leave off.
 *
 * @throws IllegalArgumentException when the text is not base64 at all.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun decodeBase64Image(encoded: String): ByteArray {
    val body = encoded.substringAfter("base64,").trim()
    val padded = when (body.length % 4) {
        1 -> "$body==="
        2 -> "$body=="
        3 -> "$body="
        else -> body
    }
    return Base64.decode(padded)
}

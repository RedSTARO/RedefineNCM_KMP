package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.util.LyricParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Shared pieces of the Legacy AMLL WebView bridge.
 *
 * The Android (System WebView), Desktop (WebView2) and iOS (WKWebView) hosts all drive the same
 * `amllAssets/amll/player.html` bundle through the same `AmllBridge.*` calls, so the argument
 * encoding lives here instead of being copied into each host.
 */
internal object AmllWebBridge {
    private val json = Json { encodeDefaults = true }

    /**
     * Quotes [value] as a JavaScript string literal, escaping exactly like `JSONObject.quote`.
     * Every value crossing into `evaluateJavaScript` must go through this — lyric payloads are
     * arbitrary remote text and routinely contain quotes, backslashes and newlines.
     */
    fun quote(value: String): String = json.encodeToString(String.serializer(), value)

    fun lyricOptionsJson(
        translatedLyric: String,
        romanLyric: String,
        showTranslatedLyric: Boolean,
        showRomanLyric: Boolean,
    ): String = json.encodeToString(
        AmllLyricOptions.serializer(),
        AmllLyricOptions(
            translatedLyric = translatedLyric,
            romanLyric = romanLyric,
            showTranslation = showTranslatedLyric,
            showRoman = showRomanLyric,
        ),
    )
}

@Serializable
internal data class AmllLyricOptions(
    val translatedLyric: String,
    val romanLyric: String,
    val showTranslation: Boolean,
    val showRoman: Boolean,
)

/** Renders a timestamp→text lyric map back into LRC text for hosts that need a plain fallback. */
internal fun LinkedHashMap<Long?, String?>.toLrcFallbackText(): String =
    entries
        .mapNotNull { (time, text) ->
            val line = text?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            "${LyricParser.formatLrcTimestamp(time ?: 0L)}$line"
        }
        .joinToString("\n")

/**
 * Ceiling for artwork inlined into the AMLL page as a `data:` URI.
 *
 * Kept far below the durable 16 MiB download cap: Base64 plus JSON/JS escaping creates several
 * in-memory copies inside the embedded browser. Larger local covers fall back to the remote URI.
 */
internal const val MAX_AMLL_ARTWORK_BYTES: Int = 4 * 1024 * 1024

/** Read-chunk size for streaming a local cover into that buffer. */
internal const val DEFAULT_AMLL_ARTWORK_BUFFER_BYTES: Int = 16 * 1024

/**
 * Maps a cover file name to the MIME type its `data:` URI must declare, or null when no host
 * can be relied on to decode it.
 *
 * HEIC/HEIF is deliberately absent: Android System WebView support varies by version and
 * WebView2 depends on optional host codecs, so those fall back to the remote URI instead.
 */
internal fun amllArtworkMimeType(fileName: String): String? =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "avif" -> "image/avif"
        else -> null
    }

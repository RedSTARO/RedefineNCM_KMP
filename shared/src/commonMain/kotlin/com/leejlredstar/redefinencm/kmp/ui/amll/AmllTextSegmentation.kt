/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * The segmentation contract follows @applemusic-like-lyrics/core 0.5.2, which constructs
 * `Intl.Segmenter(undefined, { granularity: "word" | "grapheme" })`.
 *
 * Modified for RedefineNCM KMP on 2026-07-26.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

internal data class AmllWordSegment(
    val text: String,
    val isWordLike: Boolean,
)

internal enum class AmllSegmentationBackend {
    PLATFORM,
    FALLBACK,
}

internal data class AmllSegmentationResult<T>(
    val segments: List<T>,
    val backend: AmllSegmentationBackend,
)

/**
 * Platform equivalent of the two `Intl.Segmenter` instances owned by AMLL's LyricLineBase.
 *
 * Returning null is reserved for a genuinely unavailable or failed platform API. Callers then use
 * the explicit, observable [AmllSegmentationBackend.FALLBACK] path.
 */
internal expect object AmllPlatformTextSegmenter {
    fun segmentWords(text: String): List<AmllWordSegment>?

    fun segmentGraphemes(text: String): List<String>?
}

internal fun segmentAmllWords(text: String): AmllSegmentationResult<AmllWordSegment> =
    resolveAmllWordSegmentation(
        source = text,
        platformSegments = AmllPlatformTextSegmenter.segmentWords(text),
    )

/**
 * Upstream does not synthesize dictionary-free word segments when `Intl.Segmenter` is absent.
 * `LyricLineEl` simply has no LineBalancer, so FALLBACK intentionally carries no segments.
 */
internal fun resolveAmllWordSegmentation(
    source: String,
    platformSegments: List<AmllWordSegment>?,
): AmllSegmentationResult<AmllWordSegment> {
    if (platformSegments.isLosslessFor(source, AmllWordSegment::text)) {
        return AmllSegmentationResult(
            segments = platformSegments.orEmpty(),
            backend = AmllSegmentationBackend.PLATFORM,
        )
    }
    return AmllSegmentationResult(
        segments = emptyList(),
        backend = AmllSegmentationBackend.FALLBACK,
    )
}

internal fun segmentAmllGraphemes(text: String): AmllSegmentationResult<String> {
    if (text.isEmpty()) {
        return AmllSegmentationResult(emptyList(), AmllSegmentationBackend.PLATFORM)
    }
    val platformSegments = AmllPlatformTextSegmenter.segmentGraphemes(text)
    if (platformSegments.isLosslessFor(text) { it }) {
        return AmllSegmentationResult(
            segments = platformSegments.orEmpty(),
            backend = AmllSegmentationBackend.PLATFORM,
        )
    }
    return AmllSegmentationResult(
        segments = splitAmllGraphemesFallback(text),
        backend = AmllSegmentationBackend.FALLBACK,
    )
}

private fun <T> List<T>?.isLosslessFor(
    source: String,
    textOf: (T) -> String,
): Boolean {
    if (this == null) return false
    return all { textOf(it).isNotEmpty() } &&
        joinToString(separator = "", transform = textOf) == source
}

internal data class AmllGraphemeRange(
    val text: String,
    val startUtf16: Int,
    val endUtf16: Int,
)

internal fun segmentAmllGraphemeRanges(text: String): List<AmllGraphemeRange> {
    var offset = 0
    return segmentAmllGraphemes(text).segments.map { grapheme ->
        AmllGraphemeRange(
            text = grapheme,
            startUtf16 = offset,
            endUtf16 = (offset + grapheme.length).also { offset = it },
        )
    }
}

/**
 * Exact fallback used by upstream `createWord()` when `Intl.Segmenter` is absent:
 * JavaScript `Array.from(text)` iterates Unicode code points, not extended grapheme clusters.
 */
internal fun splitAmllGraphemesFallback(text: String): List<String> =
    splitAmllCodePoints(text)

internal fun splitAmllCodePoints(text: String): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        val length = codePointUtf16LengthAt(text, index)
        result += text.substring(index, index + length)
        index += length
    }
    return result
}

/**
 * JavaScript's `String.prototype.split("")` splits UTF-16 code units, including surrogate pairs.
 * AMLL uses that operation in the CJK atomization branch, so this deliberately does not use
 * [splitAmllCodePoints] or the platform grapheme segmenter.
 */
internal fun splitAmllUtf16CodeUnits(text: String): List<String> =
    text.map { codeUnit -> codeUnit.toString() }

internal fun splitAmllWhitespaceParts(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    var partStart = 0
    var index = 0
    var whitespace = isEcmaWhitespace(codePointAt(text, 0))
    while (index < text.length) {
        val codePoint = codePointAt(text, index)
        val currentWhitespace = isEcmaWhitespace(codePoint)
        if (currentWhitespace != whitespace) {
            result += text.substring(partStart, index)
            partStart = index
            whitespace = currentWhitespace
        }
        index += codePointUtf16Length(codePoint)
    }
    result += text.substring(partStart)
    return result
}

internal fun String.trimEcmaWhitespace(): String {
    var start = 0
    var end = length
    while (start < end) {
        val codePoint = codePointAt(this, start)
        if (!isEcmaWhitespace(codePoint)) break
        start += codePointUtf16Length(codePoint)
    }
    while (end > start) {
        val previous = previousCodePointStart(this, end)
        if (!isEcmaWhitespace(codePointAt(this, previous))) break
        end = previous
    }
    return substring(start, end)
}

internal fun String.isEcmaWhitespaceOnly(): Boolean =
    isEmpty() || trimEcmaWhitespace().isEmpty()

internal fun countNonWhitespaceUtf16Units(text: String): Int {
    var count = 0
    var index = 0
    while (index < text.length) {
        val codePoint = codePointAt(text, index)
        val codeUnitCount = codePointUtf16Length(codePoint)
        if (!isEcmaWhitespace(codePoint)) count += codeUnitCount
        index += codeUnitCount
    }
    return count
}

internal fun isAmllWordLikeSegment(text: String): Boolean {
    val trimmed = text.trimEcmaWhitespace()
    if (trimmed.isEmpty()) return false
    return isAmllCjk(trimmed) || splitAmllCodePoints(trimmed).any { codePointText ->
        val codePoint = codePointAt(codePointText, 0)
        codePoint <= Char.MAX_VALUE.code && codePoint.toChar().isLetterOrDigit()
    }
}

internal fun isAmllCjk(text: String): Boolean {
    if (text.isEmpty()) return false
    var index = 0
    while (index < text.length) {
        val codePoint = codePointAt(text, index)
        if (!isAmllCjkCodePoint(codePoint)) return false
        index += codePointUtf16Length(codePoint)
    }
    return true
}

private fun isAmllCjkCodePoint(codePoint: Int): Boolean = when {
    // Preserve AMLL 0.5.2's explicit `\u0800-\u9FFC` range.
    codePoint in 0x0800..0x9FFC -> true
    // `\p{Unified_Ideograph}` ranges not covered by the preceding BMP interval.
    codePoint in 0x9FFD..0x9FFF -> true
    codePoint in 0x20000..0x2A6DF -> true
    codePoint in 0x2A700..0x2B81D -> true
    codePoint in 0x2B820..0x2CEAD -> true
    codePoint in 0x2CEB0..0x2EBE0 -> true
    codePoint in 0x2EBF0..0x2EE5D -> true
    codePoint in 0x30000..0x3134A -> true
    codePoint in 0x31350..0x33479 -> true
    codePoint == 0xFA0E || codePoint == 0xFA0F || codePoint == 0xFA11 -> true
    codePoint == 0xFA13 || codePoint == 0xFA14 || codePoint == 0xFA1F -> true
    codePoint == 0xFA21 || codePoint == 0xFA23 || codePoint == 0xFA24 -> true
    codePoint == 0xFA27 || codePoint == 0xFA28 || codePoint == 0xFA29 -> true
    else -> false
}

private fun isEcmaWhitespace(codePoint: Int): Boolean =
    codePoint in 0x0009..0x000D ||
        codePoint == 0x0020 ||
        codePoint == 0x00A0 ||
        codePoint == 0x1680 ||
        codePoint in 0x2000..0x200A ||
        codePoint == 0x2028 ||
        codePoint == 0x2029 ||
        codePoint == 0x202F ||
        codePoint == 0x205F ||
        codePoint == 0x3000 ||
        codePoint == 0xFEFF

private fun codePointAt(text: String, index: Int): Int {
    val first = text[index]
    return if (
        first.isHighSurrogate() &&
        index + 1 < text.length &&
        text[index + 1].isLowSurrogate()
    ) {
        ((first.code - 0xD800) shl 10) +
            (text[index + 1].code - 0xDC00) +
            0x10000
    } else {
        first.code
    }
}

private fun codePointUtf16LengthAt(text: String, index: Int): Int =
    codePointUtf16Length(codePointAt(text, index))

private fun codePointUtf16Length(codePoint: Int): Int = if (codePoint > 0xFFFF) 2 else 1

private fun previousCodePointStart(text: String, exclusiveEnd: Int): Int {
    val last = exclusiveEnd - 1
    return if (
        last > 0 &&
        text[last].isLowSurrogate() &&
        text[last - 1].isHighSurrogate()
    ) {
        last - 1
    } else {
        last
    }
}

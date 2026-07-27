/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * The lyric optimization and grouping code in this file is a Kotlin translation of
 * @applemusic-like-lyrics/core 0.5.2 (utils/optimize-lyric.ts and the DOM player's
 * setLyricLines grouping loop).
 *
 * Modified for RedefineNCM KMP on 2026-07-26.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import com.leejlredstar.redefinencm.kmp.util.LyricParser
import kotlin.math.abs
import kotlin.math.roundToLong

const val AMLL_SUPPLEMENT_TOLERANCE_MS: Long = 850L

/**
 * Platform-independent lyric document consumed by the native AMLL renderer.
 *
 * [lines] contains the six-step AMLL-optimized timeline. [groups] mirrors the upstream player's
 * exact grouping pass: every non-background line starts a group and the following background line
 * is attached to it. Interlude and active state are deliberately absent from this data pass; AMLL
 * derives both from the live playback timeline.
 */
data class AmllLyricDocument(
    val lines: List<AmllLyricLine>,
    val groups: List<AmllLyricGroup> = groupAmllLyricLines(lines),
) {
    val hasWordTiming: Boolean
        get() = lines.any { it.words.size > 1 }

    val hasTranslatedLyrics: Boolean
        get() = lines.any { !it.translatedText.isNullOrBlank() }

    val hasRomanLyrics: Boolean
        get() = lines.any { line ->
            !line.romanText.isNullOrBlank() ||
                line.words.any { !it.romanWord.isNullOrBlank() }
        }

    /**
     * Compatibility view for the previous native prototype. Interludes are now computed from the
     * live group timeline, exactly as AMLL does, so the parsed document never contains fixed ones.
     */
    val interludes: List<AmllLyricInterlude>
        get() = emptyList()

    fun activeLineIndexAt(positionMs: Long): Int {
        val time = positionMs.toDouble()
        return lines.indexOfLast { it.exactStartTimeMs <= time }
    }

    fun activeGroupIndexAt(positionMs: Long): Int {
        val time = positionMs.toDouble()
        return groups.indexOfLast { it.exactStartTimeMs <= time }
    }
}

data class AmllLyricGroup(
    val id: String,
    val mainLine: AmllLyricLine,
    val backgroundLine: AmllLyricLine? = null,
    /**
     * AMLL compares the first word timestamps because line timestamps have already been synced.
     */
    val isBackgroundFirst: Boolean = backgroundLine != null &&
        (backgroundLine.words.firstOrNull()?.exactStartTimeMs ?: backgroundLine.exactStartTimeMs) <
        (mainLine.words.firstOrNull()?.exactStartTimeMs ?: mainLine.exactStartTimeMs),
) {
    val exactStartTimeMs: Double
        get() = mainLine.exactStartTimeMs

    val exactEndTimeMs: Double
        get() = mainLine.exactEndTimeMs
}

data class AmllLyricLine(
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val mainText: String,
    val translatedText: String? = null,
    val romanText: String? = null,
    val words: List<AmllLyricWord> = emptyList(),
    val isBackground: Boolean = false,
    val isDuet: Boolean = false,
    /**
     * Kept only so the in-flight renderer remains source compatible. Data parsing never populates
     * it; AMLL's interlude is a live timeline result, not a line attribute.
     */
    val interludeBefore: AmllLyricInterlude? = null,
    /** Timestamp before AMLL presentation optimization. */
    val sourceStartTimeMs: Long = startTimeMs,
    val sourceEndTimeMs: Long = endTimeMs,
    /** Exact JavaScript-number result; `tryAdvanceStartTime` may produce fractional milliseconds. */
    val exactStartTimeMs: Double = startTimeMs.toDouble(),
    val exactEndTimeMs: Double = endTimeMs.toDouble(),
)

data class AmllLyricWord(
    val id: String,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    /** Per-word romanization from AMLL's `LyricWord.romanWord`. */
    val romanWord: String? = null,
    /** Carries AMLL's source flag even though the fixed host leaves obscene masking disabled. */
    val obscene: Boolean = false,
    /** AMLL TTML's authoring-only empty-beat hint, retained losslessly through optimization. */
    val emptyBeat: Int? = null,
    /** Timed ruby/furigana segments rendered above this word. */
    val ruby: List<AmllLyricRubySegment> = emptyList(),
    /** Exact JavaScript-number timestamps; the Long fields remain compatibility transport values. */
    val exactStartTimeMs: Double = startTimeMs.toDouble(),
    val exactEndTimeMs: Double = endTimeMs.toDouble(),
)

/**
 * Direct Kotlin representation of AMLL core's `LyricWordBase` when nested in `LyricWord.ruby`.
 */
data class AmllLyricRubySegment(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val exactStartTimeMs: Double = startTimeMs.toDouble(),
    val exactEndTimeMs: Double = endTimeMs.toDouble(),
)

/**
 * Legacy model retained while the timeline renderer is translated. New document construction does
 * not create these eagerly.
 */
data class AmllLyricInterlude(
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val anchorLineId: String?,
    val nextLineId: String,
    val nextLineIsDuet: Boolean = false,
) {
    val durationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(0L)
}

data class AmllLyricOptimizeOptions(
    val normalizeSpaces: Boolean = true,
    val resetLineTimestamps: Boolean = true,
    val convertExcessiveBackgroundLines: Boolean = true,
    val syncMainAndBackgroundLines: Boolean = true,
    val cleanUnintentionalOverlaps: Boolean = true,
    val tryAdvanceStartTime: Boolean = true,
)

/**
 * Builds the same data that the AMLL WebView passed to `LyricPlayer.setLyricLines()`.
 *
 * YRC is the primary source whenever it parsed successfully. Lossless [lrcLines] are the second
 * choice. [lyricMap] is retained as a compatibility fallback, but maps cannot represent repeated
 * timestamps or background-vocal roles.
 */
fun buildAmllLyricDocument(
    lyricMap: Map<Long?, String?>,
    wordLines: List<LyricParser.WordLine>,
    translatedLrc: String = "",
    romanLrc: String = "",
    showTranslated: Boolean = false,
    showRoman: Boolean = false,
    lrcLines: List<LyricParser.WordLine> = emptyList(),
    optimizeOptions: AmllLyricOptimizeOptions = AmllLyricOptimizeOptions(),
    rawLrc: String = "",
    rawYrc: String = "",
): AmllLyricDocument {
    val parsedYrc = rawYrc
        .takeIf(String::isNotBlank)
        ?.let(LyricParser::parseYrc)
        .orEmpty()
    val effectiveWordLines = parsedYrc.ifEmpty { wordLines }
    val parsedLrc = rawLrc
        .takeIf(String::isNotBlank)
        ?.let(LyricParser::parseLrcLines)
        .orEmpty()
    val effectiveLrcLines = parsedLrc.ifEmpty { lrcLines }
    val sourceLines = when {
        effectiveWordLines.isNotEmpty() -> effectiveWordLines
        effectiveLrcLines.isNotEmpty() -> effectiveLrcLines
        else -> legacyMapLines(lyricMap)
    }
    if (sourceLines.isEmpty()) return AmllLyricDocument(emptyList())

    val translations = if (showTranslated) parseSupplement(translatedLrc) else emptyList()
    val romans = if (showRoman) parseSupplement(romanLrc) else emptyList()
    val duplicateCounts = mutableMapOf<Long, Int>()

    val rawLines = sourceLines.map { source ->
        val duplicateIndex = duplicateCounts.getOrElse(source.startTimeMs) { 0 }
        duplicateCounts[source.startTimeMs] = duplicateIndex + 1
        val lineId = stableLineId(source.startTimeMs, duplicateIndex)
        val translatedText = if (showTranslated) {
            findNearestSupplement(translations, source.startTimeMs)
                ?.takeIf(String::isNotEmpty)
                ?: source.translatedLyric.takeIf(String::isNotEmpty)
        } else {
            null
        }
        val romanText = if (showRoman) {
            findNearestSupplement(romans, source.startTimeMs)
                ?.takeIf(String::isNotEmpty)
                ?: source.romanLyric.takeIf(String::isNotEmpty)
        } else {
            null
        }

        AmllLyricLine(
            id = lineId,
            startTimeMs = source.startTimeMs,
            endTimeMs = source.endTimeMs,
            mainText = source.text,
            translatedText = translatedText,
            romanText = romanText,
            words = source.words.mapIndexed { wordIndex, word ->
                AmllLyricWord(
                    id = "$lineId:word:${word.startTimeMs}:$wordIndex",
                    text = word.text,
                    startTimeMs = word.startTimeMs,
                    endTimeMs = word.endTimeMs,
                    romanWord = word.romanWord.takeIf { showRoman },
                    obscene = word.obscene,
                    emptyBeat = word.emptyBeat,
                    ruby = word.ruby.map { ruby ->
                        AmllLyricRubySegment(
                            text = ruby.text,
                            startTimeMs = ruby.startTimeMs,
                            endTimeMs = ruby.endTimeMs,
                            exactStartTimeMs = ruby.exactStartTimeMs,
                            exactEndTimeMs = ruby.exactEndTimeMs,
                        )
                    },
                    exactStartTimeMs = word.exactStartTimeMs,
                    exactEndTimeMs = word.exactEndTimeMs,
                )
            },
            isBackground = source.isBackground,
            isDuet = source.isDuet,
            sourceStartTimeMs = source.startTimeMs,
            sourceEndTimeMs = source.endTimeMs,
            exactStartTimeMs = source.exactStartTimeMs,
            exactEndTimeMs = source.exactEndTimeMs,
        )
    }

    val optimized = optimizeAmllLyricLines(rawLines, optimizeOptions)
    return AmllLyricDocument(
        lines = optimized,
        groups = groupAmllLyricLines(optimized),
    )
}

/**
 * Pure Kotlin port of all six ordered passes in AMLL core 0.5.2 `optimize-lyric.ts`.
 */
fun optimizeAmllLyricLines(
    lines: List<AmllLyricLine>,
    options: AmllLyricOptimizeOptions = AmllLyricOptimizeOptions(),
): List<AmllLyricLine> {
    val mutableLines = lines.map(MutableAmllLine::from).toMutableList()

    if (options.normalizeSpaces) normalizeSpaces(mutableLines)
    if (options.resetLineTimestamps) resetLineTimestamps(mutableLines)
    if (options.convertExcessiveBackgroundLines) {
        convertExcessiveBackgroundLines(mutableLines)
    }
    if (options.syncMainAndBackgroundLines) syncMainAndBackgroundLines(mutableLines)
    if (options.cleanUnintentionalOverlaps) cleanUnintentionalOverlaps(mutableLines)
    if (options.tryAdvanceStartTime) tryAdvanceStartTime(mutableLines)

    return mutableLines.map(MutableAmllLine::toImmutable)
}

/**
 * Upstream DOM grouping: a non-BG line (or a leading BG line) starts a group, then a BG line
 * attaches to the current group.
 */
fun groupAmllLyricLines(lines: List<AmllLyricLine>): List<AmllLyricGroup> {
    val groups = mutableListOf<AmllLyricGroup>()
    var currentGroup: AmllLyricGroup? = null

    lines.forEach { line ->
        if (!line.isBackground || currentGroup == null) {
            val newGroup = AmllLyricGroup(
                id = "group:${line.id}",
                mainLine = line,
            )
            currentGroup = newGroup
            groups += newGroup
        } else {
            val existingGroup = currentGroup
            val mainStart = existingGroup.mainLine.words.firstOrNull()?.exactStartTimeMs
                ?: existingGroup.mainLine.exactStartTimeMs
            val updatedGroup = existingGroup.copy(
                backgroundLine = line,
                isBackgroundFirst = (
                    line.words.firstOrNull()?.exactStartTimeMs ?: line.exactStartTimeMs
                    ) < mainStart,
            )
            currentGroup = updatedGroup
            groups[groups.lastIndex] = updatedGroup
        }
    }
    return groups
}

/**
 * Returns a deterministic 0..1 mask progress for a timed word.
 */
fun calculateAmllWordProgress(
    word: AmllLyricWord,
    positionMs: Long,
): Float {
    val safeEndTimeMs = word.exactEndTimeMs.coerceAtLeast(word.exactStartTimeMs)
    val position = positionMs.toDouble()
    if (position < word.exactStartTimeMs) return 0f
    if (safeEndTimeMs == word.exactStartTimeMs || position >= safeEndTimeMs) return 1f
    return ((position - word.exactStartTimeMs) / (safeEndTimeMs - word.exactStartTimeMs))
        .toFloat()
        .coerceIn(0f, 1f)
}

fun calculateAmllWordProgress(
    positionMs: Long,
    startTimeMs: Long,
    endTimeMs: Long,
): Float {
    val safeEndTimeMs = endTimeMs.coerceAtLeast(startTimeMs)
    if (positionMs < startTimeMs) return 0f
    if (safeEndTimeMs == startTimeMs || positionMs >= safeEndTimeMs) return 1f
    return ((positionMs - startTimeMs).toDouble() / (safeEndTimeMs - startTimeMs).toDouble())
        .toFloat()
        .coerceIn(0f, 1f)
}

enum class AmllLyricViewportClass {
    NARROW,
    COMPACT,
    WIDE,
}

/**
 * Logical-pixel rendering parameters. The numeric type size is a CSS-pixel/dp value; the
 * renderer converts it to `sp` with the inverse font scale so Compose does not silently change
 * the viewport-derived AMLL metrics.
 *
 * The responsive values mirror AMLL's CSS: 8vw type on narrow screens, otherwise the larger of
 * 5vh and 2.5vw. Core declares `--amll-lp-line-width-aspect`, but DOM `rebuildStyle()` leaves the
 * assignment to `--amll-lp-width` commented out in 0.5.2, so the effective line width is 100%.
 */
data class AmllLyricVisualParameters(
    val viewportClass: AmllLyricViewportClass,
    val baseFontSizeSp: Float,
    val lineWidthFraction: Float,
    val horizontalPaddingDp: Float,
    val lineVerticalPaddingDp: Float,
    val lineCornerRadiusDp: Float,
    val lineGapDp: Float,
    val subLineFontSizeSp: Float,
    val backgroundLineFontSizeSp: Float,
    val inactiveLineOpacity: Float,
    val backgroundLineOpacity: Float,
    val blurDistanceScale: Float,
    val springEnabled: Boolean,
    val animatedScrollEnabled: Boolean,
    val transitionDurationMs: Int,
)

fun calculateAmllLyricVisualParameters(
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    reducedMotion: Boolean,
): AmllLyricVisualParameters {
    val width = viewportWidthDp.coerceAtLeast(0f)
    val height = viewportHeightDp.coerceAtLeast(0f)
    val viewportClass = when {
        width <= 500f -> AmllLyricViewportClass.NARROW
        width <= 1_024f -> AmllLyricViewportClass.COMPACT
        else -> AmllLyricViewportClass.WIDE
    }
    val baseFontSizeSp = if (width <= 768f) {
        maxOf(width * 0.08f, 12f)
    } else {
        maxOf(height * 0.05f, width * 0.025f, 12f)
    }
    val horizontalPaddingDp = if (width <= 500f) 20f else baseFontSizeSp

    return AmllLyricVisualParameters(
        viewportClass = viewportClass,
        baseFontSizeSp = baseFontSizeSp,
        lineWidthFraction = 1f,
        horizontalPaddingDp = horizontalPaddingDp,
        lineVerticalPaddingDp = baseFontSizeSp * 0.4f,
        lineCornerRadiusDp = baseFontSizeSp * 0.25f,
        lineGapDp = baseFontSizeSp * 0.3f,
        subLineFontSizeSp = maxOf(baseFontSizeSp * 0.5f, 10f),
        backgroundLineFontSizeSp = maxOf(baseFontSizeSp * 0.7f, 10f),
        inactiveLineOpacity = 0.2f,
        // player.html deliberately overrides core's .4 background-vocal opacity.
        backgroundLineOpacity = 0.88f,
        blurDistanceScale = if (width <= 1_024f) 0.8f else 1f,
        springEnabled = !reducedMotion,
        animatedScrollEnabled = !reducedMotion,
        transitionDurationMs = if (reducedMotion) 0 else 400,
    )
}

private data class MutableAmllWord(
    val id: String,
    var text: String,
    var startTimeMs: Double,
    var endTimeMs: Double,
    val romanWord: String?,
    val obscene: Boolean,
    val emptyBeat: Int?,
    val ruby: List<AmllLyricRubySegment>,
)

private data class MutableAmllLine(
    val id: String,
    var startTimeMs: Double,
    var endTimeMs: Double,
    val sourceStartTimeMs: Long,
    val sourceEndTimeMs: Long,
    val translatedText: String?,
    val romanText: String?,
    val words: MutableList<MutableAmllWord>,
    var isBackground: Boolean,
    val isDuet: Boolean,
) {
    fun toImmutable(): AmllLyricLine = AmllLyricLine(
        id = id,
        startTimeMs = startTimeMs.roundToLong(),
        endTimeMs = endTimeMs.roundToLong(),
        mainText = words.joinToString(separator = "") { it.text },
        translatedText = translatedText,
        romanText = romanText,
        words = words.map { word ->
            AmllLyricWord(
                id = word.id,
                text = word.text,
                startTimeMs = word.startTimeMs.roundToLong(),
                endTimeMs = word.endTimeMs.roundToLong(),
                romanWord = word.romanWord,
                obscene = word.obscene,
                emptyBeat = word.emptyBeat,
                ruby = word.ruby,
                exactStartTimeMs = word.startTimeMs,
                exactEndTimeMs = word.endTimeMs,
            )
        },
        isBackground = isBackground,
        isDuet = isDuet,
        interludeBefore = null,
        sourceStartTimeMs = sourceStartTimeMs,
        sourceEndTimeMs = sourceEndTimeMs,
        exactStartTimeMs = startTimeMs,
        exactEndTimeMs = endTimeMs,
    )

    companion object {
        fun from(line: AmllLyricLine): MutableAmllLine = MutableAmllLine(
            id = line.id,
            startTimeMs = line.exactStartTimeMs,
            endTimeMs = line.exactEndTimeMs,
            sourceStartTimeMs = line.sourceStartTimeMs,
            sourceEndTimeMs = line.sourceEndTimeMs,
            translatedText = line.translatedText,
            romanText = line.romanText,
            words = line.words.map { word ->
                MutableAmllWord(
                    id = word.id,
                    text = word.text,
                    startTimeMs = word.exactStartTimeMs,
                    endTimeMs = word.exactEndTimeMs,
                    romanWord = word.romanWord,
                    obscene = word.obscene,
                    emptyBeat = word.emptyBeat,
                    ruby = word.ruby,
                )
            }.toMutableList(),
            isBackground = line.isBackground,
            isDuet = line.isDuet,
        )
    }
}

private data class SupplementEntry(
    val timeMs: Long,
    val text: String,
)

private val supplementTimeRegex = Regex("""\[(\d+):(\d+(?:\.\d+)?)\]""")
private val ecmaScriptWhitespaceRunRegex =
    Regex("""[\u0009-\u000D\u0020\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]+""")

private fun legacyMapLines(lyricMap: Map<Long?, String?>): List<LyricParser.WordLine> {
    val entries = lyricMap.entries
        .mapNotNull { (time, text) ->
            val startTimeMs = time?.takeIf { it >= 0L } ?: return@mapNotNull null
            val mainText = text ?: return@mapNotNull null
            if (mainText.isEmpty()) return@mapNotNull null
            startTimeMs to mainText
        }
        .sortedBy { it.first }

    return entries.mapIndexed { index, (startTimeMs, text) ->
        val endTimeMs = entries.getOrNull(index + 1)?.first ?: LyricParser.MAX_LRC_TIMESTAMP_MS
        LyricParser.WordLine(
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            words = listOf(
                LyricParser.Word(
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    text = text,
                ),
            ),
        )
    }
}

private fun stableLineId(startTimeMs: Long, duplicateIndex: Int): String =
    "line:$startTimeMs:$duplicateIndex"

private fun normalizeSpaces(lines: List<MutableAmllLine>) {
    lines.forEach { line ->
        line.words.forEach { word ->
            word.text = word.text.replace(ecmaScriptWhitespaceRunRegex, " ")
        }
    }
}

private fun resetLineTimestamps(lines: List<MutableAmllLine>) {
    lines.forEach { line ->
        if (
            line.words.size == 1 &&
            line.words[0].startTimeMs == 0.0 &&
            line.words[0].endTimeMs == 0.0 &&
            (line.startTimeMs != 0.0 || line.endTimeMs != 0.0)
        ) {
            line.words[0].startTimeMs = line.startTimeMs
            line.words[0].endTimeMs = line.endTimeMs
        } else if (line.words.isNotEmpty()) {
            line.startTimeMs = line.words.first().startTimeMs
            line.endTimeMs = line.words.last().endTimeMs
        }
    }
}

private fun convertExcessiveBackgroundLines(lines: List<MutableAmllLine>) {
    var consecutiveBackgroundCount = 0
    lines.forEach { line ->
        if (line.isBackground) {
            consecutiveBackgroundCount++
            if (consecutiveBackgroundCount > 1) line.isBackground = false
        } else {
            consecutiveBackgroundCount = 0
        }
    }
}

private fun syncMainAndBackgroundLines(lines: List<MutableAmllLine>) {
    for (index in lines.lastIndex downTo 0) {
        val line = lines[index]
        if (line.isBackground) continue

        val nextLine = lines.getOrNull(index + 1)
        if (nextLine?.isBackground == true) {
            val allWords = (line.words + nextLine.words)
                .filter { it.text.trimEcmaScriptWhitespace().isNotEmpty() }
            if (allWords.isNotEmpty()) {
                val minStart = allWords.minOf { it.startTimeMs }
                val maxEnd = allWords.maxOf { it.endTimeMs }
                val finalStart = minOf(minStart, line.startTimeMs, nextLine.startTimeMs)
                val finalEnd = maxOf(maxEnd, line.endTimeMs, nextLine.endTimeMs)
                line.startTimeMs = finalStart
                line.endTimeMs = finalEnd
                nextLine.startTimeMs = finalStart
                nextLine.endTimeMs = finalEnd
            }
        }
    }
}

private fun cleanUnintentionalOverlaps(lines: List<MutableAmllLine>) {
    for (index in 0 until lines.lastIndex) {
        val line = lines[index]
        if (line.isBackground) continue

        var nextMainIndex = index + 1
        while (nextMainIndex < lines.size && lines[nextMainIndex].isBackground) {
            nextMainIndex++
        }
        if (nextMainIndex >= lines.size) continue

        val nextLine = lines[nextMainIndex]
        val overlap = line.endTimeMs - nextLine.startTimeMs
        if (overlap > 0.0) {
            val nextDuration = nextLine.endTimeMs - nextLine.startTimeMs
            val percentageThreshold = nextDuration * 0.1
            val isIntentionalOverlap = overlap > 100.0 && overlap > percentageThreshold
            if (!isIntentionalOverlap) {
                line.endTimeMs = nextLine.startTimeMs
                val attachedBackgroundLine = lines.getOrNull(index + 1)
                if (attachedBackgroundLine?.isBackground == true) {
                    attachedBackgroundLine.endTimeMs = nextLine.startTimeMs
                }
            }
        }
    }
}

private fun tryAdvanceStartTime(lines: List<MutableAmllLine>) {
    val defaultAdvanceAmount = 600.0
    val fallbackAdvanceAmount = 400.0
    val fallbackAdvanceRatio = 0.3

    var previousLineStartTime = 0.0
    var previousLineEndTime = 0.0
    var previousMainGroupStartTime = 0.0
    var previousMainGroupEndTime = 0.0
    var hasPreviousLine = false

    lines.forEachIndexed { index, line ->
        if (line.isBackground) return@forEachIndexed

        val originalStartTime = line.startTimeMs
        val originalEndTime = line.endTimeMs

        val targetAdvanceAmount: Double
        val safeBoundary: Double
        if (hasPreviousLine) {
            val originallyHadGap = originalStartTime >= previousLineEndTime
            if (originallyHadGap) {
                targetAdvanceAmount = defaultAdvanceAmount
                safeBoundary = previousMainGroupEndTime
            } else {
                targetAdvanceAmount = fallbackAdvanceAmount
                val previousDuration = previousLineEndTime - previousLineStartTime
                safeBoundary = previousLineStartTime + previousDuration * fallbackAdvanceRatio
            }
        } else {
            targetAdvanceAmount = defaultAdvanceAmount
            safeBoundary = 0.0
        }

        val targetTime = line.startTimeMs - targetAdvanceAmount
        val newStartTime = maxOf(safeBoundary, targetTime)
        if (newStartTime < line.startTimeMs) line.startTimeMs = newStartTime

        val nextLine = lines.getOrNull(index + 1)
        if (nextLine?.isBackground == true) {
            nextLine.startTimeMs = line.startTimeMs
        }

        if (hasPreviousLine) {
            val overlapsPreviousGroup =
                originalStartTime < previousMainGroupEndTime &&
                    originalEndTime > previousMainGroupStartTime
            if (overlapsPreviousGroup) {
                previousMainGroupStartTime = minOf(
                    previousMainGroupStartTime,
                    originalStartTime,
                )
                previousMainGroupEndTime = maxOf(previousMainGroupEndTime, originalEndTime)
            } else {
                previousMainGroupStartTime = originalStartTime
                previousMainGroupEndTime = originalEndTime
            }
        } else {
            previousMainGroupStartTime = originalStartTime
            previousMainGroupEndTime = originalEndTime
        }

        previousLineStartTime = originalStartTime
        previousLineEndTime = originalEndTime
        hasPreviousLine = true
    }
}

private fun parseSupplement(lrcText: String): List<SupplementEntry> = buildList {
    lrcText.split(Regex("""\r?\n""")).forEach lineLoop@{ rawLine ->
        if (
            rawLine.isEmpty() ||
            rawLine.trimEcmaScriptWhitespace().startsWith("{")
        ) {
            return@lineLoop
        }
        val matches = supplementTimeRegex.findAll(rawLine).toList()
        if (matches.isEmpty()) return@lineLoop
        val text = rawLine
            .replace(supplementTimeRegex, "")
            .trimEcmaScriptWhitespace()
        if (text.isEmpty()) return@lineLoop
        matches.forEach matchLoop@{ match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toDoubleOrNull() ?: 0.0
            add(
                SupplementEntry(
                    timeMs = ((minutes * 60.0 + seconds) * 1_000.0).roundToLong(),
                    text = text,
                ),
            )
        }
    }
}.sortedBy { it.timeMs }

private fun findNearestSupplement(
    entries: List<SupplementEntry>,
    targetTimeMs: Long,
): String? {
    var best: SupplementEntry? = null
    var bestDistance = Long.MAX_VALUE
    entries.forEach { entry ->
        val distance = abs(entry.timeMs - targetTimeMs)
        if (distance <= AMLL_SUPPLEMENT_TOLERANCE_MS && distance < bestDistance) {
            best = entry
            bestDistance = distance
        }
    }
    return best?.text
}

private fun String.trimEcmaScriptWhitespace(): String =
    trim { character -> character.isEcmaScriptWhitespace() }

private fun Char.isEcmaScriptWhitespace(): Boolean = when (code) {
    in 0x0009..0x000D,
    0x0020,
    0x00A0,
    0x1680,
    in 0x2000..0x200A,
    0x2028,
    0x2029,
    0x202F,
    0x205F,
    0x3000,
    0xFEFF,
    -> true

    else -> false
}

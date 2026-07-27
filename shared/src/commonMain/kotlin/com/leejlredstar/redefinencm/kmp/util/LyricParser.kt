/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * The LRC parser in this file is a Kotlin translation of
 * @applemusic-like-lyrics/lyric 1.0.2 (formats/lrc.ts). The NetEase YRC parser
 * follows RedefineNCM KMP's AMLL bridge entry.js.
 *
 * Modified for RedefineNCM KMP on 2026-07-26.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.util

import kotlin.math.roundToLong

/**
 * LRC/YRC lyric parser.
 *
 * [parseLrcLines] is the lossless AMLL-facing API: it retains repeated timestamps and vocal-role
 * metadata. [parse] remains as the legacy map adapter for call sites that only support one line per
 * timestamp.
 */
object LyricParser {

    const val MAX_LRC_TIMESTAMP_MS: Long = 60_039_999L

    /**
     * AMLL `LyricWord.ruby` transport.
     *
     * The compatibility [startTimeMs]/[endTimeMs] values remain available to Long-only parsers,
     * while TTML and other fractional sources can preserve the original JavaScript-number timing
     * through [exactStartTimeMs]/[exactEndTimeMs].
     */
    data class RubySegment(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val text: String,
        val exactStartTimeMs: Double = startTimeMs.toDouble(),
        val exactEndTimeMs: Double = endTimeMs.toDouble(),
    )

    data class Word(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val text: String,
        val romanWord: String = "",
        val obscene: Boolean = false,
        /**
         * AMLL TTML's authoring-only empty-beat hint. Core 0.5.2 does not currently consume this
         * field while rendering, but the native transport must not discard it during parsing.
         */
        val emptyBeat: Int? = null,
        val ruby: List<RubySegment> = emptyList(),
        val exactStartTimeMs: Double = startTimeMs.toDouble(),
        val exactEndTimeMs: Double = endTimeMs.toDouble(),
    )

    /**
     * AMLL-compatible raw lyric line. The parser does not run AMLL's presentation optimizer.
     */
    data class WordLine(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val words: List<Word>,
        val translatedLyric: String = "",
        val romanLyric: String = "",
        val isBackground: Boolean = false,
        val isDuet: Boolean = false,
        val exactStartTimeMs: Double = startTimeMs.toDouble(),
        val exactEndTimeMs: Double = endTimeMs.toDouble(),
    ) {
        val text: String
            get() = words.joinToString(separator = "") { it.text }
    }

    private data class MutableLine(
        val startTimeMs: Long,
        var endTimeMs: Long,
        val words: MutableList<Word>,
        val isBackground: Boolean,
    )

    private val lrcTagRegex = Regex("""^\[([a-z]+):([^\]]+)]$""")
    private val lrcTimeRegex = Regex("""^\[((?:\d+:)*\d+(?:\.\d+)?)](.*)$""")
    private val backgroundRegex = Regex("""^[（(](.+)[）)]$""")
    private val yrcLineRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val yrcWordRegex = Regex("""\((\d+),(\d+)(?:,\d+)?\)([^()]*)""")
    private val chinesePattern = Regex("[\\u4E00-\\u9FFF]")

    /**
     * Kotlin translation of `parseLrc()` from `@applemusic-like-lyrics/lyric` 1.0.2.
     */
    fun parseLrcLines(lyric: String): List<WordLine> {
        val parsed = mutableListOf<MutableLine>()

        lyric
            .split(Regex("""\r?\n"""))
            .asSequence()
            .map { it.trimEcmaScriptWhitespace() }
            .filter(String::isNotEmpty)
            .forEach { rawLine ->
                if (lrcTagRegex.matches(rawLine)) return@forEach

                var remainder = rawLine
                val timestamps = mutableListOf<Long>()
                while (true) {
                    val match = lrcTimeRegex.matchEntire(remainder) ?: break
                    val timestamp = parseLrcTime(match.groupValues[1]) ?: break
                    timestamps += timestamp
                    remainder = match.groupValues[2]
                }
                if (timestamps.isEmpty()) return@forEach

                var text = remainder.trimEcmaScriptWhitespace()
                val backgroundMatch = backgroundRegex.matchEntire(text)
                val isBackground = backgroundMatch != null
                if (backgroundMatch != null) text = backgroundMatch.groupValues[1]

                timestamps.forEach { timestamp ->
                    parsed += MutableLine(
                        startTimeMs = timestamp,
                        endTimeMs = MAX_LRC_TIMESTAMP_MS,
                        words = mutableListOf(
                            Word(
                                startTimeMs = timestamp,
                                endTimeMs = timestamp,
                                text = text,
                            ),
                        ),
                        isBackground = isBackground,
                    )
                }
            }

        // Kotlin's sortedBy is stable, matching modern JavaScript Array.prototype.sort.
        val sorted = parsed.sortedBy { it.startTimeMs }
        for (index in 0 until sorted.lastIndex) {
            val nextStart = sorted[index + 1].startTimeMs
            val line = sorted[index]
            line.endTimeMs = nextStart
            line.words[0] = line.words[0].copy(
                endTimeMs = nextStart,
                exactEndTimeMs = nextStart.toDouble(),
            )
        }

        return sorted
            .asSequence()
            // Upstream filters only the empty string. It intentionally retains whitespace inside
            // a parenthesized background line.
            .filter { it.words[0].text.isNotEmpty() }
            .map { line ->
                WordLine(
                    startTimeMs = line.startTimeMs,
                    endTimeMs = line.endTimeMs,
                    words = line.words.toList(),
                    isBackground = line.isBackground,
                    isDuet = false,
                )
            }
            .toList()
    }

    /**
     * Legacy one-line-per-timestamp adapter. Repeated timestamps necessarily collapse here; AMLL
     * code must use [parseLrcLines] when repetition or background-vocal metadata matters.
     */
    fun parse(lyric: String): LinkedHashMap<Long?, String?> =
        toLineLyricMap(parseLrcLines(lyric))

    /**
     * Kotlin translation of the custom YRC parser from the former AMLL host's `entry.js`.
     */
    fun parseYrc(lyric: String): List<WordLine> {
        val lines = lyric
            .split(Regex("""\r?\n"""))
            .asSequence()
            .mapNotNull { parseYrcLine(it.trimEcmaScriptWhitespace()) }
            .sortedBy { it.startTimeMs }
            .toMutableList()

        for (index in 0 until lines.lastIndex) {
            val line = lines[index]
            val nextStart = lines[index + 1].startTimeMs
            if (line.endTimeMs <= line.startTimeMs || line.endTimeMs > nextStart) {
                lines[index] = line.copy(
                    endTimeMs = nextStart,
                    exactEndTimeMs = nextStart.toDouble(),
                )
            }
        }
        return lines
    }

    fun toLineLyricMap(lines: List<WordLine>): LinkedHashMap<Long?, String?> {
        val map = LinkedHashMap<Long?, String?>()
        lines.sortedWith(compareBy<WordLine> { it.startTimeMs }.thenBy { it.isBackground })
            .forEach { line ->
                val text = if (line.isBackground) "（${line.text}）" else line.text
                val current = map[line.startTimeMs]
                map[line.startTimeMs] = if (current.isNullOrBlank()) {
                    text
                } else {
                    "$current\n$text"
                }
            }
        return map
    }

    fun toLrcText(lines: List<WordLine>): String {
        return lines.sortedWith(compareBy<WordLine> { it.startTimeMs }.thenBy { it.isBackground })
            .joinToString(separator = "\n") { line ->
                val text = if (line.isBackground) "（${line.text}）" else line.text
                "${formatLrcTimestamp(line.startTimeMs)}$text"
            }
    }

    private fun parseYrcLine(line: String): WordLine? {
        val lineMatch = yrcLineRegex.matchEntire(line) ?: return null
        val lineStart = lineMatch.groupValues[1].toLongOrNull() ?: 0L
        val lineDuration = lineMatch.groupValues[2].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val body = lineMatch.groupValues[3]
        val words = yrcWordRegex
            .findAll(body)
            .mapNotNull { match ->
                val rawStart = match.groupValues[1].toLongOrNull() ?: 0L
                val duration = match.groupValues[2].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val text = match.groupValues[3]
                if (text.isEmpty()) return@mapNotNull null

                val start = normalizeYrcWordStart(lineStart, lineDuration, rawStart)
                Word(
                    startTimeMs = start,
                    endTimeMs = start + duration,
                    text = text,
                )
            }
            .toList()

        if (words.isEmpty()) return null

        return WordLine(
            startTimeMs = lineStart,
            endTimeMs = maxOf(lineStart + lineDuration, words.maxOf { it.endTimeMs }),
            words = words,
            translatedLyric = "",
            romanLyric = "",
            isBackground = false,
            isDuet = false,
        )
    }

    private fun normalizeYrcWordStart(lineStart: Long, lineDuration: Long, rawStart: Long): Long {
        return if (rawStart < lineStart && rawStart <= lineDuration) {
            lineStart + rawStart
        } else {
            rawStart
        }
    }

    private fun parseLrcTime(timeString: String): Long? {
        val parts = timeString.split(":")
        var multiplierSeconds = 1.0
        var totalSeconds = 0.0
        for (part in parts.asReversed()) {
            val value = part.toDoubleOrNull() ?: return null
            totalSeconds += value * multiplierSeconds
            multiplierSeconds *= 60.0
        }
        val milliseconds = totalSeconds * 1_000.0
        if (!milliseconds.isFinite()) return null
        return milliseconds.roundToLong()
    }

    internal fun formatLrcTimestamp(timeMs: Long): String {
        val safe = timeMs.coerceAtLeast(0L)
        val totalSeconds = safe / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val milliseconds = safe % 1000L
        return "[${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${
            milliseconds.toString().padStart(3, '0')
        }]"
    }

    fun isLyricContainsChinese(lyric: String): Boolean {
        return chinesePattern.containsMatchIn(lyric)
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
}

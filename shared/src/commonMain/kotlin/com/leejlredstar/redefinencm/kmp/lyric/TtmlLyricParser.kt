package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.util.LyricParser
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming
import kotlin.math.roundToLong

/**
 * Common TTML parser for non-WebView targets, notifications and source validation.
 *
 * Android/Windows AMLL still receive the original TTML and parse it with AMLL's official
 * `parseTTML`, preserving advanced ruby/background-vocal metadata. This parser extracts the
 * interoperable timed-line subset needed by shared Compose and native lyric surfaces.
 */
object TtmlLyricParser {
    fun parse(ttml: String): List<LyricParser.WordLine> {
        require(ttml.length <= MAX_TTML_CHARS) { "TTML document is too large" }
        require(!ttml.contains("<!DOCTYPE", ignoreCase = true)) {
            "TTML document declarations are not supported"
        }

        val reader = xmlStreaming.newReader(ttml)
        var sawTtmlRoot = false
        var currentLine: LineBuilder? = null
        val spanStack = mutableListOf<SpanFrame>()
        val lines = mutableListOf<LyricParser.WordLine>()
        var elementDepth = 0
        var elementCount = 0
        var totalWordCount = 0

        try {
            while (reader.hasNext()) {
                when (val event = reader.next()) {
                    EventType.START_ELEMENT -> {
                        elementDepth += 1
                        elementCount += 1
                        require(elementDepth <= MAX_ELEMENT_DEPTH) { "TTML nesting is too deep" }
                        require(elementCount <= MAX_ELEMENT_COUNT) { "TTML has too many elements" }
                        when (reader.localName.lowercase()) {
                            "tt" -> sawTtmlRoot = true
                            "p" -> {
                                require(currentLine == null) { "Nested TTML lines are not supported" }
                                spanStack.clear()
                                currentLine = LineBuilder(
                                    startTimeMs = reader.timeAttribute("begin"),
                                    endTimeMs = reader.timeAttribute("end"),
                                    isDuet = reader.attribute("agent") == "v2",
                                )
                            }
                            "span" -> currentLine?.let {
                                require(spanStack.size < MAX_SPAN_DEPTH) {
                                    "TTML span nesting is too deep"
                                }
                                spanStack += SpanFrame(
                                    role = reader.attribute("role").orEmpty().lowercase(),
                                    startTimeMs = reader.timeAttribute("begin"),
                                    endTimeMs = reader.timeAttribute("end"),
                                )
                            }
                        }
                    }

                    EventType.TEXT,
                    EventType.CDSECT,
                    EventType.ENTITY_REF,
                    EventType.IGNORABLE_WHITESPACE,
                    -> currentLine?.let { line ->
                        val text = reader.text
                        val active = spanStack.lastOrNull()
                        if (active == null) {
                            line.appendText(text)
                        } else {
                            active.appendText(text)
                        }
                    }

                    EventType.END_ELEMENT -> {
                        when (reader.localName.lowercase()) {
                            "span" -> {
                                val line = currentLine
                                val frame = spanStack.removeLastOrNull()
                                if (line != null && frame != null) {
                                    val parent = spanStack.lastOrNull()
                                    if (parent == null) {
                                        line.accept(frame)
                                    } else {
                                        parent.acceptChild(frame)
                                    }
                                }
                            }
                            "p" -> {
                                val builtLines = currentLine?.build().orEmpty()
                                totalWordCount += builtLines.sumOf { it.words.size }
                                require(lines.size + builtLines.size <= MAX_LINE_COUNT) {
                                    "TTML has too many lyric lines"
                                }
                                require(totalWordCount <= MAX_TOTAL_WORD_COUNT) {
                                    "TTML has too many timed words"
                                }
                                lines += builtLines
                                currentLine = null
                                spanStack.clear()
                            }
                            else -> Unit
                        }
                        elementDepth = (elementDepth - 1).coerceAtLeast(0)
                    }

                    else -> Unit
                }
            }
        } finally {
            reader.close()
        }

        require(sawTtmlRoot) { "Not a TTML document" }
        return lines.sortedWith(
            compareBy<LyricParser.WordLine> { it.startTimeMs }
                .thenBy { it.isBackground },
        )
    }

    private class LineBuilder(
        val startTimeMs: Long?,
        val endTimeMs: Long?,
        val isDuet: Boolean,
    ) {
        val mainText = StringBuilder()
        val words = mutableListOf<LyricParser.Word>()
        val backgroundFrames = mutableListOf<SpanFrame>()
        var translatedLyric = ""
        var romanLyric = ""
        private val pendingUntimedText = StringBuilder()

        fun appendText(rawText: String) {
            mainText.append(rawText)
            val mixedText = rawText.mixedContentText()
            when {
                mixedText.isEmpty() -> Unit
                words.isNotEmpty() -> {
                    val last = words.last()
                    words[words.lastIndex] = last.copy(text = last.text + mixedText)
                }
                mixedText.isNotBlank() -> pendingUntimedText.append(mixedText)
            }
        }

        fun accept(frame: SpanFrame) {
            when {
                frame.role == ROLE_TRANSLATION -> {
                    val candidate = frame.text.toString().trim()
                    if (translatedLyric.isEmpty() && candidate.isNotEmpty()) {
                        translatedLyric = candidate
                    }
                }
                frame.role == ROLE_ROMAN -> {
                    val candidate = frame.text.toString().trim()
                    if (romanLyric.isEmpty() && candidate.isNotEmpty()) {
                        romanLyric = candidate
                    }
                }
                frame.role == ROLE_BACKGROUND -> backgroundFrames += frame
                else -> {
                    mainText.append(frame.text)
                    backgroundFrames += frame.backgroundFrames
                    addWords(normalizeWords(frame))
                }
            }
            require(words.size <= MAX_WORDS_PER_LINE) { "TTML line has too many timed words" }
            require(backgroundFrames.size <= MAX_BACKGROUND_LINES_PER_LINE) {
                "TTML line has too many background vocals"
            }
        }

        private fun addWords(newWords: List<LyricParser.Word>) {
            if (newWords.isEmpty()) return
            if (pendingUntimedText.isNotEmpty()) {
                val first = newWords.first()
                words += first.copy(text = pendingUntimedText.toString() + first.text)
                pendingUntimedText.clear()
                words += newWords.drop(1)
            } else {
                words += newWords
            }
        }

        fun build(): List<LyricParser.WordLine> {
            val backgroundLines = backgroundFrames.mapNotNull(::buildBackgroundLine)
            val start = startTimeMs
                ?: words.minOfOrNull { it.startTimeMs }
                ?: return backgroundLines
            val plainText = mainText.toString().trim()
            val normalizedWords = if (words.isNotEmpty()) {
                words.sortedBy { it.startTimeMs }
            } else {
                if (plainText.isEmpty()) return backgroundLines
                listOf(
                    LyricParser.Word(
                        startTimeMs = start,
                        endTimeMs = (endTimeMs ?: start).coerceAtLeast(start),
                        text = plainText,
                    ),
                )
            }
            val end = maxOf(
                endTimeMs ?: start,
                normalizedWords.maxOf { it.endTimeMs },
                start,
            )
            val primaryLine = LyricParser.WordLine(
                startTimeMs = start,
                endTimeMs = end,
                words = normalizedWords,
                translatedLyric = translatedLyric,
                romanLyric = romanLyric,
                isDuet = isDuet,
            )
            return listOf(primaryLine) + backgroundLines
        }

        private fun buildBackgroundLine(frame: SpanFrame): LyricParser.WordLine? {
            val normalizedWords = normalizeWords(frame)
            val start = frame.startTimeMs
                ?.let { normalizeChildTime(startTimeMs ?: 0L, endTimeMs, it) }
                ?: normalizedWords.minOfOrNull { it.startTimeMs }
                ?: startTimeMs
                ?: return null
            val text = frame.text.toString().trim()
            val finalWords = normalizedWords.ifEmpty {
                if (text.isEmpty()) return null
                listOf(
                    LyricParser.Word(
                        startTimeMs = start,
                        endTimeMs = frame.endTimeMs
                            ?.let { normalizeChildTime(startTimeMs ?: 0L, endTimeMs, it) }
                            ?.coerceAtLeast(start)
                            ?: (endTimeMs ?: start).coerceAtLeast(start),
                        text = text,
                    ),
                )
            }
            val end = maxOf(
                frame.endTimeMs
                    ?.let { normalizeChildTime(startTimeMs ?: 0L, endTimeMs, it) }
                    ?: start,
                finalWords.maxOf { it.endTimeMs },
                start,
            )
            return LyricParser.WordLine(
                startTimeMs = start,
                endTimeMs = end,
                words = finalWords,
                translatedLyric = frame.translatedLyric,
                romanLyric = frame.romanLyric,
                isBackground = true,
                isDuet = isDuet,
            )
        }

        private fun normalizeWords(frame: SpanFrame): List<LyricParser.Word> {
            val rawWords = frame.words.ifEmpty {
                val text = frame.text.toString()
                val rawStart = frame.startTimeMs
                if (text.isBlank() || rawStart == null) {
                    emptyList()
                } else {
                    listOf(
                        LyricParser.Word(
                            startTimeMs = rawStart,
                            endTimeMs = frame.endTimeMs ?: rawStart,
                            text = text,
                        ),
                    )
                }
            }
            val lineStart = startTimeMs ?: 0L
            return rawWords.map { word ->
                val start = normalizeChildTime(lineStart, endTimeMs, word.startTimeMs)
                val end = normalizeChildTime(lineStart, endTimeMs, word.endTimeMs)
                    .coerceAtLeast(start)
                word.copy(startTimeMs = start, endTimeMs = end)
            }
        }
    }

    private class SpanFrame(
        val role: String,
        val startTimeMs: Long?,
        val endTimeMs: Long?,
        val text: StringBuilder = StringBuilder(),
    ) {
        val words = mutableListOf<LyricParser.Word>()
        val backgroundFrames = mutableListOf<SpanFrame>()
        var translatedLyric = ""
        var romanLyric = ""
        private val pendingUntimedText = StringBuilder()

        fun appendText(rawText: String) {
            text.append(rawText)
            val mixedText = rawText.mixedContentText()
            when {
                mixedText.isEmpty() -> Unit
                words.isNotEmpty() -> {
                    val last = words.last()
                    words[words.lastIndex] = last.copy(text = last.text + mixedText)
                }
                mixedText.isNotBlank() -> pendingUntimedText.append(mixedText)
            }
        }

        fun acceptChild(child: SpanFrame) {
            when (child.role) {
                ROLE_TRANSLATION -> {
                    val candidate = child.text.toString().trim()
                    if (translatedLyric.isEmpty() && candidate.isNotEmpty()) {
                        translatedLyric = candidate
                    }
                }
                ROLE_ROMAN -> {
                    val candidate = child.text.toString().trim()
                    if (romanLyric.isEmpty() && candidate.isNotEmpty()) {
                        romanLyric = candidate
                    }
                }
                ROLE_BACKGROUND -> backgroundFrames += child
                else -> {
                    text.append(child.text)
                    backgroundFrames += child.backgroundFrames
                    val childWords = if (child.words.isNotEmpty()) {
                        child.words
                    } else {
                        val childText = child.text.toString()
                        val start = child.startTimeMs
                        if (childText.isNotBlank() && start != null) {
                            listOf(
                                LyricParser.Word(
                                    startTimeMs = start,
                                    endTimeMs = child.endTimeMs ?: start,
                                    text = childText,
                                ),
                            )
                        } else {
                            emptyList()
                        }
                    }
                    addWords(childWords)
                }
            }
            require(words.size <= MAX_WORDS_PER_LINE) { "TTML line has too many timed words" }
            require(backgroundFrames.size <= MAX_BACKGROUND_LINES_PER_LINE) {
                "TTML line has too many background vocals"
            }
        }

        private fun addWords(newWords: List<LyricParser.Word>) {
            if (newWords.isEmpty()) return
            if (pendingUntimedText.isNotEmpty()) {
                val first = newWords.first()
                words += first.copy(text = pendingUntimedText.toString() + first.text)
                pendingUntimedText.clear()
                words += newWords.drop(1)
            } else {
                words += newWords
            }
        }
    }

    private fun XmlReader.attribute(localName: String): String? {
        for (index in 0 until attributeCount) {
            if (getAttributeLocalName(index).equals(localName, ignoreCase = true)) {
                return getAttributeValue(index)
            }
        }
        return null
    }

    private fun XmlReader.timeAttribute(localName: String): Long? =
        attribute(localName)?.let(::parseTtmlTime)

    internal fun parseTtmlTime(value: String): Long? {
        val raw = value.trim().lowercase()
        if (raw.isEmpty()) return null
        return when {
            raw.endsWith("ms") ->
                raw.dropLast(2).toDoubleOrNull()?.roundToLong()
            raw.endsWith("s") ->
                raw.dropLast(1).toDoubleOrNull()?.times(1_000.0)?.roundToLong()
            ':' in raw -> {
                val parts = raw.split(':')
                when (parts.size) {
                    2 -> {
                        val minutes = parts[0].toLongOrNull() ?: return null
                        val seconds = parts[1].toDoubleOrNull() ?: return null
                        ((minutes * 60.0 + seconds) * 1_000.0).roundToLong()
                    }
                    3 -> {
                        val hours = parts[0].toLongOrNull() ?: return null
                        val minutes = parts[1].toLongOrNull() ?: return null
                        val seconds = parts[2].toDoubleOrNull() ?: return null
                        ((hours * 3_600.0 + minutes * 60.0 + seconds) * 1_000.0).roundToLong()
                    }
                    else -> null
                }
            }
            else -> raw.toDoubleOrNull()?.times(1_000.0)?.roundToLong()
        }?.coerceAtLeast(0L)
    }

    private fun normalizeChildTime(
        lineStart: Long,
        lineEnd: Long?,
        childTime: Long,
    ): Long {
        val lineDuration = lineEnd?.minus(lineStart)?.coerceAtLeast(0L)
        return if (
            lineStart > 0L &&
            childTime < lineStart &&
            lineDuration != null &&
            childTime <= lineDuration
        ) {
            lineStart + childTime
        } else {
            childTime
        }
    }

    private fun String.mixedContentText(): String =
        if (isBlank() && ('\n' in this || '\r' in this)) "" else this

    private const val ROLE_TRANSLATION = "x-translation"
    private const val ROLE_ROMAN = "x-roman"
    private const val ROLE_BACKGROUND = "x-bg"
    private const val MAX_TTML_CHARS = 2 * 1024 * 1024
    private const val MAX_ELEMENT_DEPTH = 64
    private const val MAX_SPAN_DEPTH = 32
    private const val MAX_ELEMENT_COUNT = 50_000
    private const val MAX_LINE_COUNT = 10_000
    private const val MAX_WORDS_PER_LINE = 4_096
    private const val MAX_BACKGROUND_LINES_PER_LINE = 64
    private const val MAX_TOTAL_WORD_COUNT = 100_000
}

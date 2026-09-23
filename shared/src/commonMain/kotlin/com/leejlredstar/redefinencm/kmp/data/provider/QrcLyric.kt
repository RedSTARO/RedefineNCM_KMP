package com.leejlredstar.redefinencm.kmp.data.provider

/**
 * QQ Music's word-timed lyrics (QRC), as the gateway hands them over once decrypted, turned into
 * the two forms the lyric pipeline parses.
 *
 * The decrypted document is XML whose `LyricContent` attribute holds lines like
 *
 * ```
 * [14273,3225]词(14273,184)曲(14457,272) (14729,0)作(14729,392)
 * ```
 *
 * The square brackets hold the line's start and length in milliseconds, and each word is
 * followed by its own absolute start and length. NetEase's YRC, which
 * [com.leejlredstar.amll.compose.lyric.LyricParser.parseYrc] reads, puts the timing in front of
 * the word instead: `[14273,3225](14273,184,0)词(14457,272,0)曲`. The shape was read from the
 * gateway's answer for a real track on 2026-09-23.
 */
internal object QrcLyric {
    /** The `LyricContent` of a decrypted QRC document, or null when [text] is not one. */
    fun contentOrNull(text: String): String? {
        if (!text.trimStart().startsWith("<")) return null
        val start = text.indexOf(ContentAttribute).takeIf { it >= 0 } ?: return null
        val valueStart = start + ContentAttribute.length
        // An attribute value cannot hold a raw quote; the first one closes it.
        val valueEnd = text.indexOf('"', valueStart).takeIf { it >= 0 } ?: return null
        return unescapeXml(text.substring(valueStart, valueEnd))
    }

    /** QRC lines as YRC. Lines that are not timed, such as `[ti:…]`, are dropped. */
    fun toYrc(content: String): String = timedLines(content).joinToString("\n") { line ->
        buildString {
            append('[').append(line.start).append(',').append(line.duration).append(']')
            line.words.forEach { word ->
                append('(').append(word.start).append(',').append(word.duration).append(",0)")
                // YRC reads a word up to the next parenthesis, so a bracketed word would be cut
                // short; the full-width pair reads the same and survives.
                append(word.text.replace('(', '（').replace(')', '）'))
            }
        }
    }

    /** QRC lines as line-timed LRC, for the line lyric and for a romanization supplement. */
    fun toLrc(content: String): String = timedLines(content).joinToString("\n") { line ->
        "${lrcTimestamp(line.start)}${line.words.joinToString("") { it.text }}"
    }

    private class Word(val text: String, val start: Long, val duration: Long)

    private class Line(val start: Long, val duration: Long, val words: List<Word>)

    private fun timedLines(content: String): List<Line> =
        content.lineSequence()
            .mapNotNull { raw ->
                val match = LinePattern.matchEntire(raw.trim()) ?: return@mapNotNull null
                val words = WordPattern.findAll(match.groupValues[3]).mapNotNull { word ->
                    val text = word.groupValues[1]
                    if (text.isEmpty()) return@mapNotNull null
                    Word(
                        text = text,
                        start = word.groupValues[2].toLongOrNull() ?: return@mapNotNull null,
                        duration = word.groupValues[3].toLongOrNull() ?: return@mapNotNull null,
                    )
                }.toList()
                if (words.isEmpty()) return@mapNotNull null
                Line(
                    start = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null,
                    duration = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null,
                    words = words,
                )
            }
            .toList()

    private fun lrcTimestamp(millis: Long): String {
        val minutes = millis / 60_000
        val seconds = (millis % 60_000) / 1_000
        val hundredths = (millis % 1_000) / 10
        return "[${minutes.pad()}:${seconds.pad()}.${hundredths.pad()}]"
    }

    private fun Long.pad(): String = toString().padStart(2, '0')

    private fun unescapeXml(value: String): String = EntityPattern.replace(value) { match ->
        when (val entity = match.groupValues[1]) {
            "quot" -> "\""
            "apos" -> "'"
            "lt" -> "<"
            "gt" -> ">"
            "amp" -> "&"
            else -> when {
                entity.startsWith("#x") || entity.startsWith("#X") ->
                    entity.drop(2).toIntOrNull(16)?.let(::codePointText) ?: match.value
                entity.startsWith("#") ->
                    entity.drop(1).toIntOrNull()?.let(::codePointText) ?: match.value
                else -> match.value
            }
        }
    }

    private fun codePointText(codePoint: Int): String? = when {
        codePoint in 0..0xFFFF -> codePoint.toChar().toString()
        codePoint in 0x10000..0x10FFFF -> {
            val offset = codePoint - 0x10000
            charArrayOf(
                (0xD800 + (offset shr 10)).toChar(),
                (0xDC00 + (offset and 0x3FF)).toChar(),
            ).concatToString()
        }
        else -> null
    }

    private const val ContentAttribute = "LyricContent=\""
    private val LinePattern = Regex("""^\[(\d+),(\d+)](.*)$""")

    /** A word, lazily up to the first `(start,length)` that follows it. */
    private val WordPattern = Regex("""(.*?)\((\d+),(\d+)\)""")
    private val EntityPattern = Regex("""&(#?[A-Za-z0-9]+);""")
}

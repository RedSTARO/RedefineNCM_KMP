package com.leejlredstar.redefinencm.kmp.util

/**
 * LRC/YRC lyric parser.
 * Pure Kotlin, no platform dependencies.
 */
object LyricParser {

    data class Word(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val text: String,
    )

    data class WordLine(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val words: List<Word>,
    ) {
        val text: String
            get() = words.joinToString(separator = "") { it.text }
    }

    private val regexWord = Regex(".*](.*)")
    private val regexTime = Regex("\\[([0-9.:]*)]")
    private val yrcLineRegex = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val yrcWordRegex = Regex("""\((\d+),(\d+)(?:,\d+)?\)([^()]*)""")
    private val chinesePattern = Regex("[\\u4E00-\\u9FFF]")

    fun parse(lyric: String): LinkedHashMap<Long?, String?> {
        val lyricPair = LinkedHashMap<Long?, String?>()

        lyric.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach

            val matchWord = regexWord.find(line)
            val word = matchWord?.groups?.get(1)?.value

            val matchTime = regexTime.findAll(line)
            for (item in matchTime) {
                val timeString = item.groups[1]?.value ?: continue
                // 畸形时间戳（空括号 []、缺秒 [00:] 等）只跳过该标签，不让整首歌词失效
                val time = runCatching { parseTimeString(timeString) }.getOrNull() ?: continue
                lyricPair[time] = word
            }
        }

        val sorted = LinkedHashMap<Long?, String?>()
        lyricPair.entries
            .sortedWith(compareBy { it.key ?: Long.MAX_VALUE })
            .forEach { (key, value) -> sorted[key] = value }

        return sorted
    }

    fun parseYrc(lyric: String): List<WordLine> {
        return lyric
            .lineSequence()
            .mapNotNull { parseYrcLine(it.trim()) }
            .sortedBy { it.startTimeMs }
            .toList()
    }

    fun toLineLyricMap(lines: List<WordLine>): LinkedHashMap<Long?, String?> {
        val map = LinkedHashMap<Long?, String?>()
        lines.forEach { line ->
            map[line.startTimeMs] = line.text
        }
        return map
    }

    fun toLrcText(lines: List<WordLine>): String {
        return lines.joinToString(separator = "\n") { line ->
            "${formatLrcTimestamp(line.startTimeMs)}${line.text}"
        }
    }

    private fun parseYrcLine(line: String): WordLine? {
        val lineMatch = yrcLineRegex.matchEntire(line) ?: return null
        val lineStart = lineMatch.groupValues[1].toLongOrNull() ?: return null
        val lineDuration = lineMatch.groupValues[2].toLongOrNull() ?: 0L
        val body = lineMatch.groupValues[3]
        val rawWords = yrcWordRegex
            .findAll(body)
            .mapNotNull { match ->
                val rawStart = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
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

        if (rawWords.isEmpty()) return null

        val words = rawWords.mapIndexed { index, word ->
            val nextStart = rawWords.getOrNull(index + 1)?.startTimeMs
            val fallbackEnd = nextStart ?: (lineStart + lineDuration)
            val end = when {
                word.endTimeMs > word.startTimeMs -> word.endTimeMs
                fallbackEnd > word.startTimeMs -> fallbackEnd
                else -> word.startTimeMs
            }
            word.copy(endTimeMs = end)
        }
        val lineEnd = maxOf(lineStart + lineDuration, words.maxOf { it.endTimeMs })
        return WordLine(
            startTimeMs = lineStart,
            endTimeMs = lineEnd.coerceAtLeast(lineStart),
            words = words,
        )
    }

    private fun normalizeYrcWordStart(lineStart: Long, lineDuration: Long, rawStart: Long): Long {
        return if (rawStart < lineStart && rawStart <= lineDuration) {
            lineStart + rawStart
        } else {
            rawStart
        }
    }

    private fun parseTimeString(timeString: String): Long {
        val parts = timeString.split(":")
        val minutes = parts[0].toInt()
        val seconds = parts[1].toFloat()
        return ((minutes * 60 + seconds) * 1000).toLong()
    }

    internal fun formatLrcTimestamp(timeMs: Long): String {
        val safe = timeMs.coerceAtLeast(0L)
        val totalSeconds = safe / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val centiseconds = safe % 1000L / 10L
        return "[${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${centiseconds.toString().padStart(2, '0')}]"
    }

    fun isLyricContainsChinese(lyric: String): Boolean {
        return chinesePattern.containsMatchIn(lyric)
    }
}

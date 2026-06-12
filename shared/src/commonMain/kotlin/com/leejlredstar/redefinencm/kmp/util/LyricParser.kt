package com.leejlredstar.redefinencm.kmp.util

/**
 * LRC lyric parser.
 * Ported from the original Android LyricParser — pure Kotlin, no platform dependencies.
 */
object LyricParser {

    private val regexWord = Regex(".*](.*)")
    private val regexTime = Regex("\\[([0-9.:]*)]")
    private val chinesePattern = Regex("[一-龥]")

    /**
     * Parse LRC lyric text into a time-sorted LinkedHashMap.
     * @param lyric Raw LRC text
     * @return Sorted map of timeMillis → lyric line
     */
    fun parse(lyric: String): LinkedHashMap<Long?, String?> {
        val lyricPair = LinkedHashMap<Long?, String?>()

        lyric.lines().forEach { line ->
            if (line.isBlank()) return@forEach

            val matchWord = regexWord.find(line)
            val word = matchWord?.groups?.get(1)?.value

            val matchTime = regexTime.findAll(line)
            for (item in matchTime) {
                val timeString = item.groups[1]?.value ?: continue
                val time = parseTimeString(timeString)
                lyricPair[time] = word
            }
        }

        // Sort by time (toSortedMap is JVM-only; sortedWith works on all KMP targets)
        val sorted = LinkedHashMap<Long?, String?>()
        lyricPair.entries
            .sortedWith(compareBy { it.key ?: Long.MAX_VALUE })
            .forEach { (key, value) -> sorted[key] = value }

        return sorted
    }

    private fun parseTimeString(timeString: String): Long {
        val parts = timeString.split(":")
        val minutes = parts[0].toInt()
        val seconds = parts[1].toFloat()
        return ((minutes * 60 + seconds) * 1000).toLong()
    }

    /**
     * Check if the lyric contains Chinese characters.
     */
    fun isLyricContainsChinese(lyric: String): Boolean {
        return chinesePattern.containsMatchIn(lyric)
    }
}

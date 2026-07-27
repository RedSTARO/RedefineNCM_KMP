package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricParserTest {

    @Test
    fun parseYrcMatchesBridgeWordTimingAndRelativeStartRules() {
        val lines = LyricParser.parseYrc(
            """
            [1000,1500](1000,500,0)hello(1500,0,0) (2000,500,0)world
            [3000,800](0,400,0)next(400,400,0)line
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].startTimeMs)
        assertEquals("hello world", lines[0].text)
        assertEquals(1500L, lines[0].words[1].startTimeMs)
        assertEquals(1500L, lines[0].words[1].endTimeMs)
        assertEquals(3000L, lines[1].startTimeMs)
        assertEquals(3400L, lines[1].words[1].startTimeMs)
        assertFalse(lines.any { it.isBackground })
        assertFalse(lines.any { it.isDuet })
    }

    @Test
    fun parseYrcClampsOnlyLineEndAgainstNextLine() {
        val lines = LyricParser.parseYrc(
            """
            [1000,5000](1000,6000,0)long
            [3000,500](3000,500,0)next
            """.trimIndent(),
        )

        assertEquals(3000L, lines[0].endTimeMs)
        assertEquals(7000L, lines[0].words.single().endTimeMs)
        assertEquals(3500L, lines[1].endTimeMs)
    }

    @Test
    fun parseLrcLinesRetainsRepeatedTimestampsAndBackgroundRoles() {
        val lines = LyricParser.parseLrcLines(
            """
            [ar:artist]
            [00:01.000][00:02]main
            [00:01.000](echo)
            [00:03.250]（中文和声）
            """.trimIndent(),
        )

        assertEquals(4, lines.size)
        assertEquals(listOf(1000L, 1000L, 2000L, 3250L), lines.map { it.startTimeMs })
        assertEquals(listOf("main", "echo", "main", "中文和声"), lines.map { it.text })
        assertEquals(listOf(false, true, false, true), lines.map { it.isBackground })
        assertEquals(1000L, lines[0].endTimeMs)
        assertEquals(2000L, lines[1].endTimeMs)
        assertEquals(3250L, lines[2].endTimeMs)
        assertEquals(LyricParser.MAX_LRC_TIMESTAMP_MS, lines[3].endTimeMs)
    }

    @Test
    fun legacyMapAdapterDocumentsItsDuplicateCollapse() {
        val map = LyricParser.parse(
            """
            [00:01.000]main
            [00:01.000](echo)
            """.trimIndent(),
        )

        assertEquals(1, map.size)
        assertEquals("main\n（echo）", map[1000L])
    }

    @Test
    fun yrcCanFallbackToLineLyricMapAndThreeDigitLrcText() {
        val lines = LyricParser.parseYrc("[1200,600](1200,300,0)a(1500,300,0)b")

        assertEquals("ab", LyricParser.toLineLyricMap(lines)[1200L])
        assertEquals("[00:01.200]ab", LyricParser.toLrcText(lines))
    }

    @Test
    fun lrcSupportsMoreThanTwoTimeComponentsAndSkipsMalformedTimestamps() {
        val lines = LyricParser.parseLrcLines(
            """
            [1:02:03.004]long
            [00:]invalid
            []invalid
            """.trimIndent(),
        )

        assertEquals(1, lines.size)
        assertEquals(3_723_004L, lines.single().startTimeMs)
        assertTrue(lines.single().text == "long")
    }
}

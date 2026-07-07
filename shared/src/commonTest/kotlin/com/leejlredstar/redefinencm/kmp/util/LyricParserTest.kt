package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricParserTest {

    @Test
    fun parseYrcBuildsWordTimedLines() {
        val lines = LyricParser.parseYrc(
            """
            [1000,1500](1000,500,0)hello(1500,500,0) (2000,500,0)world
            [3000,800](0,400,0)next(400,400,0)line
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].startTimeMs)
        assertEquals("hello world", lines[0].text)
        assertEquals(1500L, lines[0].words[1].startTimeMs)
        assertEquals(3000L, lines[1].startTimeMs)
        assertEquals(3400L, lines[1].words[1].startTimeMs)
    }

    @Test
    fun yrcCanFallbackToLineLyricMapAndLrcText() {
        val lines = LyricParser.parseYrc("[1200,600](1200,300,0)a(1500,300,0)b")

        assertEquals("ab", LyricParser.toLineLyricMap(lines)[1200L])
        assertEquals("[00:01.20]ab", LyricParser.toLrcText(lines))
    }
}

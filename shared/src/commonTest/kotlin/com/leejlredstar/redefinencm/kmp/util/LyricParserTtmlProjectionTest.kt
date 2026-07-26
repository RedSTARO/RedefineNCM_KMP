package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricParserTtmlProjectionTest {
    @Test
    fun duplicateAndBackgroundLinesRemainVisibleInLineProjection() {
        val lines = listOf(
            line("lead", isBackground = false),
            line("duet", isBackground = false),
            line("back", isBackground = true),
        )

        val projected = LyricParser.toLineLyricMap(lines)

        assertEquals(1, projected.size)
        assertEquals("lead\nduet\n（back）", projected[1_000L])
        assertTrue(LyricParser.toLrcText(lines).contains("（back）"))
    }

    private fun line(text: String, isBackground: Boolean): LyricParser.WordLine =
        LyricParser.WordLine(
            startTimeMs = 1_000L,
            endTimeMs = 2_000L,
            words = listOf(LyricParser.Word(1_000L, 2_000L, text)),
            isBackground = isBackground,
        )
}

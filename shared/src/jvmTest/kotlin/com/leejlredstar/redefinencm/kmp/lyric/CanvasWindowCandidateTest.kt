package com.leejlredstar.redefinencm.kmp.lyric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CanvasWindowCandidateTest {
    @Test
    fun selectsOnlyCanvasWithMatchingPhysicalSize() {
        val candidates = listOf(
            CanvasWindowCandidate("compose-root", "SunAwtCanvas", 1280, 820),
            CanvasWindowCandidate("amll", "SunAwtCanvas", 1184, 772),
            CanvasWindowCandidate("panel", "SunAwtPanel", 1184, 772),
        )

        assertEquals(
            "amll",
            selectCanvasWindowCandidate(candidates, expectedWidth = 1184, expectedHeight = 772),
        )
    }

    @Test
    fun acceptsSmallNativeRoundingDifference() {
        val candidate = CanvasWindowCandidate("amll", "SunAwtCanvas1", 1185, 770)

        assertEquals(
            "amll",
            selectCanvasWindowCandidate(listOf(candidate), expectedWidth = 1184, expectedHeight = 772),
        )
    }

    @Test
    fun failsFastWhenMatchingCanvasIsAmbiguous() {
        val candidates = listOf(
            CanvasWindowCandidate("first", "SunAwtCanvas", 1184, 772),
            CanvasWindowCandidate("second", "SunAwtCanvas1", 1184, 772),
        )

        assertNull(selectCanvasWindowCandidate(candidates, 1184, 772))
    }

    @Test
    fun rejectsWrongWindowClassOrSize() {
        val candidates = listOf(
            CanvasWindowCandidate("panel", "SunAwtPanel", 1184, 772),
            CanvasWindowCandidate("root", "SunAwtCanvas", 1280, 820),
        )

        assertNull(selectCanvasWindowCandidate(candidates, 1184, 772))
    }
}

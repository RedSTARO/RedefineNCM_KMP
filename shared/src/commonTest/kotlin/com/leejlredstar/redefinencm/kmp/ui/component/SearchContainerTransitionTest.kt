package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchContainerTransitionTest {
    // Browse-pane-relative positions: the rail is outside this viewport.
    private val home = SearchContainerFrame(Rect(66f, 291f, 2046f, 445f), 77f, editorFraction = 0f)
    private val editor = SearchContainerFrame(Rect(66f, 99f, 2046f, 253f), 55f, editorFraction = 1f)
    private val largePane = Rect(0f, 77f, 2144f, 1672f)

    @Test
    fun oneSurfaceInterpolatesBothWaysWithoutAnUnsafeOvershoot() {
        assertEquals(home, home.towards(editor, 0f))
        assertEquals(editor, home.towards(editor, 1.1f))
        assertEquals(home, editor.towards(home, 1.1f))
        val halfway = SearchContainerFrame(Rect(66f, 195f, 2046f, 349f), 66f, editorFraction = 0.5f)
        assertEquals(halfway, home.towards(editor, 0.5f))
        assertEquals(halfway, editor.towards(home, 0.5f))
        assertEquals(halfway, halfway.towards(home, 0f), "rapid reversal starts at the visible frame")
    }

    @Test
    fun shorteningOnlyTheBottomForTheImeLeavesBothEndpointsEligible() {
        for (viewport in listOf(largePane, largePane.copy(bottom = 818f))) {
            assertTrue(visibleSearchEndpoint(home.bounds, home.bounds, viewport))
            assertTrue(visibleSearchEndpoint(editor.bounds, editor.bounds, viewport))
        }
    }

    @Test
    fun clippedLazyPillAndStaleOffPaneBoundsAreNotReplicated() {
        val clippedHome = home.bounds.copy(top = 320f)
        assertFalse(visibleSearchEndpoint(home.bounds, clippedHome, largePane))
        assertFalse(visibleSearchEndpoint(home.bounds.copy(top = 40f), home.bounds.copy(top = 40f), largePane))
        assertFalse(visibleSearchEndpoint(home.bounds, home.bounds, largePane.copy(right = 1100f)))
        assertFalse(visibleSearchEndpoint(Rect.Zero, Rect.Zero, largePane))
    }
}

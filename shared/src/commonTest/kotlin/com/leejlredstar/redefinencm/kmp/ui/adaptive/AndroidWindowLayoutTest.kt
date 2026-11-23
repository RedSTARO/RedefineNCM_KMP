package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidWindowLayoutTest {
    @Test
    fun navigationFollowsUsableWindowNotThePhysicalDisplay() {
        assertEquals(AndroidNavigationLayout.Floating, androidNavigationLayout(443.dp, 994.dp))
        assertEquals(AndroidNavigationLayout.Rail, androidNavigationLayout(852.dp, 883.dp))
        assertEquals(AndroidNavigationLayout.Compact, androidNavigationLayout(474.dp, 241.dp))
        assertEquals(AndroidNavigationLayout.Compact, androidNavigationLayout(608.dp, 304.dp))
        assertEquals(AndroidNavigationLayout.Compact, androidNavigationLayout(288.dp, 608.dp))
    }

    @Test
    fun railNeedsBothWidthAndHeightAndTallPhoneKeepsFloatingNavigation() {
        assertEquals(AndroidNavigationLayout.Compact, androidNavigationLayout(319.dp, 480.dp))
        assertEquals(AndroidNavigationLayout.Floating, androidNavigationLayout(320.dp, 480.dp))
        assertEquals(AndroidNavigationLayout.Floating, androidNavigationLayout(599.dp, 480.dp))
        assertEquals(AndroidNavigationLayout.Rail, androidNavigationLayout(600.dp, 480.dp))
        assertEquals(AndroidNavigationLayout.Compact, androidNavigationLayout(600.dp, 479.dp))
    }

    @Test
    fun verticalHingeIsProjectedFromWindowToInsetContentWithoutMirroring() {
        val fold = WindowFold(IntRect(380, 0, 420, 1_000), isVertical = true, isOccluding = true)
        val panes = fold.panesIn(IntOffset(40, 64), IntSize(720, 900), clearancePx = 8)
        assertEquals(FoldPanes(IntRect(0, 0, 332, 900), IntRect(388, 0, 720, 900)), panes)
    }

    @Test
    fun tabletopCreaseCanHaveZeroThickness() {
        val fold = WindowFold(IntRect(0, 400, 1_000, 400), isVertical = false)
        val panes = fold.panesIn(IntOffset(12, 20), IntSize(980, 740))
        assertEquals(FoldPanes(IntRect(0, 0, 980, 380), IntRect(0, 380, 980, 740)), panes)
    }

    @Test
    fun browsingKeepsOneLargestPaneOnlyForAnOpaqueHinge() {
        val fold = WindowFold(IntRect(380, 0, 420, 1_000), isVertical = true)
        val origin = IntOffset(40, 64)
        val size = IntSize(840, 900)
        assertEquals(IntRect(0, 0, 840, 900), fold.browsePaneIn(origin, size))
        assertEquals(IntRect(380, 0, 840, 900), fold.copy(isOccluding = true).browsePaneIn(origin, size))
    }

    @Test
    fun opaqueHingeAtEitherWindowEdgeKeepsTheRemainingContentOnscreen() {
        val vertical = WindowFold(IntRect(380, 0, 420, 1_000), isVertical = true, isOccluding = true)
        val origin = IntOffset(400, 64)
        assertEquals(IntRect(20, 0, 370, 600), vertical.browsePaneIn(origin, IntSize(370, 600)))
        assertEquals(IntRect(20, 0, 210, 480), vertical.browsePaneIn(origin, IntSize(210, 480)))
        assertEquals(IntRect(0, 0, 20, 600), vertical.browsePaneIn(IntOffset(360, 64), IntSize(40, 600)))
        val horizontal = WindowFold(IntRect(0, 380, 1_000, 420), isVertical = false, isOccluding = true)
        assertEquals(IntRect(0, 20, 600, 370), horizontal.browsePaneIn(IntOffset(64, 400), IntSize(600, 370)))
    }

    @Test
    fun hingeThatOnlyCrossesPartOfTheViewportChoosesTheLargestClearStrip() {
        val fold = WindowFold(IntRect(380, 0, 420, 100), isVertical = true, isOccluding = true)
        assertEquals(IntRect(0, 100, 800, 600), fold.browsePaneIn(IntOffset.Zero, IntSize(800, 600)))
        assertEquals(IntRect(0, 0, 800, 400), fold.browsePaneIn(IntOffset(0, 100), IntSize(800, 400)))
    }

    @Test
    fun completelyOccludedViewportHasNoUsablePane() {
        val fold = WindowFold(IntRect(380, 0, 420, 1_000), isVertical = true, isOccluding = true)
        assertEquals(IntRect(0, 0, 0, 0), fold.browsePaneIn(IntOffset(390, 64), IntSize(20, 600)))
    }

    @Test
    fun seamOutsideAResizedWindowDoesNotCreateEmptyPanes() {
        val fold = WindowFold(IntRect(380, 0, 420, 1_000), isVertical = true)
        assertNull(fold.panesIn(IntOffset(0, 0), IntSize(370, 740)))
        assertNull(fold.panesIn(IntOffset(0, 1_000), IntSize(800, 600)))
        assertNull(fold.panesIn(IntOffset(400, 0), IntSize(370, 740)))
        assertNull(fold.panesIn(IntOffset(0, 0), IntSize.Zero))
    }
}

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
        assertEquals(AndroidNavigationLayout.Floating, androidNavigationLayout(384.dp, 760.dp))
        assertEquals(AndroidNavigationLayout.Rail, androidNavigationLayout(600.dp, 800.dp))
        assertEquals(AndroidNavigationLayout.Rail, androidNavigationLayout(800.dp, 600.dp))
        assertEquals(AndroidNavigationLayout.Rail, androidNavigationLayout(480.dp, 290.dp))
        assertEquals(AndroidNavigationLayout.Compact, androidNavigationLayout(350.dp, 300.dp))
        assertEquals(AndroidNavigationLayout.Compact, androidNavigationLayout(800.dp, 200.dp))
        assertEquals(AndroidNavigationLayout.Compact, androidNavigationLayout(300.dp, 760.dp))
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
    fun seamOutsideAResizedWindowDoesNotCreateEmptyPanes() {
        val fold = WindowFold(IntRect(380, 0, 420, 1_000), isVertical = true)
        assertNull(fold.panesIn(IntOffset(0, 0), IntSize(370, 740)))
        assertNull(fold.panesIn(IntOffset(0, 1_000), IntSize(800, 600)))
        assertNull(fold.panesIn(IntOffset(400, 0), IntSize(370, 740)))
        assertNull(fold.panesIn(IntOffset(0, 0), IntSize.Zero))
    }
}

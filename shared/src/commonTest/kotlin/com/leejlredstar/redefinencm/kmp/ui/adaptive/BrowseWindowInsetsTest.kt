package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowseWindowInsetsTest {
    @Test
    fun browseObstructionsReserveRealEdgesWithoutCountingStatusOrBottomForWindowClassification() {
        val bars = WindowInsets(left = 8, top = 42, right = 24, bottom = 32)
        val density = Density(2f)
        val caption = WindowInsets(top = 70)
        val noCutout = WindowInsets(0, 0, 0, 0)
        assertEquals(
            IntRect(8, 0, 24, 0),
            browseObstructions(bars, noCutout, noCutout).asEdges(density, LayoutDirection.Ltr),
        )
        val tallCutout = WindowInsets(left = 117, top = 96)
        for (direction in LayoutDirection.entries) {
            assertEquals(IntRect(117, 96, 24, 0), browseObstructions(bars, tallCutout, caption).asEdges(density, direction))
            assertEquals(IntRect(8, 70, 24, 0), browseObstructions(bars, noCutout, caption).asEdges(density, direction))
        }
    }

    @Test
    fun paneTouchesOriginalEdgesForPaintingButDoesNotCrossAHinge() {
        val size = IntSize(800, 600)
        val edges = IntRect(120, 96, 24, 0)
        val vertical = WindowFold(IntRect(390, 0, 410, 600), isVertical = true, isOccluding = true)
        assertEquals(IntRect(410, 0, 800, 600), vertical.edgeToEdgeBrowsePane(IntOffset.Zero, size, edges))
        val horizontal = WindowFold(IntRect(0, 300, 800, 324), isVertical = false, isOccluding = true)
        assertEquals(IntRect(0, 324, 800, 600), horizontal.edgeToEdgeBrowsePane(IntOffset.Zero, size, edges))
        assertEquals(IntRect(0, 0, 800, 600), horizontal.copy(isOccluding = false).edgeToEdgeBrowsePane(IntOffset.Zero, size, edges))
    }
}

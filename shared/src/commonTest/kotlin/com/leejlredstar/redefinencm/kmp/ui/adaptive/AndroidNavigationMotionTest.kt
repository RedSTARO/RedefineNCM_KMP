package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.component.AndroidReflowWindow
import com.leejlredstar.redefinencm.kmp.ui.component.ReflowChange
import com.leejlredstar.redefinencm.kmp.ui.component.playbackPaneArrangement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidNavigationMotionTest {
    private val cover = target(443, 994)
    private val inner = target(852, 883)
    private val split = target(852, 306, multiWindow = true)

    @Test
    fun aWindowHandoffChangesSlotsInBothDirectionsButANearSquareRotationKeepsTheRail() {
        assertEquals(ReflowChange.WidthClass, androidNavigationReflowChange(cover, inner))
        assertEquals(ReflowChange.WidthClass, androidNavigationReflowChange(inner, cover))
        assertEquals(ReflowChange.None, androidNavigationReflowChange(inner, target(883, 852, rotation = 1)))
    }

    @Test
    fun aDockAndRailChangeOnWindowModeOrRotationButNotOnDividerDrag() {
        val tallSplit = target(852, 620, multiWindow = true)
        assertEquals(ReflowChange.Window, androidNavigationReflowChange(inner, split))
        assertEquals(ReflowChange.Window, androidNavigationReflowChange(split, inner))
        assertEquals(ReflowChange.None, androidNavigationReflowChange(tallSplit, split))
        assertEquals(ReflowChange.None, androidNavigationReflowChange(split, tallSplit))
        assertEquals(ReflowChange.Window, androidNavigationReflowChange(split, tallSplit.copy(window = tallSplit.window!!.copy(rotation = 1))))
    }

    @Test
    fun theSameDockChangesWhenAnOpaqueHingeMovesItBetweenPhysicalPanes() {
        val lower = WindowFold(IntRect(0, 416, 852, 452), isVertical = false, isOccluding = true)
        val upper = lower.copy(bounds = IntRect(0, 452, 852, 487))
        val lowerDock = target(852, 883, fold = lower)
        val upperDock = target(852, 883, fold = upper)
        assertEquals(AndroidNavigationLayout.Compact, lowerDock.layout)
        assertEquals(AndroidNavigationLayout.Compact, upperDock.layout)
        assertEquals(ReflowChange.Pane, androidNavigationReflowChange(inner, lowerDock))
        assertEquals(ReflowChange.Pane, androidNavigationReflowChange(lowerDock, inner))
        assertEquals(ReflowChange.Pane, androidNavigationReflowChange(lowerDock, upperDock))
        assertEquals(ReflowChange.None, androidNavigationReflowChange(lowerDock, target(852, 883, fold = lower.copy(bounds = IntRect(0, 420, 852, 454)))))
        assertEquals(ReflowChange.None, androidNavigationReflowChange(inner, target(852, 883, fold = lower.copy(isOccluding = false))))
        assertEquals(ReflowChange.None, androidNavigationReflowChange(inner, target(852, 883, fold = lower.copy(bounds = IntRect(0, 900, 852, 930)))))
    }

    @Test
    fun onlyIndependentGeometryDuringImeMayAnimate() {
        val transientDock = split.copy(window = inner.window)
        assertEquals(ReflowChange.None, androidNavigationReflowChange(inner, transientDock, keyboardChanging = true))
        assertEquals(ReflowChange.WidthClass, androidNavigationReflowChange(cover, inner, keyboardChanging = true))
    }

    @Test
    fun fabSlotChangesOnlyWithGeometrySoRouteAndMediaCanUseTheirExistingVisibilityAnimation() {
        assertEquals(cover.layout, androidMiniPlayerFabSlot(cover.layout, keyboardVisible = false, fits = true))
        assertEquals(inner.layout, androidMiniPlayerFabSlot(inner.layout, keyboardVisible = false, fits = true))
        assertNull(androidMiniPlayerFabSlot(split.layout, keyboardVisible = false, fits = true))
        assertNull(androidMiniPlayerFabSlot(cover.layout, keyboardVisible = true, fits = true))
        assertNull(androidMiniPlayerFabSlot(inner.layout, keyboardVisible = false, fits = false))
    }

    private fun target(
        width: Int,
        height: Int,
        fold: WindowFold? = null,
        multiWindow: Boolean = false,
        rotation: Int = 0,
    ): AndroidNavigationTarget {
        val size = IntSize(width, height)
        val full = IntRect(0, 0, width, height)
        val pane = fold?.browsePaneIn(IntOffset.Zero, size) ?: full
        return AndroidNavigationTarget(
            androidNavigationLayout(pane.width.dp, pane.height.dp),
            DpSize(pane.width.dp, pane.height.dp),
            playbackPaneArrangement(full, pane),
            fold,
            AndroidReflowWindow(size, rotation, multiWindow),
        )
    }
}

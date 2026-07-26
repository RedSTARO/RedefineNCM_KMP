package com.leejlredstar.redefinencm.kmp.ui.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopOverlayWindowShapeTest {
    @Test
    fun rectangularOverlayDoesNotInstallAnAwtShape() {
        assertNull(
            desktopOverlayWindowShape(
                windowShape = DesktopOverlayWindowShape.Rectangle,
                width = 652,
                height = 240,
            ),
        )
    }

    @Test
    fun expandedControlsExposeOnlyTheTwoRoundedControlSurfaces() {
        val shape = assertNotNull(
            desktopOverlayWindowShape(
                windowShape = DesktopOverlayWindowShape.ExpandedPlaybackControls,
                width = 652,
                height = 240,
            ),
        )

        assertTrue(shape.contains(326.0, 80.0))
        assertTrue(shape.contains(326.0, 160.5))
        assertTrue(shape.contains(326.0, 200.0))
        assertFalse(shape.contains(326.0, 164.0))
        assertFalse(shape.contains(4.0, 120.0))
    }

    @Test
    fun collapsedControlsExcludeTheHostMargins() {
        val shape = assertNotNull(
            desktopOverlayWindowShape(
                windowShape = DesktopOverlayWindowShape.CollapsedPlaybackControls,
                width = 436,
                height = 64,
            ),
        )

        assertTrue(shape.contains(218.0, 32.0))
        assertFalse(shape.contains(2.0, 32.0))
        assertFalse(shape.contains(218.0, 2.0))
    }
}

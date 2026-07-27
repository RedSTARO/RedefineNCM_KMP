package com.leejlredstar.redefinencm.kmp.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongWikiDetailsTest {
    @Test
    fun cssOneHundredThirtyFiveDegreeGradientUsesMagicCornerGeometry() {
        val square = cssLinearGradientAxis(
            width = 100f,
            height = 100f,
            angleDegrees = 135f,
        )
        assertEquals(0f, square.start.x, absoluteTolerance = 0.001f)
        assertEquals(0f, square.start.y, absoluteTolerance = 0.001f)
        assertEquals(100f, square.end.x, absoluteTolerance = 0.001f)
        assertEquals(100f, square.end.y, absoluteTolerance = 0.001f)

        val wideHero = cssLinearGradientAxis(
            width = 720f,
            height = 192f,
            angleDegrees = 135f,
        )
        assertEquals(132f, wideHero.start.x, absoluteTolerance = 0.001f)
        assertEquals(-132f, wideHero.start.y, absoluteTolerance = 0.001f)
        assertEquals(588f, wideHero.end.x, absoluteTolerance = 0.001f)
        assertEquals(324f, wideHero.end.y, absoluteTolerance = 0.001f)
    }

    @Test
    fun mobileGeometryIncludesOnlyTheSourceTopSafeArea() {
        val geometry = songWikiDialogGeometry(
            viewportWidthDp = 600f,
            viewportHeightDp = 800f,
            safeStartDp = 7f,
            safeTopDp = 24f,
            safeEndDp = 9f,
            safeBottomDp = 20f,
        )

        assertTrue(geometry.mobile)
        assertFalse(geometry.lowHeight)
        assertEquals(0f, geometry.overlayStartDp)
        assertEquals(72f, geometry.overlayTopDp)
        assertEquals(0f, geometry.overlayEndDp)
        assertEquals(0f, geometry.overlayBottomDp)
        assertEquals(728f, geometry.maxDialogHeightDp)
    }

    @Test
    fun lowHeightDesktopOverridesOnlyVerticalOverlayPadding() {
        val geometry = songWikiDialogGeometry(
            viewportWidthDp = 601f,
            viewportHeightDp = 560f,
            safeStartDp = 7f,
            safeTopDp = 24f,
            safeEndDp = 9f,
            safeBottomDp = 20f,
        )

        assertFalse(geometry.mobile)
        assertTrue(geometry.lowHeight)
        assertEquals(25f, geometry.overlayStartDp)
        assertEquals(10f, geometry.overlayTopDp)
        assertEquals(27f, geometry.overlayEndDp)
        assertEquals(10f, geometry.overlayBottomDp)
        assertEquals(540f, geometry.maxDialogHeightDp)
    }

    @Test
    fun desktopGeometryUsesSafeAreaAndEightySixViewportHeightCap() {
        val geometry = songWikiDialogGeometry(
            viewportWidthDp = 1_200f,
            viewportHeightDp = 900f,
            safeStartDp = 7f,
            safeTopDp = 24f,
            safeEndDp = 9f,
            safeBottomDp = 20f,
        )

        assertFalse(geometry.mobile)
        assertFalse(geometry.lowHeight)
        assertEquals(25f, geometry.overlayStartDp)
        assertEquals(42f, geometry.overlayTopDp)
        assertEquals(27f, geometry.overlayEndDp)
        assertEquals(38f, geometry.overlayBottomDp)
        assertEquals(774f, geometry.maxDialogHeightDp, absoluteTolerance = 0.001f)
    }
}

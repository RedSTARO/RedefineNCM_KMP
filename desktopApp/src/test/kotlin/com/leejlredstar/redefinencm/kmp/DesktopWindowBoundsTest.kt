package com.leejlredstar.redefinencm.kmp

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopWindowBoundsTest {
    /** A 1920x1080 screen with a 40-unit taskbar along the bottom. */
    private val laptop = Rectangle(0, 0, 1920, 1040)

    @Test
    fun aWindowThatFitsKeepsItsPlace() {
        val bounds = SavedWindowBounds(x = 100f, y = 80f, width = 1280f, height = 820f)
        assertEquals(bounds, bounds.fittedTo(listOf(laptop)))
    }

    @Test
    fun aWindowFromALargerMonitorIsShrunkAndMovedOntoTheScreen() {
        // Left on a 2560x1440 monitor, reopened on a laptop.
        val bounds = SavedWindowBounds(x = 900f, y = 200f, width = 2400f, height = 1300f, maximized = true)
        val fitted = bounds.fittedTo(listOf(laptop))
        assertEquals(SavedWindowBounds(x = 0f, y = 0f, width = 1920f, height = 1040f, maximized = true), fitted)
    }

    @Test
    fun aWindowPartlyOffTheBottomIsMovedUp() {
        val bounds = SavedWindowBounds(x = 100f, y = 600f, width = 1000f, height = 800f)
        assertEquals(240f, bounds.fittedTo(listOf(laptop))?.y)
    }

    @Test
    fun aWindowOnAScreenThatIsGoneIsNotRestored() {
        val bounds = SavedWindowBounds(x = 2500f, y = 100f, width = 800f, height = 600f)
        assertNull(bounds.fittedTo(listOf(laptop)))
    }

    @Test
    fun minimizedAndFullScreenPlacesAreNotSaved() {
        val screen = Rectangle(0, 0, 1920, 1080)
        assertTrue(isTransientWindowPlace(SavedWindowBounds(-32000f, -32000f, 160f, 28f), listOf(screen)))
        assertTrue(isTransientWindowPlace(SavedWindowBounds(0f, 0f, 1920f, 1080f), listOf(screen)))
        // A maximized window covers the usable area, and keeps the floating size it saved before.
        assertFalse(isTransientWindowPlace(SavedWindowBounds(0f, 0f, 1920f, 1080f, maximized = true), listOf(screen)))
        assertFalse(isTransientWindowPlace(SavedWindowBounds(100f, 80f, 1280f, 820f), listOf(screen)))
    }
}

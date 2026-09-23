package com.leejlredstar.redefinencm.kmp

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLayoutModeTest {
    @Test
    fun compactModeHandlesNarrowOrShortWindows() {
        assertEquals(DesktopLayoutMode.Compact, desktopLayoutMode(599.dp, 900.dp))
        assertEquals(DesktopLayoutMode.Compact, desktopLayoutMode(600.dp, 479.dp))
        assertEquals(DesktopLayoutMode.Compact, desktopLayoutMode(900.dp, 479.dp))
    }

    @Test
    fun railModeCoversEveryWindowWideAndTallEnough() {
        // Windows of 900 by 900 and more get no player card of their own: the playback bar
        // along the bottom serves them too, so there is no third mode.
        assertEquals(DesktopLayoutMode.Rail, desktopLayoutMode(600.dp, 480.dp))
        assertEquals(DesktopLayoutMode.Rail, desktopLayoutMode(899.dp, 900.dp))
        assertEquals(DesktopLayoutMode.Rail, desktopLayoutMode(1280.dp, 899.dp))
        assertEquals(DesktopLayoutMode.Rail, desktopLayoutMode(1600.dp, 1200.dp))
    }
}

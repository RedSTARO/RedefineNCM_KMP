package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLyricControlsWindowSizeTest {
    @Test
    fun usesAndroidControllerBoundsForExpandedAndCollapsedStates() {
        assertEquals(
            DpSize(652.dp, 320.dp),
            desktopLyricControlsWindowSize(
                availableWidth = 1_280.dp,
                availableHeight = 820.dp,
                expanded = true,
                sheetVisible = false,
            ),
        )
        assertEquals(
            DpSize(468.dp, 64.dp),
            desktopLyricControlsWindowSize(
                availableWidth = 1_280.dp,
                availableHeight = 820.dp,
                expanded = false,
                sheetVisible = false,
            ),
        )
    }

    @Test
    fun sheetTakesPriorityOverControllerExpansion() {
        assertEquals(
            DpSize(840.dp, 680.dp),
            desktopLyricControlsWindowSize(
                availableWidth = 1_280.dp,
                availableHeight = 820.dp,
                expanded = false,
                sheetVisible = true,
            ),
        )
    }

    @Test
    fun clampsEveryModeToTheAvailableOwnerContent() {
        assertEquals(
            DpSize(268.dp, 64.dp),
            desktopLyricControlsWindowSize(
                availableWidth = 300.dp,
                availableHeight = 80.dp,
                expanded = true,
                sheetVisible = false,
            ),
        )
        assertEquals(
            DpSize.Zero,
            desktopLyricControlsWindowSize(
                availableWidth = 24.dp,
                availableHeight = 8.dp,
                expanded = false,
                sheetVisible = false,
            ),
        )
    }
}

package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.calculateAmllLyricVisualParameters
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackLyricPaneTest {
    @Test
    fun landscapeWindowKeepsDefaultFontEvenWhenItsLyricPaneIsPortrait() {
        assertEquals(
            calculateAmllLyricVisualParameters(480f, 620f, reducedMotion = false),
            calculateNativeAmllVisualParameters(
                viewportWidth = 480.dp,
                viewportHeight = 620.dp,
                windowWidth = 960.dp,
                windowHeight = 640.dp,
                androidPresentation = true,
                reducedMotion = false,
            ),
        )
    }

    @Test
    fun portraitWindowKeepsCapWhenTheLyricPaneIsWideAndShort() {
        assertEquals(
            36f,
            calculateNativeAmllVisualParameters(
                viewportWidth = 600.dp,
                viewportHeight = 380.dp,
                windowWidth = 640.dp,
                windowHeight = 880.dp,
                androidPresentation = true,
                reducedMotion = false,
            ).baseFontSizeSp,
        )
    }
}

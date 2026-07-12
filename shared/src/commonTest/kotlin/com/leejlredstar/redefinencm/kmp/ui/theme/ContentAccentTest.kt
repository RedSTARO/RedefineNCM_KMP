package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentAccentTest {
    @Test
    fun blackAndWhiteHaveMaximumContrast() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), absoluteTolerance = 0.01f)
        assertEquals(21f, contrastRatio(Color.Black, Color.White), absoluteTolerance = 0.01f)
    }

    @Test
    fun lightBackgroundChoosesDarkContent() {
        val background = Color(0xFFE8EFEA)

        assertEquals(Color(0xFF101010), contentColorFor(background))
    }

    @Test
    fun darkBackgroundChoosesLightContent() {
        val background = Color(0xFF202622)

        assertEquals(Color.White, contentColorFor(background))
    }

    @Test
    fun middleBackgroundChoosesCandidateWithHigherContrast() {
        val background = Color(0xFF808080)
        val selected = contentColorFor(background)
        val rejected = if (selected == Color.White) Color(0xFF101010) else Color.White

        assertEquals(Color(0xFF101010), selected)
        assertTrue(contrastRatio(selected, background) > contrastRatio(rejected, background))
    }

    @Test
    fun translucentColorsAreCompositedBeforeMeasuringContrast() {
        val translucentWhite = Color.White.copy(alpha = 0.5f)

        assertEquals(
            5.32f,
            contrastRatio(translucentWhite, Color.Black),
            absoluteTolerance = 0.03f,
        )
    }

    @Test
    fun secondaryForegroundsRemainOpaqueAndMeetBodyTextContrast() {
        val backgrounds = listOf(
            Color(0xFF101410),
            Color(0xFF275D50),
            Color(0xFF808080),
            Color(0xFFDCE5DE),
            Color(0xFFF8FBF5),
        )

        backgrounds.forEach { background ->
            val foreground = secondaryContentColorFor(background)
            assertEquals(1f, foreground.alpha, absoluteTolerance = 0.001f)
            assertTrue(
                contrastRatio(foreground, background) >= 4.5f,
                "Expected AA contrast for $foreground on $background",
            )
        }
    }
}

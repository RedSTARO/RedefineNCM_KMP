package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ContentAccentPaletteTest {

    /** The saturated yellow that produced the olive page washes this palette was rewritten for. */
    private val loudYellow = Color(0xFFFFE94A)

    @Test
    fun pageTintsStayNearlyNeutralEvenForAMaximallySaturatedSource() {
        listOf(LightColors, DarkColors).forEach { scheme ->
            val palette = buildContentAccentPalette(loudYellow, scheme)
            listOf(
                "pageStart" to palette.pageStart,
                "pageMiddle" to palette.pageMiddle,
            ).forEach { (name, color) ->
                val chroma = color.toOklch().chroma
                assertTrue(
                    chroma <= 0.035f,
                    "$name kept too much chroma ($chroma); page backgrounds must stay a tint",
                )
            }
        }
    }

    @Test
    fun containersAreTintedButNotSwatches() {
        val palette = buildContentAccentPalette(loudYellow, DarkColors)
        val chroma = palette.container.toOklch().chroma
        assertTrue(chroma <= 0.06f, "container is a swatch, not a surface: $chroma")
    }

    @Test
    fun pageTintsTrackTheSurfaceToneRatherThanTheSourceLightness() {
        // The source is very light; in a dark scheme the page must still be dark.
        val palette = buildContentAccentPalette(loudYellow, DarkColors)
        val surfaceTone = DarkColors.surface.toOklch().lightness
        assertTrue(
            palette.pageStart.toOklch().lightness < surfaceTone + 0.15f,
            "dark scheme page start drifted toward the source's lightness",
        )
    }

    @Test
    fun sourceHueSurvivesTheDerivation() {
        val palette = buildContentAccentPalette(loudYellow, DarkColors)
        val sourceHue = loudYellow.toOklch().hue
        assertTrue(
            abs(palette.accent.toOklch().hue - sourceHue) < 0.15f,
            "accent lost the album's hue, which is the whole point of extracting it",
        )
    }

    @Test
    fun aGreySourceProducesAnUntintedPage() {
        val palette = buildContentAccentPalette(Color(0xFF9E9E9E), DarkColors)
        assertTrue(palette.pageStart.toOklch().chroma < 0.02f)
    }
}

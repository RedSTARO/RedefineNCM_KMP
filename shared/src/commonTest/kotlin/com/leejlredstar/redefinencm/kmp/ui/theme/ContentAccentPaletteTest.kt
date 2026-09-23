package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentAccentPaletteTest {

    /** The saturated yellow behind the olive page washes this palette is written to prevent. */
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

    private val labelSources = listOf(
        0xFFFFE94A, 0xFF006B5B, 0xFFB03060, 0xFF3355FF, 0xFF9E9E9E, 0xFFFF7043,
    )

    /**
     * The home page draws its "全部 N 首" button in the page's own hue over
     * [ContentAccentPalette.pageStart], because the scheme's own primary is a brand green with
     * no relation to the cover. The accent's tone is fixed per scheme while the page's tint
     * follows the artwork, so the pair has to be measured rather than assumed: a light yellow
     * cover in the light theme leaves the raw accent at about 4.2:1.
     */
    @Test
    fun theAccentLabelStaysLegibleOnItsOwnPage() {
        labelSources.forEach { source ->
            listOf("light" to LightColors, "dark" to DarkColors).forEach { (name, scheme) ->
                val palette = buildContentAccentPalette(Color(source), scheme)
                val label = legibleAccentFor(
                    accent = palette.accent,
                    background = palette.pageStart,
                    backdrop = scheme.surface,
                )
                val ratio = contrastRatio(label, palette.pageStart, scheme.surface)
                assertTrue(
                    ratio >= 4.5f,
                    "label on pageStart in $name for ${source.toString(16)} is $ratio",
                )
            }
        }
    }

    /** An accent that already passes is handed back untouched, hue and all. */
    @Test
    fun anAccentThatAlreadyCarriesTextIsNotBlended() {
        labelSources.forEach { source ->
            val palette = buildContentAccentPalette(Color(source), DarkColors)
            if (contrastRatio(palette.accent, palette.pageStart, DarkColors.surface) >= 4.5f) {
                assertEquals(
                    palette.accent,
                    legibleAccentFor(
                        accent = palette.accent,
                        background = palette.pageStart,
                        backdrop = DarkColors.surface,
                    ),
                )
            }
        }
    }
}

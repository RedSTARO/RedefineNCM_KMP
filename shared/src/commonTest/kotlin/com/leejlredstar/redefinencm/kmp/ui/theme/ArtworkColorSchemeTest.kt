package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The scheme is generated, so it cannot be checked by looking at it: there is one per cover.
 *
 * Two properties stand in for looking. Every colour the app writes on a container has to be
 * readable on it, and no role may carry a hue the cover did not give it. That second failure is
 * the one this generator exists to prevent, and a forgotten role silently brings it back, since
 * `lightColorScheme()` fills what it is not passed from Material's own purple baseline.
 */
class ArtworkColorSchemeTest {
    private val seeds = listOf(
        0xFFFFE94A, // the yellow that has broken every tone ladder in this app so far
        0xFF006B5B, // the green the app used to ship as its brand
        0xFFB03060,
        0xFF3355FF,
        0xFFFF7043,
        0xFF9E9E9E, // nearly neutral: the derivation must not invent chroma
    )

    /** Every role, named, so a role missing from the generator is missing from the check too. */
    private fun ColorScheme.everyRole(): List<Pair<String, Color>> = listOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "primaryFixed" to primaryFixed,
        "primaryFixedDim" to primaryFixedDim,
        "onPrimaryFixed" to onPrimaryFixed,
        "onPrimaryFixedVariant" to onPrimaryFixedVariant,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "secondaryFixed" to secondaryFixed,
        "secondaryFixedDim" to secondaryFixedDim,
        "onSecondaryFixed" to onSecondaryFixed,
        "onSecondaryFixedVariant" to onSecondaryFixedVariant,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "tertiaryFixed" to tertiaryFixed,
        "tertiaryFixedDim" to tertiaryFixedDim,
        "onTertiaryFixed" to onTertiaryFixed,
        "onTertiaryFixedVariant" to onTertiaryFixedVariant,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "scrim" to scrim,
        "surfaceBright" to surfaceBright,
        "surfaceDim" to surfaceDim,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "error" to error,
        "onError" to onError,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
    )

    /** The pairs the app draws: a container and the colour written on it. */
    private fun ColorScheme.readablePairs(): List<Triple<String, Color, Color>> = listOf(
        Triple("primary", primary, onPrimary),
        Triple("primaryContainer", primaryContainer, onPrimaryContainer),
        Triple("primaryFixed", primaryFixed, onPrimaryFixed),
        Triple("primaryFixedDim", primaryFixedDim, onPrimaryFixedVariant),
        Triple("secondary", secondary, onSecondary),
        Triple("secondaryContainer", secondaryContainer, onSecondaryContainer),
        Triple("secondaryFixed", secondaryFixed, onSecondaryFixed),
        Triple("secondaryFixedDim", secondaryFixedDim, onSecondaryFixedVariant),
        Triple("tertiary", tertiary, onTertiary),
        Triple("tertiaryContainer", tertiaryContainer, onTertiaryContainer),
        Triple("tertiaryFixed", tertiaryFixed, onTertiaryFixed),
        Triple("tertiaryFixedDim", tertiaryFixedDim, onTertiaryFixedVariant),
        Triple("background", background, onBackground),
        Triple("surface", surface, onSurface),
        Triple("surfaceVariant", surfaceVariant, onSurfaceVariant),
        Triple("surfaceBright", surfaceBright, onSurface),
        Triple("surfaceDim", surfaceDim, onSurface),
        Triple("surfaceContainer", surfaceContainer, onSurface),
        Triple("surfaceContainerHigh", surfaceContainerHigh, onSurface),
        Triple("surfaceContainerHighest", surfaceContainerHighest, onSurface),
        Triple("surfaceContainerLow", surfaceContainerLow, onSurface),
        Triple("surfaceContainerLowest", surfaceContainerLowest, onSurface),
        Triple("inverseSurface", inverseSurface, inverseOnSurface),
        Triple("error", error, onError),
        Triple("errorContainer", errorContainer, onErrorContainer),
    )

    @Test
    fun everyContainerCarriesTheColourWrittenOnIt() {
        seeds.forEach { seed ->
            listOf("light" to false, "dark" to true).forEach { (name, dark) ->
                val scheme = artworkColorScheme(Color(seed), dark)
                scheme.readablePairs().forEach { (role, container, content) ->
                    val ratio = contrastRatio(content, container, scheme.surface)
                    assertTrue(
                        ratio >= 4.5f,
                        "$role in $name for ${seed.toString(16)} is $ratio",
                    )
                }
            }
        }
    }

    @Test
    fun noRoleCarriesAHueTheCoverDidNotGiveIt() {
        seeds.forEach { seed ->
            val source = Color(seed).toOklch()
            listOf("light" to false, "dark" to true).forEach { (name, dark) ->
                artworkColorScheme(Color(seed), dark).everyRole()
                    .filter { (_, role) -> isDerivedRole(role) }
                    .forEach { (name0, role) ->
                        val derived = role.toOklch()
                        // Below this a role is grey to the eye and its measured hue is the
                        // last bit of 8-bit rounding, not a colour decision.
                        if (derived.chroma < 0.02f) return@forEach
                        val fromSeed = hueDistance(derived.hue, source.hue)
                        val fromTertiary =
                            hueDistance(derived.hue, source.hue + (PI / 3f).toFloat())
                        assertTrue(
                            minOf(fromSeed, fromTertiary) < 0.25f,
                            "$name0 in $name drifted off ${seed.toString(16)}: " +
                                "$fromSeed rad from the seed, $fromTertiary from its tertiary",
                        )
                    }
            }
        }
    }

    @Test
    fun withNoCoverTheSchemeIsGrey() {
        listOf("light" to false, "dark" to true).forEach { (name, dark) ->
            neutralColorScheme(dark).everyRole()
                .filter { (_, role) -> isDerivedRole(role) }
                .forEach { (role, color) ->
                    val chroma = color.toOklch().chroma
                    assertTrue(
                        chroma <= 0.02f,
                        "$role is tinted in the $name scheme with nothing playing: $chroma",
                    )
                }
        }
    }

    /**
     * The surfaces stay near-neutral whatever the cover, because [buildContentAccentPalette]
     * places its own tones against `scheme.surface` and reads its chroma back out.
     */
    @Test
    fun surfacesStayATintWhateverTheCover() {
        seeds.forEach { seed ->
            listOf(false, true).forEach { dark ->
                val scheme = artworkColorScheme(Color(seed), dark)
                listOf(
                    "surface" to scheme.surface,
                    "background" to scheme.background,
                    "surfaceContainer" to scheme.surfaceContainer,
                ).forEach { (role, color) ->
                    assertTrue(
                        color.toOklch().chroma <= 0.02f,
                        "$role is a swatch, not a surface, for ${seed.toString(16)}",
                    )
                }
            }
        }
    }
}

/** The shorter way round the hue circle, in radians. */
private fun hueDistance(a: Float, b: Float): Float {
    val full = (2f * PI).toFloat()
    val raw = abs(a - b) % full
    return minOf(raw, full - raw)
}

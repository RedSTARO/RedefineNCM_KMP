package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.PI

/**
 * The whole Material scheme, generated from the cover that is playing.
 *
 * The app has no brand colour. It used to ship one — a teal green written out as thirteen
 * literals per scheme — and every component that takes its colour from the scheme rather than
 * from a [ContentAccentPalette] drew in it: the switches and sliders in settings, the text
 * buttons, the navigation indicator, the dialogs. On pages tinted from the artwork that green
 * was the one thing on screen with no relation to what was playing.
 *
 * So the scheme itself is derived now: hold the cover's hue, choose a chroma per palette, read
 * off a tone. [seed] is the accent extracted from the current song's artwork; with none — before
 * anything has played — the scheme is neutral, and the app is grey until a cover gives it a
 * colour.
 *
 * The error roles are not derived. Red is what an error means, not a brand choice, and a scheme
 * whose errors are tinted by the album cover cannot say "this went wrong" at a glance.
 *
 * The tones are Oklch lightness, not Material's L\*: the two ladders do not transcribe (mid-grey
 * is L\* 50 but Oklch 0.60). They are anchored on the values [buildContentAccentPalette] already
 * uses in this app, and held in place by `ArtworkColorSchemeTest`, which measures every
 * container against the colour written on it rather than trusting the numbers below.
 */
internal fun artworkColorScheme(seed: Color?, dark: Boolean): ColorScheme {
    val hue = seed?.takeIf { it.alpha > 0f } ?: NeutralSchemeSeed
    // The ceiling is a ceiling, not a target: a cover that is nearly grey has no hue worth
    // holding, and raising it to the ceiling would invent one out of quantisation noise — the
    // hue of a grey is whatever its last bit of rounding says it is.
    val available = hue.toOklch().chroma
    fun tone(lightness: Float, ceiling: Float): Color =
        hue.withTone(lightness, minOf(available, ceiling))
    fun p(lightness: Float): Color = tone(lightness, PrimaryChroma)
    fun s(lightness: Float): Color = tone(lightness, SecondaryChroma)
    fun n(lightness: Float): Color = tone(lightness, NeutralChroma)
    fun nv(lightness: Float): Color = tone(lightness, NeutralVariantChroma)

    // Material's third palette is a hue away from the first, which is what keeps a tertiary
    // accent from reading as a second attempt at the primary one.
    val tertiaryHue = hue.toOklch().let { it.copy(hue = it.hue + TertiaryHueShift) }
    fun t(lightness: Float): Color = tertiaryHue
        .copy(lightness = lightness, chroma = minOf(available, TertiaryChroma))
        .toColor()

    return if (dark) {
        darkColorScheme(
            primary = p(0.80f),
            onPrimary = p(0.30f),
            primaryContainer = p(0.42f),
            onPrimaryContainer = p(0.90f),
            inversePrimary = p(0.52f),
            secondary = s(0.80f),
            onSecondary = s(0.30f),
            secondaryContainer = s(0.42f),
            onSecondaryContainer = s(0.90f),
            tertiary = t(0.80f),
            onTertiary = t(0.30f),
            tertiaryContainer = t(0.42f),
            onTertiaryContainer = t(0.90f),
            background = n(0.19f),
            onBackground = n(0.90f),
            surface = n(0.19f),
            onSurface = n(0.90f),
            surfaceVariant = nv(0.35f),
            onSurfaceVariant = nv(0.80f),
            surfaceTint = p(0.80f),
            inverseSurface = n(0.90f),
            inverseOnSurface = n(0.25f),
            outline = nv(0.60f),
            outlineVariant = nv(0.35f),
            scrim = Color.Black,
            surfaceBright = n(0.33f),
            surfaceDim = n(0.17f),
            surfaceContainer = n(0.23f),
            surfaceContainerHigh = n(0.27f),
            surfaceContainerHighest = n(0.31f),
            surfaceContainerLow = n(0.21f),
            surfaceContainerLowest = n(0.14f),
            // The "fixed" roles are the same in both schemes by design, so a light and a dark
            // surface can carry the same accent chip without it changing under them.
            primaryFixed = p(0.90f),
            primaryFixedDim = p(0.80f),
            onPrimaryFixed = p(0.20f),
            onPrimaryFixedVariant = p(0.40f),
            secondaryFixed = s(0.90f),
            secondaryFixedDim = s(0.80f),
            onSecondaryFixed = s(0.20f),
            onSecondaryFixedVariant = s(0.40f),
            tertiaryFixed = t(0.90f),
            tertiaryFixedDim = t(0.80f),
            onTertiaryFixed = t(0.20f),
            onTertiaryFixedVariant = t(0.40f),
            error = ErrorDark,
            onError = OnErrorDark,
            errorContainer = ErrorContainerDark,
            onErrorContainer = OnErrorContainerDark,
        )
    } else {
        lightColorScheme(
            primary = p(0.52f),
            onPrimary = p(1f),
            primaryContainer = p(0.90f),
            onPrimaryContainer = p(0.30f),
            inversePrimary = p(0.80f),
            secondary = s(0.52f),
            onSecondary = s(1f),
            secondaryContainer = s(0.90f),
            onSecondaryContainer = s(0.30f),
            tertiary = t(0.52f),
            onTertiary = t(1f),
            tertiaryContainer = t(0.90f),
            onTertiaryContainer = t(0.30f),
            background = n(0.985f),
            onBackground = n(0.22f),
            surface = n(0.985f),
            onSurface = n(0.22f),
            surfaceVariant = nv(0.91f),
            onSurfaceVariant = nv(0.40f),
            surfaceTint = p(0.52f),
            inverseSurface = n(0.30f),
            inverseOnSurface = n(0.96f),
            outline = nv(0.58f),
            outlineVariant = nv(0.85f),
            scrim = Color.Black,
            surfaceBright = n(0.99f),
            surfaceDim = n(0.90f),
            surfaceContainer = n(0.96f),
            surfaceContainerHigh = n(0.945f),
            surfaceContainerHighest = n(0.93f),
            surfaceContainerLow = n(0.975f),
            surfaceContainerLowest = n(1f),
            // The "fixed" roles are the same in both schemes by design, so a light and a dark
            // surface can carry the same accent chip without it changing under them.
            primaryFixed = p(0.90f),
            primaryFixedDim = p(0.80f),
            onPrimaryFixed = p(0.20f),
            onPrimaryFixedVariant = p(0.40f),
            secondaryFixed = s(0.90f),
            secondaryFixedDim = s(0.80f),
            onSecondaryFixed = s(0.20f),
            onSecondaryFixedVariant = s(0.40f),
            tertiaryFixed = t(0.90f),
            tertiaryFixedDim = t(0.80f),
            onTertiaryFixed = t(0.20f),
            onTertiaryFixedVariant = t(0.40f),
            error = ErrorLight,
            onError = OnErrorLight,
            errorContainer = ErrorContainerLight,
            onErrorContainer = OnErrorContainerLight,
        )
    }
}

/**
 * Whether [role] belongs to the palettes derived from the cover.
 *
 * Kept beside the generator so the test that walks every role can tell a derived one from an
 * error role without repeating the list.
 */
internal fun isDerivedRole(role: Color): Boolean = role !in ErrorRoles

private val ErrorLight = Color(0xFFB3261E)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFF9DEDC)
private val OnErrorContainerLight = Color(0xFF410E0B)
private val ErrorDark = Color(0xFFF2B8B5)
private val OnErrorDark = Color(0xFF601410)
private val ErrorContainerDark = Color(0xFF8C1D18)
private val OnErrorContainerDark = Color(0xFFF9DEDC)

private val ErrorRoles = setOf(
    ErrorLight, OnErrorLight, ErrorContainerLight, OnErrorContainerLight,
    ErrorDark, OnErrorDark, ErrorContainerDark, OnErrorContainerDark,
)

/** Before anything has played there is no cover to hold a hue, so the scheme holds none. */
internal val NeutralSchemeSeed = Color(0xFF808080)

/** The accent palette is the one allowed real colour; the surfaces around it are barely tinted. */
private const val PrimaryChroma = 0.130f
private const val SecondaryChroma = 0.055f
private const val TertiaryChroma = 0.095f
private const val NeutralChroma = 0.005f
private const val NeutralVariantChroma = 0.016f

/** 60°, the distance Material puts between its first and third palettes. */
private const val TertiaryHueShift = (PI / 3f).toFloat()

/** The scheme with no cover behind it: neutral, and the same in every hue. */
internal fun neutralColorScheme(dark: Boolean): ColorScheme = artworkColorScheme(seed = null, dark = dark)

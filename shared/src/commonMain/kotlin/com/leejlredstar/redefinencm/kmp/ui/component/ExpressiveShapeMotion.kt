package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * Material 3 Expressive shape-and-motion primitives.
 *
 * M3 Expressive's defining interaction is shape *morphing* — a surface changing its silhouette
 * in response to touch rather than only its colour or elevation. Material provides that natively
 * for `ButtonGroup`, `ToggleButton` and `SplitButton`; this file supplies the same behaviour for
 * the artwork surfaces this app draws itself, so the language is consistent instead of only
 * appearing on stock components.
 */

/**
 * A [Shape] that renders a point along a [Morph] between two [RoundedPolygon]s.
 *
 * [MaterialShapes] polygons are **normalised into a unit `[0,1]` box**, so the path only needs
 * scaling by the full target size. The widely-copied `scale(width / 2f, height / 2f)` +
 * `translate(1f, 1f)` recipe applies to polygons built centred on the origin with radius 1 (a
 * `[-1,1]` box); using it here renders the shape at half size in the lower-right quadrant.
 *
 * The [Path] and [Matrix] are held per instance and rewound on each pass to avoid allocating on
 * every frame of a morph animation.
 */
private class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    private val matrix = Matrix()
    private val path = Path()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        path.rewind()
        morph.toPath(progress = progress, path = path)
        matrix.reset()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is MorphPolygonShape && other.morph === morph && other.progress == progress

    override fun hashCode(): Int = morph.hashCode() * 31 + progress.hashCode()
}

/** Named pairs of [MaterialShapes] used for the app's morph interactions. */
enum class ExpressiveMorphPair(
    internal val start: () -> RoundedPolygon,
    internal val end: () -> RoundedPolygon,
) {
    /**
     * Artwork frames: a rounded square that blooms into a scalloped cookie while pressed.
     *
     * The **start** shape must be the resting silhouette, because progress `0f` is what the
     * artwork displays whenever it is not being touched. Starting from a cookie leaves every
     * cover permanently scalloped and crops the image badly, so the rest end is
     * [MaterialShapes.Square] — a rounded square close to the `medium` shape these covers used
     * before — and only the pressed end is decorative.
     */
    ArtworkBloom({ MaterialShapes.Square }, { MaterialShapes.Cookie12Sided }),
}

/**
 * Remember a [Shape] that sits at [progress] along the given [pair].
 *
 * `0f` is the resting silhouette and `1f` the fully morphed one; feed it an animated float to
 * drive the transition.
 */
@Composable
fun rememberMorphShape(pair: ExpressiveMorphPair, progress: Float): Shape =
    rememberMorphShape(pair.start(), pair.end(), progress)

/**
 * Remember a [Shape] morphing between two explicit [MaterialShapes] polygons.
 *
 * Use this when the resting silhouette is chosen at runtime — for example when a surface's shape
 * encodes state — so it cannot be expressed as a fixed [ExpressiveMorphPair].
 */
@Composable
fun rememberMorphShape(
    start: RoundedPolygon,
    end: RoundedPolygon,
    progress: Float,
): Shape {
    val morph = remember(start, end) { Morph(start, end) }
    return remember(morph, progress) { MorphPolygonShape(morph, progress.coerceIn(0f, 1f)) }
}

/**
 * Drive a morph from an [InteractionSource] so a surface reshapes while it is held.
 *
 * Uses the theme's expressive [androidx.compose.material3.MotionScheme] spring so the rebound
 * matches Material's own components rather than introducing a second motion feel.
 */
@Composable
fun rememberPressMorphProgress(interactionSource: InteractionSource): State<Float> {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val animatable = remember { Animatable(0f) }
    val currentSpec = rememberUpdatedState(spec)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            val target = when (interaction) {
                is PressInteraction.Press -> 1f
                is PressInteraction.Release, is PressInteraction.Cancel -> 0f
                else -> null
            }
            if (target != null) animatable.animateTo(target, currentSpec.value)
        }
    }
    return animatable.asState()
}

/**
 * Determinate wavy progress — the Expressive replacement for a flat linear bar.
 *
 * Centralised so every progress surface in the app (downloads, recognition, caching) shares one
 * amplitude and colour treatment.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveWavyProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/** Indeterminate wavy progress, for work with no measurable completion fraction. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveWavyProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    LinearWavyProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

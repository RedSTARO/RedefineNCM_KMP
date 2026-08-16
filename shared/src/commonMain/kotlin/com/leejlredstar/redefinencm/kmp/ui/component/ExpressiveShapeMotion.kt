package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
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
 * the surfaces this app draws itself (artwork frames, hero medallions, badges), so the language
 * is consistent instead of only appearing on stock components.
 */

/**
 * A [Shape] that renders a point along a [Morph] between two [RoundedPolygon]s.
 *
 * [MaterialShapes] polygons are normalised to a 2×2 box centred on the origin, so the path is
 * scaled by half the target size and then translated by one unit to land in the layout bounds.
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
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
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
    /** Artwork frames: a soft squircle that blooms into a scalloped cookie while pressed. */
    ArtworkBloom({ MaterialShapes.Cookie9Sided }, { MaterialShapes.Cookie12Sided }),

    /** Hero medallions: a round badge that snaps into a many-pointed burst. */
    HeroBurst({ MaterialShapes.Circle }, { MaterialShapes.SoftBurst }),

    /** Playful accents on empty states and identity surfaces. */
    Bloom({ MaterialShapes.Flower }, { MaterialShapes.Clover8Leaf }),

    /** Compact status badges: pill relaxing into a gem while active. */
    BadgeGem({ MaterialShapes.Pill }, { MaterialShapes.Gem }),
}

/**
 * Remember a [Shape] that sits at [progress] along the given [pair].
 *
 * `0f` is the resting silhouette and `1f` the fully morphed one; feed it an animated float to
 * drive the transition.
 */
@Composable
fun rememberMorphShape(pair: ExpressiveMorphPair, progress: Float): Shape {
    val morph = remember(pair) { Morph(pair.start(), pair.end()) }
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

/** A static [MaterialShapes] silhouette, for decorative frames that do not animate. */
@Composable
fun materialShape(polygon: RoundedPolygon): Shape = polygon.toShape()

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

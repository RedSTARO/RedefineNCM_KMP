package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A glow that breathes around an element while a song transition sounds: a halo that follows the
 * element's rounded outline and reaches [spread] past it, and two light spots circling inside it,
 * so the colour seems to flow round. It fades in and out with [visible]; with [animate] false it
 * holds still. Drawn behind the element, past its bounds, so no ancestor may clip there.
 *
 * The halo is stacked translucent rounded rectangles rather than a blur, which not every platform
 * can draw.
 */
@Composable
fun TransitionAura(
    visible: Boolean,
    color: Color,
    highlight: Color,
    modifier: Modifier = Modifier,
    corner: CornerSize? = null,
    spread: Dp = 36.dp,
    animate: Boolean = true,
) {
    val strength by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 900 else 1_200),
        label = "transitionAuraStrength",
    )
    if (strength <= 0f) return
    val motion = if (animate) rememberInfiniteTransition(label = "transitionAura") else null
    val breath: State<Float> = motion?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "transitionAuraBreath",
    ) ?: StillBreath
    val orbit: State<Float> = motion?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ORBIT_MILLIS, easing = LinearEasing)),
        label = "transitionAuraOrbit",
    ) ?: StillOrbit
    Spacer(
        modifier
            .graphicsLayer { alpha = strength }
            .drawBehind {
                // Read here, not in composition, so each frame redraws without recomposing.
                val swell = 0.72f + 0.28f * breath.value
                val reach = spread.toPx() * swell
                val half = min(size.width, size.height) / 2f
                val radius = corner?.toPx(size, this)?.coerceIn(0f, half) ?: half
                // Largest first: where the layers overlap, near the edge, they add up to the
                // brightest band, and the glow thins towards the reach.
                for (layer in HALO_LAYERS downTo 1) {
                    val grow = reach * layer / HALO_LAYERS
                    drawRoundRect(
                        color = color.copy(alpha = HALO_LAYER_ALPHA),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + 2 * grow, size.height + 2 * grow),
                        cornerRadius = CornerRadius(radius + grow),
                    )
                }
                val turn = orbit.value * 2f * PI.toFloat()
                for (spot in 0 until 2) {
                    val angle = turn + spot * PI.toFloat()
                    val spotCenter = center + Offset(cos(angle), sin(angle)) * (half * 0.62f)
                    val spotRadius = half * 0.9f + reach
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to highlight.copy(alpha = SPOT_ALPHA),
                            1f to highlight.copy(alpha = 0f),
                            center = spotCenter,
                            radius = spotRadius,
                        ),
                        radius = spotRadius,
                        center = spotCenter,
                    )
                }
            },
    )
}

/** Reduced motion: a half-swollen halo with its spots parked. */
private val StillBreath: State<Float> = mutableFloatStateOf(0.5f)
private val StillOrbit: State<Float> = mutableFloatStateOf(0.125f)

private const val BREATH_MILLIS = 2_400
private const val ORBIT_MILLIS = 9_000
private const val HALO_LAYERS = 12
private const val HALO_LAYER_ALPHA = 0.055f
private const val SPOT_ALPHA = 0.30f

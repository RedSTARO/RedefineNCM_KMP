package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Light running around the rim of a pill: two comets chasing each other along the outline at a
 * constant speed, over a faint glow of the whole rim, with a soft halo that reaches past the
 * edge. It fades in when [visible] turns true and out when it turns false; faded out, nothing is
 * drawn and nothing animates.
 *
 * Place it over the pill with the pill's own bounds. It draws past them, so no ancestor may clip
 * there, and it takes no input, so taps go through to the pill.
 */
@Composable
fun FlowingLightRing(
    visible: Boolean,
    color: Color,
    headColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.5.dp,
    glowWidth: Dp = 8.dp,
    lapMillis: Int = 2_600,
) {
    val strength by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 450 else 700),
        label = "flowingLightStrength",
    )
    if (strength <= 0f) return
    val phase by rememberInfiniteTransition(label = "flowingLight").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(lapMillis, easing = LinearEasing)),
        label = "flowingLightPhase",
    )
    Spacer(
        modifier
            // The fade is the layer's opacity, so the tails' stacked strokes keep their profile
            // at every strength instead of each stroke fading on its own.
            .graphicsLayer { this.alpha = strength }
            .drawWithCache {
                val stroke = strokeWidth.toPx()
                val glow = glowWidth.toPx()
                val inset = stroke / 2f
                val rim = Rect(inset, inset, size.width - inset, size.height - inset)
                val radius = min(rim.width, rim.height) / 2f
                val outline = Path().apply { addRoundRect(RoundRect(rim, CornerRadius(radius))) }
                val measure = PathMeasure().apply { setPath(outline, forceClosed = true) }
                val length = measure.length
                val tail = length * TAIL_FRACTION
                val piece = Path()
                val rimStroke = Stroke(stroke)
                val coreStroke = Stroke(stroke, cap = StrokeCap.Butt)
                // A stroke has a hard edge; three of falling width stacked make a halo that
                // softens outwards, which a blur would do better but not on every platform.
                val glowStrokes = GlowLayers.map { (width, _) -> Stroke(glow * width, cap = StrokeCap.Butt) }
                val coreAlphas = nestedAlphas(CORE_PIECES) { 1f }
                val headAlphas = nestedAlphas(HEAD_PIECES) { HEAD_OPACITY }
                val glowAlphas = GlowLayers.map { (_, opacity) -> nestedAlphas(GLOW_PIECES) { opacity } }
                onDrawBehind {
                    // Read here, not in composition, so each frame redraws without recomposing.
                    val lap = phase
                    drawPath(outline, color.copy(alpha = RIM_ALPHA), style = rimStroke)
                    for (comet in 0 until COMETS) {
                        val head = ((lap + comet.toFloat() / COMETS) % 1f) * length
                        // Each tail is strokes that all end at the head and reach back further
                        // and further, so where they overlap they add up to a fade with no seams.
                        GlowLayers.indices.forEach { layer ->
                            glowAlphas[layer].forEachIndexed { index, pieceAlpha ->
                                extract(measure, length, head - tail * (index + 1) / GLOW_PIECES, head, piece)
                                drawPath(piece, color.copy(alpha = pieceAlpha), style = glowStrokes[layer])
                            }
                        }
                        coreAlphas.forEachIndexed { index, pieceAlpha ->
                            extract(measure, length, head - tail * (index + 1) / CORE_PIECES, head, piece)
                            drawPath(piece, color.copy(alpha = pieceAlpha), style = coreStroke)
                        }
                        headAlphas.forEachIndexed { index, pieceAlpha ->
                            extract(measure, length, head - tail * HEAD_FRACTION * (index + 1) / HEAD_PIECES, head, piece)
                            drawPath(piece, headColor.copy(alpha = pieceAlpha), style = coreStroke)
                        }
                        // The head is a soft round spot rather than the square end of a stroke.
                        val spot = measure.getPosition(head)
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to headColor,
                                0.35f to headColor.copy(alpha = 0.55f),
                                1f to color.copy(alpha = 0f),
                                center = spot,
                                radius = glow * 0.9f,
                            ),
                            radius = glow * 0.9f,
                            center = spot,
                        )
                    }
                }
            },
    )
}

/**
 * Opacities for [pieces] nested strokes, the k-th reaching k + 1 steps back from the head, such
 * that where they overlap they add up to [peak] times a quadratic fall-off: full at the head, zero
 * at the far end.
 */
private fun nestedAlphas(pieces: Int, peak: () -> Float): List<Float> {
    fun target(step: Int): Float {
        if (step >= pieces) return 0f
        val left = 1f - step.toFloat() / pieces
        return (peak() * left * left).coerceIn(0f, 0.999f)
    }
    return List(pieces) { k -> 1f - (1f - target(k)) / (1f - target(k + 1)) }
}

/** The piece of the closed outline between two distances, which may wrap past its start. */
private fun extract(measure: PathMeasure, length: Float, from: Float, to: Float, into: Path) {
    into.reset()
    val start = ((from % length) + length) % length
    val stop = start + (to - from)
    if (stop <= length) {
        measure.getSegment(start, stop, into, startWithMoveTo = true)
    } else {
        measure.getSegment(start, length, into, startWithMoveTo = true)
        measure.getSegment(0f, stop - length, into, startWithMoveTo = true)
    }
}

private const val COMETS = 2

/** How much of the outline one comet's tail covers, and how much of that burns in the head colour. */
private const val TAIL_FRACTION = 0.32f
private const val HEAD_FRACTION = 0.35f

private const val CORE_PIECES = 24
private const val HEAD_PIECES = 8
private const val GLOW_PIECES = 16
private const val HEAD_OPACITY = 0.9f
private const val RIM_ALPHA = 0.30f

/** The halo's layers: width as a share of the glow width, and opacity at the comet's head. */
private val GlowLayers = listOf(1f to 0.12f, 0.66f to 0.16f, 0.36f to 0.22f)

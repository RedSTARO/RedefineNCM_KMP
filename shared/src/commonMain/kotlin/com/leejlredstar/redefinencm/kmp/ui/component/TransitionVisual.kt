package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.Image
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.TransitionBlend
import com.leejlredstar.redefinencm.kmp.transition.TransitionKind
import com.leejlredstar.redefinencm.kmp.transition.TransitionPlan
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * A song transition as screens draw it: the two tracks, what kind of blend the listener hears,
 * and how much of the picture belongs to the incoming track.
 *
 * The two kinds look different because they sound different. A plain crossfade dissolves
 * evenly. A beat-matched blend holds both tracks on one beat, and its picture moves on that beat:
 * each beat carries the dissolve a step further and then it rests, and each beat gives the cover
 * a small kick, strongest on the downbeat. With reduced motion both simply dissolve.
 *
 * The players report the blend in steps (every 20 ms on Android and the web, every 100 ms on the
 * desktop). Between reports the blend's time runs on with the frame clock, so the beat lands with
 * the sound instead of trailing it. Read [weight] and [beatPulse] in draw and layer blocks: a
 * blend then redraws without recomposing the screen.
 */
@Stable
class TransitionVisual internal constructor(
    val outgoing: MediaInfo,
    val incoming: MediaInfo,
    private val plan: TransitionPlan,
    private val elapsedMs: State<Float>,
    reducedMotion: Boolean,
) {
    /** What the listener hears: a beat-matched blend or a plain crossfade. */
    val kind: TransitionKind get() = plan.kind

    /** Whether the picture moves on the shared beat. */
    val movesWithBeat: Boolean =
        !reducedMotion && plan.kind == TransitionKind.BEAT_MATCHED && plan.incomingBeat != null

    /** How far the blend has gone, 0 to 1, continuously. */
    private val progress: Float get() = (elapsedMs.value / plan.overlapMs).coerceIn(0f, 1f)

    /**
     * The incoming track's share of the picture, 0 to 1, eased in and out so the picture is
     * exactly half and half at the swap, where the title changes.
     */
    val weight: Float
        get() {
            val beat = plan.incomingBeat
            val linear = if (movesWithBeat && beat != null) {
                val beats = beat.beatsAt(plan.incomingEntryMs + elapsedMs.value.toDouble())
                val whole = floor(beats)
                val step = easeOutCubic(((beats - whole) / BEAT_STEP_SHARE).coerceAtMost(1.0))
                val steppedMs = beat.downbeatMs - plan.incomingEntryMs + (whole + step) * beat.periodMs
                (steppedMs / plan.overlapMs).toFloat().coerceIn(0f, 1f)
            } else {
                progress
            }
            return linear * linear * (3f - 2f * linear)
        }

    /**
     * The kick of the latest beat, 0 to 1: it rises right after the beat, peaks [PULSE_PEAK_MS]
     * later and dies away before the next. Full on a downbeat, weaker on the other beats, and
     * strongest in the middle of the blend, where both tracks are loudest together. Always 0
     * for a crossfade.
     */
    val beatPulse: Float
        get() {
            val beat = plan.incomingBeat
            if (!movesWithBeat || beat == null) return 0f
            val beats = beat.beatsAt(plan.incomingEntryMs + elapsedMs.value.toDouble())
            if (beats < 0.0) return 0f
            val index = floor(beats).toLong()
            val t = (beats - index) * beat.periodMs / PULSE_PEAK_MS
            val kick = t * exp(1.0 - t)
            val strength = if (index % beat.beatsPerBar == 0L) 1.0 else OFFBEAT_PULSE
            val presence = sin(PI * progress)
            return (kick * strength * presence).toFloat().coerceIn(0f, 1f)
        }
}

/** The transition [player] is blending, or null while it is not blending. */
@Composable
fun rememberTransitionVisual(player: PlatformPlayer): TransitionVisual? {
    val blend = player.transitionBlend.collectAsState()
    val reducedMotion = rememberReducedMotionEnabled()
    val identity by remember {
        derivedStateOf { blend.value?.let { BlendIdentity(it.outgoing, it.incoming, it.plan) } }
    }
    val current = identity ?: return null
    val elapsedMs = remember(current) { mutableFloatStateOf(blend.value?.elapsedMs?.toFloat() ?: 0f) }
    LaunchedEffect(current) {
        // Each report anchors the blend's time; frames in between run it on, up to a limit, so a
        // blend that stops reporting (paused) stops moving too.
        var report: TransitionBlend? = null
        var anchorMs = 0f
        var anchorNanos = 0L
        while (true) {
            withFrameNanos { now ->
                val latest = blend.value
                if (latest != null && latest !== report) {
                    report = latest
                    anchorMs = latest.elapsedMs.toFloat()
                    anchorNanos = now
                }
                val ahead = ((now - anchorNanos) / 1_000_000f).coerceIn(0f, MAX_RUN_ON_MS)
                val next = anchorMs + ahead
                val shown = elapsedMs.floatValue
                // A report a few milliseconds behind the frame clock is held, not followed back:
                // stepping back over a beat would kick the cover twice. A real jump is followed.
                elapsedMs.floatValue = if (next < shown && shown - next < RESYNC_MS) shown else next
            }
        }
    }
    return remember(current, reducedMotion) {
        TransitionVisual(current.outgoing, current.incoming, current.plan, elapsedMs, reducedMotion)
    }
}

private data class BlendIdentity(val outgoing: MediaInfo, val incoming: MediaInfo, val plan: TransitionPlan)

private fun easeOutCubic(x: Double): Double = 1.0 - (1.0 - x).let { it * it * it }

/**
 * Covers for a screen that shows a transition: [current] alone, or through a blend the outgoing
 * cover under the incoming one, which dissolves in with [visual]'s weight. With [focusPull] it
 * also settles into focus as it comes, from slightly enlarged and soft to sharp; the blur is
 * lost on Android before 12, where the dissolve remains.
 *
 * Layers are keyed by track, so when the blend ends the incoming cover's layer simply stays,
 * already loaded, and nothing reloads or flashes. [layer] draws one cover: it receives the track,
 * a modifier for the whole layer, one for the image inside the frame, and the callback to call
 * with the loaded image. [onCurrentImage] gets the current track's image, also when it finished
 * loading while the track was still the incoming one; [onIncomingImage] gets the incoming one's.
 */
@Composable
fun TransitionArtworkStack(
    current: MediaInfo?,
    visual: TransitionVisual?,
    modifier: Modifier = Modifier,
    focusPull: Boolean = false,
    onCurrentImage: (Image) -> Unit = {},
    onIncomingImage: (Image) -> Unit = {},
    layer: @Composable BoxScope.(
        media: MediaInfo?,
        layerModifier: Modifier,
        imageModifier: Modifier,
        onLoaded: (Image) -> Unit,
    ) -> Unit,
) {
    val layers = if (visual != null) listOf(visual.outgoing, visual.incoming) else listOf(current)
    val loaded = remember { mutableStateMapOf<String, Image>() }
    val layerIds = layers.mapNotNull { it?.id }
    LaunchedEffect(layerIds) { loaded.keys.retainAll(layerIds.toSet()) }
    val currentImage = current?.id?.let { loaded[it] }
    LaunchedEffect(current?.id, currentImage) { currentImage?.let(onCurrentImage) }
    Box(modifier) {
        layers.forEachIndexed { index, media ->
            key(media?.id) {
                val incoming = visual != null && index == 1
                val layerModifier = if (incoming) {
                    // Nothing shows until the cover has loaded: a half-faded placeholder would
                    // read as a flash of grey.
                    Modifier.graphicsLayer { alpha = if (media?.id in loaded) visual!!.weight else 0f }
                } else {
                    Modifier
                }
                val imageModifier = if (incoming && focusPull) {
                    Modifier.graphicsLayer {
                        val away = 1f - visual!!.weight
                        val scale = 1f + FOCUS_PULL_SCALE * away
                        scaleX = scale
                        scaleY = scale
                        val blur = FOCUS_PULL_BLUR.toPx() * away
                        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Clamp) else null
                    }
                } else {
                    Modifier
                }
                layer(media, layerModifier, imageModifier) { image ->
                    media?.id?.let { loaded[it] = image }
                    if (incoming) onIncomingImage(image)
                }
            }
        }
    }
}

/** How long the time between two reports may run on with the frame clock. */
private const val MAX_RUN_ON_MS = 150f

/** A report further behind the frame clock than this is a jump, not jitter. */
private const val RESYNC_MS = 250f

/** Of each beat, the share in which the dissolve takes its step; it rests for the rest. */
private const val BEAT_STEP_SHARE = 0.45

/** When a beat's kick peaks, after the beat. */
private const val PULSE_PEAK_MS = 70.0

/** A beat's kick against a downbeat's. */
private const val OFFBEAT_PULSE = 0.45

/** How much bigger, and how soft, the incoming cover starts. */
private const val FOCUS_PULL_SCALE = 0.06f
private val FOCUS_PULL_BLUR: Dp = 14.dp

package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalSoftKeyboardVisible
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class PlaybackPaneArrangement { Full, Left, Right, Top, Bottom, SideBySide, Stacked }

/** Use the occupied rectangles, not hinge state or angle, to distinguish placements. */
internal fun playbackPaneArrangement(
    viewport: IntRect,
    primary: IntRect,
    secondary: IntRect? = null,
): PlaybackPaneArrangement = when {
    secondary != null && secondary != primary ->
        if (secondary.right <= primary.left || primary.right <= secondary.left) {
            PlaybackPaneArrangement.SideBySide
        } else {
            PlaybackPaneArrangement.Stacked
        }
    primary == viewport -> PlaybackPaneArrangement.Full
    primary.right < viewport.right -> PlaybackPaneArrangement.Left
    primary.left > viewport.left -> PlaybackPaneArrangement.Right
    primary.bottom < viewport.bottom -> PlaybackPaneArrangement.Top
    else -> PlaybackPaneArrangement.Bottom
}

/** Android's compact-width boundary also distinguishes most cover and inner screens. */
internal fun hasExpandedPlaybackWidth(width: Dp): Boolean = width >= 600.dp

internal data class PlaybackReflowTarget<Layout : Any>(
    val viewport: IntRect,
    val fold: PlaybackFold?,
    val layout: Layout,
)

internal enum class PlaybackReflowAction { Snap, Keep, Fade }

internal fun <Layout : Any> playbackReflowAction(
    previous: PlaybackReflowTarget<Layout>?,
    current: PlaybackReflowTarget<Layout>?,
    reducedMotion: Boolean,
    motionScale: Float,
    keyboardVisible: Boolean,
    fading: Boolean,
): PlaybackReflowAction = when {
    reducedMotion || motionScale <= 0f || keyboardVisible || previous == null || current == null ->
        PlaybackReflowAction.Snap
    fading || previous.layout == current.layout -> PlaybackReflowAction.Keep
    // A content change (e.g. the new song loading its lyrics) isn't a window reflow.
    previous.viewport == current.viewport && previous.fold == current.fold -> PlaybackReflowAction.Keep
    else -> PlaybackReflowAction.Fade
}

private const val PlaybackReflowStartAlpha = 0.78f

/**
 * A single brief reveal at the new geometry. Use inside the current pane's clip; neither pointer
 * bounds nor composition travel. An in-flight fade already covers a second bounds/hinge update:
 * don't restart it or wait to flash the page once a stream of resize events settles.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <Layout : Any> rememberPlaybackReflow(
    enabled: Boolean,
    target: PlaybackReflowTarget<Layout>?,
    reducedMotion: Boolean,
): Modifier {
    if (!enabled) return Modifier
    val scope = rememberCoroutineScope()
    val scale = scope.coroutineContext[MotionDurationScale]
    val scaleFactor = scale?.scaleFactor ?: 1f
    val ime = WindowInsets.ime
    val keyboardVisible = LocalSoftKeyboardVisible.current || ime.getBottom(LocalDensity.current) > 0
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val motion = remember { PlaybackReflowMotion<Layout>() }
    SideEffect {
        val action = playbackReflowAction(
            motion.previous,
            target,
            reducedMotion,
            scaleFactor,
            keyboardVisible || motion.keyboardWasVisible,
            motion.fade?.isActive == true,
        )
        motion.previous = target
        motion.keyboardWasVisible = keyboardVisible
        when (action) {
            PlaybackReflowAction.Snap -> {
                motion.fade?.cancel()
                motion.alpha.floatValue = 1f
            }
            PlaybackReflowAction.Keep -> Unit
            PlaybackReflowAction.Fade -> {
                motion.alpha.floatValue = PlaybackReflowStartAlpha
                motion.fade = scope.launch {
                    animate(PlaybackReflowStartAlpha, 1f, animationSpec = effects) { value, _ ->
                        motion.alpha.floatValue = value.coerceIn(PlaybackReflowStartAlpha, 1f)
                    }
                }
            }
        }
    }
    return Modifier.graphicsLayer {
        alpha = if (reducedMotion || keyboardVisible || (scale?.scaleFactor ?: 1f) <= 0f) 1f
        else motion.alpha.floatValue
    }
}

private class PlaybackReflowMotion<Layout : Any> {
    var previous: PlaybackReflowTarget<Layout>? = null
    var keyboardWasVisible = false
    var fade: Job? = null
    val alpha = mutableFloatStateOf(1f)
}

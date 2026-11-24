package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.leejlredstar.redefinencm.kmp.ui.adaptive.FoldPanes
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalSoftKeyboardVisible
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalWindowFold
import com.leejlredstar.redefinencm.kmp.ui.adaptive.WindowFold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

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

internal fun hasExpandedPlaybackWidth(width: Dp): Boolean = width >= 600.dp

/** A report that does not split or occlude this viewport must use the regular flat layout. */
internal data class PlaybackFoldPlacement(val split: FoldPanes?, val single: IntRect)

internal fun PlaybackFold.placementIn(viewport: IntRect): PlaybackFoldPlacement? {
    val split = spanningPanes(viewport)
    val single = singlePane(viewport, split)
    return if (split != null || single != viewport) PlaybackFoldPlacement(split, single) else null
}

/** Separate window changes from IME insets and continuous dragging of a multi-window divider. */
internal data class AndroidReflowWindow(val size: IntSize, val rotation: Int, val inMultiWindow: Boolean)

internal val LocalAndroidReflowWindow = compositionLocalOf<AndroidReflowWindow?> { null }

/** Wait for the target window's own draw to reach the renderer on a full-size handoff. */
internal val LocalReflowFrameCommit = staticCompositionLocalOf<suspend () -> Unit> { {} }

internal data class PlaybackReflowTarget<Layout : Any>(
    val viewport: IntRect,
    val fold: PlaybackFoldPlacement?,
    val layout: Layout,
    // Classify from the same measured viewport; an outer constraint update may arrive earlier.
    val expandedWidth: Boolean,
    val reportedFold: WindowFold? = null,
    val window: AndroidReflowWindow? = null,
)

internal enum class ReflowChange { None, Pane, Window, WidthClass }

internal fun adaptiveReflowChange(
    before: AndroidReflowWindow?,
    after: AndroidReflowWindow?,
    usableBoundsChanged: Boolean,
    effectiveFoldChanged: Boolean,
    crossesWidthClass: Boolean,
    keyboardChanging: Boolean,
): ReflowChange {
    // While toggling the IME an existing feature can project into a differently sized viewport,
    // but only an independent window/feature change counts. Still allow a real fold during input.
    val continuousSplitResize = before?.inMultiWindow == true && after?.inMultiWindow == true &&
        before.rotation == after.rotation
    // A size handoff and a hinge report can land together. Retain the width-class settle only
    // when the host width also changed, not when a same-size hinge merely picked a smaller pane.
    if (effectiveFoldChanged) {
        return if (crossesWidthClass && before != null && after != null && before.size.width != after.size.width &&
            !continuousSplitResize
        ) ReflowChange.WidthClass else ReflowChange.Pane
    }
    if (!usableBoundsChanged || continuousSplitResize || (keyboardChanging && (before == null || before == after))) {
        return ReflowChange.None
    }
    return if (crossesWidthClass) ReflowChange.WidthClass else ReflowChange.Window
}

internal fun <Layout : Any> playbackReflowChange(
    before: PlaybackReflowTarget<Layout>,
    after: PlaybackReflowTarget<Layout>,
    keyboardChanging: Boolean = false,
): ReflowChange {
    val crossesWidthClass = before.expandedWidth != after.expandedWidth
    if (before.layout == after.layout && !crossesWidthClass) return ReflowChange.None
    val changedProjection = before.fold != after.fold
    return adaptiveReflowChange(
        before.window, after.window,
        usableBoundsChanged = before.viewport != after.viewport,
        effectiveFoldChanged = changedProjection && before.reportedFold != after.reportedFold,
        crossesWidthClass = crossesWidthClass,
        keyboardChanging = keyboardChanging,
    )
}

@Composable
internal fun <Layout : Any> rememberPlaybackReflow(
    enabled: Boolean,
    target: PlaybackReflowTarget<Layout>?,
    reducedMotion: Boolean,
): ReflowProgress? {
    if (!enabled) return null
    val reportedFold = LocalWindowFold.current
    val window = LocalAndroidReflowWindow.current
    val motion = rememberAdaptiveReflow<PlaybackReflowTarget<Layout>>(reducedMotion, ::playbackReflowChange)
    motion.Observe(target?.copy(reportedFold = reportedFold, window = window))
    return motion
}

internal interface ReflowProgress {
    val progress: Float
}

private const val ReflowMillis = 320
private const val WidthClassSettleMillis = 240L
private const val MaxPresentationWaitMillis = 1_450L

/** Keep only the latest valid geometry; a temporary zero/unplaced target is not a new baseline. */
internal class ReflowHistory<Target : Any> {
    var previous: Target? = null
        private set
    private var keyboardWasVisible = false

    fun observe(active: Boolean, target: Target?, keyboardVisible: Boolean): ReflowObservation<Target> {
        val observation = ReflowObservation(previous, keyboardVisible || keyboardWasVisible)
        keyboardWasVisible = keyboardVisible
        if (!active) previous = null
        else if (target != null) previous = target
        return observation
    }
}

internal data class ReflowObservation<Target : Any>(val previous: Target?, val keyboardChanging: Boolean)

/**
 * Keep this observer outside the branches that install the targets. Placement is immediate; the
 * optional animation belongs to the new slot, never to a copy of the outgoing controls.
 */
@Composable
internal fun <Target : Any> rememberAdaptiveReflow(
    reducedMotion: Boolean,
    change: (Target, Target, keyboardChanging: Boolean) -> ReflowChange,
): AdaptiveReflow<Target> {
    val scope = rememberCoroutineScope()
    val scale = scope.coroutineContext[MotionDurationScale]
    val reduced = rememberUpdatedState(reducedMotion)
    val classify = rememberUpdatedState(change)
    val windowInfo = LocalWindowInfo.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = rememberUpdatedState(LocalAndroidReflowWindow.current)
    val frameCommit = rememberUpdatedState(LocalReflowFrameCommit.current)
    return remember(scope, windowInfo, lifecycle) {
        AdaptiveReflow(
            scope = scope,
            canAnimate = { !reduced.value && (scale?.scaleFactor ?: 1f) > 0f },
            canPresent = {
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                    (windowInfo.isWindowFocused || window.value?.inMultiWindow == true)
            },
            awaitFrameCommit = { frameCommit.value() },
            classify = { before, after, keyboardChanging -> classify.value(before, after, keyboardChanging) },
        )
    }
}

internal class AdaptiveReflow<Target : Any>(
    private val scope: CoroutineScope,
    private val canAnimate: () -> Boolean,
    private val canPresent: () -> Boolean,
    private val awaitFrameCommit: suspend () -> Unit,
    private val classify: (Target, Target, Boolean) -> ReflowChange,
) : ReflowProgress {
    private val history = ReflowHistory<Target>()
    private val value = mutableFloatStateOf(1f)
    private var animation: Job? = null
    private var pending: Pending<Target>? = null
    private var targetIsPlaced = false

    override val progress: Float get() = if (canAnimate()) value.floatValue else 1f

    @Composable
    fun Observe(target: Target?, active: Boolean = true) {
        val keyboardVisible = LocalSoftKeyboardVisible.current || WindowInsets.ime.getBottom(LocalDensity.current) > 0
        SideEffect { observe(target, active, keyboardVisible) }
    }

    internal fun observe(target: Target?, active: Boolean, keyboardVisible: Boolean) {
        val (previous, keyboardChanging) = history.observe(active, target, keyboardVisible)
        targetIsPlaced = active && target != null
        if (!active || !canAnimate()) {
            snap()
            return
        }
        if (target == null || previous == null || target == previous) return
        val waiting = pending
        if (waiting != null) {
            val updatedChange = classify(waiting.from, target, waiting.keyboardChanging || keyboardChanging)
            if (updatedChange == ReflowChange.None) {
                snap()
            } else {
                // Fold and inset reports can catch up in a later frame. Do not turn a real
                // window handoff into an immediate pane transition while the resize is in flight.
                if (waiting.kind != ReflowChange.WidthClass) waiting.kind = updatedChange
                waiting.revision++
            }
            return
        }
        val change = classify(previous, target, keyboardChanging)
        if (change == ReflowChange.None) return
        // A window handoff can supersede a pane animation that started before its resize arrived.
        // Ordinary follow-up geometry uses the remaining motion rather than replaying it.
        if (animation?.isActive == true) {
            if (change != ReflowChange.WidthClass) return
            animation?.cancel()
        }
        val next = Pending(previous, change, keyboardChanging)
        pending = next
        value.floatValue = 0f
        animation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val ready = withTimeoutOrNull(MaxPresentationWaitMillis) {
                awaitPresentation(next)
            } == true
            pending = null
            if (ready && canAnimate()) {
                animate(0f, 1f, animationSpec = tween(ReflowMillis, easing = FastOutSlowInEasing)) { progress, _ ->
                    value.floatValue = progress
                }
            }
            value.floatValue = 1f
        }
    }

    private suspend fun awaitPresentation(waiting: Pending<Target>): Boolean {
        while (canAnimate()) {
            if (!targetIsPlaced || !canPresent()) {
                withFrameNanos { }
                continue
            }
            val revision = waiting.revision
            if (waiting.kind == ReflowChange.WidthClass) {
                // A new Compose size can arrive before the window's resize transaction is shown.
                // Wait for its draw and allow a short interval for the system's outgoing snapshot.
                awaitFrameCommit()
                if (!canAnimate()) return false
                if (waiting.revision != revision) continue
                delay(WidthClassSettleMillis)
            }
            repeat(2) { withFrameNanos { } }
            if (targetIsPlaced && canPresent() && waiting.revision == revision) return true
        }
        return false
    }

    private fun snap() {
        animation?.cancel()
        animation = null
        pending = null
        value.floatValue = 1f
    }

    private class Pending<T : Any>(val from: T, var kind: ReflowChange, val keyboardChanging: Boolean) {
        var revision = 0
    }
}

/** Use only for an artwork or a discrete pill with room for its animated hit bounds. */
internal fun Modifier.reflowScale(motion: ReflowProgress?, start: Float): Modifier =
    if (motion == null) this else graphicsLayer {
        val amount = start + (1f - start) * motion.progress
        scaleX = amount
        scaleY = amount
    }

internal enum class ReflowEntry { TowardEndFromCenter, TowardEndFromStart, TowardBottomFromTop, TowardTopFromBottom }

/** Move a live, already positioned child only through spare room in its target slot. */
internal fun Modifier.reflowPlacement(
    motion: ReflowProgress?,
    entry: ReflowEntry,
    distance: Dp = 18.dp,
): Modifier = if (motion == null) this else layout { measurable, constraints ->
    val child = measurable.measure(constraints)
    val freeWidth = if (constraints.hasBoundedWidth) constraints.maxWidth - child.width else 0
    val freeHeight = if (constraints.hasBoundedHeight) constraints.maxHeight - child.height else 0
    val slack = when (entry) {
        ReflowEntry.TowardEndFromCenter -> freeWidth / 2
        ReflowEntry.TowardEndFromStart -> freeWidth
        ReflowEntry.TowardBottomFromTop, ReflowEntry.TowardTopFromBottom -> freeHeight
    }
    val travel = minOf(distance.roundToPx(), slack.coerceAtLeast(0))
    layout(child.width, child.height) {
        val offset = (travel * (1f - motion.progress)).roundToInt()
        when (entry) {
            ReflowEntry.TowardEndFromCenter, ReflowEntry.TowardEndFromStart -> child.placeRelative(offset, 0)
            ReflowEntry.TowardBottomFromTop -> child.placeRelative(0, offset)
            ReflowEntry.TowardTopFromBottom -> child.placeRelative(0, -offset)
        }
    }
}

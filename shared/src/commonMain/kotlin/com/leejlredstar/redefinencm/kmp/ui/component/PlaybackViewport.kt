package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.adaptive.FoldPanes
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalWindowFold
import com.leejlredstar.redefinencm.kmp.ui.adaptive.WindowFold
import kotlin.math.roundToInt

/** The app keeps its artwork edge to edge; Android playback content avoids bars, cutouts and IME. */
internal val WindowInsets.Companion.playbackSafeContent: WindowInsets
    @Composable get() = safeDrawing.union(safeGestures)

/**
 * The Android playback host is window-sized before its visual transition. Project the hinge with
 * the same inset origin that places the controls, not an on-position callback from a sliding page.
 */
@Composable
internal fun PlaybackViewport(
    modifier: Modifier,
    safeInsets: WindowInsets?,
    content: @Composable (PlaybackFold?) -> Unit,
) {
    val fold = LocalWindowFold.current.takeIf { safeInsets != null }
    val projectedFold = if (safeInsets != null && fold != null) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val origin = IntOffset(safeInsets.getLeft(density, direction), safeInsets.getTop(density))
        PlaybackFold(fold, origin)
    } else null
    val insetModifier = if (safeInsets != null) Modifier.windowInsetsPadding(safeInsets).clipToBounds() else Modifier
    Box(modifier.then(insetModifier)) { content(projectedFold) }
}

/** Constrain a child to a physical, not RTL-relative, rectangle inside a full-size viewport. */
internal fun Modifier.inPlaybackPane(pane: IntRect): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(pane.width, pane.height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(pane.left, pane.top)
    }
}.clipToBounds()

internal data class PlaybackFold(private val fold: WindowFold, private val origin: IntOffset) {
    /** A partial or edge-clipped feature is not enough to arrange separate content around it. */
    fun spanningPanes(viewport: IntRect): FoldPanes? {
        val bounds = fold.bounds
        val spans = if (fold.isVertical) {
            bounds.top <= origin.y && bounds.bottom >= origin.y + viewport.height
        } else {
            bounds.left <= origin.x && bounds.right >= origin.x + viewport.width
        }
        return if (spans) fold.panesIn(origin, IntSize(viewport.width, viewport.height)) else null
    }

    fun singlePane(viewport: IntRect, panes: FoldPanes?): IntRect = when {
        panes != null -> if (panes.first.width.toLong() * panes.first.height >=
            panes.second.width.toLong() * panes.second.height
        ) panes.first else panes.second
        // Even when it cannot divide the viewport in two, an opaque hinge must not cover controls.
        else -> fold.browsePaneIn(origin, IntSize(viewport.width, viewport.height))
    }
}

/** Insets alone should not flip a nearly square window into a side-by-side layout. */
internal fun isWidePlaybackViewport(width: Dp, height: Dp): Boolean =
    width >= height * 1.25f

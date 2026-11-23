package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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

/** Places the fold in this playback page's already inset-adjusted coordinate space. */
@Composable
internal fun PlaybackViewport(
    modifier: Modifier,
    content: @Composable (PlaybackFold?) -> Unit,
) {
    val fold = LocalWindowFold.current
    var localOrigin by remember { mutableStateOf<IntOffset?>(null) }
    val observePosition = if (fold != null) {
        Modifier.onGloballyPositioned { coordinates ->
            val p = coordinates.positionInWindow()
            if (p.x.isFinite() && p.y.isFinite()) localOrigin = IntOffset(p.x.roundToInt(), p.y.roundToInt())
        }
    } else {
        Modifier
    }
    Box(modifier.then(observePosition)) {
        content(fold?.let { feature -> localOrigin?.let { PlaybackFold(feature, it) } })
    }
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

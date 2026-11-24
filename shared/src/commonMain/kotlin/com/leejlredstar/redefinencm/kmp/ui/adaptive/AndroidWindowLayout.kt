package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackPaneArrangement
import com.leejlredstar.redefinencm.kmp.ui.component.playbackPaneArrangement
import kotlin.math.roundToInt

internal enum class AndroidNavigationLayout { Floating, Compact, Rail }

/** Keep a compact top bar when a large header would crowd the Android content viewport. */
internal val LocalCompactAppBar = compositionLocalOf { false }
internal val CompactAppBarHeightThreshold = 380.dp

/** Use the actual window (less safe side insets), not the device's smallest width. */
internal fun androidNavigationLayout(width: Dp, height: Dp): AndroidNavigationLayout = when {
    // A single bottom row leaves the tabs and player visible together in a short window.
    height < 480.dp || width < 320.dp -> AndroidNavigationLayout.Compact
    width >= 600.dp -> AndroidNavigationLayout.Rail
    else -> AndroidNavigationLayout.Floating
}

/** On dual-screen hardware, keep single-pane browsing out of a physically hidden hinge. */
@Composable
internal fun FoldSafeBrowseContent(
    modifier: Modifier,
    enabled: Boolean,
    obstructions: WindowInsets?,
    contentModifier: Modifier,
    content: @Composable (width: Dp, height: Dp, obstructedTop: Dp, pane: PlaybackPaneArrangement?) -> Unit,
) {
    val fold = LocalWindowFold.current?.takeIf { enabled && it.isOccluding }
    var placedPane by remember(fold) { mutableStateOf<PlaybackPaneArrangement?>(null) }
    val paneModifier = if (fold != null && obstructions != null) {
        Modifier.layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val edges = obstructions.asEdges(this, layoutDirection)
            // Use the fixed viewport's coordinates during placement, before measuring the
            // child. A cached pane could be out of bounds for a frame during window resizing.
            layout(width, height) {
                val origin = coordinates?.positionInWindow()
                if (origin != null && origin.x.isFinite() && origin.y.isFinite()) {
                    val pane = fold.edgeToEdgeBrowsePane(
                        IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                        IntSize(width, height),
                        edges,
                    )
                    if (pane.width > 0 && pane.height > 0) {
                        measurable.measure(Constraints.fixed(pane.width, pane.height)).place(pane.left, pane.top)
                        placedPane = playbackPaneArrangement(IntRect(0, 0, width, height), pane)
                    } else {
                        placedPane = null
                    }
                } else {
                    placedPane = null
                }
            }
        }.clipToBounds().recalculateWindowInsets()
    } else {
        Modifier
    }
    // Choose chrome before the IME changes the available page height. Keep the same
    // content node when a hinge appears/disappears so it does not remount the active page.
    var consumed by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
    val observeInsets = if (fold != null) Modifier.onConsumedWindowInsetsChanged { consumed = it } else Modifier
    BoxWithConstraints(modifier.then(paneModifier).then(observeInsets)) {
        val remaining = if (fold == null) obstructions else obstructions?.exclude(consumed)
        val density = LocalDensity.current
        val edges = remaining?.asEdges(density, LocalLayoutDirection.current)
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        with(density) {
            val horizontal = ((edges?.left ?: 0) + (edges?.right ?: 0)).toDp()
            val top = (edges?.top ?: 0).toDp()
            Box(Modifier.fillMaxSize().then(contentModifier)) {
                content(
                    (viewportWidth - horizontal).coerceAtLeast(0.dp),
                    (viewportHeight - top).coerceAtLeast(0.dp),
                    top,
                    if (fold == null) PlaybackPaneArrangement.Full else placedPane,
                )
            }
        }
    }
}

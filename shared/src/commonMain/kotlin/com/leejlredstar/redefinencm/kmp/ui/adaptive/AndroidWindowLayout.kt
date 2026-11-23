package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    contentModifier: Modifier,
    content: @Composable (Dp, Dp) -> Unit,
) {
    val fold = LocalWindowFold.current?.takeIf { enabled && it.isOccluding }
    val paneModifier = if (fold != null) {
        Modifier.layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            // Use the fixed viewport's coordinates during placement, before measuring the
            // child. A cached pane could be out of bounds for a frame during window resizing.
            layout(width, height) {
                val origin = coordinates?.positionInWindow()
                if (origin != null && origin.x.isFinite() && origin.y.isFinite()) {
                    val pane = fold.browsePaneIn(
                        IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                        IntSize(width, height),
                    )
                    if (pane.width > 0 && pane.height > 0) {
                        measurable.measure(Constraints.fixed(pane.width, pane.height)).place(pane.left, pane.top)
                    }
                }
            }
        }.clipToBounds().recalculateWindowInsets()
    } else {
        Modifier
    }
    // Choose chrome before the IME changes the available page height. Keep the same
    // content node when a hinge appears/disappears so it does not remount the active page.
    BoxWithConstraints(modifier.then(paneModifier)) {
        val width = maxWidth
        val height = maxHeight
        Box(Modifier.fillMaxSize().then(contentModifier)) {
            content(width, height)
        }
    }
}

package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal enum class AndroidNavigationLayout { Floating, Compact, Rail }

/** Keep a compact top bar when a large header would crowd the Android content viewport. */
internal val LocalCompactAppBar = compositionLocalOf { false }
internal val CompactAppBarHeightThreshold = 380.dp

/** Use the actual window (less safe side insets), not the device's smallest width. */
internal fun androidNavigationLayout(width: Dp, height: Dp): AndroidNavigationLayout = when {
    height >= 220.dp && (width >= 600.dp || (width >= 420.dp && height < 480.dp)) -> AndroidNavigationLayout.Rail
    width < 320.dp || height < 480.dp -> AndroidNavigationLayout.Compact
    else -> AndroidNavigationLayout.Floating
}

/** On dual-screen hardware, keep single-pane browsing out of a physically hidden hinge. */
@Composable
internal fun FoldSafeBrowseContent(
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val fold = LocalWindowFold.current?.takeIf { enabled && it.isOccluding }
    var pane by remember(fold) { mutableStateOf<IntRect?>(null) }
    val observeWindow = if (fold != null) {
        Modifier.onGloballyPositioned { coordinates ->
            val origin = coordinates.positionInWindow()
            val panes = if (origin.x.isFinite() && origin.y.isFinite()) {
                fold.panesIn(IntOffset(origin.x.roundToInt(), origin.y.roundToInt()), coordinates.size)
            } else {
                null
            }
            pane = panes?.let {
                if (it.first.width * it.first.height >= it.second.width * it.second.height) it.first else it.second
            }
        }
    } else {
        Modifier
    }
    // The same content node is measured again, not swapped, when the hinge appears/disappears.
    Box(modifier.then(observeWindow)) {
        Box(
            Modifier.fillMaxSize().layout { measurable, constraints ->
                val target = pane
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                if (target == null || target.right > width || target.bottom > height) {
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                } else {
                    val placeable = measurable.measure(Constraints.fixed(target.width, target.height))
                    layout(width, height) { placeable.place(target.left, target.top) }
                }
            }.clipToBounds(),
            content = content,
        )
    }
}

package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/** The obstructions Android browsing must avoid, independent of where it paints. */
internal fun browseObstructions(bars: WindowInsets, cutout: WindowInsets, caption: WindowInsets): WindowInsets =
    bars.union(cutout).only(WindowInsetsSides.Horizontal)
        .union(caption.union(cutout).only(WindowInsetsSides.Top))

internal val WindowInsets.Companion.androidBrowseObstructions: WindowInsets
    @Composable get() = browseObstructions(systemBars, displayCutout, captionBar)

internal val LocalBrowseObstructions = staticCompositionLocalOf<WindowInsets?> { null }

internal fun WindowInsets.asEdges(density: Density, direction: LayoutDirection): IntRect = IntRect(
    getLeft(density, direction), getTop(density), getRight(density, direction), getBottom(density),
)

/** Measure the usable window, but let the chosen pane paint into its adjacent system insets. */
internal fun WindowFold.edgeToEdgeBrowsePane(origin: IntOffset, size: IntSize, edges: IntRect): IntRect {
    val safeWidth = (size.width - edges.left - edges.right).coerceAtLeast(0)
    val safeHeight = (size.height - edges.top - edges.bottom).coerceAtLeast(0)
    if (safeWidth == 0 || safeHeight == 0) return IntRect(0, 0, 0, 0)
    val safe = browsePaneIn(
        origin + IntOffset(edges.left, edges.top), IntSize(safeWidth, safeHeight),
    )
    if (safe.width == 0 || safe.height == 0) return safe
    return IntRect(
        if (safe.left == 0) 0 else safe.left + edges.left,
        if (safe.top == 0) 0 else safe.top + edges.top,
        if (safe.right == safeWidth) size.width else safe.right + edges.left,
        if (safe.bottom == safeHeight) size.height else safe.bottom + edges.top,
    )
}

/** For a custom heading painted over the page or hero, not a second strip above it. */
internal val WindowInsets.Companion.browseHeader: WindowInsets
    @Composable get() {
        val status = statusBars
        val obstructions = LocalBrowseObstructions.current ?: return status
        return status.union(obstructions.only(WindowInsetsSides.Top))
    }

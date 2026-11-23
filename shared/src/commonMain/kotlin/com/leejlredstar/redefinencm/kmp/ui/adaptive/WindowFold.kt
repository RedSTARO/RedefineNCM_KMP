package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

/** A separating fold or hinge in window-local pixels, as reported by the platform. */
internal data class WindowFold(
    val bounds: IntRect,
    val isVertical: Boolean,
    val isOccluding: Boolean = false,
) {
    /** Largest usable rectangle in a browsing viewport, even if the hinge clips an edge. */
    fun browsePaneIn(origin: IntOffset, size: IntSize): IntRect {
        val viewport = IntRect(0, 0, size.width, size.height)
        if (!isOccluding) return viewport
        val left = (bounds.left - origin.x).coerceIn(0, size.width)
        val top = (bounds.top - origin.y).coerceIn(0, size.height)
        val right = (bounds.right - origin.x).coerceIn(0, size.width)
        val bottom = (bounds.bottom - origin.y).coerceIn(0, size.height)
        if (left >= right || top >= bottom) return viewport
        // Every unoccluded rectangle fits in at least one of these strips. Compare physical
        // areas without mirroring in RTL, preserving the first pane when the two sides tie.
        val largest = listOf(
            IntRect(0, 0, left, size.height),
            IntRect(right, 0, size.width, size.height),
            IntRect(0, 0, size.width, top),
            IntRect(0, bottom, size.width, size.height),
        ).maxBy { it.width.toLong() * it.height }
        return if (largest.width == 0 || largest.height == 0) IntRect(0, 0, 0, 0) else largest
    }

    /** Two usable rectangles in the measured viewport's coordinates, not display coordinates. */
    fun panesIn(origin: IntOffset, size: IntSize, clearancePx: Int = 0): FoldPanes? {
        if (size.width <= 0 || size.height <= 0) return null
        val extent = if (isVertical) size.width else size.height
        val firstEdge = if (isVertical) bounds.left - origin.x else bounds.top - origin.y
        val secondEdge = if (isVertical) bounds.right - origin.x else bounds.bottom - origin.y
        val crossesViewport = if (isVertical) {
            bounds.top < origin.y + size.height && bounds.bottom > origin.y
        } else {
            bounds.left < origin.x + size.width && bounds.right > origin.x
        }
        if (!crossesViewport || firstEdge <= 0 || secondEdge >= extent || firstEdge > secondEdge) return null
        val start = firstEdge - clearancePx
        val end = secondEdge + clearancePx
        if (start <= 0 || end >= extent) return null
        return if (isVertical) {
            FoldPanes(IntRect(0, 0, start, size.height), IntRect(end, 0, size.width, size.height))
        } else {
            FoldPanes(IntRect(0, 0, size.width, start), IntRect(0, end, size.width, size.height))
        }
    }
}

internal data class FoldPanes(val first: IntRect, val second: IntRect)

internal val LocalWindowFold = compositionLocalOf<WindowFold?> { null }

/** True even for a floating Android keyboard that does not resize the content. */
internal val LocalSoftKeyboardVisible = compositionLocalOf { false }

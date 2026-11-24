package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.ui.component.AndroidReflowWindow
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackPaneArrangement
import com.leejlredstar.redefinencm.kmp.ui.component.ReflowChange
import com.leejlredstar.redefinencm.kmp.ui.component.ReflowProgress
import com.leejlredstar.redefinencm.kmp.ui.component.adaptiveReflowChange
import com.leejlredstar.redefinencm.kmp.ui.component.rememberAdaptiveReflow

/** A FAB slot is a geometry decision, independent of whether the route or media needs a player. */
internal fun androidMiniPlayerFabSlot(
    layout: AndroidNavigationLayout,
    keyboardVisible: Boolean,
    fits: Boolean,
): AndroidNavigationLayout? = layout.takeUnless {
    it == AndroidNavigationLayout.Compact || keyboardVisible || !fits
}

internal data class AndroidNavigationTarget(
    val layout: AndroidNavigationLayout,
    val usableSize: DpSize,
    val pane: PlaybackPaneArrangement,
    val fold: WindowFold?,
    val window: AndroidReflowWindow?,
)

internal fun androidNavigationReflowChange(
    before: AndroidNavigationTarget,
    after: AndroidNavigationTarget,
    keyboardChanging: Boolean = false,
): ReflowChange {
    if (before.layout == after.layout && before.pane == after.pane) return ReflowChange.None
    return adaptiveReflowChange(
        before.window, after.window,
        usableBoundsChanged = before.usableSize != after.usableSize,
        effectiveFoldChanged = before.pane != after.pane && before.fold != after.fold,
        crossesWidthClass = (before.usableSize.width >= 600.dp) != (after.usableSize.width >= 600.dp),
        keyboardChanging = keyboardChanging,
    )
}

@Composable
internal fun rememberAndroidNavigationMotion(enabled: Boolean) = if (enabled) {
    rememberAdaptiveReflow<AndroidNavigationTarget>(rememberReducedMotionEnabled(), ::androidNavigationReflowChange)
} else null

/** Compress only the foreground row and only when its tabs retain their touch width. */
internal fun Modifier.compactNavReflow(motion: ReflowProgress?, itemCount: Int): Modifier =
    if (motion == null) this else graphicsLayer {
        // Allow for Material's inter-item spacing as well as the 48 dp target.
        val minWidth = (48.dp * itemCount + 8.dp * (itemCount - 1)).toPx()
        val start = (minWidth / size.width.coerceAtLeast(1f)).coerceIn(0.85f, 1f)
        scaleX = start + (1f - start) * motion.progress
    }

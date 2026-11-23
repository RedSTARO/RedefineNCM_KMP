package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.ui.component.rememberReflowFade

/** A FAB slot is a geometry decision, independent of whether the route or media needs a player. */
internal fun androidMiniPlayerFabSlot(
    layout: AndroidNavigationLayout,
    keyboardVisible: Boolean,
    fits: Boolean,
): AndroidNavigationLayout? = layout.takeUnless {
    it == AndroidNavigationLayout.Compact || keyboardVisible || !fits
}

@Composable
internal fun rememberAndroidNavigationFade(layout: AndroidNavigationLayout?, showTabs: Boolean): Modifier {
    if (layout == null) return Modifier
    // The mode already reflects the usable pane. Raw bounds and fold reports aren't motion keys.
    return rememberReflowFade(
        enabled = true,
        target = layout.takeIf { showTabs },
        reducedMotion = rememberReducedMotionEnabled(),
    )
}

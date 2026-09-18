package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Desktop back navigation through the window's navigation-event dispatcher, which the window
 * feeds from the Esc key. The mouse back button is fed into the same dispatcher by the desktop
 * app shell, so the innermost enabled handler wins either way — a search overlay closes before
 * the page behind it pops.
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}

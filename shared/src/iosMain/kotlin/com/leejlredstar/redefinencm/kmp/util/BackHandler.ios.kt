package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * iOS back navigation through the window's navigation-event dispatcher, which recognises the
 * system edge-swipe gesture. With an empty actual, a screen can only be left through its
 * on-screen back button.
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}

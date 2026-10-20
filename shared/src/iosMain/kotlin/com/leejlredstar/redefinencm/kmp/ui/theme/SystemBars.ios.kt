package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

/**
 * The app sets the status bar's style itself: SwiftUI's hosting controller never asks the hosted
 * Compose view controller for one, so a view controller's preferred style would go unread.
 * Info.plist turns off view-controller-based status bar appearance for that.
 */
@Composable
actual fun SystemBarIcons(darkBackground: Boolean) {
    SideEffect {
        UIApplication.sharedApplication.setStatusBarStyle(
            if (darkBackground) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent,
            animated = true,
        )
    }
}

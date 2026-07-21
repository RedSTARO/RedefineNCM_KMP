package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class DesktopOverlayPlacement {
    TopStart,
    Center,
    BottomCenter,
}

/**
 * A bounded desktop overlay that is hosted outside the main Compose scene.
 *
 * Windows AMLL uses a native WebView2 child HWND, which is always above lightweight Compose
 * layers in the main scene. Desktop uses an owned native window here so the bounded overlay can
 * cover AMLL without hiding or pausing the WebView. Non-desktop actuals are intentionally inert.
 */
@Composable
internal expect fun DesktopOverlayWindow(
    visible: Boolean,
    title: String,
    width: Dp,
    height: Dp,
    placement: DesktopOverlayPlacement,
    topOffset: Dp = 0.dp,
    focusable: Boolean = true,
    modal: Boolean = false,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
)

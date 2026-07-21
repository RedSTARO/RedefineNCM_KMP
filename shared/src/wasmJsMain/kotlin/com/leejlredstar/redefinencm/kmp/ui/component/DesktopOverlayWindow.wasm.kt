package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
internal actual fun DesktopOverlayWindow(
    visible: Boolean,
    title: String,
    width: Dp,
    height: Dp,
    placement: DesktopOverlayPlacement,
    topOffset: Dp,
    focusable: Boolean,
    modal: Boolean,
    transparent: Boolean,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    check(!visible) { "DesktopOverlayWindow cannot be shown on Web" }
}

package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Android, iOS and the browser have no window the app owns, so there is nothing to host an
 * overlay in. Callers gate on the desktop capability before asking for one; the check keeps a
 * missed gate loud instead of silently dropping UI.
 */
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
    windowShape: DesktopOverlayWindowShape,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    check(!visible) { "DesktopOverlayWindow is desktop-only and cannot be shown on this target" }
}

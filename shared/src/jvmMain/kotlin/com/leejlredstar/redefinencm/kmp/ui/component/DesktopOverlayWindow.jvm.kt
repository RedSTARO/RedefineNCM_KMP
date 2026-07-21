package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogModalityType
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.rememberDialogState
import java.awt.Dimension
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener
import kotlin.math.roundToInt
import java.awt.Color as AwtColor

private val LocalDesktopOverlayOwner = staticCompositionLocalOf<Window?> { null }

@Composable
fun ProvideDesktopOverlayOwner(
    window: Window,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDesktopOverlayOwner provides window, content = content)
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
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
    if (!visible || width <= 0.dp || height <= 0.dp) return

    val owner = LocalDesktopOverlayOwner.current ?: return
    val dialogState = rememberDialogState(width = width, height = height)
    LaunchedEffect(width, height) {
        dialogState.size = DpSize(width, height)
    }

    DialogWindow(
        onCloseRequest = onCloseRequest,
        state = dialogState,
        visible = true,
        title = title,
        decoration = WindowDecoration.Undecorated(),
        transparent = transparent,
        resizable = false,
        enabled = true,
        focusable = focusable,
        alwaysOnTop = false,
        modalityType = if (modal) {
            DialogModalityType.DocumentModal
        } else {
            DialogModalityType.Modeless
        },
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onCloseRequest()
                true
            } else {
                false
            }
        },
    ) {
        val overlayWindow = window
        DisposableEffect(owner, overlayWindow, width, height, placement, topOffset, transparent) {
            var disposed = false
            val requestedWidth = width.value.roundToInt().coerceAtLeast(1)
            val requestedHeight = height.value.roundToInt().coerceAtLeast(1)
            val requestedTopOffset = topOffset.value.roundToInt().coerceAtLeast(0)

            fun ownerIsIconified(): Boolean =
                owner is Frame && owner.extendedState and Frame.ICONIFIED != 0

            fun alignToOwner() {
                if (disposed || !owner.isShowing || !overlayWindow.isDisplayable) return

                val ownerInsets = owner.insets
                val ownerContentWidth =
                    (owner.width - ownerInsets.left - ownerInsets.right).coerceAtLeast(1)
                val ownerContentHeight =
                    (owner.height - ownerInsets.top - ownerInsets.bottom).coerceAtLeast(1)
                val availableHeight = (ownerContentHeight - requestedTopOffset).coerceAtLeast(1)
                val targetWidth = requestedWidth.coerceAtMost(ownerContentWidth)
                val targetHeight = requestedHeight.coerceAtMost(availableHeight)
                val targetSize = Dimension(targetWidth, targetHeight)
                overlayWindow.minimumSize = targetSize
                overlayWindow.preferredSize = targetSize
                overlayWindow.maximumSize = targetSize
                if (overlayWindow.size != targetSize) {
                    overlayWindow.size = targetSize
                }

                val contentX = owner.x + ownerInsets.left
                val contentY = owner.y + ownerInsets.top + requestedTopOffset
                when (placement) {
                    DesktopOverlayPlacement.TopStart -> {
                        overlayWindow.setLocation(contentX, contentY)
                    }

                    DesktopOverlayPlacement.Center -> {
                        overlayWindow.setLocation(
                            contentX + (ownerContentWidth - targetWidth) / 2,
                            contentY + (availableHeight - targetHeight) / 2,
                        )
                    }

                    DesktopOverlayPlacement.BottomCenter -> {
                        overlayWindow.setLocation(
                            contentX + (ownerContentWidth - targetWidth) / 2,
                            contentY + availableHeight - targetHeight,
                        )
                    }
                }
                overlayWindow.isVisible = !ownerIsIconified()
            }

            val ownerListener = object : ComponentAdapter() {
                override fun componentMoved(event: ComponentEvent) = alignToOwner()
                override fun componentResized(event: ComponentEvent) = alignToOwner()
                override fun componentShown(event: ComponentEvent) = alignToOwner()
                override fun componentHidden(event: ComponentEvent) {
                    overlayWindow.isVisible = false
                }
            }
            val overlayListener = object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) = alignToOwner()
                override fun componentShown(event: ComponentEvent) = alignToOwner()
            }
            val ownerStateListener = WindowStateListener { _: WindowEvent ->
                alignToOwner()
            }

            owner.addComponentListener(ownerListener)
            overlayWindow.addComponentListener(overlayListener)
            if (owner is Frame) owner.addWindowStateListener(ownerStateListener)
            if (!transparent) overlayWindow.background = AwtColor.BLACK
            javax.swing.SwingUtilities.invokeLater(::alignToOwner)

            onDispose {
                val restoreOwnerFocus = focusable &&
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow === overlayWindow
                disposed = true
                owner.removeComponentListener(ownerListener)
                overlayWindow.removeComponentListener(overlayListener)
                if (owner is Frame) owner.removeWindowStateListener(ownerStateListener)
                if (restoreOwnerFocus && owner.isShowing && !ownerIsIconified()) {
                    owner.requestFocus()
                }
            }
        }

        content()
    }
}

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
import com.sun.jna.Native
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import java.awt.Dimension
import java.awt.Frame
import java.awt.IllegalComponentStateException
import java.awt.KeyboardFocusManager
import java.awt.Shape
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
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
    windowShape: DesktopOverlayWindowShape,
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
        DisposableEffect(
            owner,
            overlayWindow,
            width,
            height,
            placement,
            topOffset,
            transparent,
            windowShape,
        ) {
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
                    // Keep the current Win32 region while changing bounds. Clearing it first
                    // exposes the full opaque dialog for one frame, which is exactly the black
                    // rectangle this overlay exists to avoid. The new HRGN replaces it below.
                    if (!runningOnWindows()) {
                        overlayWindow.shape = null
                    }
                    overlayWindow.size = targetSize
                }
                try {
                    if (
                        !applyWindowsDesktopOverlayWindowRegion(
                            window = overlayWindow,
                            windowShape = windowShape,
                            width = targetWidth,
                            height = targetHeight,
                        )
                    ) {
                        overlayWindow.shape = desktopOverlayWindowShape(
                            windowShape = windowShape,
                            width = targetWidth,
                            height = targetHeight,
                        )
                    }
                } catch (_: UnsupportedOperationException) {
                    // Some non-Windows desktop environments do not support shaped windows.
                    // Keep the rectangular host there instead of failing the whole lyric screen.
                    runCatching { overlayWindow.shape = null }
                } catch (_: IllegalComponentStateException) {
                    runCatching { overlayWindow.shape = null }
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

/**
 * AWT's `Window.shape` can be cleared while Compose Desktop finishes creating its Skia peer.
 * Windows WebView2 makes that failure visible as the opaque dialog's black bounding rectangle.
 * Apply the equivalent Win32 window region after every alignment so the native compositor owns
 * the clipping boundary. Other JVM platforms continue to use the portable AWT shape fallback.
 */
private fun applyWindowsDesktopOverlayWindowRegion(
    window: Window,
    windowShape: DesktopOverlayWindowShape,
    width: Int,
    height: Int,
): Boolean {
    if (!runningOnWindows()) return false

    val windowPointer = runCatching { Native.getComponentPointer(window) }.getOrNull()
        ?: return false
    val hwnd = WinDef.HWND(windowPointer)
    val user32 = User32.INSTANCE
    if (!user32.IsWindow(hwnd)) return false

    if (windowShape == DesktopOverlayWindowShape.Rectangle) {
        return user32.SetWindowRgn(hwnd, null, true) != 0
    }

    val windowRect = WinDef.RECT()
    if (!user32.GetWindowRect(hwnd, windowRect)) return false
    val nativeWidth = (windowRect.right - windowRect.left).coerceAtLeast(1)
    val nativeHeight = (windowRect.bottom - windowRect.top).coerceAtLeast(1)
    val scaleX = nativeWidth.toDouble() / width.coerceAtLeast(1).toDouble()
    val scaleY = nativeHeight.toDouble() / height.coerceAtLeast(1).toDouble()
    fun x(value: Int): Int = (value * scaleX).roundToInt()
    fun y(value: Int): Int = (value * scaleY).roundToInt()

    val gdi32 = GDI32.INSTANCE
    val region = when (windowShape) {
        DesktopOverlayWindowShape.Rectangle -> null
        DesktopOverlayWindowShape.CollapsedPlaybackControls -> {
            if (width <= 16 || height <= 16) return false
            gdi32.CreateRoundRectRgn(
                x(8),
                y(8),
                x(width - 8),
                y(height - 8),
                x(48).coerceAtLeast(1),
                y(48).coerceAtLeast(1),
            )
        }
        DesktopOverlayWindowShape.ExpandedPlaybackControls -> {
            if (width <= 32 || height <= 88) return false
            val topRegion = gdi32.CreateRoundRectRgn(
                x(16),
                y(8),
                x(width - 16),
                y(height - 79),
                x(72).coerceAtLeast(1),
                y(72).coerceAtLeast(1),
            ) ?: return false
            val bottomRegion = gdi32.CreateRoundRectRgn(
                x(16),
                y(height - 72),
                x(width - 16),
                y(height - 8),
                x(64).coerceAtLeast(1),
                y(64).coerceAtLeast(1),
            ) ?: run {
                gdi32.DeleteObject(topRegion)
                return false
            }
            val combinedRegion = gdi32.CreateRectRgn(0, 0, 0, 0) ?: run {
                gdi32.DeleteObject(topRegion)
                gdi32.DeleteObject(bottomRegion)
                return false
            }
            val combineResult = gdi32.CombineRgn(
                combinedRegion,
                topRegion,
                bottomRegion,
                WinGDI.RGN_OR,
            )
            gdi32.DeleteObject(topRegion)
            gdi32.DeleteObject(bottomRegion)
            if (combineResult == 0) {
                gdi32.DeleteObject(combinedRegion)
                return false
            }
            combinedRegion
        }
    } ?: return false

    val applied = user32.SetWindowRgn(hwnd, region, true) != 0
    if (!applied) {
        // On success Windows owns the region handle. Delete it only when the transfer failed.
        gdi32.DeleteObject(region)
    }
    return applied
}

private fun runningOnWindows(): Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

internal fun desktopOverlayWindowShape(
    windowShape: DesktopOverlayWindowShape,
    width: Int,
    height: Int,
): Shape? {
    val safeWidth = width.coerceAtLeast(0)
    val safeHeight = height.coerceAtLeast(0)
    return when (windowShape) {
        DesktopOverlayWindowShape.Rectangle -> null
        DesktopOverlayWindowShape.ExpandedPlaybackControls -> if (
            safeWidth <= 32 || safeHeight <= 88
        ) {
            null
        } else {
            Area().apply {
                add(
                    Area(
                        roundedRectangle(
                            left = 16,
                            top = 8,
                            right = 16,
                            bottom = 79,
                            width = safeWidth,
                            height = safeHeight,
                            arcDiameter = 72,
                        ),
                    ),
                )
                add(
                    Area(
                        roundedRectangle(
                            left = 16,
                            top = (safeHeight - 72).coerceAtLeast(0),
                            right = 16,
                            bottom = 8,
                            width = safeWidth,
                            height = safeHeight,
                            arcDiameter = 64,
                        ),
                    ),
                )
            }
        }
        DesktopOverlayWindowShape.CollapsedPlaybackControls -> if (
            safeWidth <= 16 || safeHeight <= 16
        ) {
            null
        } else {
            roundedRectangle(
                left = 8,
                top = 8,
                right = 8,
                bottom = 8,
                width = safeWidth,
                height = safeHeight,
                arcDiameter = 48,
            )
        }
    }
}

private fun roundedRectangle(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    width: Int,
    height: Int,
    arcDiameter: Int,
): RoundRectangle2D.Double = RoundRectangle2D.Double(
    left.toDouble(),
    top.toDouble(),
    (width - left - right).coerceAtLeast(0).toDouble(),
    (height - top - bottom).coerceAtLeast(0).toDouble(),
    arcDiameter.toDouble(),
    arcDiameter.toDouble(),
)

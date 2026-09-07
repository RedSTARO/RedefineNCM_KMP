package com.leejlredstar.redefinencm.kmp.notification

import com.leejlredstar.redefinencm.kmp.DesktopOs
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import java.awt.Window

/**
 * The part of a locked desktop-lyrics window that Compose cannot express: letting the pointer
 * fall through to whatever is underneath.
 *
 * Compose can stop dragging and resizing, but a window still swallows every click that lands on
 * it, and a lyric strip parked over a browser or a game must not. On Windows the extended style
 * `WS_EX_TRANSPARENT` does exactly that for a layered window, which a transparent Compose window
 * already is. Other desktops have no equivalent in AWT; there a locked window only stops moving.
 */
object DesktopFloatingWindowNative {
    /** Whether [setClickThrough] can do anything on this host. */
    val supportsClickThrough: Boolean = DesktopOs.current == DesktopOs.Windows

    /**
     * Makes [window] ignore the pointer when [enabled], or take it again when not.
     *
     * Returns whether the style was applied; false on other operating systems or when AWT has
     * not created the native window yet.
     */
    fun setClickThrough(window: Window, enabled: Boolean): Boolean {
        if (!supportsClickThrough) return false
        val pointer = runCatching { Native.getWindowPointer(window) }.getOrNull()
        if (pointer == null || pointer == Pointer.NULL) return false
        val hwnd = HWND(pointer)
        val user32 = User32.INSTANCE
        val current = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
        val next = clickThroughExtendedStyle(current, enabled)
        if (next != current) user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, next)
        return true
    }
}

/**
 * The extended window style with the click-through bits set or cleared.
 *
 * `WS_EX_TRANSPARENT` only lets input through when the window is also `WS_EX_LAYERED`; a
 * transparent Compose window is layered already, so setting both is idempotent. Clearing leaves
 * the layered bit alone, since per-pixel transparency still needs it.
 */
internal fun clickThroughExtendedStyle(current: Int, enabled: Boolean): Int =
    if (enabled) {
        current or WinUser.WS_EX_TRANSPARENT or WinUser.WS_EX_LAYERED
    } else {
        current and WinUser.WS_EX_TRANSPARENT.inv()
    }

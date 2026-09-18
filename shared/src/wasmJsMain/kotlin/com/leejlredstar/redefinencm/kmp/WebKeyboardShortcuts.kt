@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp

import androidx.compose.ui.input.key.Key
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import kotlin.JsFun

/**
 * The desktop window's keyboard shortcuts, in the browser page. A key reaches them only when the
 * app left it alone: Compose marks the key events it uses as handled (a focused button taking
 * Space, a dialog taking Esc), and keys typed into a text field are skipped outright.
 */
internal fun installWebKeyboardShortcuts(player: PlatformPlayer) {
    installWebShortcutListener { code, command, shift ->
        val key = when (code) {
            "Space" -> Key.Spacebar
            "ArrowLeft" -> Key.DirectionLeft
            "ArrowRight" -> Key.DirectionRight
            "ArrowUp" -> Key.DirectionUp
            "ArrowDown" -> Key.DirectionDown
            "KeyF" -> Key.F
            else -> null
        }
        key?.let { appShortcutFor(it, command, shift) }?.perform(player) ?: false
    }
}

@JsFun(
    """(onShortcut) => {
        window.addEventListener("keydown", event => {
            if (event.defaultPrevented || event.isComposing || event.altKey) return;
            // Holding an arrow keeps seeking; holding anything else must not toggle over and over.
            if (event.repeat && !event.code.startsWith("Arrow")) return;
            const target = event.target;
            if (target instanceof HTMLElement &&
                (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) {
                return;
            }
            if (event.code === "Escape") {
                // The page's back handlers answer the browser's back button; Esc goes the same
                // way, but only while one is there to answer, or it would leave the site.
                if (globalThis.__redefineNcmBackState?.handlers.size > 0) {
                    event.preventDefault();
                    history.back();
                }
                return;
            }
            if (onShortcut(event.code, event.ctrlKey || event.metaKey, event.shiftKey)) {
                event.preventDefault();
            }
        });
    }""",
)
private external fun installWebShortcutListener(onShortcut: (String, Boolean, Boolean) -> Boolean)

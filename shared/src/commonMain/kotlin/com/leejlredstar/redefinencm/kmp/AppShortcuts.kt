package com.leejlredstar.redefinencm.kmp

import androidx.compose.ui.input.key.Key
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer

/**
 * The keyboard shortcuts of the desktop window and the browser page. Each turns its own key
 * events into a [Key] and the modifier state; which shortcut that is, and what it does, is
 * decided here once.
 */
enum class AppShortcut {
    PlayPause,
    Next,
    Previous,
    SeekForward,
    SeekBackward,
    VolumeUp,
    VolumeDown,
    Search,
}

/**
 * The shortcut for [key], pressed with Ctrl (⌘ on macOS) when [command] and with Shift when
 * [shift], or null. Shift is left alone throughout: with the arrows it extends text selections.
 */
fun appShortcutFor(key: Key, command: Boolean, shift: Boolean): AppShortcut? = when {
    shift -> null
    key == Key.Spacebar -> if (command) null else AppShortcut.PlayPause
    key == Key.DirectionRight -> if (command) AppShortcut.Next else AppShortcut.SeekForward
    key == Key.DirectionLeft -> if (command) AppShortcut.Previous else AppShortcut.SeekBackward
    key == Key.DirectionUp && command -> AppShortcut.VolumeUp
    key == Key.DirectionDown && command -> AppShortcut.VolumeDown
    key == Key.F && command -> AppShortcut.Search
    else -> null
}

/** Carries out the shortcut; false when it does not apply now, so the key is left alone. */
fun AppShortcut.perform(player: PlatformPlayer): Boolean {
    when (this) {
        AppShortcut.PlayPause -> player.togglePlayPause()
        AppShortcut.Next -> player.seekToNext()
        AppShortcut.Previous -> player.seekToPrevious()
        AppShortcut.SeekForward,
        AppShortcut.SeekBackward -> {
            if (player.currentMedia.value == null) return false
            val step = if (this == AppShortcut.SeekForward) SeekStepMillis else -SeekStepMillis
            val target = (player.position.value + step).coerceAtLeast(0L)
            val duration = player.duration.value
            player.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
        }
        AppShortcut.VolumeUp -> player.setVolume((player.volume.value + VolumeStep).coerceAtMost(1f))
        AppShortcut.VolumeDown -> player.setVolume((player.volume.value - VolumeStep).coerceAtLeast(0f))
        AppShortcut.Search -> AppNavigationRequests.openSearch()
    }
    return true
}

private const val SeekStepMillis = 5_000L
private const val VolumeStep = 0.1f

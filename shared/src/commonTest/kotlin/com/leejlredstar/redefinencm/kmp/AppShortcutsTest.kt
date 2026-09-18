package com.leejlredstar.redefinencm.kmp

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppShortcutsTest {
    @Test
    fun plainKeysControlPlayback() {
        assertEquals(AppShortcut.PlayPause, appShortcutFor(Key.Spacebar, command = false, shift = false))
        assertEquals(AppShortcut.SeekForward, appShortcutFor(Key.DirectionRight, command = false, shift = false))
        assertEquals(AppShortcut.SeekBackward, appShortcutFor(Key.DirectionLeft, command = false, shift = false))
    }

    @Test
    fun commandKeysSkipTracksChangeVolumeAndSearch() {
        assertEquals(AppShortcut.Next, appShortcutFor(Key.DirectionRight, command = true, shift = false))
        assertEquals(AppShortcut.Previous, appShortcutFor(Key.DirectionLeft, command = true, shift = false))
        assertEquals(AppShortcut.VolumeUp, appShortcutFor(Key.DirectionUp, command = true, shift = false))
        assertEquals(AppShortcut.VolumeDown, appShortcutFor(Key.DirectionDown, command = true, shift = false))
        assertEquals(AppShortcut.Search, appShortcutFor(Key.F, command = true, shift = false))
    }

    @Test
    fun keysWithoutAShortcutAreLeftAlone() {
        // Plain up/down scroll lists; a plain F is typing; Shift+arrows extend selections.
        assertNull(appShortcutFor(Key.DirectionUp, command = false, shift = false))
        assertNull(appShortcutFor(Key.DirectionDown, command = false, shift = false))
        assertNull(appShortcutFor(Key.F, command = false, shift = false))
        assertNull(appShortcutFor(Key.Spacebar, command = true, shift = false))
        assertNull(appShortcutFor(Key.DirectionRight, command = false, shift = true))
        assertNull(appShortcutFor(Key.Spacebar, command = false, shift = true))
    }
}

package com.leejlredstar.redefinencm.kmp

import kotlin.test.Test
import kotlin.test.assertEquals

class AppNavigationStackTest {
    @Test
    fun notificationDestinationIsAddedWithoutClearingCurrentStack() {
        val stack = mutableListOf("playlist", "details")

        stack.focusOrPush("downloads")

        assertEquals(listOf("playlist", "details", "downloads"), stack)
    }

    @Test
    fun existingNotificationDestinationIsFocusedWithoutDuplication() {
        val stack = mutableListOf("playlist", "downloads", "details")

        stack.focusOrPush("downloads")

        assertEquals(listOf("playlist", "downloads"), stack)
    }

    @Test
    fun onlyTheLocalLibraryPagesOnTopOfTheStackAreCounted() {
        assertEquals(0, localLibraryPagesOnTop(emptyList()))
        // Beneath the settings pages that switched the account off: left alone until back reaches them.
        assertEquals(
            0,
            localLibraryPagesOnTop(listOf(PushedDest.LocalLibrary, PushedDest.Settings, PushedDest.Accounts)),
        )
        assertEquals(
            2,
            localLibraryPagesOnTop(
                listOf(PushedDest.Settings, PushedDest.LocalLibrary, PushedDest.LocalPlaylist("a")),
            ),
        )
        // The first page that is not local stops the count; the local page beneath it waits its turn.
        assertEquals(
            1,
            localLibraryPagesOnTop(
                listOf(PushedDest.LocalLibrary, PushedDest.Playlist(1), PushedDest.LocalPlaylist("a")),
            ),
        )
    }
}

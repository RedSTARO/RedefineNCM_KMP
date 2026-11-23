package com.leejlredstar.redefinencm.kmp

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AppNavigationStackTest {
    @Test
    fun dismissedLaunchLoginLeavesAnExplicitEmptyStackToRestore() {
        val stack = mutableStateListOf<PushedDest>(PushedDest.Login())
        stack.clear()

        val saved = assertNotNull(with(pushedStackSaver) { SaverScope { true }.save(stack) })
        assertEquals(emptyList(), saved)
        assertEquals(emptyList(), pushedStackSaver.restore(saved)?.toList())
    }

    @Test
    fun existingNonemptyStackEncodingStillRestores() {
        val saved = arrayListOf("login:qq", "downloads")
        val stack = assertNotNull(pushedStackSaver.restore(saved))

        assertEquals(saved, with(pushedStackSaver) { SaverScope { true }.save(stack) })
    }

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

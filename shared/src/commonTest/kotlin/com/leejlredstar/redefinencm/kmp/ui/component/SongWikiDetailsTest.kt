package com.leejlredstar.redefinencm.kmp.ui.component

import com.leejlredstar.redefinencm.kmp.data.SongWikiSummary
import com.leejlredstar.redefinencm.kmp.viewmodel.SongWikiUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The details are a bottom sheet in the app's own colours now, so the dialog geometry and the
 * CSS gradient axis the web host's overlay was measured against are gone with it. What is left
 * to check is which song's state the panel is allowed to show, and when opening it fetches.
 */
class SongWikiDetailsTest {
    private val summary = SongWikiSummary(sections = emptyList())

    @Test
    fun stateBelongingToAnotherSongIsNotShown() {
        val content = SongWikiUiState.Content(mediaId = "123", summary = summary)

        assertEquals(content, content.scopedToMedia("123"))
        assertEquals(SongWikiUiState.Idle, content.scopedToMedia("456"))
        assertEquals(SongWikiUiState.Idle, content.scopedToMedia(null))
    }

    @Test
    fun everyStateIsScopedToTheSongItWasLoadedFor() {
        val states = listOf(
            SongWikiUiState.Loading(mediaId = "123"),
            SongWikiUiState.Content(mediaId = "123", summary = summary),
            SongWikiUiState.Empty(mediaId = "123"),
            SongWikiUiState.Error(mediaId = "123", message = "network"),
        )

        states.forEach { state ->
            assertEquals(state, state.scopedToMedia("123"))
            assertEquals(SongWikiUiState.Idle, state.scopedToMedia("456"))
        }
    }

    @Test
    fun openingFetchesOnlyFromIdle() {
        assertTrue(
            shouldRequestSongWikiOnOpen(
                state = SongWikiUiState.Idle,
                mediaId = "123",
            ),
        )
        // Reopening an error keeps the error on screen; its own retry is the way to try again.
        assertFalse(
            shouldRequestSongWikiOnOpen(
                state = SongWikiUiState.Error(mediaId = "123", message = "network"),
                mediaId = "123",
            ),
        )
        assertFalse(
            shouldRequestSongWikiOnOpen(
                state = SongWikiUiState.Idle,
                mediaId = null,
            ),
        )
        // A state loaded for another song is idle for this one, so this one does fetch.
        assertTrue(
            shouldRequestSongWikiOnOpen(
                state = SongWikiUiState.Content(mediaId = "456", summary = summary),
                mediaId = "123",
            ),
        )
    }
}

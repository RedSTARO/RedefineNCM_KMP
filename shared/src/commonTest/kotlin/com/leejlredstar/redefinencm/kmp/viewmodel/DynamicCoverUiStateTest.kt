package com.leejlredstar.redefinencm.kmp.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicCoverUiStateTest {
    @Test
    fun exposesUrlOnlyToItsOwningMedia() {
        val state = DynamicCoverUiState(
            mediaId = "song-a",
            url = "https://example.test/song-a.mp4",
        )

        assertEquals("https://example.test/song-a.mp4", state.urlFor("song-a"))
        assertNull(state.urlFor("song-b"))
        assertNull(state.urlFor(null))
    }

    @Test
    fun currentMediaPendingLocalArtworkBlocksDynamicCover() {
        assertFalse(
            localArtworkAllowsDynamicCover(
                mediaId = "song-b",
                state = LocalArtworkResolutionState(
                    mediaId = "song-b",
                    pending = true,
                ),
            ),
        )
    }

    @Test
    fun anotherMediasPendingResolutionDoesNotBlockCurrentMedia() {
        assertTrue(
            localArtworkAllowsDynamicCover(
                mediaId = "song-b",
                state = LocalArtworkResolutionState(
                    mediaId = "song-a",
                    pending = true,
                ),
            ),
        )
    }

    @Test
    fun currentMediaActiveLocalArtworkBlocksDynamicCover() {
        assertFalse(
            localArtworkAllowsDynamicCover(
                mediaId = "song-b",
                state = LocalArtworkResolutionState(
                    mediaId = "song-b",
                    active = true,
                ),
            ),
        )
    }

    @Test
    fun staleCompletionCannotClearNewMediasPendingGate() {
        val newMediaPending = LocalArtworkResolutionState(
            mediaId = "song-b",
            pending = true,
        )

        val completed = completeLocalArtworkResolutionState(
            state = newMediaPending,
            mediaId = "song-a",
            active = false,
        )

        assertEquals(newMediaPending, completed)
        assertTrue(completed.pending)
    }
}

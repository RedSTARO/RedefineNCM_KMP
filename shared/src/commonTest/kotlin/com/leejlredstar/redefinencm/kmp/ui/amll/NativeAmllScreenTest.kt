package com.leejlredstar.redefinencm.kmp.ui.amll

import com.leejlredstar.redefinencm.kmp.viewmodel.SongWikiUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeAmllScreenTest {
    @Test
    fun wikiPausesBackgroundOnlyAfterDetailVideoBecomesVisible() {
        assertTrue(
            shouldPlayAmllDynamicBackground(
                songWikiVisible = true,
                reducedMotion = false,
                wikiDynamicCoverVisible = false,
            ),
        )
        assertFalse(
            shouldPlayAmllDynamicBackground(
                songWikiVisible = true,
                reducedMotion = false,
                wikiDynamicCoverVisible = true,
            ),
        )
    }

    @Test
    fun reducedMotionPausesBackgroundAsSoonAsWikiOpens() {
        assertFalse(
            shouldPlayAmllDynamicBackground(
                songWikiVisible = true,
                reducedMotion = true,
                wikiDynamicCoverVisible = false,
            ),
        )
        assertTrue(
            shouldPlayAmllDynamicBackground(
                songWikiVisible = false,
                reducedMotion = true,
                wikiDynamicCoverVisible = false,
            ),
        )
    }

    @Test
    fun desktopPreservesItsThirtySecondExpandedControllerTimeout() {
        assertEquals(30_000L, amllControllerAutoHideDelayMillis(isDesktop = true))
        assertEquals(3_600L, amllControllerAutoHideDelayMillis(isDesktop = false))
    }

    @Test
    fun reopeningWikiRequestsOnlyTheDesktopHostsIdleState() {
        assertTrue(
            shouldRequestSongWikiOnOpen(
                state = SongWikiUiState.Idle,
                mediaId = "123",
            ),
        )
        assertFalse(
            shouldRequestSongWikiOnOpen(
                state = SongWikiUiState.Error(
                    mediaId = "123",
                    message = "network",
                ),
                mediaId = "123",
            ),
        )
        assertFalse(
            shouldRequestSongWikiOnOpen(
                state = SongWikiUiState.Idle,
                mediaId = null,
            ),
        )
    }

    @Test
    fun localArtworkFailureFallsBackOnceToTheRemoteArtwork() {
        assertEquals(
            "https://music.example/cover.jpg",
            nextAmllArtworkUriAfterFailure(
                failedUri = "file:///downloads/42.cover.heic",
                primaryUri = "file:///downloads/42.cover.heic",
                fallbackUri = "https://music.example/cover.jpg",
            ),
        )
        assertEquals(
            null,
            nextAmllArtworkUriAfterFailure(
                failedUri = "https://music.example/cover.jpg",
                primaryUri = "file:///downloads/42.cover.heic",
                fallbackUri = "https://music.example/cover.jpg",
            ),
        )
    }
}

package com.leejlredstar.redefinencm.kmp.ui.component

import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlayerQueueSnapshot
import com.leejlredstar.redefinencm.kmp.viewmodel.FavoriteUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The derivations the desktop strip and the mini player used to each compute for themselves.
 *
 * Their progress maths had already drifted apart — one divided the clamped position, the other
 * the raw one — so the interesting cases here are the ones where a player reports something the
 * UI has to defend against.
 */
class NowPlayingUiStateTest {

    private fun state(
        media: MediaInfo? = MediaInfo(id = "1", title = "t", artist = "a", duration = 200_000L),
        position: Long = 0L,
        duration: Long = -1L,
        queueSnapshot: PlayerQueueSnapshot = PlayerQueueSnapshot(),
        favoriteState: FavoriteUiState = FavoriteUiState(),
    ) = NowPlayingUiState(
        media = media,
        isPlaying = false,
        position = position,
        duration = duration,
        queueSnapshot = queueSnapshot,
        comments = null,
        commentsLoading = false,
        commentsLoadError = null,
        commentsFromCache = false,
        favoriteState = favoriteState,
    )

    @Test
    fun fallsBackToQueueMetadataWhenThePlayerHasNoDurationYet() {
        // -1L is what the contract says an unknown duration looks like.
        assertEquals(200_000L, state(duration = -1L).totalDuration)
        assertEquals(180_000L, state(duration = 180_000L).totalDuration)
    }

    @Test
    fun reportsNoDurationWhenNeitherSourceKnowsOne() {
        val noDuration = MediaInfo(id = "1", title = "t", artist = "a", duration = 0L)
        assertEquals(0L, state(media = noDuration, duration = -1L).totalDuration)
        assertEquals(0L, state(media = null, duration = -1L).totalDuration)
    }

    @Test
    fun clampsANegativePositionBeforeUsingIt() {
        // Some backends report a negative position between selecting and opening a track.
        val s = state(position = -5_000L, duration = 100_000L)
        assertEquals(0L, s.safePosition)
        assertEquals(0f, s.progress)
    }

    @Test
    fun progressNeverLeavesTheZeroToOneRange() {
        assertEquals(0.5f, state(position = 50_000L, duration = 100_000L).progress)
        // A position past the reported duration happens at the end-of-track boundary.
        assertEquals(1f, state(position = 500_000L, duration = 100_000L).progress)
    }

    @Test
    fun progressIsZeroRatherThanNaNWithNoDuration() {
        val noDuration = MediaInfo(id = "1", title = "t", artist = "a", duration = 0L)
        assertEquals(0f, state(media = noDuration, position = 1_000L, duration = 0L).progress)
    }

    @Test
    fun favouriteRequiresTheFlagToBelongToTheCurrentSong() {
        val liked = FavoriteUiState(mediaId = "1", isLiked = true)
        val likedOther = FavoriteUiState(mediaId = "2", isLiked = true)
        assertTrue(state(favoriteState = liked).isFavorite)
        // A stale flag from the previously played song must not colour the current one.
        assertFalse(state(favoriteState = likedOther).isFavorite)
        assertFalse(state(media = null, favoriteState = liked).isFavorite)
    }

    @Test
    fun queueViewIsReadStraightFromOneSnapshot() {
        val items = listOf(
            MediaInfo(id = "1", title = "a", artist = ""),
            MediaInfo(id = "2", title = "b", artist = ""),
        )
        val snapshot = PlayerQueueSnapshot(
            items = items,
            currentIndex = 1,
            currentMedia = items[1],
            shuffleEnabled = true,
        )
        val s = state(queueSnapshot = snapshot)
        assertEquals(items, s.playList)
        assertEquals(1, s.currentIndex)
        assertTrue(s.shuffleEnabled)
    }

    @Test
    fun hasMediaTracksTheCurrentSelection() {
        assertTrue(state().hasMedia)
        assertFalse(state(media = null).hasMedia)
    }
}

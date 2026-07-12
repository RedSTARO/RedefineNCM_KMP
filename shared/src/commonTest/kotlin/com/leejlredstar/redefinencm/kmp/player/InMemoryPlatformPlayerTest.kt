package com.leejlredstar.redefinencm.kmp.player

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryPlatformPlayerTest {
    private val tracks = List(5) { index ->
        MediaInfo(id = index.toString(), title = "track-$index", artist = "artist")
    }

    @Test
    fun queueSnapshotKeepsVisibleIndexAndMediaTogether() {
        val player = InMemoryPlatformPlayer()
        try {
            assertEquals(-1, player.queueSnapshot.value.currentIndex)
            assertEquals(-1, player.currentIndex.value)
            player.setQueue(tracks, startIndex = 2)
            player.setShuffleEnabled(true)

            val snapshot = player.queueSnapshot.value
            assertTrue(snapshot.shuffleEnabled)
            assertEquals(tracks.size, snapshot.items.size)
            assertEquals(snapshot.currentMedia, snapshot.items[snapshot.currentIndex])
            assertEquals("2", snapshot.currentMedia?.id)
        } finally {
            player.release()
        }
    }

    @Test
    fun concurrentQueueCommandsPreserveSnapshotInvariant() = runTest {
        val player = InMemoryPlatformPlayer()
        try {
            player.setQueue(tracks, startIndex = 2)
            coroutineScope {
                launch {
                    repeat(200) { iteration ->
                        player.setShuffleEnabled(iteration % 2 == 0)
                        yield()
                    }
                }
                launch {
                    repeat(200) {
                        player.seekToNext()
                        player.seekToPrevious()
                        yield()
                    }
                }
                launch {
                    repeat(200) { iteration ->
                        player.skipToIndex(iteration % tracks.size)
                        yield()
                    }
                }
            }

            val snapshot = player.queueSnapshot.value
            assertTrue(snapshot.currentIndex in snapshot.items.indices)
            assertSame(snapshot.items[snapshot.currentIndex], snapshot.currentMedia)
        } finally {
            player.release()
        }
    }

    @Test
    fun skipIndexUsesVisibleShuffleOrder() {
        val player = InMemoryPlatformPlayer()
        try {
            player.setQueue(tracks, startIndex = 2)
            player.setShuffleEnabled(true)
            val target = player.queueSnapshot.value.items[1]

            player.skipToIndex(1)

            val snapshot = player.queueSnapshot.value
            assertEquals(1, snapshot.currentIndex)
            assertSame(target, snapshot.currentMedia)
            assertSame(target, snapshot.items[snapshot.currentIndex])
        } finally {
            player.release()
        }
    }

    @Test
    fun manualNavigationDoesNotWrapAtQueueBoundaries() {
        val player = InMemoryPlatformPlayer()
        try {
            player.setQueue(tracks.take(2), startIndex = 1)
            player.seekToNext()
            assertEquals("1", player.queueSnapshot.value.currentMedia?.id)
            assertEquals(1, player.queueSnapshot.value.currentIndex)

            player.seekToPrevious()
            assertEquals("0", player.queueSnapshot.value.currentMedia?.id)
            player.seekToPrevious()
            assertEquals("0", player.queueSnapshot.value.currentMedia?.id)
            assertEquals(0, player.queueSnapshot.value.currentIndex)
        } finally {
            player.release()
        }
    }

    @Test
    fun playbackOccurrenceTracksSelectionsWithoutCollapsingEqualSongs() {
        val duplicate = MediaInfo(id = "same", title = "Same", artist = "Artist")
        val player = InMemoryPlatformPlayer()
        try {
            assertEquals(0L, player.playbackOccurrence.value)

            player.setQueue(listOf(duplicate, duplicate), startIndex = 0)
            assertEquals(1L, player.playbackOccurrence.value)

            player.play()
            player.pause()
            player.seekTo(500L)
            player.setShuffleEnabled(false)
            player.addToQueue(duplicate)
            assertEquals(1L, player.playbackOccurrence.value)

            player.seekToNext()
            assertEquals(1, player.currentIndex.value)
            assertEquals(2L, player.playbackOccurrence.value)

            player.seekToPrevious()
            assertEquals(0, player.currentIndex.value)
            assertEquals(3L, player.playbackOccurrence.value)

            player.skipToIndex(1)
            assertEquals(4L, player.playbackOccurrence.value)
            player.skipToIndex(1)
            assertEquals(4L, player.playbackOccurrence.value)

            player.restoreQueue(listOf(duplicate), startIndex = 0, positionMs = 200L)
            assertEquals(5L, player.playbackOccurrence.value)
        } finally {
            player.release()
        }
    }

    @Test
    fun naturalTransitionAdvancesPlaybackOccurrence() = runTest {
        val duplicate = MediaInfo(
            id = "same",
            title = "Same",
            artist = "Artist",
            duration = 1L,
        )
        val player = InMemoryPlatformPlayer(scope = backgroundScope, tickerIntervalMs = 1L)
        try {
            player.setQueue(listOf(duplicate, duplicate))
            player.play()

            advanceTimeBy(1L)
            runCurrent()

            assertEquals(1, player.currentIndex.value)
            assertEquals(2L, player.playbackOccurrence.value)
        } finally {
            player.release()
        }
    }

    @Test
    fun singleTrackNaturalCompletionReachesEnded() = runTest {
        val player = InMemoryPlatformPlayer(scope = backgroundScope, tickerIntervalMs = 1L)
        try {
            player.setQueue(
                listOf(MediaInfo(id = "single", title = "Single", artist = "Artist", duration = 1L))
            )
            player.play()
            advanceTimeBy(1L)
            runCurrent()

            assertEquals(PlayerState.ENDED, player.state.value)
            assertFalse(player.isPlaying.value)
            assertEquals(1L, player.position.value)
            assertEquals("single", player.currentMedia.value?.id)
            assertEquals(1L, player.playbackOccurrence.value)

            player.play()

            assertEquals(PlayerState.PLAYING, player.state.value)
            assertEquals(0L, player.position.value)
            assertEquals(2L, player.playbackOccurrence.value)
        } finally {
            player.release()
        }
    }

    @Test
    fun endedPausePreservesReplayOccurrenceBoundary() = runTest {
        val player = InMemoryPlatformPlayer(scope = backgroundScope, tickerIntervalMs = 1L)
        try {
            player.setQueue(
                listOf(MediaInfo(id = "single", title = "Single", artist = "Artist", duration = 1L)),
            )
            player.play()
            advanceTimeBy(1L)
            runCurrent()

            player.pause()
            assertEquals(PlayerState.ENDED, player.state.value)
            player.play()

            assertEquals(2L, player.playbackOccurrence.value)
        } finally {
            player.release()
        }
    }

    @Test
    fun seekBackFromEndedRemainsTheSameSelectionOccurrence() = runTest {
        val player = InMemoryPlatformPlayer(scope = backgroundScope, tickerIntervalMs = 1L)
        try {
            player.setQueue(
                listOf(MediaInfo(id = "single", title = "Single", artist = "Artist", duration = 1L)),
            )
            player.play()
            advanceTimeBy(1L)
            runCurrent()

            player.seekTo(0L)
            assertEquals(PlayerState.PAUSED, player.state.value)
            player.play()

            assertEquals(1L, player.playbackOccurrence.value)
        } finally {
            player.release()
        }
    }

    @Test
    fun replacingQueueWithEmptyResetsPositionWithoutCreatingOccurrence() {
        val player = InMemoryPlatformPlayer()
        try {
            player.setQueue(tracks.take(1), startIndex = 0)
            player.seekTo(500L)
            val occurrence = player.playbackOccurrence.value

            player.setQueue(emptyList(), startIndex = 0)

            assertEquals(PlayerState.IDLE, player.state.value)
            assertEquals(null, player.currentMedia.value)
            assertEquals(0L, player.position.value)
            assertEquals(occurrence, player.playbackOccurrence.value)
        } finally {
            player.release()
        }
    }
}

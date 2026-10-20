package com.leejlredstar.redefinencm.kmp.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricPayloadTest {

    private fun payload(
        title: String? = "Title",
        artist: String? = "Artist",
        currentLyric: String? = "Line",
        nextLyric: String? = "Next",
        artworkUri: String? = "https://example.invalid/a.jpg",
        isPlaying: Boolean = true,
        positionMs: Long = 1_000L,
        durationMs: Long = 200_000L,
    ) = lyricPayloadOf(
        title = title,
        artist = artist,
        currentLyric = currentLyric,
        nextLyric = nextLyric,
        artworkUri = artworkUri,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
    )

    @Test
    fun nullsBecomeEmptyStrings() {
        val result = payload(title = null, artist = null, currentLyric = null, nextLyric = null, artworkUri = null)
        assertEquals("", result.title)
        assertEquals("", result.artist)
        assertEquals("", result.currentLyric)
        assertEquals("", result.nextLyric)
        assertEquals("", result.artworkUri)
    }

    @Test
    fun everyTextFieldIsTrimmed() {
        val result = payload(
            title = "  Title  ",
            artist = "\tArtist\n",
            currentLyric = "  Line  ",
            nextLyric = " Next ",
            artworkUri = " uri ",
        )
        assertEquals("Title", result.title)
        assertEquals("Artist", result.artist)
        assertEquals("Line", result.currentLyric)
        assertEquals("Next", result.nextLyric)
        assertEquals("uri", result.artworkUri)
    }

    @Test
    fun aNegativePositionClampsToZero() {
        assertEquals(0L, payload(positionMs = -1_500L).positionMs)
    }

    @Test
    fun anUnknownDurationIsLeftAlone() {
        // -1 is how the contract says "unknown"; clamping it would claim a zero-length track.
        assertEquals(-1L, payload(durationMs = -1L).durationMs)
    }

    @Test
    fun theHeadlineFallsBackToTheTitleWhenThereIsNoLyric() {
        assertEquals("Line", payload().headline)
        assertEquals("Title", payload(currentLyric = "   ").headline)
        assertEquals("Title", payload(currentLyric = null).headline)
    }

    @Test
    fun aSingleLineSurfaceShowsTheHeadlineAsItsLyric() {
        val single = payload(currentLyric = null).asSingleLineSurface()
        assertEquals("Title", single?.currentLyric)
    }

    @Test
    fun twoUpdatesResolvingToTheSameLineCompareEqual() {
        // The surfaces dedupe on the payload, so a blank lyric under a title must not look like
        // a change from that same title spelled as the lyric.
        assertEquals(
            payload(currentLyric = null).asSingleLineSurface(),
            payload(currentLyric = "Title").asSingleLineSurface(),
        )
    }

    @Test
    fun aSingleLineSurfaceHasNothingToShowWithoutALyricOrATitle() {
        assertNull(payload(title = null, currentLyric = null).asSingleLineSurface())
        assertNull(payload(title = "  ", currentLyric = "  ").asSingleLineSurface())
    }

    @Test
    fun aPositionMovingAsTheBarDoesIsNotSentAgain() {
        val last = payload(positionMs = 10_000L)
        assertTrue(isRedundantTimedUpdate(last, payload(positionMs = 15_000L), elapsedMs = 5_000L))
        // Whole-second rounding and timer jitter stay inside the tolerance.
        assertTrue(isRedundantTimedUpdate(last, payload(positionMs = 16_000L), elapsedMs = 5_400L))
    }

    @Test
    fun aSeekANewLineAPauseOrAnotherTrackIsSent() {
        val last = payload(positionMs = 10_000L)
        assertFalse(isRedundantTimedUpdate(last, payload(positionMs = 60_000L), elapsedMs = 5_000L))
        assertFalse(isRedundantTimedUpdate(last, payload(positionMs = 1_000L), elapsedMs = 5_000L))
        assertFalse(isRedundantTimedUpdate(last, payload(positionMs = 15_000L, currentLyric = "Other"), elapsedMs = 5_000L))
        assertFalse(isRedundantTimedUpdate(last, payload(positionMs = 15_000L, isPlaying = false), elapsedMs = 5_000L))
        assertFalse(isRedundantTimedUpdate(last, payload(positionMs = 15_000L, title = "Next song"), elapsedMs = 5_000L))
        assertFalse(isRedundantTimedUpdate(last, payload(positionMs = 15_000L, durationMs = 180_000L), elapsedMs = 5_000L))
    }

    @Test
    fun playbackSendsAKeepAliveEveryMinute() {
        val last = payload(positionMs = 0L)
        assertTrue(isRedundantTimedUpdate(last, payload(positionMs = 59_000L), elapsedMs = 59_000L))
        assertFalse(isRedundantTimedUpdate(last, payload(positionMs = 60_000L), elapsedMs = TIMED_SURFACE_KEEP_ALIVE_MS))
    }

    @Test
    fun aPausedSurfaceIsSentOnlyWhenThePositionChanges() {
        val last = payload(positionMs = 30_000L, isPlaying = false)
        assertTrue(isRedundantTimedUpdate(last, payload(positionMs = 30_000L, isPlaying = false), elapsedMs = 600_000L))
        assertFalse(isRedundantTimedUpdate(last, payload(positionMs = 90_000L, isPlaying = false), elapsedMs = 1_000L))
    }
}

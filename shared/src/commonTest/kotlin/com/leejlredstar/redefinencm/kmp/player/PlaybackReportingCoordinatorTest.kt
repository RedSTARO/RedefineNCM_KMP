package com.leejlredstar.redefinencm.kmp.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackReportingCoordinatorTest {
    private val media = MediaInfo(
        id = "518066366",
        title = "Song",
        artist = "Artist",
        duration = 200_000L,
        sourceId = "36780169",
    )

    @Test
    fun scrobblesOnceAfterHalfOfActualListeningAndIgnoresSeekDistance() {
        val reducer = reducer()

        reducer.observe(observation(positionMs = 0L), nowMillis = 0L)
        reducer.observe(observation(positionMs = 50_000L), nowMillis = 50_000L)
        val afterSeek = reducer.observe(
            observation(positionMs = 150_000L),
            nowMillis = 50_000L,
        )
        val atHalf = reducer.observe(
            observation(positionMs = 200_000L),
            nowMillis = 100_000L,
        )
        val later = reducer.observe(
            observation(positionMs = 200_000L),
            nowMillis = 150_000L,
        )

        assertTrue(afterSeek.none { it is PlaybackReportingAction.Scrobble })
        val scrobble = atHalf.filterIsInstance<PlaybackReportingAction.Scrobble>().single()
        assertEquals(100L, scrobble.playedSeconds)
        assertEquals(200L, scrobble.totalSeconds)
        assertEquals("36780169", scrobble.sourceId)
        assertEquals("list", scrobble.source)
        assertTrue(later.none { it is PlaybackReportingAction.Scrobble })
    }

    @Test
    fun bufferingTimeAndStalledPositionDoNotCountAsListening() {
        val reducer = reducer()
        val shortMedia = media.copy(duration = 100_000L)

        reducer.observe(observation(media = shortMedia), nowMillis = 0L)
        reducer.observe(observation(media = shortMedia, positionMs = 20_000L), nowMillis = 20_000L)
        reducer.observe(
            observation(
                media = shortMedia,
                positionMs = 20_000L,
                isPlaying = false,
                state = PlayerState.BUFFERING,
            ),
            nowMillis = 30_000L,
        )
        reducer.observe(
            observation(
                media = shortMedia,
                positionMs = 20_000L,
                isPlaying = false,
                state = PlayerState.BUFFERING,
            ),
            nowMillis = 3_600_000L,
        )
        reducer.observe(
            observation(media = shortMedia, positionMs = 20_000L),
            nowMillis = 3_601_000L,
        )
        val beforeHalf = reducer.observe(
            observation(media = shortMedia, positionMs = 49_000L),
            nowMillis = 3_630_000L,
        )
        val atHalf = reducer.observe(
            observation(media = shortMedia, positionMs = 50_000L),
            nowMillis = 3_631_000L,
        )

        assertTrue(beforeHalf.none { it is PlaybackReportingAction.Scrobble })
        assertEquals(50L, atHalf.filterIsInstance<PlaybackReportingAction.Scrobble>().single().playedSeconds)
    }

    @Test
    fun unknownDurationUsesThirtySecondFallback() {
        val reducer = reducer()
        val unknownDuration = media.copy(duration = 0L)

        reducer.observe(observation(media = unknownDuration), nowMillis = 0L)
        val before = reducer.observe(
            observation(media = unknownDuration, positionMs = 29_999L),
            nowMillis = 29_999L,
        )
        val reached = reducer.observe(
            observation(media = unknownDuration, positionMs = 30_000L),
            nowMillis = 30_000L,
        )

        assertTrue(before.none { it is PlaybackReportingAction.Scrobble })
        val scrobble = reached.filterIsInstance<PlaybackReportingAction.Scrobble>().single()
        assertEquals(30L, scrobble.playedSeconds)
        assertEquals(null, scrobble.totalSeconds)
    }

    @Test
    fun relayKeepsOneSessionAndOnlySendsChangedPayloads() {
        val reducer = reducer()

        val started = reducer.observe(observation(), nowMillis = 0L)
        val tenSeconds = reducer.observe(
            observation(positionMs = 10_000L),
            nowMillis = 10_000L,
        )
        val paused = reducer.observe(
            observation(positionMs = 10_000L, isPlaying = false, state = PlayerState.PAUSED),
            nowMillis = 10_000L,
        )
        val resumed = reducer.observe(
            observation(positionMs = 10_000L),
            nowMillis = 11_000L,
        )
        val checkpoint = reducer.observe(
            observation(positionMs = 40_000L),
            nowMillis = 40_000L,
        )
        val shuffled = reducer.observe(
            observation(positionMs = 40_000L, shuffleEnabled = true),
            nowMillis = 40_001L,
        )

        val initialRelay = started.filterIsInstance<PlaybackReportingAction.SubmitPlayState>().single()
        assertTrue(tenSeconds.none { it is PlaybackReportingAction.SubmitPlayState })
        val pauseRelay = paused.filterIsInstance<PlaybackReportingAction.SubmitPlayState>().single()
        assertTrue(resumed.none { it is PlaybackReportingAction.SubmitPlayState })
        val checkpointRelay = checkpoint.filterIsInstance<PlaybackReportingAction.SubmitPlayState>().single()
        val shuffleRelay = shuffled.filterIsInstance<PlaybackReportingAction.SubmitPlayState>().single()
        assertEquals(
            setOf("SESSION00001"),
            listOf(initialRelay, pauseRelay, checkpointRelay, shuffleRelay).map { it.sessionId }.toSet(),
        )
        assertEquals(listOf(0L, 10L, 40L, 40L), listOf(
            initialRelay.progressSeconds,
            pauseRelay.progressSeconds,
            checkpointRelay.progressSeconds,
            shuffleRelay.progressSeconds,
        ))
        assertEquals("random", shuffleRelay.playMode)
    }

    @Test
    fun newOccurrenceCreatesANewSessionEvenForTheSameSong() {
        var nextSession = 1
        val reducer = PlaybackReportingReducer(
            sessionIdGenerator = { "SESSION${nextSession++.toString().padStart(5, '0')}" },
        )

        val first = reducer.observe(observation(occurrence = 1L), nowMillis = 0L)
        val boundary = reducer.observe(
            observation(occurrence = 2L, positionMs = 0L),
            nowMillis = 5_000L,
            signal = PlaybackReportingSignal.OCCURRENCE,
        )
        val stale = reducer.observe(
            observation(occurrence = 2L, positionMs = 0L),
            nowMillis = 5_001L,
            signal = PlaybackReportingSignal.POSITION,
        )
        val second = reducer.observe(
            observation(occurrence = 2L, positionMs = 0L),
            nowMillis = 5_250L,
            signal = PlaybackReportingSignal.STATE,
        )

        val firstSession = first.filterIsInstance<PlaybackReportingAction.SubmitPlayState>().single()
        val secondRelays = second.filterIsInstance<PlaybackReportingAction.SubmitPlayState>()
        assertEquals("SESSION00001", firstSession.sessionId)
        assertEquals(1L, firstSession.reportingGeneration)
        // The old session's final payload is unchanged (progress 0), so it is deduplicated.
        assertTrue(boundary.isEmpty())
        assertTrue(stale.isEmpty())
        assertEquals(listOf("SESSION00002"), secondRelays.map { it.sessionId })
        assertEquals(listOf(2L), secondRelays.map { it.reportingGeneration })
    }

    @Test
    fun mediaSnapshotDoesNotSplitSessionBeforeOccurrenceAdvances() {
        var nextSession = 1
        val reducer = PlaybackReportingReducer(
            sessionIdGenerator = { "SESSION${nextSession++.toString().padStart(5, '0')}" },
        )
        val nextMedia = media.copy(id = "518066367", title = "Next song")

        reducer.observe(observation(positionMs = 50_000L), nowMillis = 0L)
        val intermediate = reducer.observe(
            observation(media = nextMedia, positionMs = 0L),
            nowMillis = 1_000L,
            signal = PlaybackReportingSignal.MEDIA,
        )
        val boundary = reducer.observe(
            observation(occurrence = 2L, media = nextMedia),
            nowMillis = 1_000L,
            signal = PlaybackReportingSignal.OCCURRENCE,
        )
        val stale = reducer.observe(
            observation(occurrence = 2L, media = nextMedia),
            nowMillis = 1_001L,
            signal = PlaybackReportingSignal.POSITION,
        )
        val selected = reducer.observe(
            observation(occurrence = 2L, media = nextMedia),
            nowMillis = 1_250L,
            signal = PlaybackReportingSignal.IS_PLAYING,
        )

        assertTrue(intermediate.isEmpty())
        assertTrue(boundary.isEmpty())
        assertTrue(stale.isEmpty())
        assertEquals(
            listOf("SESSION00002"),
            selected.filterIsInstance<PlaybackReportingAction.SubmitPlayState>()
                .map { it.sessionId },
        )
    }

    @Test
    fun occurrenceBoundarySubmitsOldFinalBeforeNewInitial() {
        var nextSession = 1
        val reducer = PlaybackReportingReducer(
            sessionIdGenerator = { "SESSION${nextSession++.toString().padStart(5, '0')}" },
        )
        val nextMedia = media.copy(id = "518066367", title = "Next song")

        reducer.observe(observation(), nowMillis = 0L)
        reducer.observe(
            observation(positionMs = 10_000L),
            nowMillis = 10_000L,
            signal = PlaybackReportingSignal.POSITION,
        )
        val boundary = reducer.observe(
            observation(occurrence = 2L, media = nextMedia),
            nowMillis = 10_000L,
            signal = PlaybackReportingSignal.OCCURRENCE,
        )
        val selected = reducer.observe(
            observation(occurrence = 2L, media = nextMedia),
            nowMillis = 10_250L,
            signal = PlaybackReportingSignal.STATE,
        )

        val oldFinal = boundary.filterIsInstance<PlaybackReportingAction.SubmitPlayState>().single()
        val newInitial = selected.filterIsInstance<PlaybackReportingAction.SubmitPlayState>().single()
        assertEquals("SESSION00001", oldFinal.sessionId)
        assertEquals(10L, oldFinal.progressSeconds)
        assertEquals("SESSION00002", newInitial.sessionId)
        assertEquals(0L, newInitial.progressSeconds)
    }

    @Test
    fun accountChangeDropsTheOldSessionInsteadOfMisattributingIt() {
        var nextSession = 1
        val reducer = PlaybackReportingReducer(
            sessionIdGenerator = { "SESSION${nextSession++.toString().padStart(5, '0')}" },
        )

        reducer.observe(observation(credentialKey = 1L), nowMillis = 0L)
        val changed = reducer.observe(
            observation(credentialKey = 2L, positionMs = 120_000L),
            nowMillis = 120_000L,
        )

        assertTrue(changed.none { it.credentialKey == 1L })
        val newRelay = changed.filterIsInstance<PlaybackReportingAction.SubmitPlayState>().single()
        assertEquals(2L, newRelay.credentialKey)
        assertEquals("SESSION00002", newRelay.sessionId)
    }

    @Test
    fun terminalStateSubmitsTheLastChangedProgress() {
        val reducer = reducer()

        val started = reducer.observe(observation(), nowMillis = 0L)
        reducer.observe(observation(positionMs = 10_000L), nowMillis = 10_000L)
        val ended = reducer.observe(
            observation(positionMs = 10_000L, isPlaying = false, state = PlayerState.ENDED),
            nowMillis = 10_000L,
        )

        val initial = started.filterIsInstance<PlaybackReportingAction.SubmitPlayState>().single()
        val final = ended.filterIsInstance<PlaybackReportingAction.SubmitPlayState>().single()
        assertEquals(initial.sessionId, final.sessionId)
        assertEquals(10L, final.progressSeconds)
    }

    @Test
    fun clearingMediaSubmitsLastKnownProgress() {
        val reducer = reducer()

        reducer.observe(observation(), nowMillis = 0L)
        reducer.observe(observation(positionMs = 10_000L), nowMillis = 10_000L)
        val cleared = reducer.observe(
            observation(media = null, isPlaying = false, state = PlayerState.IDLE),
            nowMillis = 10_000L,
            signal = PlaybackReportingSignal.STATE,
        )
        val repeated = reducer.observe(
            observation(media = null, isPlaying = false, state = PlayerState.IDLE),
            nowMillis = 10_001L,
            signal = PlaybackReportingSignal.MEDIA,
        )

        assertEquals(
            10L,
            cleared.filterIsInstance<PlaybackReportingAction.SubmitPlayState>()
                .single()
                .progressSeconds,
        )
        assertTrue(repeated.isEmpty())
    }

    @Test
    fun missingCredentialAndInvalidSongIdsDoNotCreateSessions() {
        var generatedSessions = 0
        val reducer = PlaybackReportingReducer(
            sessionIdGenerator = {
                generatedSessions += 1
                "SESSION00001"
            },
        )

        assertTrue(reducer.observe(observation(credentialKey = null), nowMillis = 0L).isEmpty())
        listOf("not-a-number", "0", "-1").forEachIndexed { index, id ->
            assertTrue(
                reducer.observe(
                    observation(media = media.copy(id = id), occurrence = index + 2L),
                    nowMillis = index + 1L,
                ).isEmpty(),
            )
        }
        assertEquals(0, generatedSessions)
    }

    @Test
    fun invalidGeneratedSessionIdFailsFast() {
        val reducer = PlaybackReportingReducer(sessionIdGenerator = { "lowercase-id" })

        assertFailsWith<IllegalArgumentException> {
            reducer.observe(observation(), nowMillis = 0L)
        }
    }

    @Test
    fun generatedSessionIdMatchesBackendContract() {
        val id = generatePlaybackSessionId(Random(42))

        assertEquals(12, id.length)
        assertTrue(id.all { it.isDigit() || it in 'A'..'Z' })
    }

    private fun reducer(): PlaybackReportingReducer =
        PlaybackReportingReducer(sessionIdGenerator = { "SESSION00001" })

    private fun observation(
        occurrence: Long = 1L,
        media: MediaInfo? = this.media,
        positionMs: Long = 0L,
        isPlaying: Boolean = true,
        state: PlayerState = PlayerState.PLAYING,
        shuffleEnabled: Boolean = false,
        credentialKey: Long? = 123L,
    ): PlaybackReportingObservation = PlaybackReportingObservation(
        occurrence = occurrence,
        media = media,
        state = state,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = media?.duration ?: -1L,
        shuffleEnabled = shuffleEnabled,
        qualityLevel = "EXHIGH",
        credentialKey = credentialKey,
        credentialCookie = credentialKey?.let { "MUSIC_U=test-$it" },
    )

    @Test
    fun stalledRelayDoesNotBlockIndependentScrobbleDispatch() = runTest {
        val relayStarted = CompletableDeferred<Unit>()
        val releaseRelay = CompletableDeferred<Unit>()
        val scrobbleReceived = CompletableDeferred<Unit>()
        val dispatcher = PlaybackReportingDispatcher(
            scope = backgroundScope,
            report = { action ->
                when (action) {
                    is PlaybackReportingAction.SubmitPlayState -> {
                        relayStarted.complete(Unit)
                        releaseRelay.await()
                    }
                    is PlaybackReportingAction.Scrobble -> scrobbleReceived.complete(Unit)
                }
            },
        )
        val credentialCookie = "MUSIC_U=test"

        dispatcher.enqueue(
            PlaybackReportingAction.SubmitPlayState(
                credentialKey = 1L,
                credentialCookie = credentialCookie,
                songId = 42L,
                sessionId = "AB12CD34EF56",
                progressSeconds = 0L,
                playMode = "list_loop",
            ),
        )
        runCurrent()
        assertTrue(relayStarted.isCompleted)

        dispatcher.enqueue(
            PlaybackReportingAction.Scrobble(
                credentialKey = 1L,
                credentialCookie = credentialCookie,
                songId = 42L,
                playedSeconds = 30L,
                sourceId = null,
                source = "list",
                name = "Song",
                artist = "Artist",
                level = "exhigh",
                totalSeconds = 60L,
            ),
        )
        runCurrent()

        assertTrue(scrobbleReceived.isCompleted)
        releaseRelay.complete(Unit)
        dispatcher.close()
    }
}

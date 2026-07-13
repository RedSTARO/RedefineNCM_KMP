package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.PlaybackAccountComparison
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackReportingStateTest {
    @Test
    fun olderGenerationCannotOverwriteNewerSameSongResult() {
        val newer = status(
            credentialKey = 1L,
            generation = 2L,
            phase = PlaybackReportingPhase.VERIFYING,
        )
        val older = status(
            credentialKey = 1L,
            generation = 1L,
            phase = PlaybackReportingPhase.ACCOUNT_VERIFIED,
        )

        val state = PlaybackReportingState()
            .withStatus(newer)
            .withStatus(older)

        assertEquals(newer, state.scrobble)
    }

    @Test
    fun currentGenerationCanAdvanceThroughReportingPhases() {
        val sending = status(
            credentialKey = 1L,
            generation = 2L,
            phase = PlaybackReportingPhase.SENDING,
        )
        val verified = sending.copy(phase = PlaybackReportingPhase.ACCOUNT_VERIFIED)

        val state = PlaybackReportingState()
            .withStatus(sending)
            .withStatus(verified)

        assertEquals(verified, state.scrobble)
    }

    @Test
    fun credentialFilterCannotMixReportingKindsAcrossAccounts() {
        val state = PlaybackReportingState(
            relay = status(
                kind = PlaybackReportingKind.RELAY,
                credentialKey = 1L,
                generation = 1L,
                phase = PlaybackReportingPhase.UNSUPPORTED,
            ),
            scrobble = status(
                credentialKey = 2L,
                generation = 2L,
                phase = PlaybackReportingPhase.ACCOUNT_VERIFIED,
            ),
            recentPlay = status(
                kind = PlaybackReportingKind.RECENT_PLAY,
                credentialKey = 1L,
                generation = 3L,
                phase = PlaybackReportingPhase.ACCEPTED,
            ),
        )

        val accountOne = state.forCredential(1L)
        val accountTwo = state.forCredential(2L)

        assertEquals(1L, accountOne.relay?.credentialKey)
        assertEquals(1L, accountOne.recentPlay?.credentialKey)
        assertNull(accountOne.scrobble)
        assertNull(accountTwo.relay)
        assertNull(accountTwo.recentPlay)
        assertEquals(2L, accountTwo.scrobble?.credentialKey)
        assertEquals(PlaybackReportingState(), state.forCredential(null))
    }

    @Test
    fun verificationQueueBackpressuresInsteadOfDroppingTheNewerBufferedEvent() = runTest {
        val queue = PlaybackVerificationEventQueue(capacity = 1)
        val newer = verificationEvent(generation = 2L)
        val older = verificationEvent(generation = 1L)

        queue.emit(newer)
        val secondSend = launch { queue.emit(older) }
        runCurrent()
        val received = async { queue.events.take(2).toList() }

        assertEquals(listOf(newer, older), received.await())
        secondSend.join()
    }

    private fun status(
        kind: PlaybackReportingKind = PlaybackReportingKind.SCROBBLE,
        credentialKey: Long,
        generation: Long,
        phase: PlaybackReportingPhase,
    ): PlaybackReportingStatus = PlaybackReportingStatus(
        kind = kind,
        songId = 42L,
        credentialKey = credentialKey,
        reportingGeneration = generation,
        phase = phase,
    )

    private fun verificationEvent(generation: Long) = PlaybackAccountVerificationEvent(
        uid = 7L,
        credentialKey = 11L,
        reportingGeneration = generation,
        songId = 42L,
        userLevel = null,
        comparison = PlaybackAccountComparison(
            sameAccount = true,
            recordAppeared = false,
            recordPlayCountDelta = null,
            recordScoreDelta = null,
            recentBecameLatest = false,
            recentPlayTimeAdvanced = false,
            levelPlayCountDelta = null,
            accountEffectObserved = false,
        ),
    )
}

package com.leejlredstar.redefinencm.kmp.ui.screen

import com.leejlredstar.redefinencm.kmp.data.PlaybackAccountComparison
import com.leejlredstar.redefinencm.kmp.data.PlaybackReportEndpoint
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingKind
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingPhase
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingState
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingStatus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaybackSyncDisplayTest {
    @Test
    fun recentPlayIsShownAtStartAndLaterScrobbleTakesPriority() {
        val recentPlay = status(
            kind = PlaybackReportingKind.RECENT_PLAY,
            phase = PlaybackReportingPhase.ACCEPTED,
            endpoint = PlaybackReportEndpoint.WEBLOG_STARTPLAY,
        )
        val started = assertNotNull(
            playbackSyncDisplay(
                PlaybackReportingState(
                    recentPlay = recentPlay,
                    relay = status(
                        kind = PlaybackReportingKind.RELAY,
                        phase = PlaybackReportingPhase.ACCEPTED,
                        endpoint = PlaybackReportEndpoint.RELAY,
                    ),
                ),
            ),
        )
        val halfPlayed = assertNotNull(
            playbackSyncDisplay(
                PlaybackReportingState(
                    recentPlay = recentPlay,
                    scrobble = status(
                        phase = PlaybackReportingPhase.ACCEPTED,
                        endpoint = PlaybackReportEndpoint.SCROBBLE_V1,
                    ),
                ),
            ),
        )

        assertContains(started.title, "最近播放")
        assertContains(started.message, "最近播放 已被服务器接收")
        assertContains(started.message, "跨端进度")
        assertContains(halfPlayed.title, "播放记录")
        assertContains(halfPlayed.message, "NCBL 上报")
    }

    @Test
    fun verifiedScrobbleShowsAccountEvidenceAndRelayBoundary() {
        val display = assertNotNull(
            playbackSyncDisplay(
                PlaybackReportingState(
                    relay = status(
                        kind = PlaybackReportingKind.RELAY,
                        phase = PlaybackReportingPhase.UNSUPPORTED,
                        endpoint = PlaybackReportEndpoint.RELAY,
                    ),
                    scrobble = status(
                        phase = PlaybackReportingPhase.ACCOUNT_VERIFIED,
                        endpoint = PlaybackReportEndpoint.SCROBBLE_V1,
                        comparison = comparison(recordAppeared = true),
                    ),
                ),
            ),
        )

        assertFalse(display.isError)
        assertContains(display.message, "周听歌记录")
        assertContains(display.message, "当前服务器未部署 relay")
    }

    @Test
    fun unverifiedAndRejectedResultsExposeFailureAndDiagnosticCode() {
        val notReflected = assertNotNull(
            playbackSyncDisplay(
                PlaybackReportingState(
                    scrobble = status(phase = PlaybackReportingPhase.NOT_REFLECTED),
                ),
            ),
        )
        val rejected = assertNotNull(
            playbackSyncDisplay(
                PlaybackReportingState(
                    scrobble = status(
                        phase = PlaybackReportingPhase.REJECTED,
                        endpoint = PlaybackReportEndpoint.SCROBBLE_V1,
                        httpStatus = 200,
                        serverCode = 400,
                        message = "参数错误",
                    ),
                ),
            ),
        )

        assertTrue(notReflected.isError)
        assertTrue(rejected.isError)
        assertContains(rejected.message, "HTTP 200")
        assertContains(rejected.message, "code 400")
    }

    @Test
    fun newerRelayDoesNotMixWithAnOlderScrobbleSession() {
        val display = assertNotNull(
            playbackSyncDisplay(
                PlaybackReportingState(
                    relay = status(
                        kind = PlaybackReportingKind.RELAY,
                        phase = PlaybackReportingPhase.SENDING,
                        endpoint = PlaybackReportEndpoint.RELAY,
                        generation = 2L,
                    ),
                    scrobble = status(
                        phase = PlaybackReportingPhase.ACCOUNT_VERIFIED,
                        endpoint = PlaybackReportEndpoint.SCROBBLE_V1,
                        generation = 1L,
                        comparison = comparison(recordAppeared = true),
                    ),
                ),
            ),
        )

        assertContains(display.title, "跨端进度")
        assertFalse(display.message.contains("周听歌记录"))
    }

    private fun status(
        kind: PlaybackReportingKind = PlaybackReportingKind.SCROBBLE,
        phase: PlaybackReportingPhase,
        endpoint: PlaybackReportEndpoint? = null,
        httpStatus: Int? = null,
        serverCode: Int? = null,
        message: String? = null,
        comparison: PlaybackAccountComparison? = null,
        generation: Long = 1L,
    ): PlaybackReportingStatus = PlaybackReportingStatus(
        kind = kind,
        songId = 42L,
        credentialKey = 7L,
        reportingGeneration = generation,
        phase = phase,
        endpoint = endpoint,
        httpStatus = httpStatus,
        serverCode = serverCode,
        message = message,
        accountComparison = comparison,
    )

    private fun comparison(recordAppeared: Boolean): PlaybackAccountComparison =
        PlaybackAccountComparison(
            sameAccount = true,
            recordAppeared = recordAppeared,
            recordPlayCountDelta = null,
            recordScoreDelta = null,
            recentBecameLatest = false,
            recentPlayTimeAdvanced = false,
            levelPlayCountDelta = null,
            accountEffectObserved = recordAppeared,
        )
}

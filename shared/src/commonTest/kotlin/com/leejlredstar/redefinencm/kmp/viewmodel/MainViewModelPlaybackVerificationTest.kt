package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.PlaybackAccountComparison
import com.leejlredstar.redefinencm.kmp.player.PlaybackAccountVerificationEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainViewModelPlaybackVerificationTest {
    private val event = PlaybackAccountVerificationEvent(
        uid = 42L,
        credentialKey = 7L,
        reportingGeneration = 3L,
        songId = 100L,
        userLevel = null,
        comparison = PlaybackAccountComparison(
            sameAccount = true,
            recordAppeared = true,
            recordPlayCountDelta = 1L,
            recordScoreDelta = null,
            recentBecameLatest = false,
            recentPlayTimeAdvanced = false,
            levelPlayCountDelta = null,
            accountEffectObserved = true,
        ),
    )

    @Test
    fun verificationAppliesOnlyToTheSameUidAndCredential() {
        assertTrue(shouldApplyPlaybackVerification(42L, 7L, null, event))
        assertFalse(shouldApplyPlaybackVerification(99L, 7L, null, event))
        assertFalse(shouldApplyPlaybackVerification(42L, 8L, null, event))
        assertFalse(shouldApplyPlaybackVerification(42L, null, null, event))
        assertFalse(shouldApplyPlaybackVerification(42L, 7L, 3L, event))
        assertFalse(shouldApplyPlaybackVerification(42L, 7L, 4L, event))
    }
}

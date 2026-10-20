package com.leejlredstar.redefinencm.kmp.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Play after a failure resolves a fresh link instead of replaying the dead one. */
class WebPlayerSourceReuseTest {
    @Test
    fun onlyAHealthySourceOfThisTrackIsPlayedAgain() {
        assertTrue(webCanReuseLoadedSource("1", "1", hasSource = true, elementFailed = false, state = PlayerState.PAUSED))
        assertFalse(webCanReuseLoadedSource("1", "1", hasSource = true, elementFailed = true, state = PlayerState.PAUSED))
        assertFalse(webCanReuseLoadedSource("1", "1", hasSource = true, elementFailed = false, state = PlayerState.ERROR))
        assertFalse(webCanReuseLoadedSource("1", "2", hasSource = true, elementFailed = false, state = PlayerState.PAUSED))
        assertFalse(webCanReuseLoadedSource("1", "1", hasSource = false, elementFailed = false, state = PlayerState.PAUSED))
        assertFalse(webCanReuseLoadedSource(null, "1", hasSource = true, elementFailed = false, state = PlayerState.PAUSED))
    }
}

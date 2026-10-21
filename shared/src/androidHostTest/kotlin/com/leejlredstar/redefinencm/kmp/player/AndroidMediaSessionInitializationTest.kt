package com.leejlredstar.redefinencm.kmp.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidMediaSessionInitializationTest {
    @Test
    fun sessionIsExposedOnlyWhenReadyAndPresent() {
        assertFalse(canExposeAndroidMediaSession(AndroidMediaSessionInitializationState.Loading, true))
        assertFalse(canExposeAndroidMediaSession(AndroidMediaSessionInitializationState.Failed, true))
        assertFalse(canExposeAndroidMediaSession(AndroidMediaSessionInitializationState.Destroyed, true))
        assertFalse(canExposeAndroidMediaSession(AndroidMediaSessionInitializationState.Ready, false))
        assertTrue(canExposeAndroidMediaSession(AndroidMediaSessionInitializationState.Ready, true))
    }
}

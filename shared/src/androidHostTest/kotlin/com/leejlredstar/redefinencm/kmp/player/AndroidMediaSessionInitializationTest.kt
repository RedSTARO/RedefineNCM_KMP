package com.leejlredstar.redefinencm.kmp.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun undispatchedReadyDependencyPublishesBeforeLaunchReturns() {
        val dependency = CompletableDeferred<Unit>().apply { complete(Unit) }
        var state = AndroidMediaSessionInitializationState.Loading

        val job = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
            dependency.await()
            state = AndroidMediaSessionInitializationState.Ready
        }

        assertTrue(job.isCompleted)
        assertEquals(AndroidMediaSessionInitializationState.Ready, state)
    }
}

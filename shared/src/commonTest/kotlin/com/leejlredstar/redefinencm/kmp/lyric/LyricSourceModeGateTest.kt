package com.leejlredstar.redefinencm.kmp.lyric

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LyricSourceModeGateTest {
    @Test
    fun restoredPlaybackWaitsForPersistedBackendOnlyMode() = runTest {
        val gate = LyricSourceModeGate()
        val pendingMode = async { gate.awaitMode() }

        yield()

        assertFalse(pendingMode.isCompleted)
        gate.completeInitialLoad(LyricSourceMode.BACKEND_ONLY)
        assertEquals(LyricSourceMode.BACKEND_ONLY, pendingMode.await())
    }

    @Test
    fun explicitSelectionWhileLoadingWinsOverOlderSnapshot() = runTest {
        val gate = LyricSourceModeGate()

        gate.update(LyricSourceMode.TTML_ONLY)
        gate.completeInitialLoad(LyricSourceMode.BACKEND_ONLY)

        assertEquals(LyricSourceMode.TTML_ONLY, gate.awaitMode())
    }

    @Test
    fun settingsReadFailureFailsClosedToBackendOnly() = runTest {
        val gate = LyricSourceModeGate()

        gate.failInitialLoad()

        assertEquals(LyricSourceMode.BACKEND_ONLY, gate.awaitMode())
    }
}

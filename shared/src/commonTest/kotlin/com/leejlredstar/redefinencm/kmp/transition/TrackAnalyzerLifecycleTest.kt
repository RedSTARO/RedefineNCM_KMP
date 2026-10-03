package com.leejlredstar.redefinencm.kmp.transition

import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrackAnalyzerLifecycleTest {
    private fun media(id: String) = MediaInfo(id, id, "Test", duration = 180_000)
    private fun decoded() = DecodedTrackEnds(180_000, null, null)

    @Test
    fun theLastCancelledWaiterStopsAnUnneededDecode() = runTest {
        val gate = CompletableDeferred<Unit>()
        var running = false
        val analyzer = TrackAnalyzer(
            TrackEndsDecoder { _, _, _ ->
                running = true
                try { gate.await(); decoded() } finally { running = false }
            },
            AnalysisUrlResolver { "test-url" }, UnavailableBeatModelLoader("test"),
            StandardTestDispatcher(testScheduler),
        )
        val waiter = launch { analyzer.analyze(media("one")) }
        try {
            runCurrent()
            assertTrue(running)
            waiter.cancel()
            runCurrent()
            assertFalse(running)
        } finally {
            waiter.cancel()
            gate.complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun cancellingOneWaiterKeepsTheSharedDecodeForTheOther() = runTest {
        val gate = CompletableDeferred<Unit>()
        var running = false
        var decodes = 0
        val analyzer = TrackAnalyzer(
            TrackEndsDecoder { _, _, _ ->
                decodes++
                running = true
                try { gate.await(); decoded() } finally { running = false }
            },
            AnalysisUrlResolver { "test-url" }, UnavailableBeatModelLoader("test"),
            StandardTestDispatcher(testScheduler),
        )
        val first = async { analyzer.analyze(media("one")) }
        val second = async { analyzer.analyze(media("one")) }
        try {
            runCurrent()
            first.cancel()
            runCurrent()
            assertTrue(running)
            assertEquals(1, decodes)
            gate.complete(Unit)
            runCurrent()
            assertNotNull(second.await())
            assertNotNull(analyzer.analyze(media("one")))
            assertEquals(1, decodes)
        } finally {
            first.cancel()
            second.cancel()
            gate.complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun distinctRequestsCannotStartAnUnboundedNumberOfDecoders() = runTest {
        val gate = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        val analyzer = TrackAnalyzer(
            TrackEndsDecoder { _, _, _ ->
                active++
                maximum = maxOf(maximum, active)
                try { gate.await(); decoded() } finally { active-- }
            },
            AnalysisUrlResolver { "test-url" }, UnavailableBeatModelLoader("test"),
            StandardTestDispatcher(testScheduler),
        )
        val waiters = List(8) { index -> launch { analyzer.analyze(media(index.toString())) } }
        try {
            runCurrent()
            assertEquals(2, active)
            gate.complete(Unit)
            runCurrent()
            waiters.forEach { it.join() }
            assertEquals(2, maximum)
            assertEquals(0, active)
        } finally {
            waiters.forEach { it.cancel() }
            gate.complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun failedTrackRetentionUsesTheSameBoundAsSuccessfulResults() = runTest {
        var resolutions = 0
        val analyzer = TrackAnalyzer(
            TrackEndsDecoder { _, _, _ -> error("No URL should reach decoding") },
            AnalysisUrlResolver { resolutions++; null }, UnavailableBeatModelLoader("test"),
            StandardTestDispatcher(testScheduler), cacheSize = 2,
        )
        listOf("one", "two", "three", "one").forEach { analyzer.analyze(media(it)) }
        assertEquals(4, resolutions)
    }
}

/*
 * Frame-clock regression tests for the native AMLL renderer.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.leejlredstar.redefinencm.kmp.ui.amll

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmllFrameClockTest {

    @Test
    fun presentationClockPublishesAtMostSixtyTimesPerSecondOnHighRefreshInput() {
        assertEquals(30, countPresentationPublishes(inputHz = 30))
        assertEquals(60, countPresentationPublishes(inputHz = 60))
        assertEquals(60, countPresentationPublishes(inputHz = 90))
        assertEquals(60, countPresentationPublishes(inputHz = 120))
        assertEquals(60, countPresentationPublishes(inputHz = 144))
        assertEquals(60, countPresentationPublishes(inputHz = 240))
    }

    @Test
    fun presentationClockSkipsCatchUpBurstsAfterAStall() {
        val frameGate = AmllPresentationFrameGate()
        assertTrue(frameGate.shouldPublish(0L))

        assertTrue(frameGate.shouldPublish(100_000_000L))
        assertTrue(!frameGate.shouldPublish(100_000_000L))
    }

    @Test
    fun hundredMillisecondPlayerSamplesDoNotRestartThePresentationCadence() {
        for (inputHz in listOf(30, 60, 90, 120, 144, 240)) {
            val publishTimes = simulatePresentationPublishes(
                inputHz = inputHz,
                seconds = 2,
            )
            val targetPublishes = minOf(inputHz, AmllPresentationRefreshHz.toInt()) * 2
            assertTrue(
                publishTimes.size in (targetPublishes - 1)..(targetPublishes + 1),
                "inputHz=$inputHz publishes=${publishTimes.size}",
            )

            val maximumGapNanos = publishTimes
                .zipWithNext { previous, current -> current - previous }
                .maxOrNull() ?: 0L
            val inputFrameNanos = (1_000_000_000L + inputHz - 1L) / inputHz
            assertTrue(
                maximumGapNanos <= AmllPresentationFrameIntervalNanos + inputFrameNanos,
                "inputHz=$inputHz maximumGapNanos=$maximumGapNanos",
            )
        }
    }

    @Test
    fun animationDeltaPreservesHighRefreshFrameDurations() {
        assertClose(16.666667, amllFrameDeltaMillis(0L, 16_666_667L))
        assertClose(6.944444, amllFrameDeltaMillis(0L, 6_944_444L))
        assertClose(4.166667, amllFrameDeltaMillis(0L, 4_166_667L))
        assertEquals(0.0, amllFrameDeltaMillis(10L, 10L))
        assertEquals(0.0, amllFrameDeltaMillis(10L, 9L))
    }

    private fun countPresentationPublishes(inputHz: Int): Int {
        val frameGate = AmllPresentationFrameGate()
        var publishes = 0
        for (frame in 0 until inputHz) {
            val frameNanos = frame * 1_000_000_000L / inputHz
            if (frameGate.shouldPublish(frameNanos)) {
                publishes += 1
            }
        }
        return publishes
    }

    private fun simulatePresentationPublishes(
        inputHz: Int,
        seconds: Int,
    ): List<Long> {
        val frameGate = AmllPresentationFrameGate()
        val publishTimes = mutableListOf<Long>()
        var sampleWindow = -1L
        var anchoredSampleMs = 0L
        var anchorFrameNanos = 0L
        var previousPositionMs = 0L

        for (frame in 0 until (inputHz * seconds)) {
            val frameTimeNanos = frame * 1_000_000_000L / inputHz
            val currentSampleWindow = frameTimeNanos / 100_000_000L
            if (currentSampleWindow != sampleWindow) {
                sampleWindow = currentSampleWindow
                anchoredSampleMs = currentSampleWindow * 100L
                anchorFrameNanos = frameTimeNanos
            }
            if (!frameGate.shouldPublish(frameTimeNanos)) continue

            val positionMs = amllPresentationPositionAt(
                anchoredSampleMs = anchoredSampleMs,
                anchorFrameNanos = anchorFrameNanos,
                frameTimeNanos = frameTimeNanos,
                durationMs = 0L,
            )
            assertTrue(
                positionMs >= previousPositionMs,
                "inputHz=$inputHz previous=$previousPositionMs current=$positionMs",
            )
            previousPositionMs = positionMs
            publishTimes += frameTimeNanos
        }
        return publishTimes
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 0.000001,
    ) {
        assertTrue(abs(expected - actual) <= tolerance, "expected=$expected actual=$actual")
    }
}

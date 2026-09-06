/*
 * Frame-clock regression tests for the native AMLL renderer.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.leejlredstar.redefinencm.kmp.ui.amll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmllFrameClockTest {

    @Test
    fun presentationClockPublishesOnEveryFrameAtAnyRefreshRate() {
        for (inputHz in listOf(30, 60, 90, 120, 144, 200, 240)) {
            val positions = simulatePresentationPositions(inputHz = inputHz, seconds = 2)
            assertEquals(inputHz * 2, positions.size, "inputHz=$inputHz")
        }
    }

    @Test
    fun hundredMillisecondPlayerSamplesDoNotMoveThePresentationClockBackwards() {
        for (inputHz in listOf(30, 60, 90, 120, 144, 200, 240)) {
            val positions = simulatePresentationPositions(inputHz = inputHz, seconds = 2)
            positions.zipWithNext { previous, current ->
                assertTrue(
                    current >= previous,
                    "inputHz=$inputHz previous=$previous current=$current",
                )
            }
            val inputFrameMs = (1_000L + inputHz - 1L) / inputHz
            positions.forEachIndexed { frame, positionMs ->
                val frameMs = frame * 1_000L / inputHz
                assertTrue(
                    positionMs in (frameMs - inputFrameMs)..frameMs,
                    "inputHz=$inputHz frame=$frame frameMs=$frameMs position=$positionMs",
                )
            }
        }
    }

    @Test
    fun presentationPositionNeverPassesTheTrackDuration() {
        assertEquals(
            5_000L,
            amllPresentationPositionAt(
                anchoredSampleMs = 4_990L,
                anchorFrameNanos = 0L,
                frameTimeNanos = 50_000_000L,
                durationMs = 5_000L,
            ),
        )
    }

    /** The player publishes a sample every 100 ms; the clock anchors to it and extrapolates. */
    private fun simulatePresentationPositions(
        inputHz: Int,
        seconds: Int,
    ): List<Long> {
        val positions = mutableListOf<Long>()
        var sampleWindow = -1L
        var anchoredSampleMs = 0L
        var anchorFrameNanos = 0L

        for (frame in 0 until (inputHz * seconds)) {
            val frameTimeNanos = frame * 1_000_000_000L / inputHz
            val currentSampleWindow = frameTimeNanos / 100_000_000L
            if (currentSampleWindow != sampleWindow) {
                sampleWindow = currentSampleWindow
                anchoredSampleMs = currentSampleWindow * 100L
                anchorFrameNanos = frameTimeNanos
            }
            positions += amllPresentationPositionAt(
                anchoredSampleMs = anchoredSampleMs,
                anchorFrameNanos = anchorFrameNanos,
                frameTimeNanos = frameTimeNanos,
                durationMs = 0L,
            )
        }
        return positions
    }
}

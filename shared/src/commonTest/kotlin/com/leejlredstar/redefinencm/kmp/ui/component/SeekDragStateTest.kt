package com.leejlredstar.redefinencm.kmp.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeekDragStateTest {

    private val trackMillis = 200_000L

    @Test
    fun showsThePlayerPositionUntilADragStarts() {
        val state = SeekDragState()
        assertFalse(state.isDragging)
        assertEquals(0.25f, state.progressFor(0.25f))
        assertEquals(50_000L, state.positionFor(50_000L, trackMillis))
    }

    @Test
    fun showsThePendingPositionWhileDragging() {
        val state = SeekDragState()
        state.preview(0.75f)
        assertTrue(state.isDragging)
        assertEquals(0.75f, state.progressFor(0.10f))
        assertEquals(150_000L, state.positionFor(20_000L, trackMillis))
    }

    @Test
    fun committingReturnsTheSeekTargetAndEndsTheDrag() {
        val state = SeekDragState()
        state.preview(0.5f)
        assertEquals(100_000L, state.commit(trackMillis))
        assertFalse(state.isDragging)
    }

    @Test
    fun cancellingEndsTheDragWithoutASeekTarget() {
        val state = SeekDragState()
        state.preview(0.9f)
        state.cancel()
        assertFalse(state.isDragging)
        assertEquals(0.10f, state.progressFor(0.10f))
    }

    @Test
    fun aTrackWithNoKnownDurationHasNothingToSeekTo() {
        val state = SeekDragState()
        state.preview(0.5f)
        assertNull(state.commit(0L))
        assertFalse(state.isDragging)
    }

    @Test
    fun neverSeeksOrDrawsPastEitherEndOfTheTrack() {
        assertEquals(0L, seekPositionOf(-0.5f, trackMillis))
        assertEquals(trackMillis, seekPositionOf(1.5f, trackMillis))
        assertEquals(0L, seekPositionOf(0.5f, 0L))

        val state = SeekDragState()
        // Backends overshoot by a frame around a track change.
        assertEquals(trackMillis, state.positionFor(trackMillis + 500L, trackMillis))
        assertEquals(0L, state.positionFor(-1_000L, trackMillis))
    }

    @Test
    fun clampsAPendingFractionOutsideTheTrack() {
        val state = SeekDragState()
        state.preview(2f)
        assertEquals(1f, state.progressFor(0f))
        state.preview(-1f)
        assertEquals(0f, state.progressFor(1f))
    }
}

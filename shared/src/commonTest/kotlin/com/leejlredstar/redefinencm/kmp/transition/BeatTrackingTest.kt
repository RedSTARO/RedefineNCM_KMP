package com.leejlredstar.redefinencm.kmp.transition

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeatTrackingTest {

    @Test
    fun chunksMatchBeatThisSplitPiece() {
        // Expected starts and padded lengths are beat_this.inference.split_piece(…, 750, 6, True).
        val expected = mapOf(
            2250 to listOf(-6 to 750, 732 to 750, 1470 to 750, 1506 to 750),
            500 to listOf(-6 to 512),
            750 to listOf(-6 to 750, 6 to 750),
            744 to listOf(-6 to 750, 0 to 750),
            1500 to listOf(-6 to 750, 732 to 750, 756 to 750),
        )
        for ((frames, chunks) in expected) {
            assertEquals(chunks, beatModelChunks(frames).map { it.start to it.length }, "frames=$frames")
        }
    }

    /** Echoes each frame's first mel value back as its beat logit. */
    private class EchoModel : BeatActivationModel {
        override val accelerator = InferenceAccelerator.GPU
        override val deviceLabel = "echo"
        var calls = 0
        override suspend fun infer(spectrogram: FloatArray): FloatArray {
            calls += 1
            assertEquals(BEAT_MODEL_CHUNK_FRAMES * BeatModelFeatures.MEL_BINS, spectrogram.size)
            return FloatArray(BEAT_MODEL_CHUNK_FRAMES * 2) { i ->
                val frame = i / 2
                val value = spectrogram[frame * BeatModelFeatures.MEL_BINS]
                if (i % 2 == 0) value else -value
            }
        }
        override fun close() {}
    }

    @Test
    fun everyFrameIsPredictedByTheChunkThatOwnsIt() = runTest {
        for (frames in listOf(2250, 500, 750, 1500, 3001)) {
            val mel = MelFrames(frames, FloatArray(frames * BeatModelFeatures.MEL_BINS).also { values ->
                for (t in 0 until frames) values[t * BeatModelFeatures.MEL_BINS] = t.toFloat() + 1f
            })
            val model = EchoModel()
            val activations = runBeatModel(model, mel)
            for (t in 0 until frames) {
                assertEquals(t + 1f, activations.beat[t], "frames=$frames t=$t")
                assertEquals(-(t + 1f), activations.downbeat[t])
            }
            assertEquals(beatModelChunks(frames).size, model.calls)
        }
    }

    private fun logitsWithPeaks(frames: Int, peaks: List<Int>): FloatArray =
        FloatArray(frames) { -5f }.also { logits -> peaks.forEach { logits[it] = 3f } }

    @Test
    fun minimalPostprocessingPicksPeaksAndSnapsDownbeats() {
        val beat = logitsWithPeaks(500, listOf(25, 50, 75, 100, 125))
        // A two-frame plateau collapses to its mean; a downbeat one frame off snaps to its beat.
        beat[150] = 3f
        beat[151] = 3f
        val downbeat = logitsWithPeaks(500, listOf(26, 126))
        val events = pickBeatEvents(BeatActivations(beat, downbeat))
        assertEquals(listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.01), events.beats.map { (it * 100).toLong() / 100.0 })
        assertEquals(listOf(0.5, 2.5), events.downbeats.toList())
    }

    private fun events(periodS: Double, count: Int, offsetS: Double = 0.2, jitter: (Int) -> Double = { 0.0 }) =
        BeatEvents(
            beats = DoubleArray(count) { offsetS + it * periodS + jitter(it) },
            downbeats = DoubleArray((count + 3) / 4) { offsetS + it * 4 * periodS },
        )

    @Test
    fun gridRecoversTempoAndDownbeatsDespiteJitterAndOutliers() {
        val period = 0.5 // 120 BPM
        val base = events(period, 60) { i -> if (i % 7 == 0) 0.012 else -0.006 }
        // One spurious beat between two real ones must not bend the tempo.
        val beats = (base.beats.toList() + 10.45).sorted().toDoubleArray()
        val grid = assertNotNull(fitBeatGrid(BeatEvents(beats, base.downbeats), 60_000L, 60_000L, 90_000L))
        assertTrue(abs(grid.bpm - 120.0) < 0.2, "bpm ${grid.bpm}")
        assertEquals(4, grid.beatsPerBar)
        assertTrue(grid.confidence > 0.8, "confidence ${grid.confidence}")
        assertTrue(grid.downbeatConfidence > 0.9)
        // The downbeat at or after 61.0 s is 62.2 s: downbeats sit at 60.2 + 2k.
        assertTrue(abs(grid.downbeatAtOrAfterMs(61_000.0) - 62_200.0) < 20.0)
        assertTrue(abs(grid.downbeatAtOrBeforeMs(61_000.0) - 60_200.0) < 20.0)
    }

    @Test
    fun gridRefusesTooFewBeats() {
        assertNull(fitBeatGrid(events(0.5, 5), 0L, 0L, 3_000L))
    }

    @Test
    fun gridSurvivesFrameQuantisation() {
        // A 128 BPM (468.75 ms) beat detected on 20 ms frames: intervals alternate 460 and 480.
        val times = DoubleArray(64) { k -> kotlin.math.round((0.3 + k * 0.46875) * 50.0) / 50.0 }
        val events = BeatEvents(times, DoubleArray(16) { times[it * 4] })
        for (edge in GridEdge.entries) {
            val grid = assertNotNull(fitBeatGrid(events, 0L, 0L, 30_000L, edge))
            assertTrue(abs(grid.periodMs - 468.75) < 1.0, "$edge period ${grid.periodMs}")
            assertTrue(grid.confidence > 0.7, "$edge confidence ${grid.confidence}")
        }
    }

    @Test
    fun tempoRatioFoldsHalfAndDoubleTime() {
        fun grid(bpm: Double) = BeatGrid(0.0, 60_000.0 / bpm, 4, 0, 1.0, 1.0)
        assertEquals(1.0, tempoRatio(grid(120.0), grid(120.0)))
        assertTrue(abs(tempoRatio(grid(124.0), grid(120.0))!! - 120.0 / 124.0) < 1e-9)
        assertTrue(abs(tempoRatio(grid(120.0), grid(60.0))!! - 1.0) < 1e-9)
        assertNull(tempoRatio(grid(120.0), grid(80.0)))
    }

    @Test
    fun camelotNeighboursAreCompatible() {
        val cMajor = MusicalKey(0, minor = false, strength = 0.8)
        val aMinor = MusicalKey(9, minor = true, strength = 0.8)
        val gMajor = MusicalKey(7, minor = false, strength = 0.8)
        val fSharpMajor = MusicalKey(6, minor = false, strength = 0.8)
        assertEquals(8, cMajor.camelotNumber)
        assertEquals(8, aMinor.camelotNumber)
        assertEquals(0, camelotDistance(cMajor, aMinor))
        assertEquals(1, camelotDistance(cMajor, gMajor))
        assertEquals(6, camelotDistance(cMajor, fSharpMajor))
    }
}

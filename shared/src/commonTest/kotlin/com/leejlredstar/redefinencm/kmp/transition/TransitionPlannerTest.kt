package com.leejlredstar.redefinencm.kmp.transition

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransitionPlannerTest {

    private val loud = -12f
    private val silent = EnergyProfile.SILENCE_FLOOR_DB

    /** A section whose level is [loud] between [audibleFromMs] and [audibleUntilMs], silent elsewhere. */
    private fun energy(startMs: Long, endMs: Long, audibleFromMs: Long = startMs, audibleUntilMs: Long = endMs) =
        EnergyProfile(
            startMs = startMs,
            levelsDb = List(((endMs - startMs) / EnergyProfile.FRAME_MS).toInt()) { i ->
                val t = startMs + i * EnergyProfile.FRAME_MS
                if (t in audibleFromMs until audibleUntilMs) loud else silent
            },
        )

    private fun grid(bpm: Double, anchorMs: Double = 0.0, confidence: Double = 0.9) =
        BeatGrid(anchorMs, 60_000.0 / bpm, 4, 0, confidence, 0.9)

    private val cMajor = MusicalKey(0, minor = false, strength = 0.8)
    private val fSharpMajor = MusicalKey(6, minor = false, strength = 0.8)

    private fun analysis(
        id: String,
        durationMs: Long,
        headGrid: BeatGrid? = null,
        tailGrid: BeatGrid? = null,
        headAudibleFrom: Long = 0L,
        tailAudibleUntil: Long = durationMs,
        key: MusicalKey? = cMajor,
    ): TrackAnalysis {
        val tailStart = durationMs - TrackAnalysis.SECTION_MS
        return TrackAnalysis(
            mediaId = id,
            durationMs = durationMs,
            head = SectionAnalysis(0, TrackAnalysis.SECTION_MS, energy(0, TrackAnalysis.SECTION_MS, headAudibleFrom), headGrid, key),
            tail = SectionAnalysis(
                tailStart,
                durationMs,
                energy(tailStart, durationMs, audibleUntilMs = tailAudibleUntil),
                tailGrid,
                key,
            ),
            beatAccelerator = InferenceAccelerator.GPU,
        )
    }

    private fun plan(
        outgoing: TrackAnalysis?,
        incoming: TrackAnalysis?,
        mode: SongTransitionMode = SongTransitionMode.SMART,
        capability: TransitionCapability = TransitionCapability.TEMPO_MATCHED,
        outDuration: Long = 200_000L,
        inDuration: Long = 180_000L,
        outAlbum: String = "A",
        inAlbum: String = "B",
    ) = TransitionPlanner.plan(
        outgoingMediaId = "out",
        outgoingAlbum = outAlbum,
        outgoingDurationMs = outDuration,
        outgoing = outgoing,
        incomingMediaId = "in",
        incomingAlbum = inAlbum,
        incomingDurationMs = inDuration,
        incoming = incoming,
        mode = mode,
        crossfadeSeconds = 6,
        capability = capability,
    )

    @Test
    fun offOrIncapablePlansNothing() {
        assertNull(plan(null, null, mode = SongTransitionMode.OFF).plan)
        assertNull(plan(null, null, capability = TransitionCapability.NONE).plan)
        assertNull(plan(null, null, outDuration = 20_000L).plan)
    }

    @Test
    fun crossfadeEndsAtTheTrackEnd() {
        val p = assertNotNull(plan(null, null, mode = SongTransitionMode.CROSSFADE).plan)
        assertEquals(TransitionKind.CROSSFADE, p.kind)
        assertEquals(6_000L, p.overlapMs)
        assertEquals(194_000L, p.startMs)
        assertEquals(0L, p.incomingEntryMs)
        assertEquals(1.0, p.outgoingRate)
    }

    @Test
    fun matchingTempiBlendOnTheBeat() {
        val out = analysis("out", 200_000L, tailGrid = grid(124.0, anchorMs = 155_000.0))
        val inc = analysis("in", 180_000L, headGrid = grid(120.0, anchorMs = 400.0), headAudibleFrom = 400L)
        val p = assertNotNull(plan(out, inc).plan)
        assertEquals(TransitionKind.BEAT_MATCHED, p.kind)
        // The outgoing track is slowed to the incoming tempo.
        assertTrue(abs(p.outgoingRate - 120.0 / 124.0) < 1e-9)
        // Eight bars at 120 BPM is 16 s of blend.
        assertEquals(16_000L, p.overlapMs)
        // It starts on an outgoing downbeat and enters the incoming track on its first downbeat.
        val outBar = 4 * 60_000.0 / 124.0
        val barsFromAnchor = (p.startMs - 155_000.0) / outBar
        assertTrue(abs(barsFromAnchor - kotlin.math.round(barsFromAnchor)) < 0.01)
        assertEquals(400L, p.incomingEntryMs)
        // The outgoing track is silent before its end, and the ramp precedes the blend.
        assertTrue(p.outgoingEndMs <= 200_000L)
        assertTrue(p.rampStartMs < p.startMs)
        assertEquals(8_000L, p.swapAfterMs)
    }

    @Test
    fun clashingKeysShortenTheBeatMatchedBlend() {
        val out = analysis("out", 200_000L, tailGrid = grid(120.0, anchorMs = 155_000.0), key = cMajor)
        val inc = analysis("in", 180_000L, headGrid = grid(120.0), key = fSharpMajor)
        val p = assertNotNull(plan(out, inc).plan)
        assertEquals(TransitionKind.BEAT_MATCHED, p.kind)
        assertEquals(8_000L, p.overlapMs)
        assertEquals(1.0, p.outgoingRate)
        assertEquals(p.startMs, p.rampStartMs)
    }

    @Test
    fun distantTempiFallBackToACrossfadeTrimmedToTheMusic() {
        val out = analysis("out", 200_000L, tailGrid = grid(140.0, anchorMs = 155_000.0), tailAudibleUntil = 195_000L)
        val inc = analysis("in", 180_000L, headGrid = grid(100.0), headAudibleFrom = 2_000L)
        val p = assertNotNull(plan(out, inc).plan)
        assertEquals(TransitionKind.CROSSFADE, p.kind)
        // The blend ends where the music ends, not at the silent end of the file.
        assertTrue(p.outgoingEndMs <= 195_000L)
        assertTrue(p.incomingEntryMs >= 2_000L)
    }

    @Test
    fun withoutTempoControlBeatsAreNotMatched() {
        val out = analysis("out", 200_000L, tailGrid = grid(124.0, anchorMs = 155_000.0))
        val inc = analysis("in", 180_000L, headGrid = grid(120.0))
        val p = assertNotNull(plan(out, inc, capability = TransitionCapability.CROSSFADE).plan)
        assertEquals(TransitionKind.CROSSFADE, p.kind)
        assertEquals(1.0, p.outgoingRate)
    }

    @Test
    fun aContinuousAlbumIsLeftGapless() {
        val out = analysis("out", 200_000L)
        val inc = analysis("in", 180_000L)
        assertNull(plan(out, inc, outAlbum = "Live", inAlbum = "Live").plan)
        // The same pair from different albums still blends.
        assertNotNull(plan(out, inc, outAlbum = "Live", inAlbum = "Studio").plan)
    }

    @Test
    fun rampIsLinearInOutgoingMediaTime() {
        val p = TransitionPlan("a", "b", TransitionKind.BEAT_MATCHED, 10_000L, 12_000L, 0L, 8_000L, 0.96, 4_000L)
        assertEquals(1.0, p.outgoingRateAt(9_000L))
        assertTrue(abs(p.outgoingRateAt(11_000L) - 0.98) < 1e-9)
        assertEquals(0.96, p.outgoingRateAt(12_000L))
        val start = p.gainsAt(0L)
        val middle = p.gainsAt(4_000L)
        val end = p.gainsAt(8_000L)
        assertEquals(1f, start.outgoing)
        assertEquals(0f, start.incoming)
        // Equal power: the squared gains sum to one throughout.
        assertTrue(abs(middle.outgoing * middle.outgoing + middle.incoming * middle.incoming - 1f) < 1e-6)
        assertTrue(end.outgoing < 1e-6)
    }
}

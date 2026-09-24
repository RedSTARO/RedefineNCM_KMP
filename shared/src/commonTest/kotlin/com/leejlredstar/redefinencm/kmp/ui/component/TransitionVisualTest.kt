package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.runtime.mutableFloatStateOf
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.transition.BeatTiming
import com.leejlredstar.redefinencm.kmp.transition.TransitionKind
import com.leejlredstar.redefinencm.kmp.transition.TransitionPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransitionVisualTest {

    private val outgoing = MediaInfo(id = "out", title = "Out", artist = "A")
    private val incoming = MediaInfo(id = "in", title = "In", artist = "B")

    /** Eight bars at 120 BPM, entering the incoming track on its downbeat at 400 ms. */
    private val beatMatched = TransitionPlan(
        outgoingMediaId = "out",
        incomingMediaId = "in",
        kind = TransitionKind.BEAT_MATCHED,
        rampStartMs = 150_000L,
        startMs = 154_000L,
        incomingEntryMs = 400L,
        overlapMs = 16_000L,
        outgoingRate = 0.97,
        swapAfterMs = 8_000L,
        incomingBeat = BeatTiming(downbeatMs = 400.0, periodMs = 500.0, beatsPerBar = 4),
    )

    private val crossfade = TransitionPlan("out", "in", TransitionKind.CROSSFADE, 194_000L, 194_000L, 0L, 6_000L, 1.0, 3_000L)

    private fun visual(plan: TransitionPlan, reducedMotion: Boolean = false): Pair<TransitionVisual, (Float) -> Unit> {
        val elapsed = mutableFloatStateOf(0f)
        return TransitionVisual(outgoing, incoming, plan, elapsed, reducedMotion) to { ms -> elapsed.floatValue = ms }
    }

    private fun smoothstep(x: Float) = x * x * (3f - 2f * x)

    @Test
    fun aCrossfadeDissolvesEvenlyAndNeverKicks() {
        val (v, at) = visual(crossfade)
        assertFalse(v.movesWithBeat)
        for (ms in listOf(0f, 750f, 1_500f, 3_000f, 4_400f, 6_000f)) {
            at(ms)
            assertEquals(smoothstep(ms / 6_000f), v.weight, 1e-6f)
            assertEquals(0f, v.beatPulse)
        }
    }

    @Test
    fun aBeatMatchedBlendStepsOnTheBeatAndRestsBetween() {
        val (v, at) = visual(beatMatched)
        assertTrue(v.movesWithBeat)
        // Past the step, the picture rests until the next beat.
        at(8_300f)
        val resting = v.weight
        at(8_490f)
        assertEquals(resting, v.weight, 1e-6f)
        // The step itself moves it on.
        at(8_500f)
        val beat = v.weight
        at(8_600f)
        assertTrue(v.weight > beat)
        // Half and half at the swap, which falls on a downbeat.
        at(8_000f)
        assertEquals(0.5f, v.weight, 1e-6f)
        // It only ever moves forward, from nothing to everything.
        var last = -1f
        var ms = 0f
        while (ms <= 16_000f) {
            at(ms)
            assertTrue(v.weight >= last - 1e-6f, "weight fell at $ms ms")
            last = v.weight
            ms += 10f
        }
        assertEquals(1f, last, 1e-6f)
    }

    @Test
    fun theKickFollowsEachBeatAndIsStrongestOnTheDownbeat() {
        val (v, at) = visual(beatMatched)
        // Nothing on the beat itself; it peaks 70 ms after and has died away before the next.
        at(8_000f)
        assertEquals(0f, v.beatPulse, 1e-6f)
        at(8_070f)
        val downbeat = v.beatPulse
        assertTrue(downbeat > 0.95f, "downbeat kick $downbeat")
        at(8_480f)
        assertTrue(v.beatPulse < 0.05f)
        // The next beat is not a downbeat.
        at(8_570f)
        val offbeat = v.beatPulse
        assertTrue(offbeat in 0.3f..0.6f, "offbeat kick $offbeat")
        // At the ends of the blend, where one track is nearly silent, there is almost no kick.
        at(70f)
        assertTrue(v.beatPulse < 0.05f)
    }

    @Test
    fun reducedMotionDissolvesABeatMatchedBlendEvenly() {
        val (v, at) = visual(beatMatched, reducedMotion = true)
        assertFalse(v.movesWithBeat)
        at(8_300f)
        assertEquals(smoothstep(8_300f / 16_000f), v.weight, 1e-6f)
        at(8_070f)
        assertEquals(0f, v.beatPulse)
    }
}

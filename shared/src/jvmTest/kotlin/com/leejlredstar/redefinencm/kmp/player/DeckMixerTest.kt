package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.transition.TransitionKind
import com.leejlredstar.redefinencm.kmp.transition.TransitionPlan
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders a beat-matched blend of two synthetic click tracks through the real decoder, stretcher
 * and mixer, and measures the result. The outgoing track clicks in the left channel at 125 BPM,
 * the incoming one in the right channel at 120 BPM (and at 48 kHz, so the decoder's forced
 * conversion is part of the path).
 */
class DeckMixerTest {
    private val directory: File = Files.createTempDirectory("deck-mixer").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    /** A stereo WAV with a 6 ms tone burst every [periodMs] in [channel] and silence elsewhere. */
    private fun clickTrack(name: String, rate: Int, seconds: Int, periodMs: Int, channel: Int): File {
        val frames = rate * seconds
        val bytes = ByteArray(frames * 4)
        val period = rate * periodMs / 1000
        val burst = rate * 6 / 1000
        for (i in 0 until frames) {
            val phase = i % period
            val value = if (phase < burst) (0.8 * sin(2 * PI * 1_000.0 * phase / rate) * 32767).toInt() else 0
            val at = i * 4 + channel * 2
            bytes[at] = value.toByte()
            bytes[at + 1] = (value shr 8).toByte()
        }
        val format = AudioFormat(rate.toFloat(), 16, 2, true, false)
        val file = File(directory, name)
        AudioSystem.write(AudioInputStream(ByteArrayInputStream(bytes), format, frames.toLong()), AudioFileFormat.Type.WAVE, file)
        return file
    }

    /** Output frames where [channel] first exceeds 0.1 after at least 50 ms below it. */
    private fun onsets(output: FloatArray, channel: Int, rate: Int, from: Int = 0): List<Int> {
        val found = mutableListOf<Int>()
        var lastLoud = Int.MIN_VALUE / 2
        for (frame in from until output.size / 2) {
            if (abs(output[frame * 2 + channel]) <= 0.1f) continue
            if (frame - lastLoud > rate / 20) found += frame
            lastLoud = frame
        }
        return found
    }

    @Test
    fun beatMatchedBlendLandsTheBeatsTogether() {
        val outgoingFile = clickTrack("out.wav", 44_100, 40, periodMs = 480, channel = 0)
        val incomingFile = clickTrack("in.wav", 48_000, 40, periodMs = 500, channel = 1)
        val plan = TransitionPlan(
            outgoingMediaId = "out",
            incomingMediaId = "in",
            kind = TransitionKind.BEAT_MATCHED,
            rampStartMs = 24_000L, // click 50
            startMs = 26_880L,     // click 56
            incomingEntryMs = 1_000L, // click 2
            overlapMs = 8_000L,
            outgoingRate = 0.96,   // 480 ms beats stretched to 500 ms
            swapAfterMs = 4_000L,
        )
        val clock = PlaybackClock(44_100)
        val mixer = DeckMixer(FfmpegAudioSource.open(outgoingFile.toURI().toString()), "out", 0L, clock)
        mixer.armedPlan = plan
        val rendered = ArrayList<Float>()
        val block = FloatArray(2_048 * 2)
        var offered = false
        var renderedFrames = 0L
        var reportedAfterSwap = false
        while (true) {
            val frames = mixer.render(block, 2_048)
            if (frames == 0) break
            for (i in 0 until frames * 2) rendered += block[i]
            renderedFrames += frames
            // What screens are told: the same plan all through, also past the swap.
            mixer.blendAt(renderedFrames)?.let { heard ->
                assertEquals(plan, heard.plan)
                assertTrue(heard.elapsedMs in 0L..plan.overlapMs, "elapsed ${heard.elapsedMs}")
                if (heard.elapsedMs > plan.swapAfterMs) reportedAfterSwap = true
            }
            mixer.planNeedingIncoming(12_000L)?.let { wanted ->
                assertEquals(plan, wanted)
                assertTrue(!offered)
                offered = true
                mixer.offerIncoming(wanted, FfmpegAudioSource.open(incomingFile.toURI().toString(), wanted.incomingEntryMs, 44_100, 2))
            }
        }
        mixer.close()
        assertTrue(offered, "the incoming track was never requested")
        assertTrue(reportedAfterSwap, "the blend was not reported past its swap")
        val output = rendered.toFloatArray()
        val rate = 44_100

        // The clock hands over to the incoming track halfway through the blend; that fixes where
        // the blend started, since the swap comes 4 s after it.
        var swapFrame = 0L
        while (clock.at(swapFrame)?.mediaId == "out") swapFrame += 1
        val blendStart = (swapFrame - 4L * rate).toInt()
        val afterSwap = clock.at(swapFrame + rate)!!
        assertEquals("in", afterSwap.mediaId)
        assertTrue(abs(afterSwap.positionMs - 6_000L) <= 25L, "incoming position ${afterSwap.positionMs}")

        // The incoming track keeps its own 500 ms beat, starting on the blend's first frame.
        val beats = List(100) { blendStart + it * rate / 2 }
        val incoming = onsets(output, channel = 1, rate = rate, from = blendStart)
        assertTrue(incoming.size > 30, "incoming clicks: ${incoming.size}")
        for (click in incoming) {
            assertTrue(beats.minOf { abs(it - click) } <= rate * 2 / 1000, "incoming click $click off its beat")
        }

        // Inside the blend, every outgoing click lands within a few milliseconds of an incoming beat.
        val blendEnd = blendStart + 8 * rate
        val outgoingInBlend = onsets(output, channel = 0, rate = rate, from = blendStart - rate / 100)
            .filter { it < blendEnd - rate / 2 }
        assertTrue(outgoingInBlend.size >= 10, "outgoing clicks in the blend: ${outgoingInBlend.size}")
        for (click in outgoingInBlend) {
            val off = beats.minOf { abs(it - click) }
            assertTrue(off <= rate * 12 / 1000, "outgoing click $click is ${off * 1000 / rate} ms off the beat")
        }
        // Before the ramp the outgoing track kept its own 480 ms beat.
        val early = onsets(output, channel = 0, rate = rate).filter { it < 23 * rate }
        early.zipWithNext { a, b -> assertTrue(abs((b - a) - rate * 480 / 1000) <= 2, "outgoing interval ${b - a}") }

        // After the blend only the incoming track sounds.
        val left = (blendEnd + rate until output.size / 2).map { abs(output[it * 2]) }
        assertTrue(left.all { it < 1e-3f }, "outgoing still audible after the blend")
    }

    @Test
    fun aSeekPastTheStartSkipsThePlan() {
        val outgoingFile = clickTrack("out.wav", 44_100, 20, periodMs = 480, channel = 0)
        val plan = TransitionPlan("out", "in", TransitionKind.CROSSFADE, 5_000L, 5_000L, 0L, 4_000L, 1.0, 2_000L)
        val clock = PlaybackClock(44_100)
        val mixer = DeckMixer(FfmpegAudioSource.open(outgoingFile.toURI().toString(), 8_000L), "out", 8_000L, clock)
        mixer.armedPlan = plan
        val block = FloatArray(2_048 * 2)
        var frames = 0L
        while (true) {
            val got = mixer.render(block, 2_048)
            if (got == 0) break
            frames += got
            assertEquals(null, mixer.planNeedingIncoming(12_000L))
        }
        mixer.close()
        assertEquals(null, mixer.blendIncomingMediaId)
        assertTrue(abs(frames - 12L * 44_100) < 44_100 / 10, "rendered $frames frames")
    }
}

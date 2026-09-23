package com.leejlredstar.redefinencm.kmp.transition

import com.leejlredstar.redefinencm.kmp.player.FfmpegAudioSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Decodes a track's two ends with the same FFmpeg the desktop player plays through, resampled by
 * FFmpeg itself to the beat model's 22 050 Hz mono.
 *
 * The head is decoded from the start, so its timeline is exactly the one playback uses. The tail
 * is reached with a decoder seek, over HTTP a range request, so a CDN stream costs the two ends
 * and not the whole file. A seek into an MP3 reports a timestamp that is off from that timeline
 * by the encoder delay, about one MP3 frame, and a beat grid that far off is heard as a flam once
 * two tracks play together. So the decoder first seeks once inside the head it already has, finds
 * by matching samples where it really landed, and moves the tail's start by the same error.
 */
internal class FfmpegTrackEndsDecoder : TrackEndsDecoder {
    override suspend fun decode(url: String, sectionMs: Long, knownDurationMs: Long): DecodedTrackEnds? =
        withContext(Dispatchers.IO) {
            try {
                decodeBlocking(url, sectionMs, knownDurationMs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                System.err.println("FfmpegTrackEndsDecoder: ${failure.javaClass.simpleName}: ${failure.message}")
                null
            }
        }

    private fun decodeBlocking(url: String, sectionMs: Long, knownDurationMs: Long): DecodedTrackEnds {
        FfmpegAudioSource.open(url, 0L, RATE, 1).use { source ->
            val duration = source.durationMs.takeIf { it > 0L } ?: knownDurationMs.takeIf { it > 0L } ?: -1L
            val head = AnalysisWindow(0L, readMs(source, sectionMs))
            if (duration <= 0L) return DecodedTrackEnds(-1L, head, null)
            val tailStart = duration - sectionMs
            if (tailStart <= head.lengthMs) {
                // Short enough that the head already reaches the end: decode the rest and use the
                // whole track for both.
                val rest = readMs(source, duration)
                val whole = AnalysisWindow(0L, head.samples + rest)
                return DecodedTrackEnds(duration, whole, whole)
            }
            val seekErrorMs = measureSeekError(source, head.samples) ?: 0.0
            source.seekTo(tailStart)
            val samples = readMs(source, sectionMs + TAIL_MARGIN_MS)
            val landed = source.landedAtMs ?: tailStart.toDouble()
            return DecodedTrackEnds(duration, head, AnalysisWindow((landed - seekErrorMs).roundToLong(), samples))
        }
    }

    /**
     * How far this stream's seek timestamps are from its decoded-from-the-start timeline, in
     * milliseconds, positive when a seek reports a later time than where its samples really are.
     * Null when the probe lands on something too quiet or too repetitive to place.
     */
    private fun measureSeekError(source: FfmpegAudioSource, head: FloatArray): Double? {
        if (head.size < (PROBE_AT_MS + PROBE_SETTLE_MS + PROBE_LENGTH_MS + SEARCH_MS) * RATE / 1000) return null
        source.seekTo(PROBE_AT_MS)
        // The first frames after a seek can still be warming up the decoder; compare later ones.
        readMs(source, PROBE_SETTLE_MS)
        val landed = source.landedAtMs ?: return null
        val probe = readMs(source, PROBE_LENGTH_MS)
        if (probe.size < PROBE_LENGTH_MS * RATE / 1000) return null
        if (rms(probe) < 1e-3) return null
        val claimed = ((landed + PROBE_SETTLE_MS) * RATE / 1000).roundToLong().toInt()
        val search = (SEARCH_MS * RATE / 1000).toInt()
        var bestShift = 0
        var bestError = Double.MAX_VALUE
        var secondBest = Double.MAX_VALUE
        for (shift in -search..search) {
            val at = claimed + shift
            if (at < 0 || at + probe.size > head.size) continue
            var error = 0.0
            var i = 0
            while (i < probe.size) {
                error += abs(head[at + i] - probe[i])
                i += 2
            }
            if (error < bestError) {
                if (abs(shift - bestShift) > 2) secondBest = bestError
                bestError = error
                bestShift = shift
            } else if (abs(shift - bestShift) > 2 && error < secondBest) {
                secondBest = error
            }
        }
        // A true match is far better than any other alignment; a flat or periodic probe is not.
        if (bestError * 4 > secondBest) return null
        return -bestShift * 1000.0 / RATE
    }

    private fun rms(samples: FloatArray): Double {
        var sum = 0.0
        for (s in samples) sum += s * s
        return sqrt(sum / samples.size)
    }

    private fun readMs(source: FfmpegAudioSource, ms: Long): FloatArray {
        val frames = (ms * RATE / 1_000L).toInt()
        val buffer = FloatArray(frames)
        val got = source.read(buffer, 0, frames)
        return if (got == frames) buffer else buffer.copyOf(got)
    }

    private companion object {
        const val RATE = BeatModelFeatures.SAMPLE_RATE_HZ

        /** A duration from the container can be a little short; read past it to the real end. */
        const val TAIL_MARGIN_MS = 3_000L

        const val PROBE_AT_MS = 20_000L
        const val PROBE_SETTLE_MS = 200L
        const val PROBE_LENGTH_MS = 1_000L
        const val SEARCH_MS = 120L
    }
}

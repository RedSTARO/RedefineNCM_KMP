package com.leejlredstar.redefinencm.kmp.transition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Converts interleaved PCM of any rate and channel count to mono at [targetRate], for analysis.
 *
 * A windowed-sinc low-pass filter, precomputed as a table of 256 fractional phases, is applied
 * once per output sample, so the cost is one short dot product per sample. The beat model only
 * reads up to 11 kHz, and the filter's 32 taps keep what folds back from above the new Nyquist
 * frequency well below that. Platforms whose decoder can resample, such as FFmpeg and
 * `decodeAudioData`, use their own; Android's MediaCodec cannot.
 */
class MonoResampler(private val sourceRate: Int, private val targetRate: Int, private val channels: Int) {
    init {
        require(sourceRate > 0 && targetRate > 0 && channels > 0)
    }

    private val step = sourceRate.toDouble() / targetRate
    private val cutoff = 0.95 * minOf(1.0, targetRate.toDouble() / sourceRate) / 2.0
    private val table = Array(PHASES + 1) { phase ->
        val fraction = phase.toDouble() / PHASES
        DoubleArray(TAPS) { tap ->
            val x = tap - (TAPS / 2 - 1) - fraction
            val sinc = if (abs(x) < 1e-9) 2.0 * cutoff else sin(2.0 * PI * cutoff * x) / (PI * x)
            val window = 0.5 + 0.5 * cos(PI * x / (TAPS / 2))
            sinc * window
        }.let { taps ->
            val sum = taps.sum()
            DoubleArray(TAPS) { taps[it] / sum }
        }
    }

    // Mono history not yet consumed, and the absolute index of history[0].
    private var history = FloatArray(4_096)
    private var historySize = 0
    private var historyBase = 0L
    private var nextOutput = 0L

    /** Feeds [frames] interleaved frames and returns the mono output they complete. */
    fun process(input: FloatArray, frames: Int): FloatArray {
        ensureCapacity(historySize + frames)
        for (f in 0 until frames) {
            var sum = 0f
            val base = f * channels
            for (c in 0 until channels) sum += input[base + c]
            history[historySize + f] = sum / channels
        }
        historySize += frames
        val available = ((historyBase + historySize) / step - nextOutput).toInt().coerceAtLeast(0) + 1
        var out = FloatArray(available)
        var produced = 0
        while (true) {
            val position = nextOutput * step
            val center = floor(position).toLong()
            val first = center - (TAPS / 2 - 1)
            val last = first + TAPS - 1
            if (last >= historyBase + historySize) break
            val fraction = position - center
            val taps = table[(fraction * PHASES).toInt().coerceIn(0, PHASES)]
            var acc = 0.0
            for (t in 0 until TAPS) {
                val index = first + t - historyBase
                if (index >= 0) acc += taps[t] * history[index.toInt()]
            }
            if (produced == out.size) out = out.copyOf(out.size * 2)
            out[produced++] = acc.toFloat()
            nextOutput += 1
        }
        // Keep what the next output still needs.
        val keepFrom = floor(nextOutput * step).toLong() - (TAPS / 2 - 1)
        val drop = (keepFrom - historyBase).coerceIn(0L, historySize.toLong()).toInt()
        if (drop > 0) {
            history.copyInto(history, 0, drop, historySize)
            historySize -= drop
            historyBase += drop
        }
        return out.copyOf(produced)
    }

    private fun ensureCapacity(size: Int) {
        if (size <= history.size) return
        var capacity = history.size
        while (capacity < size) capacity *= 2
        history = history.copyOf(capacity)
    }

    private companion object {
        const val TAPS = 32
        const val PHASES = 256
    }
}

/**
 * Where a seek into a compressed stream really landed. A decoder reports a timestamp for the
 * first frame after a seek, and for an MP3 that timestamp is off from the decoded-from-the-start
 * timeline by the encoder delay, about one frame. A beat grid that far off is heard as a flam
 * once two tracks play together.
 *
 * [head] holds samples decoded from the start of the track; [probe] holds samples decoded after
 * a seek whose reported start is [claimedStartMs]. The result is the seek's error in milliseconds,
 * positive when the seek reports a later time than where its samples are, so the true start of
 * any later seek is `reported - error`. Null when the probe is too quiet or too repetitive to
 * place with confidence.
 */
fun measureSeekErrorMs(
    head: FloatArray,
    probe: FloatArray,
    claimedStartMs: Double,
    sampleRate: Int,
    searchMs: Double = 120.0,
): Double? {
    if (probe.isEmpty()) return null
    var energy = 0.0
    for (s in probe) energy += s * s
    if (sqrt(energy / probe.size) < 1e-3) return null
    val claimed = (claimedStartMs * sampleRate / 1_000.0).roundToLong().toInt()
    val search = (searchMs * sampleRate / 1_000.0).toInt()
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
    if (bestError == Double.MAX_VALUE) return null
    // A true match is far better than any other alignment; a flat or periodic probe is not.
    if (bestError * 4 >= secondBest) return null
    return -bestShift * 1_000.0 / sampleRate
}

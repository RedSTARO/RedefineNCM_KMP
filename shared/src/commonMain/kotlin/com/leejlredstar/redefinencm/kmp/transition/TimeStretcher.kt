package com.leejlredstar.redefinencm.kmp.transition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Changes tempo without changing pitch, for interleaved float PCM: waveform-similarity
 * overlap-add (WSOLA).
 *
 * Output is built from Hann-windowed frames of about 46 ms, laid down every half frame. Each
 * frame is read from the input at the position the rate calls for, moved by up to a quarter
 * frame to where it best continues the frame before it. At rate 1 the best continuation is the
 * input itself, so the stretcher reproduces its input sample for sample once primed. That is what
 * lets a player route a track through it only for the seconds a blend needs, without a seam
 * where it starts: [prime] it with the frames just played, and its output continues them exactly.
 *
 * Only the tempo range a beat-matched blend uses (about ±6 %) is a design goal. Wider ratios
 * work but smear transients.
 *
 * Not thread-safe; one instance per deck.
 */
class TimeStretcher(val channels: Int, sampleRate: Int) {
    init {
        require(channels > 0 && sampleRate > 0)
    }

    /** Input frames consumed per output frame; takes effect from the next frame laid down. */
    var rate: Double = 1.0
        set(value) {
            require(value > 0.25 && value < 4.0) { "rate out of range: $value" }
            field = value
        }

    /** The analysis frame, about 46 ms. Also how many frames [prime] should be given. */
    val frameSize: Int = evenAtLeast(sampleRate * 46 / 1000, 64)
    private val hop: Int = frameSize / 2
    private val tolerance: Int = frameSize / 4
    private val window = FloatArray(frameSize) { (0.5 - 0.5 * cos(2.0 * PI * it / frameSize)).toFloat() }

    // Input frames not yet discarded; inputBase is the absolute index of input[0].
    private var input = FloatArray(frameSize * 8 * channels)
    private var inputFrames = 0
    private var inputBase = 0L
    private var ended = false

    // Absolute position the next frame should nominally be read from, and where the last was.
    private var nominal = 0.0
    private var previous = -1L

    private val overlap = FloatArray(frameSize * channels)
    private val ready = FloatArray(hop * channels)
    private var readyOffset = hop
    private var discard = 0
    private val monoReference = FloatArray(frameSize)
    private val monoCandidate = FloatArray(frameSize + 2 * tolerance)

    /**
     * Feeds [frames] frames that were already played at rate 1 and swallows the output they
     * produce, so the next output frame continues them without a seam. Call once, before any
     * other input; [frames] should be at least [frameSize].
     */
    fun prime(history: FloatArray, frames: Int) {
        check(inputBase == 0L && inputFrames == 0 && previous < 0) { "prime() must come first" }
        write(history, frames)
        discard = frames
    }

    /** Appends [frames] interleaved frames from [source]. */
    fun write(source: FloatArray, frames: Int) {
        ensureCapacity(inputFrames + frames)
        source.copyInto(input, inputFrames * channels, 0, frames * channels)
        inputFrames += frames
    }

    /** Marks the input finished, so the remaining frames are flushed out. */
    fun endOfInput() {
        ended = true
    }

    /**
     * Writes up to [frames] output frames into [target]; returns how many were written. Fewer
     * than asked means more input is needed, or, after [endOfInput], that the output is done.
     */
    fun read(target: FloatArray, frames: Int): Int {
        var written = 0
        while (written < frames) {
            if (readyOffset >= hop) {
                if (!layDownFrame()) break
                readyOffset = 0
            }
            if (discard > 0) {
                val skip = minOf(discard, hop - readyOffset)
                readyOffset += skip
                discard -= skip
                continue
            }
            val take = minOf(frames - written, hop - readyOffset)
            ready.copyInto(target, written * channels, readyOffset * channels, (readyOffset + take) * channels)
            readyOffset += take
            written += take
        }
        return written
    }

    /** Frames of input a [read] of [outputFrames] wants beyond what has been written. */
    fun inputFramesWanted(outputFrames: Int): Int {
        val horizon = floor(nominal + (outputFrames + discard + hop) * maxOf(rate, 1.0)).toLong() +
            frameSize + tolerance + hop
        return (horizon - (inputBase + inputFrames)).coerceAtLeast(0L).toInt()
    }

    private fun layDownFrame(): Boolean {
        val target = floor(nominal).toLong()
        val available = inputBase + inputFrames
        val searchStart = maxOf(target - tolerance, inputBase)
        val searchEnd = target + tolerance
        val natural = previous + hop
        if (!ended) {
            val needed = maxOf(searchEnd, if (previous < 0) target else natural) + frameSize
            if (needed > available) return false
        } else if (target >= available) {
            return false
        }

        val chosen = if (previous < 0 || natural + frameSize > available) {
            target
        } else {
            bestOffset(natural, target, searchStart, searchEnd)
        }
        for (i in 0 until frameSize) {
            val source = chosen + i - inputBase
            if (source < 0 || source >= inputFrames) continue
            val w = window[i]
            val o = i * channels
            val s = source.toInt() * channels
            for (c in 0 until channels) overlap[o + c] += w * input[s + c]
        }
        overlap.copyInto(ready, 0, 0, hop * channels)
        overlap.copyInto(overlap, 0, hop * channels, frameSize * channels)
        overlap.fill(0f, (frameSize - hop) * channels, frameSize * channels)
        previous = chosen
        nominal += hop * rate
        discardBefore(minOf(previous + hop, floor(nominal).toLong() - tolerance))
        return true
    }

    /**
     * The start in [from]..[until] whose frame best continues the input at [natural], by
     * normalised correlation: a coarse pass over every second offset and every fourth sample,
     * then an exact pass around its winner. [target] wins ties, so at rate 1 nothing moves.
     */
    private fun bestOffset(natural: Long, target: Long, from: Long, until: Long): Long {
        mono(natural, frameSize, monoReference)
        val span = (until - from).toInt()
        mono(from, span + frameSize, monoCandidate)
        val targetOffset = (target - from).toInt().coerceIn(0, span)
        var best = targetOffset
        var bestScore = score(targetOffset, 4)
        var offset = 0
        while (offset <= span) {
            val s = score(offset, 4)
            if (s > bestScore + 1e-9 * abs(bestScore)) {
                bestScore = s
                best = offset
            }
            offset += 2
        }
        if (best != targetOffset) {
            var refined = best
            var refinedScore = score(best, 1)
            for (candidate in maxOf(0, best - 2)..minOf(span, best + 2)) {
                val s = score(candidate, 1)
                if (s > refinedScore + 1e-9 * abs(refinedScore)) {
                    refinedScore = s
                    refined = candidate
                }
            }
            // A refined winner that is no better than staying put exactly loses to it.
            if (refinedScore <= score(targetOffset, 1) + 1e-9) refined = targetOffset
            best = refined
        }
        return from + best
    }

    private fun score(offset: Int, step: Int): Double {
        var dot = 0.0
        var energy = 1e-12
        var i = 0
        while (i < frameSize) {
            val c = monoCandidate[offset + i]
            dot += monoReference[i] * c
            energy += c * c
            i += step
        }
        return dot / sqrt(energy)
    }

    private fun mono(start: Long, length: Int, target: FloatArray) {
        for (i in 0 until length) {
            val source = start + i - inputBase
            if (source < 0 || source >= inputFrames) {
                target[i] = 0f
                continue
            }
            val s = source.toInt() * channels
            var sum = 0f
            for (c in 0 until channels) sum += input[s + c]
            target[i] = sum
        }
    }

    private fun discardBefore(absolute: Long) {
        val drop = (absolute - inputBase).coerceIn(0L, inputFrames.toLong()).toInt()
        if (drop == 0) return
        input.copyInto(input, 0, drop * channels, inputFrames * channels)
        inputFrames -= drop
        inputBase += drop
    }

    private fun ensureCapacity(frames: Int) {
        if (frames * channels <= input.size) return
        var size = input.size
        while (size < frames * channels) size *= 2
        input = input.copyOf(size)
    }

    private companion object {
        fun evenAtLeast(value: Int, minimum: Int): Int = maxOf(value, minimum).let { it - it % 2 }
    }
}

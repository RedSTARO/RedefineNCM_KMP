package com.leejlredstar.redefinencm.kmp.transition

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * A constant-tempo grid fitted to one section's detected beats: beat `k` falls at
 * `anchorMs + k * periodMs`, and every [beatsPerBar]th beat starting at [downbeatIndex] is a
 * downbeat.
 *
 * A grid rather than the raw beat list, because beat matching needs to extrapolate — where the
 * next downbeat will be, how far two tracks drift over eight bars — and a raw list answers
 * neither. Songs whose tempo drifts inside the section fit badly and get a low [confidence],
 * which keeps them out of beat matching instead of mismatching them.
 */
@Serializable
data class BeatGrid(
    val anchorMs: Double,
    val periodMs: Double,
    val beatsPerBar: Int,
    val downbeatIndex: Int,
    /** 0..1: share of the section's expected beats that sit on the grid. */
    val confidence: Double,
    /** 0..1: share of detected downbeats that agree with [downbeatIndex]. */
    val downbeatConfidence: Double,
) {
    val bpm: Double get() = 60_000.0 / periodMs

    fun beatTimeMs(index: Long): Double = anchorMs + index * periodMs

    fun isDownbeat(index: Long): Boolean =
        floorModLong(index - downbeatIndex, beatsPerBar.toLong()) == 0L

    /** The downbeat at or after [timeMs]. */
    fun downbeatAtOrAfterMs(timeMs: Double): Double {
        var index = kotlin.math.ceil((timeMs - anchorMs) / periodMs - 1e-6).toLong()
        while (!isDownbeat(index)) index += 1
        return beatTimeMs(index)
    }

    /** The downbeat at or before [timeMs]. */
    fun downbeatAtOrBeforeMs(timeMs: Double): Double {
        var index = kotlin.math.floor((timeMs - anchorMs) / periodMs + 1e-6).toLong()
        while (!isDownbeat(index)) index -= 1
        return beatTimeMs(index)
    }

    val barMs: Double get() = periodMs * beatsPerBar
}

private fun floorModLong(x: Long, y: Long): Long = ((x % y) + y) % y

/**
 * Fits a [BeatGrid] to [events], whose times are seconds from [sectionStartMs]; the grid is in
 * track milliseconds. Returns null when there are too few beats to call it a tempo.
 *
 * The period starts from the median inter-beat interval, beats are then numbered against it
 * and a least-squares line through (number, time) refines anchor and period together; that is
 * repeated with outliers (more than 40 ms off the line) dropped, which is what keeps one
 * doubled or missed beat from bending a whole section's tempo.
 */
fun fitBeatGrid(events: BeatEvents, sectionStartMs: Long, sectionLengthMs: Long): BeatGrid? {
    val beats = events.beats.map { sectionStartMs + it * 1000.0 }
    if (beats.size < MIN_BEATS) return null
    val intervals = beats.zipWithNext { a, b -> b - a }.sorted()
    var period = intervals[intervals.size / 2]
    if (period !in MIN_PERIOD_MS..MAX_PERIOD_MS) return null
    var anchor = beats.first()
    var inliers = beats
    repeat(3) {
        val indexed = inliers.map { t -> ((t - anchor) / period).roundToLong() to t }
        val fit = leastSquares(indexed) ?: return null
        anchor = fit.first
        period = fit.second
        inliers = beats.filter { t ->
            val k = ((t - anchor) / period).roundToLong()
            abs(t - (anchor + k * period)) <= INLIER_TOLERANCE_MS
        }
        if (inliers.size < MIN_BEATS) return null
    }
    if (period !in MIN_PERIOD_MS..MAX_PERIOD_MS) return null
    // Keep the anchor near the section so beatTimeMs() stays well-conditioned.
    val shift = ((sectionStartMs - anchor) / period).roundToLong()
    anchor += shift * period

    val expected = (sectionLengthMs / period).coerceAtLeast(1.0)
    val residuals = inliers.map { t ->
        val k = ((t - anchor) / period).roundToLong()
        t - (anchor + k * period)
    }
    val rms = sqrt(residuals.sumOf { it * it } / residuals.size)
    val coverage = (inliers.size / expected).coerceIn(0.0, 1.0)
    val confidence = (coverage * (1.0 - rms / INLIER_TOLERANCE_MS)).coerceIn(0.0, 1.0)

    val downbeatIndices = events.downbeats.map { d ->
        (((sectionStartMs + d * 1000.0) - anchor) / period).roundToLong()
    }
    var bestMeter = 4
    var bestPhase = 0
    var bestShare = 0.0
    if (downbeatIndices.isNotEmpty()) {
        for (meter in intArrayOf(4, 3)) {
            val counts = IntArray(meter)
            downbeatIndices.forEach { counts[floorModLong(it, meter.toLong()).toInt()] += 1 }
            val phase = counts.indices.maxBy { counts[it] }
            val share = counts[phase].toDouble() / downbeatIndices.size
            // Four wins a tie: it is by far the commoner meter, and a three-beat bar placed
            // on a 4/4 song puts every other transition off the downbeat.
            if (share > bestShare + 0.05 || (meter == 4 && share >= bestShare)) {
                bestMeter = meter
                bestPhase = phase
                bestShare = share
            }
        }
    }
    return BeatGrid(
        anchorMs = anchor,
        periodMs = period,
        beatsPerBar = bestMeter,
        downbeatIndex = bestPhase,
        confidence = confidence,
        downbeatConfidence = bestShare,
    )
}

private fun leastSquares(points: List<Pair<Long, Double>>): Pair<Double, Double>? {
    val n = points.size.toDouble()
    if (n < 2) return null
    val meanK = points.sumOf { it.first.toDouble() } / n
    val meanT = points.sumOf { it.second } / n
    var sxx = 0.0
    var sxy = 0.0
    for ((k, t) in points) {
        val dk = k - meanK
        sxx += dk * dk
        sxy += dk * (t - meanT)
    }
    if (sxx <= 0.0) return null
    val slope = sxy / sxx
    return (meanT - slope * meanK) to slope
}

/**
 * The playback rate that gives [outgoing] the beat period of [incoming], folding half and double
 * time, or null when no fold brings them within [TEMPO_OCTAVE_TOLERANCE] of each other.
 */
fun tempoRatio(outgoing: BeatGrid, incoming: BeatGrid): Double? {
    val raw = outgoing.periodMs / incoming.periodMs
    for (octave in doubleArrayOf(1.0, 2.0, 0.5)) {
        val ratio = raw * octave
        if (abs(ratio - 1.0) <= TEMPO_OCTAVE_TOLERANCE) return ratio
    }
    return null
}

private const val MIN_BEATS = 8
private const val MIN_PERIOD_MS = 240.0   // 250 BPM
private const val MAX_PERIOD_MS = 1_200.0 // 50 BPM
private const val INLIER_TOLERANCE_MS = 40.0
private const val TEMPO_OCTAVE_TOLERANCE = 0.3

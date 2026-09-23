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
 * A grid rather than the raw beat list, because beat matching needs to extrapolate: where the
 * next downbeat will be, and how far two tracks drift over eight bars. A raw list answers
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
 * Fits a [BeatGrid] to the beats of [events] that fall between [fromMs] and [untilMs]; event
 * times are seconds from [sectionStartMs], the grid is in track milliseconds. Returns null when
 * there are too few beats there to call it a tempo.
 *
 * Beats come quantised to the model's 20 ms frames, so a 469 ms beat is detected as 460 and
 * 480 alternately, and the median interval alone can be 2 % off. Numbering a whole section
 * against a period that far off slips a beat halfway through, and no least-squares line survives
 * the slip. So the fit starts from the eight beats at [growFrom]'s end of the region, where a 2 %
 * error cannot slip, and doubles the span it numbers and refits until it covers the region. At
 * every step beats more than 40 ms off the line are left out, which is what keeps a doubled or
 * missed beat from bending the tempo.
 *
 * [confidence][BeatGrid.confidence] is the share of the region's expected beats that sit on the
 * grid. Only the region is scored because only the region is blended: a song whose tempo
 * changes after its first thirty seconds still has a usable grid for handing over into it.
 */
fun fitBeatGrid(
    events: BeatEvents,
    sectionStartMs: Long,
    fromMs: Long,
    untilMs: Long,
    growFrom: GridEdge = GridEdge.START,
): BeatGrid? {
    val beats = events.beats.map { sectionStartMs + it * 1000.0 }.filter { it >= fromMs && it <= untilMs }
    if (beats.size < MIN_BEATS || untilMs <= fromMs) return null
    val intervals = beats.zipWithNext { a, b -> b - a }.filter { it in MIN_PERIOD_MS..MAX_PERIOD_MS }.sorted()
    if (intervals.isEmpty()) return null
    var period = intervals[intervals.size / 2]
    val ordered = if (growFrom == GridEdge.START) beats else beats.asReversed()
    var anchor = ordered.first()
    var span = SEED_BEATS
    var inliers: List<Double>
    while (true) {
        val window = ordered.take(span)
        val numbered = window.map { t -> ((t - anchor) / period).roundToLong() to t }
            .filter { (k, t) -> abs(t - (anchor + k * period)) <= INLIER_TOLERANCE_MS || span == SEED_BEATS }
        val fit = leastSquares(numbered) ?: return null
        anchor = fit.first
        period = fit.second
        if (period !in MIN_PERIOD_MS..MAX_PERIOD_MS) return null
        if (span >= ordered.size) break
        span = minOf(ordered.size, span * 2)
    }
    // A last pass over every beat in the region with the refined grid.
    inliers = beats.filter { t ->
        val k = ((t - anchor) / period).roundToLong()
        abs(t - (anchor + k * period)) <= INLIER_TOLERANCE_MS
    }
    if (inliers.size < MIN_BEATS) return null
    leastSquares(inliers.map { t -> ((t - anchor) / period).roundToLong() to t })?.let { (a, p) ->
        anchor = a
        period = p
    }
    if (period !in MIN_PERIOD_MS..MAX_PERIOD_MS) return null
    // Keep the anchor near the region so beatTimeMs() stays well-conditioned.
    val shift = ((fromMs - anchor) / period).roundToLong()
    anchor += shift * period

    val expected = ((untilMs - fromMs) / period).coerceAtLeast(1.0)
    val residuals = inliers.map { t ->
        val k = ((t - anchor) / period).roundToLong()
        t - (anchor + k * period)
    }
    val rms = sqrt(residuals.sumOf { it * it } / residuals.size)
    val coverage = (inliers.size / expected).coerceIn(0.0, 1.0)
    val confidence = (coverage * (1.0 - rms / INLIER_TOLERANCE_MS)).coerceIn(0.0, 1.0)

    val downbeatIndices = events.downbeats
        .map { sectionStartMs + it * 1000.0 }
        .filter { it >= fromMs && it <= untilMs }
        .map { d -> ((d - anchor) / period).roundToLong() }
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

/** Which end of a region a grid fit grows from: the end a transition happens at. */
enum class GridEdge { START, END }

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
private const val SEED_BEATS = 8
private const val MIN_PERIOD_MS = 240.0   // 250 BPM
private const val MAX_PERIOD_MS = 1_200.0 // 50 BPM
private const val INLIER_TOLERANCE_MS = 40.0
private const val TEMPO_OCTAVE_TOLERANCE = 0.3

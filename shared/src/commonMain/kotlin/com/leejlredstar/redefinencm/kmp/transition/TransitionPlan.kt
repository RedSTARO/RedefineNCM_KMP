package com.leejlredstar.redefinencm.kmp.transition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** What a platform player can do with a planned transition. */
enum class TransitionCapability {
    /** Plays one track at a time; transitions are ignored. */
    NONE,

    /** Can overlap two tracks with independent volume. */
    CROSSFADE,

    /** Can also change the outgoing track's tempo without changing its pitch. */
    TEMPO_MATCHED,
}

enum class TransitionKind {
    /** Beats and downbeats aligned, the outgoing track time-stretched onto the incoming tempo. */
    BEAT_MATCHED,

    /** An equal-power blend, placed by loudness and, when known, on a downbeat. */
    CROSSFADE,
}

/**
 * One planned hand-over from [outgoingMediaId] to [incomingMediaId].
 *
 * Positions ending in `Ms` without "wall" are media time in their own track. The blend starts
 * when the outgoing track reaches [startMs]: the incoming track starts at [incomingEntryMs] and
 * plays at its own speed, while the outgoing one keeps the [outgoingRate] it ramped to between
 * [rampStartMs] and [startMs]. [overlapMs] is wall-clock time; after [swapAfterMs] of it the
 * incoming track is the current one everywhere the app shows playback, and after [overlapMs]
 * the outgoing track has faded to silence and stops.
 *
 * The incoming track is never time-stretched. It becomes the current track halfway through, and
 * a current track playing at its own speed keeps its position, its lyrics and the playback
 * report exactly as they are without a blend.
 */
data class TransitionPlan(
    val outgoingMediaId: String,
    val incomingMediaId: String,
    val kind: TransitionKind,
    val rampStartMs: Long,
    val startMs: Long,
    val incomingEntryMs: Long,
    val overlapMs: Long,
    val outgoingRate: Double,
    val swapAfterMs: Long,
) {
    init {
        require(rampStartMs <= startMs) { "ramp must precede the blend" }
        require(overlapMs > 0L && swapAfterMs in 0L..overlapMs)
        require(outgoingRate > 0.5 && outgoingRate < 2.0)
    }

    /** Where the outgoing track is when it falls silent. */
    val outgoingEndMs: Long get() = startMs + (overlapMs * outgoingRate).toLong()

    /** The outgoing track's rate at media position [positionMs]; 1 before the ramp. */
    fun outgoingRateAt(positionMs: Long): Double = when {
        positionMs >= startMs -> outgoingRate
        positionMs <= rampStartMs -> 1.0
        else -> 1.0 + (outgoingRate - 1.0) * (positionMs - rampStartMs) / (startMs - rampStartMs).toDouble()
    }

    /** Gains of the outgoing and incoming tracks [elapsedMs] into the blend, equal-power. */
    fun gainsAt(elapsedMs: Long): BlendGains {
        val x = (elapsedMs.toDouble() / overlapMs).coerceIn(0.0, 1.0)
        return BlendGains(outgoing = cos(x * PI / 2).toFloat(), incoming = sin(x * PI / 2).toFloat())
    }

    /** Whether a ramp is worth performing at all; a rate within 0.2 % of 1 is inaudible. */
    val changesTempo: Boolean get() = abs(outgoingRate - 1.0) >= 0.002
}

data class BlendGains(val outgoing: Float, val incoming: Float)

/** Why a plan came out the way it did, kept for the diagnostics line in Settings. */
data class PlannedTransition(val plan: TransitionPlan?, val note: String)

/**
 * Chooses where and how two tracks hand over. Pure: the same inputs give the same plan, which
 * is what lets it be tested without audio.
 */
object TransitionPlanner {
    const val MIN_TRACK_MS: Long = 30_000L
    const val MAX_TEMPO_CHANGE: Double = 0.06
    private const val MIN_GRID_CONFIDENCE = 0.5
    private const val MIN_DOWNBEAT_CONFIDENCE = 0.4
    private const val MIN_KEY_STRENGTH = 0.45
    private const val MIN_BLEND_MS = 4_000.0
    private const val MAX_BLEND_MS = 24_000.0
    private const val GAPLESS_EDGE_MS = 400L

    fun plan(
        outgoingMediaId: String,
        outgoingAlbum: String,
        outgoingDurationMs: Long,
        outgoing: TrackAnalysis?,
        incomingMediaId: String,
        incomingAlbum: String,
        incomingDurationMs: Long,
        incoming: TrackAnalysis?,
        mode: SongTransitionMode,
        crossfadeSeconds: Long,
        capability: TransitionCapability,
    ): PlannedTransition {
        if (mode == SongTransitionMode.OFF || capability == TransitionCapability.NONE) {
            return PlannedTransition(null, "off")
        }
        if (outgoingDurationMs < MIN_TRACK_MS) return PlannedTransition(null, "outgoing too short")
        if (incomingDurationMs in 1 until MIN_TRACK_MS) return PlannedTransition(null, "incoming too short")

        if (mode == SongTransitionMode.CROSSFADE) {
            val overlap = limitOverlap(
                normalizeCrossfadeSeconds(crossfadeSeconds) * 1000L,
                outgoingDurationMs,
                incomingDurationMs,
            )
            val start = outgoingDurationMs - overlap
            return PlannedTransition(
                crossfade(outgoingMediaId, incomingMediaId, start, 0L, overlap),
                "crossfade ${overlap}ms",
            )
        }

        val tail = outgoing?.tail
        val head = incoming?.head
        val outEnd = tail?.energy?.lastAudibleEndMs()?.coerceAtMost(outgoingDurationMs) ?: outgoingDurationMs
        val inStart = head?.energy?.firstAudibleMs() ?: 0L
        val fadeStart = tail?.energy?.fadeOutStartMs()

        // An album that plays straight through (a live record, a concept album) is heard as one
        // piece; blending its tracks would cut into it. Same album, no silence either side, no
        // fade: leave the hand-over to the player's own gapless advance.
        if (tail != null && head != null &&
            outgoingAlbum.isNotBlank() && outgoingAlbum == incomingAlbum &&
            outgoingDurationMs - outEnd < GAPLESS_EDGE_MS && inStart < GAPLESS_EDGE_MS && fadeStart == null
        ) {
            return PlannedTransition(null, "continuous album")
        }

        if (capability == TransitionCapability.TEMPO_MATCHED) {
            beatMatched(outgoingMediaId, incomingMediaId, tail, head, outEnd, inStart, incomingDurationMs)
                ?.let { return PlannedTransition(it, "beat matched") }
        }

        val keysClash = keysClash(tail?.key, head?.key)
        val wantedOverlap = when {
            fadeStart != null -> (outEnd - fadeStart).coerceIn(3_000L, 10_000L)
            keysClash -> 4_000L
            else -> 6_000L
        }
        val overlap = limitOverlap(wantedOverlap, outgoingDurationMs, incomingDurationMs)
        var start = (outEnd - overlap).coerceAtLeast(0L)
        val outGrid = tail?.grid?.takeIf { it.confidence >= MIN_GRID_CONFIDENCE }
        if (outGrid != null) {
            val onBeat = outGrid.downbeatAtOrBeforeMs(start.toDouble()).toLong()
            if (start - onBeat <= outGrid.barMs && onBeat >= tail.startMs) start = onBeat
        }
        var entry = inStart
        val inGrid = head?.grid?.takeIf { it.confidence >= MIN_GRID_CONFIDENCE }
        if (inGrid != null) {
            val onBeat = inGrid.downbeatAtOrAfterMs(inStart.toDouble()).toLong()
            if (onBeat - inStart <= 1_000L) entry = onBeat
        }
        return PlannedTransition(
            crossfade(outgoingMediaId, incomingMediaId, start, entry, overlap),
            if (keysClash) "short crossfade, keys clash" else "crossfade",
        )
    }

    private fun beatMatched(
        outgoingMediaId: String,
        incomingMediaId: String,
        tail: SectionAnalysis?,
        head: SectionAnalysis?,
        outEnd: Long,
        inStart: Long,
        incomingDurationMs: Long,
    ): TransitionPlan? {
        val outGrid = tail?.grid ?: return null
        val inGrid = head?.grid ?: return null
        if (outGrid.confidence < MIN_GRID_CONFIDENCE || inGrid.confidence < MIN_GRID_CONFIDENCE) return null
        if (outGrid.downbeatConfidence < MIN_DOWNBEAT_CONFIDENCE ||
            inGrid.downbeatConfidence < MIN_DOWNBEAT_CONFIDENCE
        ) return null
        val rate = tempoRatio(outGrid, inGrid) ?: return null
        if (abs(rate - 1.0) > MAX_TEMPO_CHANGE) return null

        val compatible = !keysClash(tail.key, head.key)
        var bars = if (compatible) 8 else 4
        val barWallMs = inGrid.barMs
        while (bars > 2 && bars * barWallMs > MAX_BLEND_MS) bars /= 2
        while (bars < 16 && bars * barWallMs < MIN_BLEND_MS) bars *= 2

        val entry = inGrid.downbeatAtOrAfterMs(inStart - inGrid.periodMs * 0.25).toLong().coerceAtLeast(0L)
        if (incomingDurationMs > 0 && entry > incomingDurationMs / 3) return null
        while (bars >= 2) {
            val overlapWall = bars * barWallMs
            val start = outGrid.downbeatAtOrBeforeMs(outEnd - overlapWall * rate).toLong()
            val rampStart = (start - 2 * outGrid.barMs).toLong()
            val fitsOutgoing = rampStart >= tail.startMs && start >= 0L
            val fitsIncoming = entry + overlapWall <= head.endMs
            if (fitsOutgoing && fitsIncoming) {
                return TransitionPlan(
                    outgoingMediaId = outgoingMediaId,
                    incomingMediaId = incomingMediaId,
                    kind = TransitionKind.BEAT_MATCHED,
                    rampStartMs = if (abs(rate - 1.0) < 0.002) start else rampStart,
                    startMs = start,
                    incomingEntryMs = entry,
                    overlapMs = overlapWall.toLong(),
                    outgoingRate = if (abs(rate - 1.0) < 0.002) 1.0 else rate,
                    swapAfterMs = (overlapWall / 2).toLong(),
                )
            }
            bars /= 2
        }
        return null
    }

    private fun keysClash(a: MusicalKey?, b: MusicalKey?): Boolean {
        if (a == null || b == null) return false
        if (a.strength < MIN_KEY_STRENGTH || b.strength < MIN_KEY_STRENGTH) return false
        return camelotDistance(a, b) > 1
    }

    private fun limitOverlap(wanted: Long, outgoingDurationMs: Long, incomingDurationMs: Long): Long {
        var limit = outgoingDurationMs / 4
        if (incomingDurationMs > 0) limit = minOf(limit, incomingDurationMs / 4)
        return wanted.coerceAtMost(limit).coerceAtLeast(500L)
    }

    private fun crossfade(
        outgoingMediaId: String,
        incomingMediaId: String,
        start: Long,
        entry: Long,
        overlap: Long,
    ) = TransitionPlan(
        outgoingMediaId = outgoingMediaId,
        incomingMediaId = incomingMediaId,
        kind = TransitionKind.CROSSFADE,
        rampStartMs = start,
        startMs = start,
        incomingEntryMs = entry,
        overlapMs = overlap,
        outgoingRate = 1.0,
        swapAfterMs = overlap / 2,
    )
}

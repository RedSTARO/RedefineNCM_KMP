package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.transition.TimeStretcher
import com.leejlredstar.redefinencm.kmp.transition.TransitionPlan
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Which track the frames leaving the line belong to, and where in it they are.
 *
 * The desktop player used to derive position from the wall clock since the line started, which
 * cannot follow a track whose tempo is being stretched or a second track that takes over halfway
 * through a blend. The mixer instead marks, in output frames, every point where the mapping
 * changes; the player asks with the line's own count of frames played.
 */
internal class PlaybackClock(private val sampleRate: Int) {
    private class Mark(val outputFrame: Long, val mediaId: String, val sourceMs: Double, val msPerFrame: Double)

    private val marks = ArrayDeque<Mark>()

    @Synchronized
    fun mark(outputFrame: Long, mediaId: String, sourceMs: Double, rate: Double) {
        while (marks.isNotEmpty() && marks.last().outputFrame >= outputFrame) marks.removeLast()
        marks.addLast(Mark(outputFrame, mediaId, sourceMs, rate * 1_000.0 / sampleRate))
    }

    /** The track and position [playedFrames] into the output, or null before the first mark. */
    @Synchronized
    fun at(playedFrames: Long): ClockReading? {
        while (marks.size > 1 && marks[1].outputFrame <= playedFrames) marks.removeFirst()
        val mark = marks.firstOrNull() ?: return null
        val frames = (playedFrames - mark.outputFrame).coerceAtLeast(0L)
        return ClockReading(mark.mediaId, (mark.sourceMs + frames * mark.msPerFrame).roundToLong())
    }
}

internal data class ClockReading(val mediaId: String, val positionMs: Long)

/**
 * Renders the desktop player's output: one track, or two during a song transition.
 *
 * It owns the decoders from the moment the player hands them over, and closes them. Everything
 * that must be exact to the sample happens here, in output frames:
 *
 * - the tempo ramp and blend start are placed on the outgoing track's own timeline;
 * - the incoming track starts in the same output frame the blend starts;
 * - the swap point and the blend's end are fixed in output frames when the blend starts.
 *
 * The player thread only arms plans, offers the prepared incoming decoder, and asks for a blend
 * to be abandoned; those arrive through volatile fields and take effect at the next render.
 */
internal class DeckMixer(
    source: FfmpegAudioSource,
    mediaId: String,
    startMs: Long,
    private val clock: PlaybackClock,
) : AutoCloseable {
    val channels: Int = source.channels
    val sampleRate: Int = source.sampleRate

    private class Deck(val source: FfmpegAudioSource, val mediaId: String, startMs: Long, sampleRate: Int) {
        /** Nominal position in source frames; fractional while the deck is time-stretched. */
        var sourceFrame: Double = startMs * sampleRate / 1_000.0
        var ended = false
    }

    @Volatile private var primary = Deck(source, mediaId, startMs, sampleRate)
    private var stretcher: TimeStretcher? = null
    private var outputFrames = 0L

    // The last frames the primary deck produced at rate 1, to prime a stretcher without a seam.
    private val historyFrames = TimeStretcher(channels, sampleRate).frameSize
    private val history = FloatArray(historyFrames * channels)
    private var historyFilled = 0

    /** Written by the player thread; read at every render. */
    @Volatile var armedPlan: TransitionPlan? = null

    @Volatile private var offered: Pair<TransitionPlan, FfmpegAudioSource>? = null

    @Volatile private var abandonKeeping: String? = null

    private class Blend(
        val plan: TransitionPlan,
        val incoming: Deck,
        val startFrame: Long,
        val swapFrame: Long,
        val endFrame: Long,
    ) {
        var swapped = false
        // Set when the blend is abandoned: both gains move linearly to the kept deck over these
        // frames, from where they were, so dropping a track never clicks.
        var fadeFrom: Pair<Float, Float>? = null
        var fadeStartFrame = 0L
        var keepIncoming = false
    }

    /** A blend's plan, and [elapsedMs] of it heard. */
    class HeardBlend(val plan: TransitionPlan, val elapsedMs: Long)

    @Volatile private var blend: Blend? = null
    private var blendAttempted: TransitionPlan? = null
    private var mixBuffer = FloatArray(0)

    val currentMediaId: String get() = primary.mediaId

    /** The track a blend in progress is bringing in, or null outside a blend. */
    val blendIncomingMediaId: String? get() = blend?.incoming?.mediaId

    /**
     * The blend the listener hears at [outputFrame], and how far into it that frame is. Null outside
     * a blend, before its first frame is heard, and while an abandoned one fades out. The plan is
     * kept here, with the blend, because the player drops its armed plan at the swap, halfway.
     */
    fun blendAt(outputFrame: Long): HeardBlend? {
        val active = blend ?: return null
        if (active.fadeFrom != null || outputFrame < active.startFrame) return null
        val frames = (outputFrame - active.startFrame).coerceAtMost(active.endFrame - active.startFrame)
        return HeardBlend(active.plan, frames * 1_000L / sampleRate)
    }

    /** The decoder-reported length of the track the listener is hearing as current. */
    val audibleDurationMs: Long
        get() = blend?.takeIf { it.swapped }?.incoming?.source?.durationMs ?: primary.source.durationMs

    /** The media id the output belongs to right now, as far as the user is concerned. */
    val audibleMediaId: String get() = blend?.takeIf { it.swapped }?.incoming?.mediaId ?: primary.mediaId

    init {
        clock.mark(0L, mediaId, startMs.toDouble(), 1.0)
    }

    /** The plan whose incoming track should be opened now, or null. Polled after each render. */
    fun planNeedingIncoming(leadMs: Long): TransitionPlan? {
        if (blend != null) return null
        val plan = armedPlan?.takeIf { it.outgoingMediaId == primary.mediaId } ?: return null
        if (offered?.first == plan || blendAttempted == plan) return null
        return if (positionMs(primary) >= plan.startMs - leadMs) plan else null
    }

    /** Hands over the incoming decoder for [plan]; closed at once if the plan is no longer armed. */
    fun offerIncoming(plan: TransitionPlan, source: FfmpegAudioSource) {
        if (armedPlan != plan || source.channels != channels || source.sampleRate != sampleRate) {
            source.close()
            return
        }
        offered?.second?.close()
        offered = plan to source
    }

    /** Ends a blend in progress, keeping the deck playing [keepMediaId]. */
    fun abandonBlend(keepMediaId: String) {
        abandonKeeping = keepMediaId
    }

    /**
     * Fills [target] with up to [frames] frames; returns how many. Fewer means the last track
     * the mixer holds has ended.
     */
    fun render(target: FloatArray, frames: Int): Int {
        applyAbandon()
        var produced = 0
        while (produced < frames) {
            val chunk = minOf(frames - produced, framesUntilNextEvent())
            val read = renderPrimary(target, produced, chunk)
            blend?.let { mix(it, target, produced, read) }
            produced += read
            outputFrames += read
            afterFrames()
            if (read < chunk) {
                val active = blend
                if (active != null) {
                    // The outgoing track ran out inside the blend: the incoming one takes over.
                    finishBlend(active, keepIncoming = true)
                    continue
                }
                break
            }
        }
        return produced
    }

    private fun positionMs(deck: Deck): Double = deck.sourceFrame * 1_000.0 / sampleRate

    private fun framesUntilNextEvent(): Int {
        val active = blend
        if (active != null) {
            val events = buildList {
                if (!active.swapped) add(active.swapFrame)
                add(active.endFrame)
                active.fadeFrom?.let { add(active.fadeStartFrame + ABANDON_FADE_FRAMES) }
            }
            val next = events.filter { it > outputFrames }.minOrNull() ?: return MAX_CHUNK
            return (next - outputFrames).coerceIn(1L, MAX_CHUNK.toLong()).toInt()
        }
        val plan = armedPlan?.takeIf { it.outgoingMediaId == primary.mediaId && blendAttempted != it }
            ?: return MAX_CHUNK
        val position = positionMs(primary)
        val rate = stretcher?.rate ?: 1.0
        val target = when {
            plan.changesTempo && position < plan.rampStartMs -> plan.rampStartMs.toDouble()
            position < plan.startMs -> plan.startMs.toDouble()
            else -> return 1
        }
        val framesAway = ceil((target - position) * sampleRate / 1_000.0 / rate).toLong()
        // Inside the ramp the rate is updated every RAMP_STEP frames.
        val cap = if (stretcher != null && position >= plan.rampStartMs) RAMP_STEP else MAX_CHUNK
        return framesAway.coerceIn(1L, cap.toLong()).toInt()
    }

    /** Reads [frames] frames of the primary deck, through the stretcher when one is active. */
    private fun renderPrimary(target: FloatArray, offset: Int, frames: Int): Int {
        val deck = primary
        val stretch = stretcher
        if (stretch == null) {
            val read = if (deck.ended) 0 else deck.source.read(target, offset, frames)
            if (read < frames) deck.ended = true
            remember(target, offset, read)
            deck.sourceFrame += read
            return read
        }
        var produced = 0
        val scratch = scratch(frames)
        while (produced < frames) {
            val wanted = stretch.inputFramesWanted(frames - produced)
            if (wanted > 0 && !deck.ended) {
                val input = FloatArray(wanted * channels)
                val got = deck.source.read(input, 0, wanted)
                if (got > 0) stretch.write(input, got)
                if (got < wanted) {
                    deck.ended = true
                    stretch.endOfInput()
                }
            }
            val got = stretch.read(scratch, frames - produced)
            if (got == 0) {
                if (deck.ended) break else continue
            }
            scratch.copyInto(target, (offset + produced) * channels, 0, got * channels)
            produced += got
        }
        deck.sourceFrame += produced * stretch.rate
        return produced
    }

    private fun mix(active: Blend, target: FloatArray, offset: Int, frames: Int) {
        if (frames == 0) return
        val incoming = scratch(frames)
        val got = if (active.incoming.ended) 0 else active.incoming.source.read(incoming, 0, frames)
        if (got < frames) {
            active.incoming.ended = true
            incoming.fill(0f, got * channels, frames * channels)
        }
        active.incoming.sourceFrame += got
        for (i in 0 until frames) {
            val frame = outputFrames + i
            val (gOut, gIn) = gains(active, frame)
            val base = (offset + i) * channels
            for (c in 0 until channels) {
                target[base + c] = target[base + c] * gOut + incoming[i * channels + c] * gIn
            }
        }
    }

    private fun gains(active: Blend, frame: Long): Pair<Float, Float> {
        val fade = active.fadeFrom
        if (fade != null) {
            val x = ((frame - active.fadeStartFrame).toFloat() / ABANDON_FADE_FRAMES).coerceIn(0f, 1f)
            val targetOut = if (active.keepIncoming) 0f else 1f
            val targetIn = 1f - targetOut
            return (fade.first + (targetOut - fade.first) * x) to (fade.second + (targetIn - fade.second) * x)
        }
        val g = active.plan.gainsAt(((frame - active.startFrame) * 1_000L) / sampleRate)
        return g.outgoing to g.incoming
    }

    /** Starts, swaps and ends blends, and moves the tempo ramp, at the frame just reached. */
    private fun afterFrames() {
        val active = blend
        if (active != null) {
            if (!active.swapped && outputFrames >= active.swapFrame && active.fadeFrom == null) {
                markSwap(active, outputFrames)
            }
            val fadeDone = active.fadeFrom != null && outputFrames >= active.fadeStartFrame + ABANDON_FADE_FRAMES
            if (outputFrames >= active.endFrame || fadeDone) {
                finishBlend(active, keepIncoming = active.fadeFrom == null || active.keepIncoming)
            }
            return
        }
        val plan = armedPlan?.takeIf { it.outgoingMediaId == primary.mediaId && blendAttempted != it }
        val stretch = stretcher
        if (plan == null) {
            // A plan withdrawn mid-ramp leaves the track at its own tempo again.
            if (stretch != null && stretch.rate != 1.0) {
                stretch.rate = 1.0
                clock.mark(outputFrames, primary.mediaId, positionMs(primary), 1.0)
            }
            return
        }
        val position = positionMs(primary)
        // A plan is played only by reaching it; a seek that lands past its start skips it, and
        // the track plays out on its own.
        val entry = if (plan.changesTempo) plan.rampStartMs else plan.startMs
        if (stretch == null && position > entry + LATE_TOLERANCE_MS) {
            blendAttempted = plan
            return
        }
        if (plan.changesTempo && position >= plan.rampStartMs) {
            val active2 = stretch ?: startStretcher()
            val rate = plan.outgoingRateAt(position.toLong())
            if (rate != active2.rate) {
                active2.rate = rate
                clock.mark(outputFrames, primary.mediaId, position, rate)
            }
        }
        if (position >= plan.startMs) startBlend(plan)
    }

    private fun startStretcher(): TimeStretcher {
        val stretch = TimeStretcher(channels, sampleRate)
        if (historyFilled >= historyFrames) stretch.prime(history, historyFrames)
        stretcher = stretch
        return stretch
    }

    private fun startBlend(plan: TransitionPlan) {
        blendAttempted = plan
        val ready = offered?.takeIf { it.first == plan }
        offered = null
        if (ready == null) {
            // The next track was not open in time; the outgoing one simply plays out.
            System.err.println("DeckMixer: incoming track not ready at the blend start; skipping the blend")
            return
        }
        val start = outputFrames
        blend = Blend(
            plan = plan,
            incoming = Deck(ready.second, plan.incomingMediaId, plan.incomingEntryMs, sampleRate),
            startFrame = start,
            swapFrame = start + plan.swapAfterMs * sampleRate / 1_000L,
            endFrame = start + plan.overlapMs * sampleRate / 1_000L,
        )
    }

    private fun markSwap(active: Blend, frame: Long) {
        active.swapped = true
        val incomingMs = active.plan.incomingEntryMs + (frame - active.startFrame) * 1_000.0 / sampleRate
        clock.mark(frame, active.incoming.mediaId, incomingMs, 1.0)
    }

    private fun finishBlend(active: Blend, keepIncoming: Boolean) {
        blend = null
        if (keepIncoming) {
            if (!active.swapped) markSwap(active, outputFrames)
            primary.source.close()
            primary = active.incoming
            stretcher = null
            historyFilled = 0
        } else {
            active.incoming.source.close()
            stretcher?.let { stretch ->
                if (stretch.rate != 1.0) {
                    stretch.rate = 1.0
                    clock.mark(outputFrames, primary.mediaId, positionMs(primary), 1.0)
                }
            }
        }
    }

    private fun applyAbandon() {
        val keep = abandonKeeping ?: return
        abandonKeeping = null
        val active = blend
        if (active == null) {
            // Only a ramp was under way: drop the plan and return to the track's own tempo.
            if (armedPlan?.outgoingMediaId == primary.mediaId) blendAttempted = armedPlan
            stretcher?.let { stretch ->
                if (stretch.rate != 1.0) {
                    stretch.rate = 1.0
                    clock.mark(outputFrames, primary.mediaId, positionMs(primary), 1.0)
                }
            }
            return
        }
        if (active.fadeFrom != null) return
        active.fadeFrom = gains(active, outputFrames)
        active.fadeStartFrame = outputFrames
        active.keepIncoming = keep == active.incoming.mediaId
        if (active.keepIncoming && !active.swapped) markSwap(active, outputFrames)
        if (!active.keepIncoming && active.swapped) {
            // Back to the outgoing track after the swap: the clock follows it again.
            clock.mark(outputFrames, primary.mediaId, positionMs(primary), stretcher?.rate ?: 1.0)
            active.swapped = false
        }
    }

    private fun remember(target: FloatArray, offset: Int, frames: Int) {
        if (frames <= 0) return
        if (frames >= historyFrames) {
            target.copyInto(history, 0, (offset + frames - historyFrames) * channels, (offset + frames) * channels)
            historyFilled = historyFrames
            return
        }
        val keep = historyFrames - frames
        history.copyInto(history, 0, frames * channels, historyFrames * channels)
        target.copyInto(history, keep * channels, offset * channels, (offset + frames) * channels)
        historyFilled = minOf(historyFrames, historyFilled + frames)
    }

    private fun scratch(frames: Int): FloatArray {
        if (mixBuffer.size < frames * channels) mixBuffer = FloatArray(frames * channels)
        return mixBuffer
    }

    override fun close() {
        blend?.incoming?.source?.close()
        blend = null
        offered?.second?.close()
        offered = null
        primary.source.close()
    }

    private companion object {
        const val MAX_CHUNK = 2_048
        const val RAMP_STEP = 512
        const val ABANDON_FADE_FRAMES = 2_400L
        const val LATE_TOLERANCE_MS = 250.0
    }
}

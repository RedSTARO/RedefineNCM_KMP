package com.leejlredstar.redefinencm.kmp.transition

import kotlin.math.abs

/** Frame-wise logits for one analysed section, one value per mel frame. */
class BeatActivations(val beat: FloatArray, val downbeat: FloatArray) {
    init {
        require(beat.size == downbeat.size)
    }
}

/** Beat and downbeat times, in seconds from the start of the analysed section. */
class BeatEvents(val beats: DoubleArray, val downbeats: DoubleArray)

/**
 * Runs [model] over [mel] the way `beat_this.inference.split_predict_aggregate` does: chunks
 * that overlap by the border the model cannot predict, the first chunk padded on the left and
 * the last shifted to end exactly at the piece's end, and where chunks overlap the earlier one
 * wins. The only departure is that every chunk is zero-padded on the right up to the static
 * [BEAT_MODEL_CHUNK_FRAMES], because the exported model has a fixed input shape; outputs past
 * a chunk's real length are discarded.
 */
suspend fun runBeatModel(model: BeatActivationModel, mel: MelFrames): BeatActivations {
    val frames = mel.frameCount
    val beat = FloatArray(frames) { UNSET_LOGIT }
    val downbeat = FloatArray(frames) { UNSET_LOGIT }
    if (frames == 0) return BeatActivations(beat, downbeat)
    val chunks = beatModelChunks(frames)
    val bins = BeatModelFeatures.MEL_BINS
    val input = FloatArray(BEAT_MODEL_CHUNK_FRAMES * bins)
    // Reversed, so a chunk that starts earlier overwrites the overlap a later one wrote.
    for (chunk in chunks.asReversed()) {
        input.fill(0f)
        val sourceStart = maxOf(chunk.start, 0)
        val sourceEnd = minOf(chunk.start + BEAT_MODEL_CHUNK_FRAMES, frames)
        val leftPad = maxOf(0, -chunk.start)
        mel.values.copyInto(
            destination = input,
            destinationOffset = leftPad * bins,
            startIndex = sourceStart * bins,
            endIndex = sourceEnd * bins,
        )
        val output = model.infer(input)
        require(output.size == BEAT_MODEL_CHUNK_FRAMES * 2) {
            "Beat model returned ${output.size} values, expected ${BEAT_MODEL_CHUNK_FRAMES * 2}"
        }
        val keepFrom = chunk.start + BEAT_MODEL_BORDER_FRAMES
        val keepUntil = minOf(chunk.start + chunk.length - BEAT_MODEL_BORDER_FRAMES, frames)
        for (piece in maxOf(keepFrom, 0) until keepUntil) {
            val local = piece - chunk.start
            beat[piece] = output[local * 2]
            downbeat[piece] = output[local * 2 + 1]
        }
    }
    return BeatActivations(beat, downbeat)
}

internal data class BeatModelChunk(val start: Int, val length: Int)

/** `beat_this.inference.split_piece` with `avoid_short_end=True`, as start and padded length. */
internal fun beatModelChunks(frames: Int): List<BeatModelChunk> {
    val chunk = BEAT_MODEL_CHUNK_FRAMES
    val border = BEAT_MODEL_BORDER_FRAMES
    val step = chunk - 2 * border
    val starts = mutableListOf<Int>()
    var start = -border
    while (start < frames - border) {
        starts += start
        start += step
    }
    if (frames > step && starts.isNotEmpty()) starts[starts.lastIndex] = frames - (chunk - border)
    return starts.map { s ->
        val body = minOf(s + chunk, frames) - maxOf(s, 0)
        val left = maxOf(0, -s)
        val right = maxOf(0, minOf(border, s + chunk - frames))
        BeatModelChunk(s, left + body + right)
    }
}

/**
 * `beat_this.model.postprocessor.Postprocessor(type="minimal")`: a frame is a beat when its logit
 * is positive and the largest within ±3 frames (±60 ms); runs of adjacent peaks collapse to
 * their mean; every downbeat is then moved onto its nearest beat.
 */
fun pickBeatEvents(activations: BeatActivations): BeatEvents {
    val fps = BeatModelFeatures.FRAMES_PER_SECOND.toDouble()
    val beats = deduplicatePeaks(peakFrames(activations.beat)).map { it / fps }.toDoubleArray()
    val downbeats = deduplicatePeaks(peakFrames(activations.downbeat))
        .map { it / fps }
        .map { time ->
            if (beats.isEmpty()) time else beats.minBy { abs(it - time) }
        }
        .distinct()
        .sorted()
        .toDoubleArray()
    return BeatEvents(beats, downbeats)
}

private fun peakFrames(logits: FloatArray): List<Int> {
    val peaks = mutableListOf<Int>()
    for (i in logits.indices) {
        val value = logits[i]
        if (value <= 0f) continue
        var isMax = true
        for (j in maxOf(0, i - 3)..minOf(logits.lastIndex, i + 3)) {
            if (logits[j] > value) {
                isMax = false
                break
            }
        }
        if (isMax) peaks += i
    }
    return peaks
}

private fun deduplicatePeaks(peaks: List<Int>, width: Int = 1): List<Double> {
    if (peaks.isEmpty()) return emptyList()
    val result = mutableListOf<Double>()
    var mean = peaks[0].toDouble()
    var last = peaks[0]
    var count = 1
    for (index in 1 until peaks.size) {
        val next = peaks[index]
        if (next - last <= width) {
            count += 1
            mean += (next - mean) / count
        } else {
            result += mean
            mean = next.toDouble()
            count = 1
        }
        last = next
    }
    result += mean
    return result
}

/** What `aggregate_prediction` fills frames with before any chunk writes them. */
private const val UNSET_LOGIT = -1000f

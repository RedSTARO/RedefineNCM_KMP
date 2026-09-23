package com.leejlredstar.redefinencm.kmp.transition

import kotlinx.serialization.Serializable

/**
 * What the planner knows about one stretch of a track: its loudness everywhere, its beat grid
 * when the model ran and found a steady tempo, and its key.
 */
@Serializable
data class SectionAnalysis(
    val startMs: Long,
    val endMs: Long,
    val energy: EnergyProfile,
    val grid: BeatGrid?,
    val key: MusicalKey?,
)

/**
 * A track's head and tail, analysed for handing over into it and out of it.
 *
 * Only the two ends are decoded: the planner never places a transition in the middle of a
 * song, and the ends are all a transition touches. [beatAccelerator] records which processor
 * produced the grids, or null when the model did not run and the grids are absent.
 */
@Serializable
data class TrackAnalysis(
    val mediaId: String,
    val durationMs: Long,
    val head: SectionAnalysis?,
    val tail: SectionAnalysis?,
    val beatAccelerator: InferenceAccelerator?,
    val version: Int = VERSION,
) {
    companion object {
        /** Bumped whenever a change here would make a cached analysis mean something else. */
        const val VERSION: Int = 1

        /** How much of each end is decoded. Long enough for an eight-bar blend at 60 BPM. */
        const val SECTION_MS: Long = 45_000L
    }
}

/** Mono samples at [BeatModelFeatures.SAMPLE_RATE_HZ] for one stretch of a track. */
class AnalysisWindow(val startMs: Long, val samples: FloatArray) {
    val lengthMs: Long get() = samples.size * 1000L / BeatModelFeatures.SAMPLE_RATE_HZ
}

/** A track's decoded ends, and its duration as the decoder measured it (-1 when unknown). */
class DecodedTrackEnds(val durationMs: Long, val head: AnalysisWindow?, val tail: AnalysisWindow?)

/**
 * Decodes the ends of one track for analysis. Each platform implements it over its own decoder:
 * FFmpeg on the desktop, MediaExtractor and MediaCodec on Android, AVAssetReader on iOS, and
 * `decodeAudioData` in the browser.
 *
 * [url] is whatever the platform player would play: a CDN URL or a local file or content URI.
 * Implementations resample to [BeatModelFeatures.SAMPLE_RATE_HZ] mono themselves, because every
 * platform has a better resampler at hand than one written here.
 */
fun interface TrackEndsDecoder {
    suspend fun decode(url: String, sectionMs: Long, knownDurationMs: Long): DecodedTrackEnds?
}

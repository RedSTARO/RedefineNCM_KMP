package com.leejlredstar.redefinencm.kmp.transition

import kotlinx.coroutines.flow.StateFlow

/**
 * The processor a model actually runs on.
 *
 * There is deliberately no CPU entry. Smart transitions run the beat model only on a neural
 * accelerator, or on the GPU when there is none; a device offering neither gets the analysis
 * that needs no model (energy, key, silence) and a crossfade instead of a beat-matched blend.
 */
enum class InferenceAccelerator(val label: String) {
    NPU("NPU"),
    GPU("GPU"),
}

/**
 * Beat This! (small0, MIT, CPJKU) exported with a static `[1, CHUNK_FRAMES, 128]` input and one
 * `[1, CHUNK_FRAMES, 2]` output of beat and downbeat logits. Static because every NPU compiler
 * this project targets wants one; 750 frames (15 s) because the frontend's time attention is
 * quadratic in it, and at the model's native 1500 its score matrices alone need 288 MB per block.
 * Measured on real tracks, 750-frame chunks agree with 1500-frame ones at a beat F-measure of
 * about 0.94.
 */
const val BEAT_MODEL_CHUNK_FRAMES: Int = 750

/** Frames at either edge of a chunk the model never learned to predict (its loss max-pools). */
const val BEAT_MODEL_BORDER_FRAMES: Int = 6

/** One loaded model bound to one accelerator. Calls are serialised by the caller. */
interface BeatActivationModel : AutoCloseable {
    val accelerator: InferenceAccelerator

    /** A human-readable device name, shown in Settings: "AMD Radeon RX 7900 XT · DirectML". */
    val deviceLabel: String

    /**
     * [spectrogram] is `BEAT_MODEL_CHUNK_FRAMES × MEL_BINS` row-major; the result is
     * `BEAT_MODEL_CHUNK_FRAMES × 2` row-major: beat logit, downbeat logit.
     */
    suspend fun infer(spectrogram: FloatArray): FloatArray
}

sealed interface BeatModelAvailability {
    data class Ready(val model: BeatActivationModel) : BeatModelAvailability

    /**
     * [reason] is shown to the user as is, so it names the platform fact, not a stack trace.
     * [retryable] marks a failure that may pass on its own, such as a model download that found
     * no mirror reachable; the analyzer asks again later instead of giving up for the session.
     */
    data class Unavailable(val reason: String, val retryable: Boolean = false) : BeatModelAvailability
}

/**
 * Loads the platform's model on the best accelerator it has: NPU first, GPU second, never the
 * CPU. Each target implements it over its own runtime: LiteRT on Android, Core ML on iOS, ONNX
 * Runtime with DirectML or Core ML on the desktop, ONNX Runtime Web with WebNN or WebGPU in the
 * browser.
 */
fun interface BeatModelLoader {
    suspend fun load(): BeatModelAvailability

    /**
     * The model file's download, 0 to 1 while it runs and null otherwise, for a loader that
     * downloads its model on first use. Null for one that never downloads.
     */
    val downloadProgress: StateFlow<Float?>? get() = null
}

/** For targets with no accelerated runtime at all. */
class UnavailableBeatModelLoader(private val reason: String) : BeatModelLoader {
    override suspend fun load(): BeatModelAvailability = BeatModelAvailability.Unavailable(reason)
}

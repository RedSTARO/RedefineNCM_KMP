package com.leejlredstar.redefinencm.kmp.recognition

import kotlinx.coroutines.sync.Mutex
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * A [MicrophoneRecorder] that lets one capture own the device at a time.
 *
 * The four platform recorders had written this guard identically: reject a non-positive
 * duration, fail fast with [MicrophoneBusyException] rather than queueing behind a capture
 * already in flight, and release the claim on every exit path including cancellation.
 */
abstract class ExclusiveMicrophoneRecorder : MicrophoneRecorder {

    private val captureMutex = Mutex()

    final override suspend fun capture(
        durationMillis: Long,
        onProgress: (elapsedMillis: Long, level: Float) -> Unit,
    ): CapturedPcm {
        require(durationMillis > 0L) { "录音时长必须大于 0" }
        onBeforeClaim()
        if (!captureMutex.tryLock()) throw MicrophoneBusyException()
        try {
            return captureExclusively(durationMillis, onProgress)
        } finally {
            captureMutex.unlock()
        }
    }

    /**
     * Preconditions checked before the device is claimed, so a failure cannot be reported as
     * "busy". Web uses it to reject an insecure page context.
     */
    protected open suspend fun onBeforeClaim() = Unit

    /** Runs with the capture claim held. */
    protected abstract suspend fun captureExclusively(
        durationMillis: Long,
        onProgress: (elapsedMillis: Long, level: Float) -> Unit,
    ): CapturedPcm
}

/** How many mono samples [durationMillis] of audio occupies at [sampleRateHz]. */
internal fun pcmTargetSampleCount(durationMillis: Long, sampleRateHz: Int): Int =
    ceil(durationMillis * sampleRateHz.toDouble() / 1_000.0).toInt()

/** How much audio [sampleCount] mono samples represent, for the capture progress callback. */
internal fun pcmElapsedMillis(sampleCount: Int, sampleRateHz: Int): Long =
    sampleCount * 1_000L / sampleRateHz

/** Signed 16-bit PCM scaled into the -1f..1f range [CapturedPcm] documents. */
internal fun Short.toPcmSample(): Float = toFloat() / 32_768f

/** One little-endian signed 16-bit frame, the layout every platform's raw buffer uses. */
internal fun pcmSampleAt(bytes: ByteArray, byteIndex: Int): Float =
    (((bytes[byteIndex + 1].toInt() shl 8) or (bytes[byteIndex].toInt() and 0xff)).toShort())
        .toPcmSample()

/**
 * RMS of [sampleCount] samples whose squares sum to [energy], clamped to the 0f..1f the
 * recognition UI's level meter expects. Returns 0f for an empty read rather than NaN.
 */
internal fun pcmRmsLevel(energy: Double, sampleCount: Int): Float =
    if (sampleCount <= 0) 0f else sqrt(energy / sampleCount).toFloat().coerceIn(0f, 1f)

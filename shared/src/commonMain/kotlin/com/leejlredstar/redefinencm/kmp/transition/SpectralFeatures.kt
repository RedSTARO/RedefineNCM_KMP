package com.leejlredstar.redefinencm.kmp.transition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The input the Beat This! model was trained on, reproduced without torchaudio.
 *
 * `beat_this.preprocessing.LogMelSpect` is torchaudio's `MelSpectrogram(sample_rate=22050,
 * n_fft=1024, hop_length=441, f_min=30, f_max=11000, n_mels=128, mel_scale="slaney",
 * normalized="frame_length", power=1)` followed by `log1p(1000 * x)`. Every one of those choices
 * is load-bearing: the model has no idea what a different window, mel scale or normalisation
 * means, and a mismatch shows up as confidently wrong beats rather than an error. The common
 * test pins this implementation against values torchaudio produced.
 */
object BeatModelFeatures {
    const val SAMPLE_RATE_HZ: Int = 22_050
    const val FFT_SIZE: Int = 1_024
    const val HOP_SIZE: Int = 441
    const val MEL_BINS: Int = 128
    const val FRAMES_PER_SECOND: Int = SAMPLE_RATE_HZ / HOP_SIZE
    const val MIN_FREQUENCY_HZ: Double = 30.0
    const val MAX_FREQUENCY_HZ: Double = 11_000.0
    const val LOG_MULTIPLIER: Double = 1_000.0
}

/** Row-major `frameCount × MEL_BINS` log-mel frames at [BeatModelFeatures.FRAMES_PER_SECOND]. */
class MelFrames(val frameCount: Int, val values: FloatArray) {
    init {
        require(values.size == frameCount * BeatModelFeatures.MEL_BINS)
    }
}

/**
 * Everything one pass over a section's samples yields: the model input, and the pitch-class
 * energy the key estimate needs. Both come from the same magnitude spectrum, so computing them
 * together costs one FFT per frame instead of two.
 */
class SpectralFeatures(val mel: MelFrames, val chroma: DoubleArray)

internal class SpectralAnalyzer {
    private val fft = RealFft(BeatModelFeatures.FFT_SIZE)
    private val window = DoubleArray(BeatModelFeatures.FFT_SIZE) {
        // torch.hann_window is periodic by default.
        0.5 - 0.5 * cos(2.0 * PI * it / BeatModelFeatures.FFT_SIZE)
    }
    private val filters = slaneyMelFilters()
    private val chromaBins = chromaBinMap()

    /** [samples] are mono at [BeatModelFeatures.SAMPLE_RATE_HZ]. */
    fun analyze(samples: FloatArray): SpectralFeatures {
        val n = samples.size
        val frameCount = if (n == 0) 0 else 1 + n / BeatModelFeatures.HOP_SIZE
        val values = FloatArray(frameCount * BeatModelFeatures.MEL_BINS)
        val chroma = DoubleArray(12)
        val frame = DoubleArray(BeatModelFeatures.FFT_SIZE)
        val magnitude = DoubleArray(BeatModelFeatures.FFT_SIZE / 2 + 1)
        // torch.stft(normalized=True) scales by frame_length^-1/2.
        val scale = 1.0 / sqrt(BeatModelFeatures.FFT_SIZE.toDouble())
        val padding = BeatModelFeatures.FFT_SIZE / 2
        for (t in 0 until frameCount) {
            val origin = t * BeatModelFeatures.HOP_SIZE - padding
            for (i in 0 until BeatModelFeatures.FFT_SIZE) {
                frame[i] = samples[reflectIndex(origin + i, n)] * window[i]
            }
            fft.magnitudes(frame, magnitude, scale)
            val row = t * BeatModelFeatures.MEL_BINS
            for (m in 0 until BeatModelFeatures.MEL_BINS) {
                val filter = filters[m]
                var sum = 0.0
                for (j in filter.weights.indices) sum += filter.weights[j] * magnitude[filter.firstBin + j]
                values[row + m] = ln1p(BeatModelFeatures.LOG_MULTIPLIER * sum).toFloat()
            }
            for (bin in chromaBins.indices) {
                val pitchClass = chromaBins[bin]
                if (pitchClass >= 0) chroma[pitchClass] += magnitude[bin] * magnitude[bin]
            }
        }
        return SpectralFeatures(MelFrames(frameCount, values), chroma)
    }

    private class MelFilter(val firstBin: Int, val weights: DoubleArray)

    private fun slaneyMelFilters(): List<MelFilter> {
        val freqCount = BeatModelFeatures.FFT_SIZE / 2 + 1
        // torchaudio uses `sample_rate // 2`, an integer, as the top of the linear grid.
        val nyquist = (BeatModelFeatures.SAMPLE_RATE_HZ / 2).toDouble()
        val allFreqs = DoubleArray(freqCount) { nyquist * it / (freqCount - 1) }
        val melMin = hzToSlaneyMel(BeatModelFeatures.MIN_FREQUENCY_HZ)
        val melMax = hzToSlaneyMel(BeatModelFeatures.MAX_FREQUENCY_HZ)
        val points = BeatModelFeatures.MEL_BINS + 2
        val fPts = DoubleArray(points) {
            slaneyMelToHz(melMin + (melMax - melMin) * it / (points - 1))
        }
        return List(BeatModelFeatures.MEL_BINS) { m ->
            val lower = fPts[m]
            val center = fPts[m + 1]
            val upper = fPts[m + 2]
            val weights = DoubleArray(freqCount) { k ->
                val f = allFreqs[k]
                val down = (f - lower) / (center - lower)
                val up = (upper - f) / (upper - center)
                maxOf(0.0, minOf(down, up))
            }
            val first = weights.indexOfFirst { it > 0.0 }.takeIf { it >= 0 } ?: 0
            val last = weights.indexOfLast { it > 0.0 }.takeIf { it >= 0 } ?: 0
            MelFilter(first, weights.copyOfRange(first, last + 1))
        }
    }

    /** Pitch class of each FFT bin between C2 and C7, or -1 outside the range chroma uses. */
    private fun chromaBinMap(): IntArray {
        val binHz = BeatModelFeatures.SAMPLE_RATE_HZ.toDouble() / BeatModelFeatures.FFT_SIZE
        return IntArray(BeatModelFeatures.FFT_SIZE / 2 + 1) { bin ->
            val hz = bin * binHz
            if (hz < CHROMA_MIN_HZ || hz > CHROMA_MAX_HZ) {
                -1
            } else {
                val midi = 69.0 + 12.0 * log2(hz / 440.0)
                ((midi.roundToInt() % 12) + 12) % 12
            }
        }
    }

    private companion object {
        const val CHROMA_MIN_HZ = 65.0
        const val CHROMA_MAX_HZ = 2_100.0
    }
}

/** torch's "reflect" padding: the edge sample is not repeated. */
private fun reflectIndex(index: Int, length: Int): Int {
    if (length <= 1) return 0
    var i = index
    val period = 2 * (length - 1)
    i %= period
    if (i < 0) i += period
    return if (i < length) i else period - i
}

private const val SLANEY_F_SP = 200.0 / 3.0
private const val SLANEY_MIN_LOG_HZ = 1_000.0
private const val SLANEY_MIN_LOG_MEL = SLANEY_MIN_LOG_HZ / SLANEY_F_SP
private val SLANEY_LOGSTEP = ln(6.4) / 27.0

internal fun hzToSlaneyMel(hz: Double): Double =
    if (hz >= SLANEY_MIN_LOG_HZ) {
        SLANEY_MIN_LOG_MEL + ln(hz / SLANEY_MIN_LOG_HZ) / SLANEY_LOGSTEP
    } else {
        hz / SLANEY_F_SP
    }

internal fun slaneyMelToHz(mel: Double): Double =
    if (mel >= SLANEY_MIN_LOG_MEL) {
        SLANEY_MIN_LOG_HZ * exp(SLANEY_LOGSTEP * (mel - SLANEY_MIN_LOG_MEL))
    } else {
        SLANEY_F_SP * mel
    }

package com.leejlredstar.redefinencm.kmp.recognition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

const val RECOGNITION_SAMPLE_RATE_HZ: Int = AudioFingerprint.SAMPLE_RATE_HZ
const val DEFAULT_RECOGNITION_DURATION_MILLIS: Long = AudioFingerprint.DURATION_MILLIS

/**
 * 将平台采集的 PCM 转成指纹算法要求的 8 kHz 单声道样本。
 *
 * 下采样使用带 Blackman 窗的 sinc 低通核，避免直接抽点产生混叠。输入不足时直接失败，
 * 因为补静音会改变指纹并掩盖录音设备提前停止的问题。
 */
fun prepareRecognitionSamples(
    captured: CapturedPcm,
    durationMillis: Long = DEFAULT_RECOGNITION_DURATION_MILLIS,
): FloatArray {
    require(durationMillis > 0L) { "录音时长必须大于 0" }
    require(captured.sampleRateHz > 0) { "录音采样率无效" }

    val targetCountLong = durationMillis * RECOGNITION_SAMPLE_RATE_HZ / 1_000L
    require(targetCountLong in 1..Int.MAX_VALUE.toLong()) { "录音时长超出可处理范围" }
    val targetCount = targetCountLong.toInt()

    val requiredInputLong = (
        durationMillis * captured.sampleRateHz.toLong() + 999L
    ) / 1_000L
    require(requiredInputLong <= Int.MAX_VALUE.toLong()) { "输入样本数量超出可处理范围" }
    val requiredInput = requiredInputLong.toInt()
    require(captured.monoSamples.size >= requiredInput) {
        "录音样本不足：需要 $requiredInput，实际 ${captured.monoSamples.size}"
    }
    for (index in 0 until requiredInput) {
        require(captured.monoSamples[index].isFinite()) {
            "录音在样本 $index 处包含非有限值"
        }
    }

    if (captured.sampleRateHz == RECOGNITION_SAMPLE_RATE_HZ) {
        return captured.monoSamples.copyOf(targetCount)
    }

    return windowedSincResample(
        input = captured.monoSamples,
        inputLength = requiredInput,
        sourceRateHz = captured.sampleRateHz,
        outputLength = targetCount,
    )
}

private fun windowedSincResample(
    input: FloatArray,
    inputLength: Int,
    sourceRateHz: Int,
    outputLength: Int,
): FloatArray {
    val output = FloatArray(outputLength)
    val sourcePerOutput = sourceRateHz.toDouble() / RECOGNITION_SAMPLE_RATE_HZ.toDouble()
    val cutoff = 0.47 * minOf(1.0, RECOGNITION_SAMPLE_RATE_HZ.toDouble() / sourceRateHz)
    val radius = 24.0

    for (outputIndex in output.indices) {
        val sourcePosition = outputIndex * sourcePerOutput
        val firstInput = floor(sourcePosition - radius).toInt()
        val lastInput = ceil(sourcePosition + radius).toInt()
        var weightedSample = 0.0
        var weightSum = 0.0

        for (inputIndex in firstInput..lastInput) {
            if (inputIndex !in 0 until inputLength) continue
            val distance = inputIndex - sourcePosition
            val normalizedDistance = distance / radius
            if (abs(normalizedDistance) > 1.0) continue

            val sincArgument = 2.0 * cutoff * distance
            val sinc = if (abs(sincArgument) < 1e-12) {
                1.0
            } else {
                sin(PI * sincArgument) / (PI * sincArgument)
            }
            val window = 0.42 +
                0.5 * cos(PI * normalizedDistance) +
                0.08 * cos(2.0 * PI * normalizedDistance)
            val weight = 2.0 * cutoff * sinc * window
            weightedSample += input[inputIndex] * weight
            weightSum += weight
        }

        output[outputIndex] = if (abs(weightSum) < 1e-12) {
            0f
        } else {
            (weightedSample / weightSum).toFloat().coerceIn(-1f, 1f)
        }
    }
    return output
}

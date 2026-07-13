package com.leejlredstar.redefinencm.kmp.recognition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates the native `/audio/match` fingerprint from exactly three seconds of 8 kHz mono PCM.
 *
 * The extraction algorithm and binary layout are derived from Open Orpheus commit
 * 021984fb7ad35393efe5dde4e3b1666c3d14c972. Open Orpheus is Copyright 2026 YUCLing and
 * licensed under the MIT License; see `THIRD_PARTY_NOTICES.md`.
 */
object AudioFingerprint {
    const val SAMPLE_RATE_HZ: Int = 8_000
    const val DURATION_MILLIS: Long = 3_000L

    private const val REQUIRED_SAMPLE_COUNT: Int = 24_000

    fun generate(samples: FloatArray): String {
        require(samples.size == REQUIRED_SAMPLE_COUNT) {
            "Audio fingerprint requires exactly $REQUIRED_SAMPLE_COUNT samples, got ${samples.size}"
        }

        val peaks = FingerprintExtractor.extract(samples)
        val raw = AudioFingerprintCodec.buildRawFingerprint(
            durationSeconds = samples.size.toFloat() / SAMPLE_RATE_HZ,
            peaks = peaks,
        )
        val compressed = AudioFingerprintCodec.zlibStore(raw)
        val encrypted = AudioFingerprintCodec.aes128EcbPkcs7Encrypt(
            input = compressed,
            key = AudioFingerprintCodec.AES_KEY,
        )
        return AudioFingerprintCodec.encodeBase64(encrypted)
    }
}

internal data class FingerprintPeak(
    val frequencyBin: Int,
    val timeFrame: Int,
    val amplitude: Float,
)

internal object FingerprintExtractor {
    private const val WINDOW_SIZE = 2_048
    private const val HOP_SIZE = 160
    private const val MINIMUM_FRAME_COUNT = 10
    private const val AVERAGE_FREQUENCY_RADIUS = 10
    private const val AVERAGE_TIME_RADIUS = 5
    private const val FINAL_FREQUENCY_RADIUS = 30
    private const val FINAL_TIME_RADIUS = 8
    private const val AVERAGE_THRESHOLD = 1.0
    private const val MINIMUM_MAGNITUDE = 1.11920929e-6

    private const val LOW_BIN = 25 // floor(100 Hz / (8_000 Hz / 2_048 bins))
    private const val HIGH_BIN = 1_024 // floor(4_000 Hz / (8_000 Hz / 2_048 bins))
    private const val BAND_BIN_COUNT = HIGH_BIN - LOW_BIN

    private val hammingWindow = FloatArray(WINDOW_SIZE) { index ->
        (0.54 - 0.46 * cos((2.0 * PI * index) / (WINDOW_SIZE - 1))).toFloat()
    }

    private val bitReversedIndices = IntArray(WINDOW_SIZE) { index ->
        var source = index
        var reversed = 0
        repeat(11) {
            reversed = (reversed shl 1) or (source and 1)
            source = source ushr 1
        }
        reversed
    }

    fun extract(samples: FloatArray): List<FingerprintPeak> {
        val featureMatrix = buildFeatureMatrix(samples)
        val frameCount = featureMatrix.firstOrNull()?.size ?: 0
        if (frameCount < MINIMUM_FRAME_COUNT) return emptyList()

        val candidates = ArrayList<FingerprintPeak>()
        for (frequency in 0 until BAND_BIN_COUNT) {
            val row = featureMatrix[frequency]
            for (time in 0 until frameCount) {
                if (hasGreaterInNeighborhood(
                        matrix = featureMatrix,
                        frequency = frequency,
                        time = time,
                        frequencyRadius = 1,
                        timeRadius = 1,
                    )
                ) {
                    continue
                }

                val amplitude = row[time]
                if (amplitude <= 0f || !passesLocalAverage(featureMatrix, frequency, time)) {
                    continue
                }
                candidates += FingerprintPeak(
                    frequencyBin = frequency,
                    timeFrame = time,
                    amplitude = amplitude,
                )
            }
        }

        val peaks = ArrayList<FingerprintPeak>()
        for (peak in candidates) {
            if (hasGreaterInNeighborhood(
                matrix = featureMatrix,
                frequency = peak.frequencyBin,
                time = peak.timeFrame,
                frequencyRadius = FINAL_FREQUENCY_RADIUS,
                timeRadius = FINAL_TIME_RADIUS,
            )) continue
            peaks += peak.copy(frequencyBin = peak.frequencyBin + LOW_BIN)
        }
        peaks.sortWith(compareBy(FingerprintPeak::timeFrame, FingerprintPeak::frequencyBin))
        return peaks
    }

    private fun buildFeatureMatrix(samples: FloatArray): Array<FloatArray> {
        if (samples.size < WINDOW_SIZE) return emptyArray()

        val frameCount = ((samples.size - WINDOW_SIZE) / HOP_SIZE) + 1
        val matrix = Array(BAND_BIN_COUNT) { FloatArray(frameCount) }
        val real = DoubleArray(WINDOW_SIZE)
        val imaginary = DoubleArray(WINDOW_SIZE)
        var sum = 0.0

        for (frame in 0 until frameCount) {
            val start = frame * HOP_SIZE
            for (index in 0 until WINDOW_SIZE) {
                val target = bitReversedIndices[index]
                real[target] = samples[start + index].toDouble() * hammingWindow[index].toDouble()
                imaginary[target] = 0.0
            }

            fftInPlace(real, imaginary)

            for (frequency in 0 until BAND_BIN_COUNT) {
                val bin = LOW_BIN + frequency
                // Open Orpheus stores power into Float32 before calculating magnitude.
                val power = (real[bin] * real[bin] + imaginary[bin] * imaginary[bin]).toFloat()
                val value = ln(max(sqrt(power.toDouble()), MINIMUM_MAGNITUDE))
                matrix[frequency][frame] = value.toFloat()
                sum += value
            }
        }

        val mean = sum / (BAND_BIN_COUNT * frameCount)
        for (frequency in 0 until BAND_BIN_COUNT) {
            val row = matrix[frequency]
            for (time in 0 until frameCount) {
                row[time] = (row[time] - mean).toFloat()
            }
        }
        return matrix
    }

    private fun fftInPlace(real: DoubleArray, imaginary: DoubleArray) {
        var length = 2
        while (length <= WINDOW_SIZE) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            val halfLength = length / 2

            var blockStart = 0
            while (blockStart < WINDOW_SIZE) {
                var twiddleReal = 1.0
                var twiddleImaginary = 0.0
                for (offset in 0 until halfLength) {
                    val evenIndex = blockStart + offset
                    val oddIndex = evenIndex + halfLength
                    val oddReal =
                        real[oddIndex] * twiddleReal - imaginary[oddIndex] * twiddleImaginary
                    val oddImaginary =
                        real[oddIndex] * twiddleImaginary + imaginary[oddIndex] * twiddleReal
                    val evenReal = real[evenIndex]
                    val evenImaginary = imaginary[evenIndex]

                    real[evenIndex] = evenReal + oddReal
                    imaginary[evenIndex] = evenImaginary + oddImaginary
                    real[oddIndex] = evenReal - oddReal
                    imaginary[oddIndex] = evenImaginary - oddImaginary

                    val nextTwiddleReal =
                        twiddleReal * stepReal - twiddleImaginary * stepImaginary
                    twiddleImaginary =
                        twiddleReal * stepImaginary + twiddleImaginary * stepReal
                    twiddleReal = nextTwiddleReal
                }
                blockStart += length
            }
            length *= 2
        }
    }

    private fun hasGreaterInNeighborhood(
        matrix: Array<FloatArray>,
        frequency: Int,
        time: Int,
        frequencyRadius: Int,
        timeRadius: Int,
    ): Boolean {
        val center = matrix[frequency][time]
        val firstFrequency = maxOf(0, frequency - frequencyRadius)
        val lastFrequencyExclusive = minOf(matrix.size, frequency + frequencyRadius + 1)
        val firstTime = maxOf(0, time - timeRadius)
        val lastTimeExclusive = minOf(matrix[0].size, time + timeRadius + 1)

        for (nearbyFrequency in firstFrequency until lastFrequencyExclusive) {
            val row = matrix[nearbyFrequency]
            for (nearbyTime in firstTime until lastTimeExclusive) {
                if (row[nearbyTime] > center) return true
            }
        }
        return false
    }

    private fun passesLocalAverage(
        matrix: Array<FloatArray>,
        frequency: Int,
        time: Int,
    ): Boolean {
        val firstFrequency = maxOf(0, frequency - AVERAGE_FREQUENCY_RADIUS)
        val lastFrequencyExclusive =
            minOf(matrix.size, frequency + AVERAGE_FREQUENCY_RADIUS + 1)
        val firstTime = maxOf(0, time - AVERAGE_TIME_RADIUS)
        val lastTimeExclusive = minOf(matrix[0].size, time + AVERAGE_TIME_RADIUS + 1)

        var sum = 0.0
        for (nearbyFrequency in firstFrequency until lastFrequencyExclusive) {
            val row = matrix[nearbyFrequency]
            for (nearbyTime in firstTime until lastTimeExclusive) {
                sum += row[nearbyTime]
            }
        }
        val count =
            (lastFrequencyExclusive - firstFrequency) * (lastTimeExclusive - firstTime)
        val average = sum / count
        val amplitude = matrix[frequency][time]
        return average > 2.0 || amplitude - average > AVERAGE_THRESHOLD
    }
}

internal object AudioFingerprintCodec {
    private const val VERSION = "hyai_1.2.0_client_1.0.0"
    private const val ZLIB_MODULUS = 65_521
    private const val MAX_STORED_BLOCK_SIZE = 65_535
    private const val BASE64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    val AES_KEY: ByteArray = "4B97221F27F02907".encodeToByteArray()

    fun buildRawFingerprint(
        durationSeconds: Float,
        peaks: List<FingerprintPeak>,
    ): ByteArray {
        val versionBytes = VERSION.encodeToByteArray()
        val result = ByteArray(79 + peaks.size * 12)
        var offset = 0

        offset = result.writeUInt32LittleEndian(offset, versionBytes.size)
        versionBytes.copyInto(result, destinationOffset = offset)
        offset += versionBytes.size
        offset += 8 // Reserved bytes are already zero-initialized.
        offset = result.writeFloatLittleEndian(offset, durationSeconds)
        offset = result.writeAscii(offset, "FPVER")
        offset = result.writeUInt32LittleEndian(offset, versionBytes.size)
        versionBytes.copyInto(result, destinationOffset = offset)
        offset += versionBytes.size
        offset = result.writeAscii(offset, "Peak")
        offset = result.writeUInt32LittleEndian(offset, peaks.size)

        for (peak in peaks) {
            offset = result.writeUInt32LittleEndian(offset, peak.frequencyBin)
            offset = result.writeUInt32LittleEndian(offset, peak.timeFrame)
            offset = result.writeFloatLittleEndian(offset, peak.amplitude)
        }
        check(offset == result.size)
        return result
    }

    /** Creates an RFC 1950 zlib stream containing RFC 1951 uncompressed stored blocks. */
    fun zlibStore(input: ByteArray): ByteArray {
        val blockCount = maxOf(1, (input.size + MAX_STORED_BLOCK_SIZE - 1) / MAX_STORED_BLOCK_SIZE)
        val result = ByteArray(2 + input.size + blockCount * 5 + 4)
        var outputOffset = 0
        result[outputOffset++] = 0x78
        result[outputOffset++] = 0x01

        var inputOffset = 0
        for (blockIndex in 0 until blockCount) {
            val length = minOf(MAX_STORED_BLOCK_SIZE, input.size - inputOffset)
            val isFinal = blockIndex == blockCount - 1
            result[outputOffset++] = if (isFinal) 0x01 else 0x00
            result[outputOffset++] = (length and 0xFF).toByte()
            result[outputOffset++] = ((length ushr 8) and 0xFF).toByte()
            val invertedLength = length xor 0xFFFF
            result[outputOffset++] = (invertedLength and 0xFF).toByte()
            result[outputOffset++] = ((invertedLength ushr 8) and 0xFF).toByte()
            input.copyInto(
                destination = result,
                destinationOffset = outputOffset,
                startIndex = inputOffset,
                endIndex = inputOffset + length,
            )
            inputOffset += length
            outputOffset += length
        }

        val adler32 = adler32(input)
        result[outputOffset++] = ((adler32 ushr 24) and 0xFF).toByte()
        result[outputOffset++] = ((adler32 ushr 16) and 0xFF).toByte()
        result[outputOffset++] = ((adler32 ushr 8) and 0xFF).toByte()
        result[outputOffset++] = (adler32 and 0xFF).toByte()
        check(outputOffset == result.size)
        return result
    }

    fun aes128EcbPkcs7Encrypt(input: ByteArray, key: ByteArray): ByteArray {
        require(key.size == Aes128.BLOCK_SIZE_BYTES) { "AES-128 requires a 16-byte key" }
        val paddingSize = Aes128.BLOCK_SIZE_BYTES - input.size % Aes128.BLOCK_SIZE_BYTES
        val padded = ByteArray(input.size + paddingSize)
        input.copyInto(padded)
        padded.fill(
            element = paddingSize.toByte(),
            fromIndex = input.size,
            toIndex = padded.size,
        )

        val aes = Aes128(key)
        val encrypted = ByteArray(padded.size)
        for (offset in padded.indices step Aes128.BLOCK_SIZE_BYTES) {
            aes.encryptBlock(padded, offset, encrypted, offset)
        }
        return encrypted
    }

    fun encodeBase64(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val output = StringBuilder(((input.size + 2) / 3) * 4)
        var offset = 0
        while (offset < input.size) {
            val first = input[offset].toInt() and 0xFF
            val hasSecond = offset + 1 < input.size
            val hasThird = offset + 2 < input.size
            val second = if (hasSecond) input[offset + 1].toInt() and 0xFF else 0
            val third = if (hasThird) input[offset + 2].toInt() and 0xFF else 0
            val bits = (first shl 16) or (second shl 8) or third

            output.append(BASE64_ALPHABET[(bits ushr 18) and 0x3F])
            output.append(BASE64_ALPHABET[(bits ushr 12) and 0x3F])
            output.append(if (hasSecond) BASE64_ALPHABET[(bits ushr 6) and 0x3F] else '=')
            output.append(if (hasThird) BASE64_ALPHABET[bits and 0x3F] else '=')
            offset += 3
        }
        return output.toString()
    }

    private fun adler32(input: ByteArray): Int {
        var first = 1
        var second = 0
        for (byte in input) {
            first += byte.toInt() and 0xFF
            if (first >= ZLIB_MODULUS) first -= ZLIB_MODULUS
            second += first
            if (second >= ZLIB_MODULUS) second %= ZLIB_MODULUS
        }
        return (second shl 16) or first
    }

    private fun ByteArray.writeAscii(offset: Int, value: String): Int {
        val bytes = value.encodeToByteArray()
        bytes.copyInto(this, destinationOffset = offset)
        return offset + bytes.size
    }

    private fun ByteArray.writeUInt32LittleEndian(offset: Int, value: Int): Int {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        this[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        return offset + 4
    }

    private fun ByteArray.writeFloatLittleEndian(offset: Int, value: Float): Int =
        writeUInt32LittleEndian(offset, value.toBits())
}

private class Aes128(key: ByteArray) {
    private val roundKeys = expandKey(key)

    fun encryptBlock(
        input: ByteArray,
        inputOffset: Int,
        output: ByteArray,
        outputOffset: Int,
    ) {
        val state = IntArray(BLOCK_SIZE_BYTES) { index ->
            input[inputOffset + index].toInt() and 0xFF
        }

        addRoundKey(state, round = 0)
        for (round in 1 until ROUND_COUNT) {
            substituteBytes(state)
            shiftRows(state)
            mixColumns(state)
            addRoundKey(state, round)
        }
        substituteBytes(state)
        shiftRows(state)
        addRoundKey(state, round = ROUND_COUNT)

        for (index in state.indices) {
            output[outputOffset + index] = state[index].toByte()
        }
    }

    private fun addRoundKey(state: IntArray, round: Int) {
        val keyOffset = round * BLOCK_SIZE_BYTES
        for (index in state.indices) {
            state[index] = state[index] xor roundKeys[keyOffset + index]
        }
    }

    private fun substituteBytes(state: IntArray) {
        for (index in state.indices) {
            state[index] = S_BOX[state[index]]
        }
    }

    private fun shiftRows(state: IntArray) {
        val rowOneFirst = state[1]
        state[1] = state[5]
        state[5] = state[9]
        state[9] = state[13]
        state[13] = rowOneFirst

        val rowTwoFirst = state[2]
        val rowTwoSecond = state[6]
        state[2] = state[10]
        state[6] = state[14]
        state[10] = rowTwoFirst
        state[14] = rowTwoSecond

        val rowThreeFirst = state[3]
        val rowThreeLast = state[15]
        state[15] = state[11]
        state[11] = state[7]
        state[7] = rowThreeFirst
        state[3] = rowThreeLast
    }

    private fun mixColumns(state: IntArray) {
        for (column in 0 until 4) {
            val offset = column * 4
            val first = state[offset]
            val second = state[offset + 1]
            val third = state[offset + 2]
            val fourth = state[offset + 3]
            val all = first xor second xor third xor fourth

            state[offset] = first xor all xor multiplyByTwo(first xor second)
            state[offset + 1] = second xor all xor multiplyByTwo(second xor third)
            state[offset + 2] = third xor all xor multiplyByTwo(third xor fourth)
            state[offset + 3] = fourth xor all xor multiplyByTwo(fourth xor first)
        }
    }

    private fun expandKey(key: ByteArray): IntArray {
        require(key.size == BLOCK_SIZE_BYTES)
        val expanded = IntArray(EXPANDED_KEY_SIZE_BYTES)
        for (index in key.indices) {
            expanded[index] = key[index].toInt() and 0xFF
        }

        var generated = BLOCK_SIZE_BYTES
        var roundConstant = 1
        val temporary = IntArray(4)
        while (generated < expanded.size) {
            for (index in temporary.indices) {
                temporary[index] = expanded[generated - 4 + index]
            }
            if (generated % BLOCK_SIZE_BYTES == 0) {
                val first = temporary[0]
                temporary[0] = S_BOX[temporary[1]] xor roundConstant
                temporary[1] = S_BOX[temporary[2]]
                temporary[2] = S_BOX[temporary[3]]
                temporary[3] = S_BOX[first]
                roundConstant = multiplyByTwo(roundConstant)
            }
            for (index in temporary.indices) {
                expanded[generated] = expanded[generated - BLOCK_SIZE_BYTES] xor temporary[index]
                generated += 1
            }
        }
        return expanded
    }

    companion object {
        const val BLOCK_SIZE_BYTES = 16
        private const val ROUND_COUNT = 10
        private const val EXPANDED_KEY_SIZE_BYTES = BLOCK_SIZE_BYTES * (ROUND_COUNT + 1)

        private fun multiplyByTwo(value: Int): Int =
            ((value shl 1) xor if (value and 0x80 != 0) 0x11B else 0) and 0xFF

        private val S_BOX = intArrayOf(
            0x63, 0x7C, 0x77, 0x7B, 0xF2, 0x6B, 0x6F, 0xC5,
            0x30, 0x01, 0x67, 0x2B, 0xFE, 0xD7, 0xAB, 0x76,
            0xCA, 0x82, 0xC9, 0x7D, 0xFA, 0x59, 0x47, 0xF0,
            0xAD, 0xD4, 0xA2, 0xAF, 0x9C, 0xA4, 0x72, 0xC0,
            0xB7, 0xFD, 0x93, 0x26, 0x36, 0x3F, 0xF7, 0xCC,
            0x34, 0xA5, 0xE5, 0xF1, 0x71, 0xD8, 0x31, 0x15,
            0x04, 0xC7, 0x23, 0xC3, 0x18, 0x96, 0x05, 0x9A,
            0x07, 0x12, 0x80, 0xE2, 0xEB, 0x27, 0xB2, 0x75,
            0x09, 0x83, 0x2C, 0x1A, 0x1B, 0x6E, 0x5A, 0xA0,
            0x52, 0x3B, 0xD6, 0xB3, 0x29, 0xE3, 0x2F, 0x84,
            0x53, 0xD1, 0x00, 0xED, 0x20, 0xFC, 0xB1, 0x5B,
            0x6A, 0xCB, 0xBE, 0x39, 0x4A, 0x4C, 0x58, 0xCF,
            0xD0, 0xEF, 0xAA, 0xFB, 0x43, 0x4D, 0x33, 0x85,
            0x45, 0xF9, 0x02, 0x7F, 0x50, 0x3C, 0x9F, 0xA8,
            0x51, 0xA3, 0x40, 0x8F, 0x92, 0x9D, 0x38, 0xF5,
            0xBC, 0xB6, 0xDA, 0x21, 0x10, 0xFF, 0xF3, 0xD2,
            0xCD, 0x0C, 0x13, 0xEC, 0x5F, 0x97, 0x44, 0x17,
            0xC4, 0xA7, 0x7E, 0x3D, 0x64, 0x5D, 0x19, 0x73,
            0x60, 0x81, 0x4F, 0xDC, 0x22, 0x2A, 0x90, 0x88,
            0x46, 0xEE, 0xB8, 0x14, 0xDE, 0x5E, 0x0B, 0xDB,
            0xE0, 0x32, 0x3A, 0x0A, 0x49, 0x06, 0x24, 0x5C,
            0xC2, 0xD3, 0xAC, 0x62, 0x91, 0x95, 0xE4, 0x79,
            0xE7, 0xC8, 0x37, 0x6D, 0x8D, 0xD5, 0x4E, 0xA9,
            0x6C, 0x56, 0xF4, 0xEA, 0x65, 0x7A, 0xAE, 0x08,
            0xBA, 0x78, 0x25, 0x2E, 0x1C, 0xA6, 0xB4, 0xC6,
            0xE8, 0xDD, 0x74, 0x1F, 0x4B, 0xBD, 0x8B, 0x8A,
            0x70, 0x3E, 0xB5, 0x66, 0x48, 0x03, 0xF6, 0x0E,
            0x61, 0x35, 0x57, 0xB9, 0x86, 0xC1, 0x1D, 0x9E,
            0xE1, 0xF8, 0x98, 0x11, 0x69, 0xD9, 0x8E, 0x94,
            0x9B, 0x1E, 0x87, 0xE9, 0xCE, 0x55, 0x28, 0xDF,
            0x8C, 0xA1, 0x89, 0x0D, 0xBF, 0xE6, 0x42, 0x68,
            0x41, 0x99, 0x2D, 0x0F, 0xB0, 0x54, 0xBB, 0x16,
        )
    }
}

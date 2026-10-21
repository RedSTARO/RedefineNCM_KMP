package com.leejlredstar.redefinencm.kmp.recognition

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import kotlin.coroutines.coroutineContext

private val desktopCaptureRates = intArrayOf(8_000, 44_100, 48_000)

class JvmMicrophoneRecorder : ExclusiveMicrophoneRecorder() {

    override suspend fun captureExclusively(
        durationMillis: Long,
        onProgress: (elapsedMillis: Long, level: Float) -> Unit,
    ): CapturedPcm = withContext(Dispatchers.IO) {
        val format = desktopCaptureRates
            .asSequence()
            .map(::pcmFormat)
            .firstOrNull { candidate ->
                AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, candidate))
            }
            ?: throw MicrophoneUnavailableException(strings.jvmMicNoPcmInput)

        val line = try {
            AudioSystem.getTargetDataLine(format).apply {
                open(format, maxOf((format.frameRate * format.frameSize / 5).toInt(), 4_096))
            }
        } catch (error: SecurityException) {
            throw MicrophonePermissionDeniedException(cause = error)
        } catch (error: LineUnavailableException) {
            throw MicrophoneBusyException(cause = error)
        } catch (error: IllegalArgumentException) {
            throw MicrophoneUnavailableException(cause = error)
        }

        val sampleRateHz = format.sampleRate.toInt()
        val targetSamples = pcmTargetSampleCount(durationMillis, sampleRateHz)
        val targetBytes = targetSamples * 2
        val capturedBytes = ByteArrayOutputStream(targetBytes)
        val buffer = ByteArray(minOf(maxOf(line.bufferSize / 4, 2_048), 16_384))

        try {
            line.start()
            var lastDataAt = System.nanoTime()
            while (capturedBytes.size() < targetBytes) {
                coroutineContext.ensureActive()
                // TargetDataLine.read may otherwise block indefinitely waiting for the entire
                // requested length, preventing coroutine cancellation from reaching finally.
                val availableBytes = line.available()
                val requested = minOf(
                    buffer.size,
                    targetBytes - capturedBytes.size(),
                    availableBytes,
                ).let { it - (it % format.frameSize) }
                if (requested <= 0) {
                    // A line that delivers nothing would otherwise be waited on for ever.
                    if (System.nanoTime() - lastDataAt > NO_DATA_TIMEOUT_NANOS) {
                        throw MicrophoneUnavailableException(strings.desktopMicNoData)
                    }
                    delay(10L)
                    continue
                }
                val read = line.read(buffer, 0, requested)
                if (read <= 0) continue
                lastDataAt = System.nanoTime()
                capturedBytes.write(buffer, 0, read)

                var energy = 0.0
                var frameCount = 0
                var index = 0
                while (index + 1 < read) {
                    val sample = pcmSampleAt(buffer, index)
                    energy += sample * sample
                    frameCount++
                    index += 2
                }
                if (frameCount > 0) {
                    onProgress(
                        pcmElapsedMillis(capturedBytes.size() / 2, sampleRateHz),
                        pcmRmsLevel(energy, frameCount),
                    )
                }
            }
        } finally {
            runCatching { line.stop() }
            runCatching { line.flush() }
            line.close()
        }

        val bytes = capturedBytes.toByteArray()
        // A microphone the system blocks (macOS privacy settings, Windows' "let desktop apps use
        // the microphone") still opens, and delivers exact zeros: a real microphone always
        // records some noise. Fingerprinting the silence ended in "no match" three times over.
        if (isDigitalSilence(bytes)) throw MicrophoneUnavailableException(strings.desktopMicSilent)
        val samples = FloatArray(targetSamples)
        for (sampleIndex in samples.indices) {
            samples[sampleIndex] = pcmSampleAt(bytes, sampleIndex * 2)
        }
        CapturedPcm(samples, sampleRateHz)
    }
}

/** How long a started line may deliver nothing before it counts as giving no sound at all. */
private const val NO_DATA_TIMEOUT_NANOS = 2_000_000_000L

/** Whether every 16-bit sample in [pcm] is exactly zero. */
internal fun isDigitalSilence(pcm: ByteArray): Boolean = pcm.isNotEmpty() && pcm.all { it == 0.toByte() }

private fun pcmFormat(sampleRateHz: Int): AudioFormat = AudioFormat(
    AudioFormat.Encoding.PCM_SIGNED,
    sampleRateHz.toFloat(),
    16,
    1,
    2,
    sampleRateHz.toFloat(),
    false,
)

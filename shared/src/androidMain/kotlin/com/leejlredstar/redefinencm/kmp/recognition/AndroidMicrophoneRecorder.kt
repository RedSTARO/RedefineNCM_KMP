package com.leejlredstar.redefinencm.kmp.recognition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.sqrt

private const val ANDROID_CAPTURE_SAMPLE_RATE_HZ = 44_100

class AndroidMicrophoneRecorder(
    private val context: Context,
) : MicrophoneRecorder {
    private val captureMutex = Mutex()

    override suspend fun capture(
        durationMillis: Long,
        onProgress: (elapsedMillis: Long, level: Float) -> Unit,
    ): CapturedPcm {
        require(durationMillis > 0L) { "录音时长必须大于 0" }
        if (!captureMutex.tryLock()) throw MicrophoneBusyException()
        try {
            return captureLocked(durationMillis, onProgress)
        } finally {
            captureMutex.unlock()
        }
    }

    private suspend fun captureLocked(
        durationMillis: Long,
        onProgress: (elapsedMillis: Long, level: Float) -> Unit,
    ): CapturedPcm = withContext(Dispatchers.IO) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw MicrophonePermissionDeniedException()
        }

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minimumBufferBytes = AudioRecord.getMinBufferSize(
            ANDROID_CAPTURE_SAMPLE_RATE_HZ,
            channelConfig,
            encoding,
        )
        if (minimumBufferBytes <= 0) {
            throw MicrophoneUnavailableException("设备不支持单声道 PCM 录音")
        }

        val source = if (supportsUnprocessedInput()) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        val recorder = try {
            AudioRecord(
                source,
                ANDROID_CAPTURE_SAMPLE_RATE_HZ,
                channelConfig,
                encoding,
                maxOf(minimumBufferBytes, 8_192),
            )
        } catch (error: SecurityException) {
            throw MicrophonePermissionDeniedException(cause = error)
        } catch (error: IllegalArgumentException) {
            throw MicrophoneUnavailableException(cause = error)
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw MicrophoneUnavailableException("录音设备初始化失败")
        }

        val targetSamples = ceil(
            durationMillis * ANDROID_CAPTURE_SAMPLE_RATE_HZ.toDouble() / 1_000.0,
        ).toInt()
        val samples = FloatArray(targetSamples)
        val readBuffer = ShortArray(maxOf(minimumBufferBytes / 2, 2_048))
        var written = 0

        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw MicrophoneUnavailableException("麦克风未进入录音状态")
            }

            while (written < samples.size) {
                coroutineContext.ensureActive()
                val requested = minOf(readBuffer.size, samples.size - written)
                val read = recorder.read(
                    readBuffer,
                    0,
                    requested,
                    AudioRecord.READ_BLOCKING,
                )
                if (read < 0) {
                    throw MicrophoneUnavailableException("读取麦克风失败，错误码 $read")
                }
                if (read == 0) continue

                var energy = 0.0
                for (index in 0 until read) {
                    val sample = readBuffer[index].toFloat() / 32_768f
                    samples[written + index] = sample
                    energy += sample * sample
                }
                written += read
                onProgress(
                    written * 1_000L / ANDROID_CAPTURE_SAMPLE_RATE_HZ,
                    sqrt(energy / read).toFloat().coerceIn(0f, 1f),
                )
            }
        } catch (error: SecurityException) {
            throw MicrophonePermissionDeniedException(cause = error)
        } catch (error: IllegalStateException) {
            throw MicrophoneUnavailableException(cause = error)
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { recorder.stop() }
            }
            recorder.release()
        }

        CapturedPcm(samples, ANDROID_CAPTURE_SAMPLE_RATE_HZ)
    }

    private fun supportsUnprocessedInput(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    }
}

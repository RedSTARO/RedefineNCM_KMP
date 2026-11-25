@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.recognition

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.cinterop.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioInputNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import kotlin.coroutines.resume

class IosMicrophoneRecorder : ExclusiveMicrophoneRecorder() {

    override suspend fun captureExclusively(
        durationMillis: Long,
        onProgress: (elapsedMillis: Long, level: Float) -> Unit,
    ): CapturedPcm = withContext(Dispatchers.Main) {
        captureOnMain(durationMillis, onProgress)
    }

    private suspend fun captureOnMain(
        durationMillis: Long,
        onProgress: (elapsedMillis: Long, level: Float) -> Unit,
    ): CapturedPcm {
        if (!awaitIosMicrophonePermission()) throw MicrophonePermissionDeniedException()

        val session = AVAudioSession.sharedInstance()
        var engine: AVAudioEngine? = null
        var inputNode: AVAudioInputNode? = null
        var tapInstalled = false
        var recordingSessionAttempted = false

        try {
            recordingSessionAttempted = true
            requireAudioSessionStep(
                session.setCategory(AVAudioSessionCategoryRecord, error = null),
                strings.iosAudioSessionCategoryFailed,
            )
            requireAudioSessionStep(
                session.setMode(AVAudioSessionModeMeasurement, error = null),
                strings.iosAudioSessionModeFailed,
            )
            requireAudioSessionStep(
                session.setActive(true, error = null),
                strings.iosAudioSessionActivateFailed,
            )

            // The engine and its input node are made only now: the input node takes the hardware
            // format of the session at the time it is first read, and under the playback category
            // there is no input to read.
            val recordingEngine = AVAudioEngine().also { engine = it }
            val input = recordingEngine.inputNode.also { inputNode = it }
            val format = input.outputFormatForBus(0uL)
            val sampleRateHz = format.sampleRate.toInt()
            if (sampleRateHz <= 0 || format.channelCount == 0u) {
                throw MicrophoneUnavailableException(strings.iosMicNoInputFormat)
            }
            val targetSamples = pcmTargetSampleCount(durationMillis, sampleRateHz)
            val samples = FloatArray(targetSamples)
            val completion = CompletableDeferred<CapturedPcm>()
            var written = 0

            input.installTapOnBus(
                bus = 0uL,
                bufferSize = 1_024u,
                format = format,
                block = tap@{ buffer, _ ->
                    if (completion.isCompleted) return@tap
                    val pcmBuffer = buffer ?: return@tap
                    try {
                        val channel = pcmBuffer.floatChannelData?.get(0)
                            ?: throw MicrophoneUnavailableException(strings.iosMicNoFloat32Channel)
                        val count = minOf(pcmBuffer.frameLength.toInt(), samples.size - written)
                        var energy = 0.0
                        for (index in 0 until count) {
                            val sample = channel[index].coerceIn(-1f, 1f)
                            samples[written + index] = sample
                            energy += sample * sample
                        }
                        written += count
                        if (count > 0) {
                            onProgress(
                                pcmElapsedMillis(written, sampleRateHz),
                                pcmRmsLevel(energy, count),
                            )
                        }
                        if (written == samples.size) {
                            completion.complete(CapturedPcm(samples, sampleRateHz))
                        }
                    } catch (error: Throwable) {
                        completion.completeExceptionally(error)
                    }
                },
            )
            tapInstalled = true
            recordingEngine.prepare()
            if (!recordingEngine.startAndReturnError(null)) {
                throw MicrophoneUnavailableException(strings.iosRecordingEngineStartFailed)
            }
            return completion.await()
        } finally {
            engine?.stop()
            if (tapInstalled) runCatching { inputNode?.removeTapOnBus(0uL) }
            if (recordingSessionAttempted) restorePlaybackSession(session)
        }
    }
}

internal fun requestIosMicrophonePermission(onResult: (Boolean) -> Unit) {
    val session = AVAudioSession.sharedInstance()
    when (session.recordPermission) {
        AVAudioSessionRecordPermissionGranted -> onResult(true)
        AVAudioSessionRecordPermissionDenied -> onResult(false)
        else -> session.requestRecordPermission(onResult)
    }
}

private suspend fun awaitIosMicrophonePermission(): Boolean =
    suspendCancellableCoroutine { continuation ->
        requestIosMicrophonePermission { granted ->
            if (continuation.isActive) continuation.resume(granted)
        }
    }

private fun requireAudioSessionStep(success: Boolean, message: String) {
    if (!success) throw MicrophoneUnavailableException(message)
}

private fun restorePlaybackSession(session: AVAudioSession) {
    // Restoration runs from finally, including coroutine cancellation. A restoration failure
    // must not replace the original capture error or cancellation with a second exception.
    runCatching {
        // Nothing of this app plays now (recognition paused it): the session is given back so
        // other apps may resume, and the player activates it again when it plays.
        if (!session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)) {
            println("AVAudioSession: could not deactivate the recording session")
        }
        if (!session.setCategory(AVAudioSessionCategoryPlayback, error = null)) {
            println("AVAudioSession: could not restore the playback category")
        }
        if (!session.setMode(AVAudioSessionModeDefault, error = null)) {
            println("AVAudioSession: could not restore the default mode")
        }
    }.onFailure { error ->
        println("AVAudioSession restore failed: ${error.message}")
    }
}

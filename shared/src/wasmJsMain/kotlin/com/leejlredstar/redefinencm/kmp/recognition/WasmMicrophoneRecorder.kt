@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.recognition

import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.w3c.dom.events.Event
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamConstraints
import org.w3c.dom.mediacapture.MediaTrackConstraints
import kotlin.js.JsAny
import kotlin.js.JsException
import kotlin.js.JsName
import kotlin.js.Promise
import kotlin.js.toArray
import kotlin.js.toJsBoolean
import kotlin.js.thrownValue
import kotlin.js.unsafeCast
import kotlin.math.sqrt
import kotlin.time.TimeSource

private const val WEB_CAPTURE_PADDING_MILLIS = 250L

class WasmMicrophoneRecorder : MicrophoneRecorder {
    private val captureMutex = Mutex()

    override suspend fun capture(
        durationMillis: Long,
        onProgress: (elapsedMillis: Long, level: Float) -> Unit,
    ): CapturedPcm {
        require(durationMillis > 0L) { "录音时长必须大于 0" }
        ensureSecureWebMicrophoneContext()
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
    ): CapturedPcm {
        val stream = requestWebMicrophoneStream()

        var recorder: WebMediaRecorder? = null
        var audioContext: WebAudioContext? = null
        var sourceNode: WebMediaStreamAudioSourceNode? = null
        var analyserNode: WebAnalyserNode? = null
        var gainNode: WebGainNode? = null
        try {
            val context = try {
                WebAudioContext()
            } catch (error: Throwable) {
                throw MicrophoneUnavailableException("浏览器 Web Audio 不可用", error)
            }
            audioContext = context
            // 权限弹窗返回后可能已经丢失瞬时用户激活；解码不要求 running 状态，
            // 因此恢复实时分析失败时只会让电平保持为 0，不应让录音本身失败。
            try {
                context.resume().await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Decoding works while suspended; a failed resume only disables live metering.
            }
            val source = context.createMediaStreamSource(stream)
            sourceNode = source
            val analyser = context.createAnalyser().apply { fftSize = 1_024 }
            analyserNode = analyser
            val silentGain = context.createGain().apply { gain.value = 0.0 }
            gainNode = silentGain
            source.connect(analyser)
            analyser.connect(silentGain)
            silentGain.connect(context.destination)

            val completedBlob = CompletableDeferred<WebBlob>()
            val mediaRecorder = try {
                WebMediaRecorder(stream)
            } catch (error: Throwable) {
                throw MicrophoneUnavailableException("浏览器不支持 MediaRecorder", error)
            }
            recorder = mediaRecorder
            mediaRecorder.ondataavailable = { event ->
                completedBlob.complete(event.data)
            }
            mediaRecorder.onerror = {
                completedBlob.completeExceptionally(
                    MicrophoneUnavailableException("浏览器录音器报告错误"),
                )
            }
            mediaRecorder.onstop = {
                if (!completedBlob.isCompleted) {
                    completedBlob.completeExceptionally(
                        MicrophoneUnavailableException("浏览器没有返回录音数据"),
                    )
                }
            }

            mediaRecorder.start()
            val meterData = Float32Array(1_024)
            val started = TimeSource.Monotonic.markNow()
            // MediaRecorder codecs may trim a partial final frame. Capture a small surplus and
            // let the common resampler crop exactly the requested three seconds.
            val stopAfterMillis = durationMillis + WEB_CAPTURE_PADDING_MILLIS
            while (true) {
                val elapsed = started.elapsedNow().inWholeMilliseconds
                if (elapsed >= stopAfterMillis) break
                analyser.getFloatTimeDomainData(meterData)
                var energy = 0.0
                for (index in 0 until meterData.length) {
                    val sample = meterData[index]
                    energy += sample * sample
                }
                val level = if (meterData.length == 0) {
                    0f
                } else {
                    sqrt(energy / meterData.length).toFloat().coerceIn(0f, 1f)
                }
                onProgress(elapsed.coerceAtMost(durationMillis), level)
                delay(minOf(50L, stopAfterMillis - elapsed))
            }
            mediaRecorder.stop()
            onProgress(durationMillis, 0f)

            val blob = completedBlob.await()
            val encodedAudio = blob.arrayBuffer().await()
            val decoded = context.decodeAudioData(encodedAudio).await()
            if (decoded.numberOfChannels <= 0 || decoded.sampleRate <= 0.0) {
                throw MicrophoneUnavailableException("浏览器返回了无效的录音格式")
            }
            val channel = decoded.getChannelData(0)
            val samples = FloatArray(channel.length) { index ->
                channel[index].coerceIn(-1f, 1f)
            }
            return CapturedPcm(samples, decoded.sampleRate.toInt())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: MicrophoneCaptureException) {
            throw error
        } catch (error: Throwable) {
            throw mapWebMicrophoneError(error)
        } finally {
            recorder?.let { activeRecorder ->
                if (activeRecorder.state == "recording") runCatching { activeRecorder.stop() }
                activeRecorder.ondataavailable = null
                activeRecorder.onerror = null
                activeRecorder.onstop = null
            }
            runCatching { sourceNode?.disconnect() }
            runCatching { analyserNode?.disconnect() }
            runCatching { gainNode?.disconnect() }
            stopWebMicrophoneStream(stream)
            audioContext?.let { context ->
                try {
                    context.close().await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // The stream tracks are already stopped; closing Web Audio is best-effort.
                }
            }
        }
    }
}

private suspend fun requestWebMicrophoneStream(): MediaStream {
    val pending = try {
        window.navigator.mediaDevices.getUserMedia(audioOnlyConstraints())
    } catch (error: Throwable) {
        throw mapWebMicrophoneError(error)
    }

    return try {
        pending.await()
    } catch (cancelled: CancellationException) {
        // Promise.await() has prompt cancellation but cannot cancel getUserMedia itself. If the
        // permission prompt later succeeds, retain a success callback that immediately releases
        // every returned track instead of leaking an unreachable live microphone stream.
        pending.then(
            onFulfilled = { lateStream ->
                runCatching { stopWebMicrophoneStream(lateStream) }
                null
            },
            onRejected = { null },
        )
        throw cancelled
    } catch (error: Throwable) {
        throw mapWebMicrophoneError(error)
    }
}

internal fun ensureSecureWebMicrophoneContext() {
    val protocol = window.location.protocol.lowercase()
    val host = window.location.hostname.lowercase()
    val isLocalhost = host == "localhost" || host == "127.0.0.1" || host == "::1"
    if (protocol != "https:" && !isLocalhost) throw InsecureMicrophoneContextException()
}

private fun audioOnlyConstraints(): MediaStreamConstraints = MediaStreamConstraints(
    audio = MediaTrackConstraints(
        echoCancellation = false.toJsBoolean(),
        autoGainControl = false.toJsBoolean(),
        noiseSuppression = false.toJsBoolean(),
    ),
)

private fun stopWebMicrophoneStream(stream: MediaStream) {
    stream.getTracks().toArray().forEach { track -> track.stop() }
}

private fun mapWebMicrophoneError(error: Throwable): MicrophoneCaptureException {
    if (error is MicrophoneCaptureException) return error
    val jsError = (error as? JsException)?.thrownValue?.unsafeCast<WebDomError>()
    val detail = jsError?.message?.takeIf { it.isNotBlank() }
    return when (jsError?.name) {
        "NotAllowedError" -> MicrophonePermissionDeniedException(detail ?: "浏览器拒绝了麦克风权限", error)
        "NotFoundError" -> MicrophoneUnavailableException(detail ?: "没有找到麦克风", error)
        "NotReadableError", "AbortError" -> MicrophoneBusyException(detail ?: "浏览器无法读取麦克风", error)
        "SecurityError" -> InsecureMicrophoneContextException(detail ?: "当前页面不能访问麦克风", error)
        else -> MicrophoneUnavailableException(detail ?: "浏览器麦克风录音失败", error)
    }
}

private external interface WebDomError : JsAny {
    val name: String
    val message: String
}

private external interface WebBlob : JsAny {
    fun arrayBuffer(): Promise<ArrayBuffer>
}

private external interface WebBlobEvent : JsAny {
    val data: WebBlob
}

@JsName("MediaRecorder")
private external class WebMediaRecorder(stream: MediaStream) : JsAny {
    var ondataavailable: ((WebBlobEvent) -> Unit)?
    var onerror: ((Event) -> Unit)?
    var onstop: ((Event) -> Unit)?
    val state: String

    fun start()
    fun stop()
}

private external interface WebAudioNode : JsAny {
    fun connect(destination: WebAudioNode): WebAudioNode
    fun disconnect()
}

private external interface WebMediaStreamAudioSourceNode : WebAudioNode

private external interface WebAnalyserNode : WebAudioNode {
    var fftSize: Int
    fun getFloatTimeDomainData(array: Float32Array)
}

private external interface WebAudioParam : JsAny {
    var value: Double
}

private external interface WebGainNode : WebAudioNode {
    val gain: WebAudioParam
}

private external interface WebAudioBuffer : JsAny {
    val numberOfChannels: Int
    val sampleRate: Double
    fun getChannelData(channel: Int): Float32Array
}

@JsName("AudioContext")
private external class WebAudioContext : JsAny {
    constructor()

    val destination: WebAudioNode
    fun createMediaStreamSource(stream: MediaStream): WebMediaStreamAudioSourceNode
    fun createAnalyser(): WebAnalyserNode
    fun createGain(): WebGainNode
    fun decodeAudioData(audioData: ArrayBuffer): Promise<WebAudioBuffer>
    fun resume(): Promise<JsAny?>
    fun close(): Promise<JsAny?>
}

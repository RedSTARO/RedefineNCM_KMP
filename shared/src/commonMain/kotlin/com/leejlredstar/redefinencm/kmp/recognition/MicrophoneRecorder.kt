package com.leejlredstar.redefinencm.kmp.recognition

import com.leejlredstar.redefinencm.kmp.i18n.strings

/** 一段已复制到 Kotlin 内存中的单声道 PCM。样本值应位于 -1f..1f。 */
data class CapturedPcm(
    val monoSamples: FloatArray,
    val sampleRateHz: Int,
)

/**
 * 平台麦克风输入。
 *
 * 调用方通过取消当前协程来中止录音；平台实现必须在取消和异常路径释放录音设备。
 */
interface MicrophoneRecorder {
    suspend fun capture(
        durationMillis: Long,
        onProgress: (elapsedMillis: Long, level: Float) -> Unit,
    ): CapturedPcm
}

sealed class MicrophoneCaptureException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class MicrophonePermissionDeniedException(
    message: String = strings.microphonePermissionDenied,
    cause: Throwable? = null,
) : MicrophoneCaptureException(message, cause)

class MicrophoneUnavailableException(
    message: String = strings.microphoneUnavailable,
    cause: Throwable? = null,
) : MicrophoneCaptureException(message, cause)

class MicrophoneBusyException(
    message: String = strings.microphoneBusy,
    cause: Throwable? = null,
) : MicrophoneCaptureException(message, cause)

class InsecureMicrophoneContextException(
    message: String = strings.microphoneNeedsSecureContext,
    cause: Throwable? = null,
) : MicrophoneCaptureException(message, cause)

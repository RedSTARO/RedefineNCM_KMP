@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.transition

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.js.JsAny
import kotlin.js.JsException
import kotlin.js.Promise
import kotlin.js.thrownValue
import kotlin.js.unsafeCast

/**
 * The beat model in the browser, through ONNX Runtime Web, on an NPU or a GPU and never the CPU.
 *
 * WebNN reaches a neural accelerator where the browser exposes one and falls back to the GPU on
 * request; WebGPU reaches the GPU everywhere it is enabled. Each is tried with ONNX Runtime's CPU
 * fallback disabled, so a session that would run part of the model in WebAssembly on the CPU is
 * refused instead of created. ONNX Runtime Web is imported dynamically, so its JavaScript and its
 * WebAssembly are fetched only when smart transitions first need the model.
 */
internal class WebBeatModelLoader : BeatModelLoader {
    override suspend fun load(): BeatModelAvailability {
        val ort = try {
            importOnnxRuntimeWeb().await<JsAny>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            return BeatModelAvailability.Unavailable(strings.onnxWebLoadFailed(failure.jsMessage()))
        }
        val model = try {
            fetchModel(MODEL_URL).await<JsAny>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            return BeatModelAvailability.Unavailable(strings.beatModelDownloadFailed(failure.jsMessage()))
        }
        val failures = mutableListOf<String>()
        val attempts = buildList {
            if (hasWebNn()) {
                add(Attempt("webnn", "npu", InferenceAccelerator.NPU, "WebNN"))
                add(Attempt("webnn", "gpu", InferenceAccelerator.GPU, "WebNN"))
            }
            if (hasWebGpu()) add(Attempt("webgpu", "", InferenceAccelerator.GPU, "WebGPU"))
        }
        if (attempts.isEmpty()) {
            return BeatModelAvailability.Unavailable(strings.browserNoWebNnOrWebGpu)
        }
        for (attempt in attempts) {
            val session = try {
                createSession(ort, model, attempt.provider, attempt.deviceType).await<JsAny>()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                failures += strings.labelValue(attempt.label, failure.jsMessage())
                continue
            }
            val beatModel = WebBeatActivationModel(ort, session, attempt.accelerator, attempt.label)
            try {
                // A session that compiles can still fail its first run; find out now.
                beatModel.infer(FloatArray(BEAT_MODEL_CHUNK_FRAMES * BeatModelFeatures.MEL_BINS))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                failures += strings.labelValue(attempt.label, failure.jsMessage())
                beatModel.close()
                continue
            }
            return BeatModelAvailability.Ready(beatModel)
        }
        return BeatModelAvailability.Unavailable(
            strings.browserBeatModelDevicesFailed(failures.joinToString(strings.clauseSeparator)),
        )
    }

    private class Attempt(
        val provider: String,
        val deviceType: String,
        val accelerator: InferenceAccelerator,
        val label: String,
    )

    private companion object {
        const val MODEL_URL = "automix/beat_this_small0_t750.onnx"
    }
}

private class WebBeatActivationModel(
    private val ort: JsAny,
    private val session: JsAny,
    override val accelerator: InferenceAccelerator,
    override val deviceLabel: String,
) : BeatActivationModel {
    override suspend fun infer(spectrogram: FloatArray): FloatArray {
        val input = Float32Array(spectrogram.size)
        for (i in spectrogram.indices) input[i] = spectrogram[i]
        val output = runSession(ort, session, input).await<Float32Array>()
        return FloatArray(output.length) { output[it] }
    }

    override fun close() {
        releaseSession(session)
    }
}

private fun Throwable.jsMessage(): String =
    ((this as? JsException)?.thrownValue?.unsafeCast<JsError>()?.message)
        ?: message
        ?: this::class.simpleName.orEmpty()

private external interface JsError : JsAny {
    val message: String?
}

@JsFun("() => import('onnxruntime-web')")
private external fun importOnnxRuntimeWeb(): Promise<JsAny>

@JsFun(
    """(url) => fetch(url).then(response => {
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.arrayBuffer();
    }).then(buffer => new Uint8Array(buffer))""",
)
private external fun fetchModel(url: String): Promise<JsAny>

@JsFun("() => typeof navigator !== 'undefined' && 'ml' in navigator")
private external fun hasWebNn(): Boolean

@JsFun("() => typeof navigator !== 'undefined' && 'gpu' in navigator")
private external fun hasWebGpu(): Boolean

@JsFun(
    """(ort, model, provider, deviceType) => ort.InferenceSession.create(model, {
        executionProviders: [deviceType ? { name: provider, deviceType: deviceType } : provider],
        graphOptimizationLevel: 'all',
        extra: { session: { disable_cpu_ep_fallback: '1' } },
    })""",
)
private external fun createSession(ort: JsAny, model: JsAny, provider: String, deviceType: String): Promise<JsAny>

@JsFun(
    """(ort, session, input) => session.run({
        spectrogram: new ort.Tensor('float32', input, [1, 750, 128]),
    }).then(result => result.logits.data)""",
)
private external fun runSession(ort: JsAny, session: JsAny, input: Float32Array): Promise<Float32Array>

@JsFun("(session) => { try { session.release(); } catch (_) {} }")
private external fun releaseSession(session: JsAny)

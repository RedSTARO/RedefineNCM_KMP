package com.leejlredstar.redefinencm.kmp.transition

import android.content.Context
import android.os.Build
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import com.google.ai.edge.litert.TensorBufferType
import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * The beat model on Android, through LiteRT's CompiledModel API, on the NPU or the GPU and never
 * the CPU.
 *
 * - **NPU**: LiteRT reaches a phone's NPU through its SoC vendor's dispatch and compiler-plugin
 *   libraries (Qualcomm AI Engine Direct, MediaTek NeuroPilot, Google Tensor), looked up in the
 *   app's native library directory. This build does not bundle them: they are the vendors' own
 *   licences and several megabytes per SoC family. So the attempt fails on every device today and
 *   the GPU is used; it stays for a build that adds them.
 * - **GPU**: LiteRT's GPU accelerator, OpenCL where the driver has it and OpenGL ES otherwise.
 *
 * LiteRT quietly runs any op its GPU backend lacks on the CPU. The model used here was rewritten
 * until LiteRT reports it fully accelerated on a GPU (`tools/automix-model/litert_gpu_rewrite.py`
 * and `check_litert_gpu.py`); on a Snapdragon 8 Gen 3 all 1158 of its ops run in one OpenCL
 * partition. Worse, when the accelerator a model asks for cannot start at all, LiteRT runs the
 * whole model on the CPU and still succeeds: on that phone, with no dispatch library, asking for
 * the NPU gave an XNNPACK CPU model. So the NPU is asked for only when a dispatch library is
 * there, and a compiled model is accepted only when the input buffers LiteRT offers for it belong
 * to the accelerator ([acceleratorBuffers]).
 *
 * The model is not in the APK: [BeatModelDownloads.liteRt] is downloaded the first time it is
 * needed, after LiteRT itself has loaded, so a device that cannot run LiteRT never downloads it.
 *
 * Every LiteRT call runs on one thread of its own: an OpenGL ES context belongs to the thread
 * that created it.
 */
internal class LiteRtBeatModelLoader(
    private val context: Context,
    private val download: ModelDownload = ModelDownload(BeatModelDownloads.liteRt) {
        File(context.noBackupFilesDir, "automix")
    },
) : BeatModelLoader {

    override val downloadProgress: StateFlow<Float?> get() = download.progress

    override suspend fun load(): BeatModelAvailability {
        val thread = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRT beat model").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        var environment: Environment? = null
        var ready = false
        try {
            // One environment serves both attempts; the NPU provider only adds where to look for
            // the vendor libraries. Creating it loads LiteRT's native library.
            val created = withContext(thread) { Environment.create(context, BuiltinNpuAcceleratorProvider(context)) }
            environment = created
            val availability = when (val model = download.file()) {
                is ModelFile.Failed -> BeatModelAvailability.Unavailable(
                    strings.beatModelDownloadFailed(model.reason),
                    retryable = true,
                )
                is ModelFile.Ready -> withContext(thread) { compile(created, thread, model.file.absolutePath) }
            }
            ready = availability is BeatModelAvailability.Ready
            return availability
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // UnsatisfiedLinkError and friends: LiteRT's native library does not load here.
            return BeatModelAvailability.Unavailable(
                strings.liteRtLoadFailed(failure.message ?: failure.javaClass.simpleName),
            )
        } finally {
            if (!ready) {
                environment?.let { unused -> thread.executor.execute { unused.close() } }
                thread.close()
            }
        }
    }

    private fun compile(
        environment: Environment,
        thread: ExecutorCoroutineDispatcher,
        modelPath: String,
    ): BeatModelAvailability {
        val label = listOfNotNull(socName(), "LiteRT").joinToString(" · ")
        val available = runCatching { environment.getAvailableAccelerators() }.getOrDefault(emptySet())
        val failures = mutableListOf<String>()
        val attempts = listOf(Accelerator.NPU to InferenceAccelerator.NPU, Accelerator.GPU to InferenceAccelerator.GPU)
        for ((accelerator, kind) in attempts) {
            // The GPU is always tried: LiteRT may register its GPU accelerator only when a model
            // asks for it. LiteRT registers its NPU accelerator even with no vendor library to
            // reach the NPU, so that is checked for directly.
            if (accelerator == Accelerator.NPU && (accelerator !in available || !hasNpuDispatchLibrary())) {
                failures += strings.labelValue("NPU", strings.liteRtNpuRuntimeMissing)
                continue
            }
            val model = tryCreate(modelPath, accelerator, environment, failures) ?: continue
            return BeatModelAvailability.Ready(LiteRtBeatActivationModel(model, environment, thread, kind, label))
        }
        return BeatModelAvailability.Unavailable(
            strings.beatModelDevicesFailed(failures.joinToString(strings.clauseSeparator)),
        )
    }

    private fun tryCreate(
        path: String,
        accelerator: Accelerator,
        environment: Environment,
        failures: MutableList<String>,
    ): CompiledModel? {
        var model: CompiledModel? = null
        return try {
            model = CompiledModel.create(path, CompiledModel.Options(accelerator), environment)
            val buffers = model.getInputBufferRequirements(MODEL_INPUT).supportedTypes
            println("LiteRtBeatModelLoader: ${accelerator.name} input buffers $buffers")
            if (buffers.none { it in acceleratorBuffers.getValue(accelerator) }) {
                failures += strings.labelValue(accelerator.name, strings.liteRtFellBackToCpu)
                model.close()
                return null
            }
            // A model that compiles can still fail its first run on a driver; find out now.
            LiteRtBeatActivationModel.runOnce(model, FloatArray(BEAT_MODEL_CHUNK_FRAMES * BeatModelFeatures.MEL_BINS))
            model
        } catch (failure: Throwable) {
            failures += strings.labelValue(accelerator.name, failure.message?.lineSequence()?.firstOrNull().orEmpty())
            model?.close()
            null
        }
    }

    private fun hasNpuDispatchLibrary(): Boolean =
        File(context.applicationInfo.nativeLibraryDir).list().orEmpty().any { it.startsWith("libLiteRtDispatch") }

    private fun socName(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
            .filter { it.isNotBlank() && it != Build.UNKNOWN }
            .joinToString(" ")
            .ifEmpty { null }
    }
}

/** The model's one input, as the converter named it. */
private const val MODEL_INPUT = "args_0"

/**
 * Input buffer types that only exist when a model runs on that accelerator. A model LiteRT has
 * quietly put on the CPU offers host memory alone.
 */
private val acceleratorBuffers = mapOf(
    Accelerator.NPU to setOf(TensorBufferType.Ahwb, TensorBufferType.Ion, TensorBufferType.DmaBuf, TensorBufferType.FastRpc),
    Accelerator.GPU to TensorBufferType.entries.filter { type ->
        type.name.startsWith("Gl") || type.name.startsWith("OpenCl") ||
            type.name.startsWith("Vulkan") || type.name.startsWith("WebGpu")
    }.toSet(),
)

private class LiteRtBeatActivationModel(
    private val model: CompiledModel,
    private val environment: Environment,
    private val thread: ExecutorCoroutineDispatcher,
    override val accelerator: InferenceAccelerator,
    override val deviceLabel: String,
) : BeatActivationModel {
    override suspend fun infer(spectrogram: FloatArray): FloatArray = withContext(thread) {
        runOnce(model, spectrogram)
    }

    override fun close() {
        thread.executor.execute {
            model.close()
            environment.close()
        }
        thread.close()
    }

    companion object {
        fun runOnce(model: CompiledModel, spectrogram: FloatArray): FloatArray {
            val inputs = model.createInputBuffers()
            val outputs = model.createOutputBuffers()
            try {
                inputs[0].writeFloat(spectrogram)
                model.run(inputs, outputs)
                return outputs[0].readFloat()
            } finally {
                (inputs + outputs).forEach(TensorBuffer::close)
            }
        }
    }
}

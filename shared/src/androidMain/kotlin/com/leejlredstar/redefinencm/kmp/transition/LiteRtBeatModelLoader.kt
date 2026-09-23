package com.leejlredstar.redefinencm.kmp.transition

import android.content.Context
import android.os.Build
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
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
 * LiteRT quietly runs any op its GPU backend lacks on the CPU. The model shipped here was
 * rewritten until LiteRT reports it fully accelerated on a GPU
 * (`tools/automix-model/litert_gpu_rewrite.py` and `check_litert_gpu.py`).
 *
 * Every LiteRT call runs on one thread of its own: an OpenGL ES context belongs to the thread
 * that created it.
 */
internal class LiteRtBeatModelLoader(private val context: Context) : BeatModelLoader {

    override suspend fun load(): BeatModelAvailability {
        val thread = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRT beat model").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        val availability = try {
            withContext(thread) { loadOnThread(thread) }
        } catch (failure: Throwable) {
            // UnsatisfiedLinkError and friends: LiteRT's native library does not load here.
            BeatModelAvailability.Unavailable(strings.liteRtLoadFailed(failure.message ?: failure.javaClass.simpleName))
        }
        if (availability !is BeatModelAvailability.Ready) thread.close()
        return availability
    }

    private fun loadOnThread(thread: ExecutorCoroutineDispatcher): BeatModelAvailability {
        val modelPath = extractModel()?.absolutePath
            ?: return BeatModelAvailability.Unavailable(strings.beatModelFileMissing)
        val label = listOfNotNull(socName(), "LiteRT").joinToString(" · ")
        // One environment serves both attempts; the NPU provider only adds where to look for
        // the vendor libraries.
        val environment = Environment.create(context, BuiltinNpuAcceleratorProvider(context))
        val available = runCatching { environment.getAvailableAccelerators() }.getOrDefault(emptySet())
        val failures = mutableListOf<String>()
        val attempts = listOf(Accelerator.NPU to InferenceAccelerator.NPU, Accelerator.GPU to InferenceAccelerator.GPU)
        for ((accelerator, kind) in attempts) {
            // The GPU is always tried: LiteRT may register its GPU accelerator only when a model
            // asks for it.
            if (accelerator == Accelerator.NPU && accelerator !in available) {
                failures += strings.labelValue("NPU", strings.liteRtNpuRuntimeMissing)
                continue
            }
            val model = tryCreate(modelPath, accelerator, environment, failures) ?: continue
            return BeatModelAvailability.Ready(LiteRtBeatActivationModel(model, environment, thread, kind, label))
        }
        environment.close()
        return BeatModelAvailability.Unavailable(strings.beatModelDevicesFailed(failures.joinToString(strings.clauseSeparator)))
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
            // A model that compiles can still fail its first run on a driver; find out now.
            LiteRtBeatActivationModel.runOnce(model, FloatArray(BEAT_MODEL_CHUNK_FRAMES * BeatModelFeatures.MEL_BINS))
            model
        } catch (failure: Throwable) {
            failures += strings.labelValue(accelerator.name, failure.message?.lineSequence()?.firstOrNull().orEmpty())
            model?.close()
            null
        }
    }

    /**
     * LiteRT loads a model from a file path; the model travels in the APK as a Java resource, so
     * it is copied out once per model version.
     */
    private fun extractModel(): File? {
        val target = File(File(context.noBackupFilesDir, "automix"), MODEL_FILE)
        val bytes = LiteRtBeatModelLoader::class.java.getResourceAsStream(MODEL_RESOURCE)?.use { it.readBytes() }
            ?: return null
        if (target.isFile && target.length() == bytes.size.toLong()) return target
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "$MODEL_FILE.partial")
        partial.writeBytes(bytes)
        if (!partial.renameTo(target)) {
            target.delete()
            if (!partial.renameTo(target)) return null
        }
        return target
    }

    private fun socName(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
            .filter { it.isNotBlank() && it != Build.UNKNOWN }
            .joinToString(" ")
            .ifEmpty { null }
    }

    private companion object {
        const val MODEL_FILE = "beat_this_small0_t750.tflite"
        const val MODEL_RESOURCE = "/automix/$MODEL_FILE"
    }
}

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

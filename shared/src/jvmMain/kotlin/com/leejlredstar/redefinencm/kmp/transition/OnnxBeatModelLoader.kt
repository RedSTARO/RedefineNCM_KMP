package com.leejlredstar.redefinencm.kmp.transition

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtEpDevice
import ai.onnxruntime.OrtHardwareDevice
import ai.onnxruntime.OrtSession
import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer

/**
 * The beat model on the desktop, through ONNX Runtime, on an NPU or a GPU and never the CPU.
 *
 * - **Windows**: ONNX Runtime's DirectML build, from Microsoft's MIT-licensed
 *   `Microsoft.ML.OnnxRuntime.DirectML` package, bundled next to the app. DirectML itself is
 *   the copy Windows ships in System32, so nothing proprietary is redistributed. Every
 *   execution-provider device ONNX Runtime reports is tried, NPUs first and then GPUs, the
 *   system's high-performance adapter first.
 * - **macOS**: ONNX Runtime's Core ML execution provider, asked for the Neural Engine first and
 *   the GPU second.
 * - **Linux**: no accelerated execution provider ships for the JVM, so the model is not loaded.
 *
 * Every session is created with CPU fallback disabled, so creation fails outright if any
 * operator would have run on the CPU, instead of quietly running part of the model there.
 *
 * The model is not in the package: [BeatModelDownloads.onnx] is downloaded into the app's data
 * folder the first time it is needed, and only on a platform that has a runtime to run it.
 */
internal class OnnxBeatModelLoader(
    private val nativeDirectory: () -> File? = ::bundledOnnxRuntimeDirectory,
    private val download: ModelDownload = ModelDownload(BeatModelDownloads.onnx) {
        File(File(System.getProperty("user.home"), ".redefinencm"), "models")
    },
    private val modelFile: suspend () -> ModelFile = download::file,
) : BeatModelLoader {

    override val downloadProgress: StateFlow<Float?> get() = download.progress

    override suspend fun load(): BeatModelAvailability {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val windows = os.contains("windows")
        if (!windows && !os.contains("mac") && !os.contains("darwin")) {
            return BeatModelAvailability.Unavailable(strings.beatModelLinuxNoRuntime)
        }
        val directory = if (windows) {
            nativeDirectory()?.takeIf { File(it, "onnxruntime.dll").isFile }
                ?: return BeatModelAvailability.Unavailable(strings.directMLRuntimeMissing)
        } else {
            null
        }
        val file = when (val found = modelFile()) {
            is ModelFile.Failed -> return BeatModelAvailability.Unavailable(
                strings.beatModelDownloadFailed(found.reason),
                retryable = true,
            )
            is ModelFile.Ready -> found.file
        }
        return withContext(Dispatchers.IO) {
            try {
                val model = file.readBytes()
                if (directory != null) loadWindows(directory, model) else loadMac(model)
            } catch (failure: Throwable) {
                // UnsatisfiedLinkError and friends: the native library is missing or does not load.
                BeatModelAvailability.Unavailable(
                    strings.onnxRuntimeLoadFailed(failure.message ?: failure.javaClass.simpleName),
                )
            }
        }
    }

    private fun loadWindows(directory: File, model: ByteArray): BeatModelAvailability {
        // Must be set before the first ONNX Runtime class initialises; it is read once, then.
        System.setProperty("onnxruntime.native.path", directory.absolutePath)
        val environment = OrtEnvironment.getEnvironment()
        val devices = runCatching { environment.epDevices }.getOrElse { failure ->
            return BeatModelAvailability.Unavailable(strings.onnxDeviceListFailed(failure.message))
        }
        val candidates = devices
            .filter { it.device.type == OrtHardwareDevice.OrtHardwareDeviceType.NPU } +
            devices
                .filter { it.device.type == OrtHardwareDevice.OrtHardwareDeviceType.GPU }
                .sortedWith(
                    compareBy<OrtEpDevice>(
                        { it.device.metadata["DxgiHighPerformanceIndex"]?.toIntOrNull() ?: Int.MAX_VALUE },
                        { if (it.device.metadata["Discrete"] == "1") 0 else 1 },
                    ),
                )
        if (candidates.isEmpty()) {
            return BeatModelAvailability.Unavailable(strings.directMLNoDevice)
        }
        val failures = mutableListOf<String>()
        for (device in candidates) {
            val accelerator = if (device.device.type == OrtHardwareDevice.OrtHardwareDeviceType.NPU) {
                InferenceAccelerator.NPU
            } else {
                InferenceAccelerator.GPU
            }
            val label = listOfNotNull(
                device.device.metadata["Description"]?.trim()?.takeIf(String::isNotEmpty) ?: device.device.vendor,
                device.epName.removeSuffix("ExecutionProvider"),
            ).joinToString(" · ")
            val session = tryCreate(environment, model, failures, label) { options ->
                options.addExecutionProvider(listOf(device), emptyMap())
            } ?: continue
            return BeatModelAvailability.Ready(OnnxBeatActivationModel(environment, session, accelerator, label))
        }
        return BeatModelAvailability.Unavailable(
            strings.beatModelDevicesFailed(failures.joinToString(strings.clauseSeparator)),
        )
    }

    private fun loadMac(model: ByteArray): BeatModelAvailability {
        val environment = OrtEnvironment.getEnvironment()
        val failures = mutableListOf<String>()
        val attempts = listOf(
            Triple(InferenceAccelerator.NPU, "CPUAndNeuralEngine", "Apple Neural Engine · Core ML"),
            Triple(InferenceAccelerator.GPU, "CPUAndGPU", "Apple GPU · Core ML"),
        )
        for ((accelerator, units, label) in attempts) {
            val session = tryCreate(environment, model, failures, label) { options ->
                options.addCoreML(mapOf("MLComputeUnits" to units, "ModelFormat" to "MLProgram"))
            } ?: continue
            return BeatModelAvailability.Ready(OnnxBeatActivationModel(environment, session, accelerator, label))
        }
        return BeatModelAvailability.Unavailable(
            strings.coreMlBeatModelFailed(failures.joinToString(strings.clauseSeparator)),
        )
    }

    private fun tryCreate(
        environment: OrtEnvironment,
        model: ByteArray,
        failures: MutableList<String>,
        label: String,
        addProvider: (OrtSession.SessionOptions) -> Unit,
    ): OrtSession? {
        val options = OrtSession.SessionOptions()
        return try {
            options.addConfigEntry("session.disable_cpu_ep_fallback", "1")
            options.setMemoryPatternOptimization(false)
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            addProvider(options)
            val session = environment.createSession(model, options)
            // A session that compiles can still fail its first run on a driver; find out now.
            OnnxBeatActivationModel.runOnce(environment, session, FloatArray(BEAT_MODEL_CHUNK_FRAMES * BeatModelFeatures.MEL_BINS))
            session
        } catch (failure: Throwable) {
            failures += strings.labelValue(label, failure.message?.lineSequence()?.firstOrNull().orEmpty())
            null
        } finally {
            options.close()
        }
    }
}

private class OnnxBeatActivationModel(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    override val accelerator: InferenceAccelerator,
    override val deviceLabel: String,
) : BeatActivationModel {
    override suspend fun infer(spectrogram: FloatArray): FloatArray = withContext(Dispatchers.IO) {
        runOnce(environment, session, spectrogram)
    }

    override fun close() {
        session.close()
    }

    companion object {
        fun runOnce(environment: OrtEnvironment, session: OrtSession, spectrogram: FloatArray): FloatArray {
            val shape = longArrayOf(1, BEAT_MODEL_CHUNK_FRAMES.toLong(), BeatModelFeatures.MEL_BINS.toLong())
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(spectrogram), shape).use { input ->
                session.run(mapOf("spectrogram" to input)).use { result ->
                    val output = result[0] as OnnxTensor
                    val buffer = output.floatBuffer
                    return FloatArray(buffer.remaining()).also { buffer.get(it) }
                }
            }
        }
    }
}

/**
 * Where the desktop packaging puts ONNX Runtime's DirectML build: the app's resources directory,
 * which Compose Desktop names in `compose.application.resources.dir`. A development run can
 * point `redefinencm.onnxruntime.dir` at an unpacked copy instead.
 */
internal fun bundledOnnxRuntimeDirectory(): File? {
    System.getProperty("redefinencm.onnxruntime.dir")?.let { return File(it) }
    val resources = System.getProperty("compose.application.resources.dir") ?: return null
    return File(resources, "onnxruntime")
}

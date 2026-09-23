package com.leejlredstar.redefinencm.kmp.transition

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What [ModelDownload.file] found. */
sealed interface ModelFile {
    data class Ready(val file: File) : ModelFile

    /** [reason] names every mirror that was tried and why it failed. */
    data class Failed(val reason: String) : ModelFile
}

/**
 * Keeps one [DownloadableModel] in [directory]. The file is used when it is there and its size
 * and SHA-256 match; otherwise it is downloaded from each mirror in turn into a partial file,
 * hashed on the way, and moved into place only when it matches. A damaged or foreign file is
 * never loaded, and a failed download leaves nothing behind.
 */
internal class ModelDownload(
    private val model: DownloadableModel,
    private val directory: () -> File,
) {
    private val _progress = MutableStateFlow<Float?>(null)

    /** 0 to 1 while a download runs, null otherwise. */
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    private val lock = Mutex()
    @Volatile private var verified: File? = null

    suspend fun file(): ModelFile = lock.withLock {
        withContext(Dispatchers.IO) {
            verified?.takeIf { it.isFile }?.let { return@withContext ModelFile.Ready(it) }
            val folder = directory().apply { mkdirs() }
            val target = File(folder, model.fileName)
            if (target.isFile && target.length() == model.sizeBytes && sha256(target) == model.sha256) {
                verified = target
                return@withContext ModelFile.Ready(target)
            }
            val failures = mutableListOf<String>()
            for (url in model.urls) {
                try {
                    fetch(url, target) { ensureActive() }
                    verified = target
                    return@withContext ModelFile.Ready(target)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    val host = runCatching { URL(url).host }.getOrNull() ?: url
                    failures += strings.labelValue(host, failure.message ?: failure.javaClass.simpleName)
                } finally {
                    _progress.value = null
                }
            }
            ModelFile.Failed(failures.joinToString(strings.clauseSeparator))
        }
    }

    private fun fetch(url: String, target: File, checkCancelled: () -> Unit) {
        val partial = File(target.parentFile, "${target.name}.partial")
        val connection = URL(url).openConnection().apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        try {
            if (connection is HttpURLConnection) {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            _progress.value = 0f
            connection.getInputStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled()
                        val read = input.read(buffer)
                        if (read < 0) break
                        received += read
                        if (received > model.sizeBytes) throw IOException(strings.beatModelChecksumMismatch)
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        _progress.value = (received * 100 / model.sizeBytes) / 100f
                    }
                }
            }
            if (received != model.sizeBytes || hex(digest.digest()) != model.sha256) {
                throw IOException(strings.beatModelChecksumMismatch)
            }
            if (!partial.renameTo(target)) {
                target.delete()
                if (!partial.renameTo(target)) throw IOException("rename ${partial.name}")
            }
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
            partial.delete()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return hex(digest.digest())
}

private fun hex(bytes: ByteArray): String = bytes.joinToString("") { byte ->
    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
}

package com.leejlredstar.redefinencm.kmp.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import kotlin.coroutines.coroutineContext

/**
 * Android actual：应用内流式下载，不再使用系统 DownloadManager。
 * Android 10+ 通过 MediaStore 写入 Downloads/RedefineNCM/，旧系统写公共下载目录。
 */
actual object SongDownloader {
    actual suspend fun download(
        item: DownloadRequestItem,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): DownloadedSongFile = withContext(Dispatchers.IO) {
        require(item.url.isNotBlank()) { "下载地址为空" }

        findDownloadedSongUri(item.id)?.let { existingUri ->
            return@withContext DownloadedSongFile(fileName = "${item.id}", uri = existingUri)
        }

        val context = KoinPlatform.getKoin().get<Context>()
        val extension = extensionFromUrl(item.url)
        val fileName = "${item.id}.$extension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, item, fileName, onProgress)
        } else {
            writeLegacyFile(item, fileName, onProgress)
        }
    }
}

private suspend fun writeViaMediaStore(
    context: Context,
    item: DownloadRequestItem,
    fileName: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): DownloadedSongFile {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFromFileName(fileName))
        put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOAD_RELATIVE_PATH)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建下载文件")

    try {
        val connection = URI(item.url).toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: item.expectedBytes
        connection.getInputStream().use { input ->
            resolver.openOutputStream(uri, "w")?.use { output ->
                copyWithProgress(input, output, totalBytes, onProgress)
            } ?: error("无法写入下载文件")
        }

        ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }.also { resolver.update(uri, it, null, null) }
        return DownloadedSongFile(fileName = fileName, uri = uri.toString())
    } catch (t: Throwable) {
        resolver.delete(uri, null, null)
        throw t
    }
}

private suspend fun writeLegacyFile(
    item: DownloadRequestItem,
    fileName: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): DownloadedSongFile {
    val dir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS + "/$DOWNLOAD_SUBDIR",
    )
    if (!dir.exists() && !dir.mkdirs()) error("无法创建下载目录")

    val target = File(dir, fileName)
    if (target.exists()) return DownloadedSongFile(fileName = fileName, uri = target.toURI().toString())
    val part = File(dir, "$fileName.part")

    try {
        val connection = URI(item.url).toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: item.expectedBytes
        connection.getInputStream().use { input ->
            part.outputStream().use { output ->
                copyWithProgress(input, output, totalBytes, onProgress)
            }
        }
        if (!part.renameTo(target)) error("无法保存下载文件")
        return DownloadedSongFile(fileName = fileName, uri = target.toURI().toString())
    } catch (t: Throwable) {
        part.delete()
        throw t
    }
}

private suspend fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long?,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var downloadedBytes = 0L
    onProgress(downloadedBytes, totalBytes)
    while (true) {
        coroutineContext.ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        downloadedBytes += read
        onProgress(downloadedBytes, totalBytes)
    }
    output.flush()
}

private fun mimeTypeFromFileName(fileName: String): String = when (fileName.substringAfterLast('.').lowercase()) {
    "flac" -> "audio/flac"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "wav" -> "audio/wav"
    else -> "audio/mpeg"
}

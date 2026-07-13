package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import kotlin.coroutines.coroutineContext

/**
 * JVM/Desktop actual：由 common 下载队列调度，这里只负责流式写入文件。
 */
actual object SongDownloader {
    actual fun discardPartial(songId: Long) = Unit

    actual suspend fun download(
        item: DownloadRequestItem,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onReadyToPublish: () -> Boolean,
    ): DownloadedSongFile = withContext(Dispatchers.IO) {
        require(item.url.isNotBlank()) { "下载地址为空" }

        val dir = jvmDownloadDirectory()
        if (!dir.exists() && !dir.mkdirs()) error("无法创建下载目录")

        val extension = extensionFromUrl(item.url)
        val target = File(dir, "${item.id}.$extension")
        if (target.exists()) {
            if (!onReadyToPublish()) throw CancellationException("下载发布已取消")
            return@withContext DownloadedSongFile(fileName = target.name, uri = target.toURI().toString())
        }

        val partFile = File(dir, "${target.name}.part")
        try {
            val connection = URI(item.url).toURL().openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: item.expectedBytes
            connection.getInputStream().use { input ->
                partFile.outputStream().use { output ->
                    copyWithProgress(input, output, totalBytes, onProgress)
                }
            }
            if (!onReadyToPublish()) throw CancellationException("下载发布已取消")
            if (!partFile.renameTo(target)) error("无法保存下载文件")
            DownloadedSongFile(fileName = target.name, uri = target.toURI().toString())
        } catch (t: Throwable) {
            partFile.delete()
            throw t
        }
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

package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    ): DownloadedSongFile = downloadSongToDirectory(item, jvmDownloadDirectory(), onProgress, onReadyToPublish)
}

/** The directory is explicit so local HTTP tests never need the user's music folder. */
internal suspend fun downloadSongToDirectory(
    item: DownloadRequestItem,
    dir: File,
    onProgress: (Long, Long?) -> Unit,
    onReadyToPublish: () -> Boolean,
): DownloadedSongFile = withContext(Dispatchers.IO) {
    require(item.url.isNotBlank()) { strings.downloadUrlEmpty }

    if (!dir.exists() && !dir.mkdirs()) error(strings.downloadFolderCreateFailed)

    val extension = extensionFromUrl(item.url)
    val target = File(dir, "${item.id}.$extension")
    if (target.isFile && target.length() > 0) {
        onProgress(target.length(), target.length())
        if (!onReadyToPublish()) throw CancellationException(strings.downloadCanceledNotSaved)
        return@withContext DownloadedSongFile(fileName = target.name, uri = target.toURI().toString())
    }

    val partFile = File(dir, "${target.name}.part")
    try {
        val connection = URI(item.url).toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (connection is HttpURLConnection) {
                // No range was requested, so a partial/status-only answer is not a full song.
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    strings.downloadHttpFailed(connection.responseCode)
                }
            }
            val authoritativeBytes = connection.contentLengthLong.takeIf { it >= 0L }
            val progressBytes = authoritativeBytes ?: item.expectedBytes
            connection.getInputStream().use { input ->
                partFile.outputStream().use { output ->
                    copyWithProgress(input, output, progressBytes, onProgress)
                }
            }
            val received = partFile.length()
            check(received > 0) { strings.downloadEmptyNotSaved }
            check(authoritativeBytes == null || received == authoritativeBytes) {
                strings.downloadIncomplete(received, authoritativeBytes)
            }
            coroutineContext.ensureActive()
            // The queue accepts byte updates only until the publish claim changes its state.
            onProgress(received, received)
            if (!onReadyToPublish()) throw CancellationException(strings.downloadCanceledNotSaved)
            Files.move(partFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            DownloadedSongFile(fileName = target.name, uri = target.toURI().toString())
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    } catch (t: Throwable) {
        partFile.delete()
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

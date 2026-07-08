@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.remove
import platform.posix.rename

/** iOS actual：写入应用 Documents/RedefineNCM/，供本地库扫描与离线播放复用。 */
actual object SongDownloader {
    actual suspend fun download(
        item: DownloadRequestItem,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): DownloadedSongFile = withContext(Dispatchers.Default) {
        require(item.url.isNotBlank()) { "下载地址为空" }

        scanDownloadedSongs().firstOrNull { it.id == item.id }?.let { existing ->
            return@withContext DownloadedSongFile(fileName = existing.fileName, uri = existing.uri)
        }

        NSURL.URLWithString(item.url) ?: error("下载地址无效")
        val dir = ensureIosDownloadDirectory()
        val extension = extensionFromUrl(item.url)
        val fileName = "${item.id}.$extension"
        val targetPath = "$dir/$fileName"
        val partPath = "$targetPath.part"
        val manager = NSFileManager.defaultManager

        onProgress(0, item.expectedBytes)
        val bytes = HttpClient(Darwin).use { client ->
            val response = client.get(item.url)
            if (!response.status.isSuccess()) {
                error("下载失败：HTTP ${response.status.value}")
            }
            response.bodyAsBytes()
        }
        writeBytes(partPath, bytes)
        if (manager.fileExistsAtPath(targetPath)) {
            manager.removeItemAtPath(targetPath, error = null)
        }
        if (rename(partPath, targetPath) != 0) {
            remove(partPath)
            error("无法保存下载文件")
        }
        val byteCount = bytes.size.toLong()
        onProgress(byteCount, byteCount.takeIf { it > 0L } ?: item.expectedBytes)
        DownloadedSongFile(fileName = fileName, uri = NSURL.fileURLWithPath(targetPath).absoluteString)
    }
}

private fun extensionFromUrl(url: String): String =
    url.substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast('.', "mp3")
        .ifBlank { "mp3" }
        .take(12)

private fun writeBytes(path: String, bytes: ByteArray) {
    val file = fopen(path, "wb") ?: error("无法写入下载文件")
    try {
        bytes.usePinned { pinned ->
            val written = fwrite(
                pinned.addressOf(0),
                1.convert(),
                bytes.size.convert(),
                file,
            )
            if (written.toLong() != bytes.size.toLong()) {
                error("无法完整写入下载文件")
            }
        }
    } finally {
        fclose(file)
    }
}

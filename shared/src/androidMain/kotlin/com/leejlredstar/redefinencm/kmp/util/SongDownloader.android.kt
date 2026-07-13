package com.leejlredstar.redefinencm.kmp.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.system.Os
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.coroutines.coroutineContext

/**
 * Android actual：应用内流式下载，不再使用系统 DownloadManager。
 * Android 10+ 通过 MediaStore 写入 Downloads/RedefineNCM/，旧系统写公共下载目录。
 */
actual object SongDownloader {
    actual fun discardPartial(songId: Long) {
        val context = KoinPlatform.getKoin().get<Context>()
        deletePartialDownloads(context, songId)
    }

    actual suspend fun download(
        item: DownloadRequestItem,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onReadyToPublish: () -> Boolean,
    ): DownloadedSongFile = withContext(Dispatchers.IO) {
        require(item.url.isNotBlank()) { "下载地址为空" }

        val context = KoinPlatform.getKoin().get<Context>()
        val requestedExtension = extensionFromUrl(item.url)
        val partialFile = partialDownloadFile(context, item)

        findDownloadedSongUri(item.id)?.let { existingUri ->
            if (!onReadyToPublish()) throw CancellationException("下载发布已取消")
            deletePartialDownloads(context, item.id)
            return@withContext DownloadedSongFile(
                fileName = "${item.id}.$requestedExtension",
                uri = existingUri,
            )
        }

        val fileExtension = downloadToPartialFile(
            item = item,
            partialFile = partialFile,
            requestedRepresentationKey = item.representationKey,
            requestedExtension = requestedExtension,
            onProgress = onProgress,
        )
        if (!onReadyToPublish()) throw CancellationException("下载发布已取消")
        val fileName = "${item.id}.$fileExtension"

        withContext(NonCancellable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishViaMediaStore(context, item.id, partialFile, fileName)
            } else {
                publishLegacyFile(partialFile, fileName)
            }
        }
    }
}

private suspend fun downloadToPartialFile(
    item: DownloadRequestItem,
    partialFile: File,
    requestedRepresentationKey: String,
    requestedExtension: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): String {
    val expectedBytes = item.expectedBytes?.takeIf { it > 0L }
    var retriedWithoutRange = false
    while (true) {
        val storedMetadata = prepareStoredPartial(
            partialFile = partialFile,
            requestedRepresentationKey = requestedRepresentationKey,
            requestedExtension = requestedExtension,
        )
        val storedValidator = storedMetadata?.validator
        val storedAuthoritativeTotal = storedMetadata?.authoritativeTotalBytes
        val requestedOffset = partialFile.length()
        if (
            isTrustedCompletePartial(
                partialBytes = requestedOffset,
                authoritativeTotalBytes = storedAuthoritativeTotal,
            )
        ) {
            onProgress(requestedOffset, storedAuthoritativeTotal)
            return checkNotNull(storedMetadata).fileExtension
        }

        val connection = openDownloadConnection(
            url = item.url,
            requestedOffset = requestedOffset,
            validator = storedValidator,
        )
        try {
            val responseCode = connection.responseCode
            val contentLength = connection.contentLengthLong.takeIf { it >= 0L }
            val decision = decideResumableDownload(
                requestedOffset = requestedOffset,
                responseCode = responseCode,
                contentRange = connection.getHeaderField("Content-Range"),
                contentLength = contentLength,
                storedAuthoritativeTotalBytes = storedAuthoritativeTotal,
            )
            val responseEtag = connection.getHeaderField("ETag")
            val responseLastModified = connection.getHeaderField("Last-Modified")
            val retryReason = when {
                decision is ResumableDownloadDecision.RetryWithoutRange -> decision.reason
                decision is ResumableDownloadDecision.Append &&
                    storedValidator != null &&
                    hasResumableEntityValidatorConflict(
                        storedValidator = storedValidator,
                        responseEtag = responseEtag,
                        responseLastModified = responseLastModified,
                    ) -> "HTTP 206 entity validator changed while resuming"
                else -> null
            }
            if (retryReason != null) {
                resetPartialDownload(partialFile)
                check(!retriedWithoutRange) {
                    "$retryReason; mismatch remained after one retry without Range"
                }
                retriedWithoutRange = true
                continue
            }

            val (initialBytes, authoritativeTotalBytes, append) = when (decision) {
                is ResumableDownloadDecision.Append ->
                    DownloadWritePlan(decision.offset, decision.totalBytes, append = true)
                is ResumableDownloadDecision.Restart -> {
                    resetPartialDownload(partialFile)
                    DownloadWritePlan(
                        initialBytes = 0L,
                        totalBytes = decision.totalBytes,
                        append = false,
                    )
                }
                is ResumableDownloadDecision.Reject -> error(decision.reason)
                is ResumableDownloadDecision.RetryWithoutRange -> error("Unreachable retry decision")
            }

            val responseValidator = selectResumableEntityValidator(
                etag = responseEtag,
                lastModified = responseLastModified,
            )
            val validatorToPersist = if (append) {
                storedValidator ?: responseValidator
            } else {
                responseValidator
            }
            val fileExtension = requestedExtension
            persistResumableMetadata(
                partialFile = partialFile,
                metadata = ResumableDownloadMetadata(
                    validator = validatorToPersist,
                    authoritativeTotalBytes = authoritativeTotalBytes,
                    representationKey = requestedRepresentationKey,
                    fileExtension = fileExtension,
                ),
            )

            connection.inputStream.use { input ->
                FileOutputStream(partialFile, append).use { output ->
                    copyWithProgress(
                        input = input,
                        output = output,
                        initialBytes = initialBytes,
                        totalBytes = authoritativeTotalBytes ?: expectedBytes,
                        onProgress = onProgress,
                    )
                }
            }

            val finalBytes = partialFile.length()
            check(finalBytes > 0L) { "下载响应为空，拒绝发布空音频文件" }
            if (authoritativeTotalBytes != null && finalBytes != authoritativeTotalBytes) {
                error(
                    "下载长度不完整：已写入 $finalBytes 字节，" +
                        "响应声明 $authoritativeTotalBytes 字节",
                )
            }
            if (authoritativeTotalBytes == null) {
                persistResumableMetadata(
                    partialFile = partialFile,
                    metadata = ResumableDownloadMetadata(
                        validator = validatorToPersist,
                        authoritativeTotalBytes = finalBytes,
                        representationKey = requestedRepresentationKey,
                        fileExtension = fileExtension,
                    ),
                )
                onProgress(finalBytes, finalBytes)
            }
            return fileExtension
        } finally {
            connection.disconnect()
        }
    }
}

private fun openDownloadConnection(
    url: String,
    requestedOffset: Long,
    validator: ResumableEntityValidator?,
): HttpURLConnection {
    val connection = URI(url).toURL().openConnection() as? HttpURLConnection
        ?: error("下载地址不是 HTTP(S) URL")
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.instanceFollowRedirects = true
    if (requestedOffset > 0L) {
        checkNotNull(validator) { "A partial download cannot resume without an entity validator" }
        connection.setRequestProperty("Range", "bytes=$requestedOffset-")
        connection.setRequestProperty("If-Range", validator.value)
    }
    return connection
}

private fun prepareStoredPartial(
    partialFile: File,
    requestedRepresentationKey: String,
    requestedExtension: String,
): ResumableDownloadMetadata? {
    val metadata = readResumableMetadata(partialFile)
    if (
        shouldResetStoredPartial(
            partialBytes = partialFile.length(),
            metadata = metadata,
        ) || shouldResetStoredPartialForRepresentation(
            metadata = metadata,
            requestedRepresentationKey = requestedRepresentationKey,
            requestedExtension = requestedExtension,
        )
    ) {
        resetPartialDownload(partialFile)
        return null
    }
    if (partialFile.length() == 0L) {
        deleteResumableMetadata(partialFile, requireSuccess = true)
        return null
    }
    return metadata
}

private fun readResumableMetadata(partialFile: File): ResumableDownloadMetadata? {
    val metadataFile = resumableMetadataFile(partialFile)
    if (!metadataFile.isFile) return null
    return decodeResumableDownloadMetadata(metadataFile.readText())
}

private fun persistResumableMetadata(
    partialFile: File,
    metadata: ResumableDownloadMetadata,
) {
    if (metadata.validator == null && metadata.authoritativeTotalBytes == null) {
        deleteResumableMetadata(partialFile, requireSuccess = true)
        return
    }

    val target = resumableMetadataFile(partialFile)
    val temporary = temporaryResumableMetadataFile(partialFile)
    val bytes = encodeResumableDownloadMetadata(metadata).encodeToByteArray()
    FileOutputStream(temporary, false).use { output ->
        output.write(bytes)
        output.fd.sync()
    }
    try {
        Os.rename(temporary.path, target.path)
    } catch (failure: Throwable) {
        temporary.delete()
        throw IllegalStateException("无法原子持久化断点下载元数据", failure)
    }
}

private fun resetPartialDownload(partialFile: File) {
    FileOutputStream(partialFile, false).use { output -> output.fd.sync() }
    deleteResumableMetadata(partialFile, requireSuccess = true)
}

private fun deleteResumableMetadata(partialFile: File, requireSuccess: Boolean) {
    listOf(
        resumableMetadataFile(partialFile),
        temporaryResumableMetadataFile(partialFile),
    ).forEach { file ->
        val deleted = !file.exists() || file.delete()
        if (requireSuccess) check(deleted) { "无法删除断点下载元数据" }
    }
}

private fun deletePartialArtifacts(partialFile: File) {
    deleteFileOrThrow(partialFile, "无法删除断点音频文件")
    deleteResumableMetadata(partialFile, requireSuccess = true)
}

private fun resumableMetadataFile(partialFile: File): File =
    File("${partialFile.path}$RESUME_METADATA_SUFFIX")

private fun temporaryResumableMetadataFile(partialFile: File): File =
    File("${partialFile.path}$RESUME_METADATA_TEMP_SUFFIX")

private suspend fun publishViaMediaStore(
    context: Context,
    songId: Long,
    partialFile: File,
    fileName: String,
): DownloadedSongFile {
    val resolver = context.contentResolver
    deleteStalePendingMediaRows(context, songId)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFromFileName(fileName))
        put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOAD_RELATIVE_PATH)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建下载文件")

    try {
        resolver.openOutputStream(uri, "w")?.use { output ->
            partialFile.inputStream().use { input -> copyStream(input, output) }
        } ?: error("无法写入下载文件")

        ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }.also { completedValues ->
            check(resolver.update(uri, completedValues, null, null) == 1) {
                "无法发布下载文件"
            }
        }
        deletePartialArtifacts(partialFile)
        return DownloadedSongFile(fileName = fileName, uri = uri.toString())
    } catch (t: Throwable) {
        runCatching {
            check(resolver.delete(uri, null, null) == 1) {
                "无法清理未发布的 MediaStore 下载文件"
            }
        }.exceptionOrNull()?.let(t::addSuppressed)
        throw t
    }
}

private suspend fun publishLegacyFile(
    partialFile: File,
    fileName: String,
): DownloadedSongFile {
    val dir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS + "/$DOWNLOAD_SUBDIR",
    )
    if (!dir.exists() && !dir.mkdirs()) error("无法创建下载目录")

    val target = File(dir, fileName)
    if (target.exists()) {
        deletePartialArtifacts(partialFile)
        return DownloadedSongFile(fileName = fileName, uri = target.toURI().toString())
    }
    val publishingFile = File(dir, "$fileName.publishing")
    var targetCommitted = false

    try {
        FileOutputStream(publishingFile, false).use { output ->
            partialFile.inputStream().use { input -> copyStream(input, output) }
        }
        if (!publishingFile.renameTo(target)) error("无法保存下载文件")
        targetCommitted = true
        deletePartialArtifacts(partialFile)
        return DownloadedSongFile(fileName = fileName, uri = target.toURI().toString())
    } catch (t: Throwable) {
        runCatching {
            if (targetCommitted) {
                deleteFileOrThrow(target, "无法回滚已发布的下载文件")
            } else {
                deleteFileOrThrow(publishingFile, "无法清理未发布的下载文件")
            }
        }.exceptionOrNull()?.let(t::addSuppressed)
        throw t
    }
}

private fun deleteStalePendingMediaRows(context: Context, songId: Long) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val cursor = context.contentResolver.query(
        collection,
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
            "${MediaStore.MediaColumns.IS_PENDING} = 1",
        arrayOf("$songId.%", DOWNLOAD_RELATIVE_PATH),
        null,
    ) ?: error("MediaStore pending query returned no cursor")
    val pendingUris = cursor.use {
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        buildList<Uri> {
            while (cursor.moveToNext()) {
                add(ContentUris.withAppendedId(collection, cursor.getLong(idIndex)))
            }
        }
    }
    pendingUris.forEach { uri ->
        check(context.contentResolver.delete(uri, null, null) == 1) {
            "无法清理遗留的 MediaStore 下载文件"
        }
    }
}

private suspend fun copyWithProgress(
    input: InputStream,
    output: OutputStream,
    initialBytes: Long,
    totalBytes: Long?,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var downloadedBytes = initialBytes
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

private suspend fun copyStream(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        coroutineContext.ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
    }
    output.flush()
}

private fun partialDownloadFile(
    context: Context,
    item: DownloadRequestItem,
): File {
    val directory = File(context.filesDir, PARTIAL_DOWNLOAD_DIRECTORY)
    check(directory.isDirectory || directory.mkdirs()) { "无法创建断点下载目录" }

    val partialFile = File(directory, resumablePartialFileName(item.id, item.resumeKey))
    val retainedNames = setOf(
        partialFile.name,
        resumableMetadataFile(partialFile).name,
    )
    checkNotNull(directory.listFiles()) { "无法读取断点下载目录" }
        .asSequence()
        .filter(File::isFile)
        .filter {
            it.name !in retainedNames && isPartialArtifactForSong(it, item.id)
        }
        .forEach { file -> deleteFileOrThrow(file, "无法删除旧的断点下载文件") }
    return partialFile
}

private fun deletePartialDownloads(context: Context, songId: Long) {
    val directory = File(context.filesDir, PARTIAL_DOWNLOAD_DIRECTORY)
    if (!directory.exists()) return
    checkNotNull(directory.listFiles()) { "无法读取断点下载目录" }
        .asSequence()
        .filter(File::isFile)
        .filter { isPartialArtifactForSong(it, songId) }
        .forEach { file -> deleteFileOrThrow(file, "无法删除断点下载文件") }
}

private fun deleteFileOrThrow(file: File, message: String) {
    val deleted = !file.exists() || file.delete()
    check(deleted || !file.exists()) { message }
}

private fun isPartialArtifactForSong(file: File, songId: Long): Boolean =
    file.name.startsWith("$songId.") &&
        (
            file.name.endsWith(".part") ||
                file.name.endsWith(RESUME_METADATA_SUFFIX) ||
                file.name.endsWith(RESUME_METADATA_TEMP_SUFFIX)
        )

private data class DownloadWritePlan(
    val initialBytes: Long,
    val totalBytes: Long?,
    val append: Boolean,
)

private fun mimeTypeFromFileName(fileName: String): String = when (fileName.substringAfterLast('.').lowercase()) {
    "flac" -> "audio/flac"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "wav" -> "audio/wav"
    else -> "audio/mpeg"
}

private const val PARTIAL_DOWNLOAD_DIRECTORY = "song-download-parts"
private const val RESUME_METADATA_SUFFIX = ".validator"
private const val RESUME_METADATA_TEMP_SUFFIX = ".validator.tmp"

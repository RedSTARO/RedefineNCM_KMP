@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.CancellationException
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.posix.remove
import platform.posix.rename

/** Called under the coordinator lock. No asynchronous callback may delete the final file. */
internal fun publishIosDownload(
    temporaryUrl: NSURL,
    directory: String,
    fileName: String,
    taskIdentifier: ULong,
    onProgress: (Long) -> Unit,
    onReadyToPublish: () -> Boolean,
): DownloadedSongFile {
    require(isValidIosDownloadFileName(fileName)) { strings.downloadFileNameInvalid }
    val temporaryPath = temporaryUrl.path ?: error(strings.downloadFileSaveFailed)
    val bytes = iosRegularDownloadFileSize(temporaryPath) ?: error(strings.downloadFileSaveFailed)
    check(bytes > 0L) { strings.downloadEmptyNotSaved }
    onProgress(bytes)
    if (!onReadyToPublish()) throw CancellationException(strings.downloadCanceledNotSaved)

    val targetPath = "$directory/$fileName"
    val partPath = "$targetPath.$taskIdentifier.part"
    try {
        // A surviving background task may finish after a later task already saved this song.
        // Match the downloader's existing-file policy and retain that complete audio.
        if ((iosRegularDownloadFileSize(targetPath) ?: 0L) <= 0L) {
            remove(partPath)
            val moved = NSFileManager.defaultManager.moveItemAtURL(
                srcURL = temporaryUrl,
                toURL = NSURL.fileURLWithPath(partPath),
                error = null,
            )
            check(moved) { strings.backgroundDownloadMoveFailed }
            check(rename(partPath, targetPath) == 0) { strings.backgroundDownloadSaveFailed }
        }
        return DownloadedSongFile(fileName, NSURL.fileURLWithPath(targetPath).absoluteString)
    } finally {
        // Only this task's staging file is disposable; other tasks and final audio are untouched.
        remove(partPath)
    }
}

internal fun iosRegularDownloadFileSize(path: String): Long? {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return null
    if (attributes[NSFileType] != NSFileTypeRegular) return null
    return (attributes[NSFileSize] as? NSNumber)?.longLongValue
}

internal fun isValidIosDownloadFileName(fileName: String): Boolean = IosDownloadFileName.matches(fileName)

private val IosDownloadFileName = Regex("^[0-9]+[.][A-Za-z0-9]{1,12}$")

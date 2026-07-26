@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fileno
import platform.posix.fopen
import platform.posix.fsync
import platform.posix.fwrite
import platform.posix.memcpy

actual object LocalMediaAssetStorage {
    private val mutex = Mutex()

    actual suspend fun replaceLyrics(
        songId: Long,
        files: List<LocalTextMediaAsset>,
    ) {
        val validated = validateLocalLyricAssets(songId, files)
        mutex.withLock {
            withContext(Dispatchers.Default) {
                val directory = ensureIosDownloadDirectory()
                val manager = NSFileManager.defaultManager
                val staged = mutableListOf<Pair<String, String>>()
                val backups = mutableListOf<Pair<String, String>>()
                val published = mutableListOf<String>()
                try {
                    validated.forEach { asset ->
                        val target = "$directory/${asset.fileName}"
                        val temporary = temporaryIosAssetPath(directory, asset.fileName)
                        staged += temporary to target
                        writeIosAsset(temporary, asset.content.encodeToByteArray())
                    }
                    iosAssetFileNames(directory)
                        .filter { isLocalLyricSidecarFileName(songId, it) }
                        .forEach { fileName ->
                            val original = "$directory/$fileName"
                            val backup = backupIosAssetPath(directory, fileName)
                            check(manager.moveItemAtPath(original, backup, error = null)) {
                                "无法备份 iOS 本地歌词：$fileName"
                            }
                            backups += backup to original
                        }
                    staged.forEach { (temporary, target) ->
                        check(manager.moveItemAtPath(temporary, target, error = null)) {
                            "无法发布 iOS 本地歌词：${target.substringAfterLast('/')}"
                        }
                        published += target
                    }
                    backups.forEach { (backup, _) ->
                        manager.removeItemAtPath(backup, error = null)
                    }
                } catch (failure: Throwable) {
                    staged.forEach { (temporary, _) ->
                        manager.removeItemAtPath(temporary, error = null)
                    }
                    published.forEach { target ->
                        manager.removeItemAtPath(target, error = null)
                    }
                    backups.forEach { (backup, original) ->
                        if (manager.fileExistsAtPath(backup)) {
                            val restored = manager.moveItemAtPath(backup, original, error = null)
                            if (!restored) {
                                failure.addSuppressed(
                                    IllegalStateException(
                                        "无法恢复 iOS 本地歌词：${original.substringAfterLast('/')}"
                                    )
                                )
                            }
                        }
                    }
                    throw failure
                }
            }
        }
    }

    actual suspend fun readLyrics(
        songId: Long,
        fileNames: Set<String>,
    ): List<LocalTextMediaAsset> {
        val validatedNames = validateLocalLyricFileNames(songId, fileNames)
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                val directory = iosDownloadDirectoryPath()
                val manager = NSFileManager.defaultManager
                if (!manager.fileExistsAtPath(directory)) {
                    emptyList()
                } else {
                    iosAssetFileNames(directory)
                        .filter { isLocalLyricSidecarFileName(songId, it) }
                        .filter { it in validatedNames }
                        .sorted()
                        .map { fileName ->
                            LocalTextMediaAsset(
                                fileName = fileName,
                                content = readIosAsset("$directory/$fileName").decodeToString(),
                            )
                        }
                }
            }
        }
    }

    actual suspend fun replaceArtwork(
        songId: Long,
        fileExtension: String,
        mimeType: String,
        bytes: ByteArray,
    ): String {
        requireLocalMediaSongId(songId)
        val extension = validateLocalArtworkExtension(fileExtension, mimeType, bytes)
        val fileName = localArtworkFileName(songId, extension)
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                val directory = ensureIosDownloadDirectory()
                val manager = NSFileManager.defaultManager
                val temporary = temporaryIosAssetPath(directory, fileName)
                val target = "$directory/$fileName"
                val backups = mutableListOf<Pair<String, String>>()
                var published = false
                try {
                    writeIosAsset(temporary, bytes)
                    iosAssetFileNames(directory)
                        .filter { isLocalArtworkSidecarFileName(songId, it) }
                        .forEach { oldFileName ->
                            val original = "$directory/$oldFileName"
                            val backup = backupIosAssetPath(directory, oldFileName)
                            check(manager.moveItemAtPath(original, backup, error = null)) {
                                "无法备份 iOS 本地封面：$oldFileName"
                            }
                            backups += backup to original
                        }
                    check(manager.moveItemAtPath(temporary, target, error = null)) {
                        "无法发布 iOS 本地封面：$fileName"
                    }
                    published = true
                    backups.forEach { (backup, _) ->
                        manager.removeItemAtPath(backup, error = null)
                    }
                } catch (failure: Throwable) {
                    manager.removeItemAtPath(temporary, error = null)
                    if (published) manager.removeItemAtPath(target, error = null)
                    backups.forEach { (backup, original) ->
                        if (manager.fileExistsAtPath(backup)) {
                            val restored = manager.moveItemAtPath(backup, original, error = null)
                            if (!restored) {
                                failure.addSuppressed(
                                    IllegalStateException(
                                        "无法恢复 iOS 本地封面：${original.substringAfterLast('/')}"
                                    )
                                )
                            }
                        }
                    }
                    throw failure
                }
                fileName
            }
        }
    }

    actual suspend fun inspect(songId: Long): LocalMediaAssetSnapshot {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                val directory = iosDownloadDirectoryPath()
                val manager = NSFileManager.defaultManager
                val names = if (manager.fileExistsAtPath(directory)) {
                    iosAssetFileNames(directory)
                } else {
                    emptyList()
                }
                localMediaAssetSnapshot(songId, names)
            }
        }
    }

    actual suspend fun resolveArtworkUri(songId: Long): String? {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                val directory = iosDownloadDirectoryPath()
                val manager = NSFileManager.defaultManager
                if (!manager.fileExistsAtPath(directory)) {
                    null
                } else {
                    iosAssetFileNames(directory)
                        .filter { isLocalArtworkSidecarFileName(songId, it) }
                        .sorted()
                        .firstOrNull()
                        ?.let { NSURL.fileURLWithPath("$directory/$it").absoluteString }
                }
            }
        }
    }

    actual fun releaseArtworkUri(uri: String) = Unit

    actual suspend fun deleteAssets(songId: Long): Boolean {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                val directory = iosDownloadDirectoryPath()
                val manager = NSFileManager.defaultManager
                if (!manager.fileExistsAtPath(directory)) {
                    false
                } else {
                    val files = iosAssetFileNames(directory).filter { fileName ->
                        isLocalLyricSidecarFileName(songId, fileName) ||
                            isLocalArtworkSidecarFileName(songId, fileName) ||
                            isLocalMediaAssetTransactionFileName(songId, fileName)
                    }
                    files.forEach { deleteIosAssetOrThrow("$directory/$it") }
                    files.isNotEmpty()
                }
            }
        }
    }
}

private fun iosAssetFileNames(directory: String): List<String> {
    val entries = NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory, error = null)
        ?: error("无法读取 iOS 下载目录：$directory")
    return entries.mapNotNull { it as? String }
}

private fun temporaryIosAssetPath(directory: String, targetName: String): String =
    "$directory/.$targetName.${NSUUID.UUID().UUIDString}.asset-pending"

private fun backupIosAssetPath(directory: String, targetName: String): String =
    "$directory/.$targetName.${NSUUID.UUID().UUIDString}.asset-backup"

private fun writeIosAsset(path: String, bytes: ByteArray) {
    val file = fopen(path, "wb")
        ?: error("无法创建 iOS 本地媒体边车：${path.substringAfterLast('/')}")
    try {
        if (bytes.isNotEmpty()) {
            val written = bytes.usePinned { pinned ->
                fwrite(
                    pinned.addressOf(0),
                    1.convert(),
                    bytes.size.convert(),
                    file,
                )
            }
            check(written.toLong() == bytes.size.toLong()) {
                "iOS 本地媒体边车写入不完整：${path.substringAfterLast('/')}"
            }
        }
        check(fflush(file) == 0) {
            "无法刷新 iOS 本地媒体边车：${path.substringAfterLast('/')}"
        }
        check(fsync(fileno(file)) == 0) {
            "无法持久化 iOS 本地媒体边车：${path.substringAfterLast('/')}"
        }
    } finally {
        fclose(file)
    }
}

private fun readIosAsset(path: String): ByteArray {
    val data = NSFileManager.defaultManager.contentsAtPath(path)
        ?: error("无法读取 iOS 本地媒体边车：${path.substringAfterLast('/')}")
    val size = data.length.toInt()
    return ByteArray(size).also { bytes ->
        if (size > 0) {
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
    }
}

private fun deleteIosAssetOrThrow(path: String) {
    val manager = NSFileManager.defaultManager
    check(!manager.fileExistsAtPath(path) || manager.removeItemAtPath(path, error = null)) {
        "无法删除 iOS 本地媒体边车：${path.substringAfterLast('/')}"
    }
}

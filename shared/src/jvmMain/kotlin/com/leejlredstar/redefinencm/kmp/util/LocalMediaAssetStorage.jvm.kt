package com.leejlredstar.redefinencm.kmp.util

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

actual object LocalMediaAssetStorage {
    private val mutex = Mutex()

    actual suspend fun replaceLyrics(
        songId: Long,
        files: List<LocalTextMediaAsset>,
    ) {
        val validated = validateLocalLyricAssets(songId, files)
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val directory = ensureJvmAssetDirectory()
                val staged = mutableListOf<Pair<File, File>>()
                val backups = mutableListOf<Pair<File, File>>()
                val published = mutableListOf<File>()
                try {
                    validated.forEach { asset ->
                        val target = File(directory, asset.fileName)
                        val temporary = temporaryAssetFile(directory, asset.fileName)
                        staged += temporary to target
                        writeAndSync(temporary, asset.content.encodeToByteArray())
                    }
                    directory.assetFiles(songId, ::isLocalLyricSidecarFileName).forEach { original ->
                        val backup = backupAssetFile(directory, original.name)
                        moveAssetFile(original, backup)
                        backups += backup to original
                    }
                    staged.forEach { (temporary, target) ->
                        moveAssetFile(temporary, target)
                        published += target
                    }
                    backups.forEach { (backup, _) -> backup.delete() }
                } catch (failure: Throwable) {
                    staged.forEach { (temporary, _) -> temporary.delete() }
                    published.forEach { it.delete() }
                    backups.forEach { (backup, original) ->
                        if (backup.exists()) {
                            runCatching { moveAssetFile(backup, original) }
                                .exceptionOrNull()
                                ?.let(failure::addSuppressed)
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
            withContext(Dispatchers.IO) {
                val directory = jvmDownloadDirectory()
                if (!directory.exists()) {
                    emptyList()
                } else {
                    directory.assetFiles(songId, ::isLocalLyricSidecarFileName)
                        .filter { it.name in validatedNames }
                        .sortedBy { it.name }
                        .map { file ->
                            LocalTextMediaAsset(
                                fileName = file.name,
                                content = file.readText(Charsets.UTF_8),
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
            withContext(Dispatchers.IO) {
                val directory = ensureJvmAssetDirectory()
                val temporary = temporaryAssetFile(directory, fileName)
                val backups = mutableListOf<Pair<File, File>>()
                var published: File? = null
                try {
                    writeAndSync(temporary, bytes)
                    directory.assetFiles(songId, ::isLocalArtworkSidecarFileName).forEach { original ->
                        val backup = backupAssetFile(directory, original.name)
                        moveAssetFile(original, backup)
                        backups += backup to original
                    }
                    val target = File(directory, fileName)
                    moveAssetFile(temporary, target)
                    published = target
                    backups.forEach { (backup, _) -> backup.delete() }
                    fileName
                } catch (failure: Throwable) {
                    temporary.delete()
                    published?.delete()
                    backups.forEach { (backup, original) ->
                        if (backup.exists()) {
                            runCatching { moveAssetFile(backup, original) }
                                .exceptionOrNull()
                                ?.let(failure::addSuppressed)
                        }
                    }
                    throw failure
                }
            }
        }
    }

    actual suspend fun inspect(songId: Long): LocalMediaAssetSnapshot {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                localMediaAssetSnapshot(songId, assetFileNames(jvmDownloadDirectory()))
            }
        }
    }

    actual suspend fun resolveArtworkUri(songId: Long): String? {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val directory = jvmDownloadDirectory()
                val snapshot = localMediaAssetSnapshot(songId, assetFileNames(directory))
                snapshot.artworkFileName
                    ?.let { File(directory, it) }
                    ?.takeIf(File::isFile)
                    ?.toURI()
                    ?.toString()
            }
        }
    }

    actual fun releaseArtworkUri(uri: String) = Unit

    actual suspend fun deleteAssets(songId: Long): Boolean {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val directory = jvmDownloadDirectory()
                if (!directory.exists()) {
                    false
                } else {
                    val assets = directory.assetFiles(songId, ::isLocalLyricSidecarFileName) +
                        directory.assetFiles(songId, ::isLocalArtworkSidecarFileName) +
                        directory.assetFiles(songId, ::isLocalMediaAssetTransactionFileName)
                    assets.forEach(::deleteAssetFileOrThrow)
                    assets.isNotEmpty()
                }
            }
        }
    }
}

private fun ensureJvmAssetDirectory(): File =
    jvmDownloadDirectory().also { directory ->
        check(directory.isDirectory || directory.mkdirs()) {
            "无法创建桌面下载目录：$directory"
        }
    }

private fun assetFileNames(directory: File): List<String> {
    if (!directory.exists()) return emptyList()
    check(directory.isDirectory) { "下载路径不是目录：$directory" }
    return checkNotNull(directory.listFiles()) { "无法读取下载目录：$directory" }
        .asSequence()
        .filter(File::isFile)
        .map { it.name }
        .toList()
}

private fun File.assetFiles(
    songId: Long,
    predicate: (Long, String) -> Boolean,
): List<File> {
    if (!exists()) return emptyList()
    check(isDirectory) { "下载路径不是目录：$this" }
    return checkNotNull(listFiles()) { "无法读取下载目录：$this" }
        .asSequence()
        .filter(File::isFile)
        .filter { predicate(songId, it.name) }
        .toList()
}

private fun temporaryAssetFile(directory: File, targetName: String): File =
    File(directory, ".$targetName.${UUID.randomUUID()}.asset-pending")

private fun backupAssetFile(directory: File, targetName: String): File =
    File(directory, ".$targetName.${UUID.randomUUID()}.asset-backup")

private fun writeAndSync(file: File, bytes: ByteArray) {
    FileOutputStream(file, false).use { output ->
        output.write(bytes)
        output.fd.sync()
    }
}

private fun moveAssetFile(source: File, target: File) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

private fun deleteAssetFileOrThrow(file: File) {
    check(!file.exists() || file.delete() || !file.exists()) {
        "无法删除本地媒体边车：${file.name}"
    }
}

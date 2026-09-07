package com.leejlredstar.redefinencm.kmp.util

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
                replaceLocalMediaAssetFiles(
                    directory = directory,
                    replacements = validated.map {
                        LocalMediaAssetWrite(it.fileName, it.content.encodeToByteArray())
                    },
                    displaced = jvmAssetFiles(directory, songId, ::isLocalLyricSidecarFileName),
                    move = ::moveAssetFile,
                )
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
                    jvmAssetFiles(directory, songId, ::isLocalLyricSidecarFileName)
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
                replaceLocalMediaAssetFiles(
                    directory = directory,
                    replacements = listOf(LocalMediaAssetWrite(fileName, bytes)),
                    displaced = jvmAssetFiles(directory, songId, ::isLocalArtworkSidecarFileName),
                    move = ::moveAssetFile,
                )
                fileName
            }
        }
    }

    actual suspend fun inspect(songId: Long): LocalMediaAssetSnapshot {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                localMediaAssetSnapshot(songId, jvmAssetFileNames(jvmDownloadDirectory()))
            }
        }
    }

    actual suspend fun inspectAll(songIds: Collection<Long>): Map<Long, LocalMediaAssetSnapshot> {
        songIds.forEach(::requireLocalMediaSongId)
        if (songIds.isEmpty()) return emptyMap()
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                localMediaAssetSnapshots(songIds, jvmAssetFileNames(jvmDownloadDirectory()))
            }
        }
    }

    actual suspend fun resolveArtworkUri(songId: Long): String? {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val directory = jvmDownloadDirectory()
                val snapshot = localMediaAssetSnapshot(songId, jvmAssetFileNames(directory))
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
                    val assets = listOf(
                        ::isLocalLyricSidecarFileName,
                        ::isLocalArtworkSidecarFileName,
                        ::isLocalMediaAssetTransactionFileName,
                    ).flatMap { predicate ->
                        jvmAssetFiles(directory, songId, predicate)
                    }
                    assets.forEach(::deleteLocalMediaAssetOrThrow)
                    assets.isNotEmpty()
                }
            }
        }
    }
}

private const val JVM_DOWNLOAD_DIRECTORY_LABEL = "桌面下载路径"

private fun ensureJvmAssetDirectory(): File =
    ensureLocalMediaAssetDirectory(jvmDownloadDirectory(), JVM_DOWNLOAD_DIRECTORY_LABEL)

private fun jvmAssetFileNames(directory: File): List<String> =
    localMediaAssetFileNames(directory, JVM_DOWNLOAD_DIRECTORY_LABEL)

private fun jvmAssetFiles(
    directory: File,
    songId: Long,
    predicate: (Long, String) -> Boolean,
): List<File> = localMediaAssetFiles(directory, songId, JVM_DOWNLOAD_DIRECTORY_LABEL, predicate)

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

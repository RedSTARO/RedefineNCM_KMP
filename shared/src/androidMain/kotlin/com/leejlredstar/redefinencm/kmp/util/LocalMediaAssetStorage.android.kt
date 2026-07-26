package com.leejlredstar.redefinencm.kmp.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

actual object LocalMediaAssetStorage {
    private val mutex = Mutex()

    actual suspend fun replaceLyrics(
        songId: Long,
        files: List<LocalTextMediaAsset>,
    ) {
        val validated = validateLocalLyricAssets(songId, files)
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val context = KoinPlatform.getKoin().get<Context>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    replaceMediaStoreLyrics(context, songId, validated)
                } else {
                    replaceLegacyLyrics(songId, validated)
                }
            }
        }
    }

    actual suspend fun readLyrics(
        songId: Long,
        fileNames: Set<String>,
    ): List<LocalTextMediaAsset> {
        val requestedFileNames = validateLocalLyricFileNames(songId, fileNames)
        if (requestedFileNames.isEmpty()) return emptyList()
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val context = KoinPlatform.getKoin().get<Context>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    queryMediaStoreAssetRows(context, songId)
                        .filter { it.fileName in requestedFileNames }
                        .sortedBy { it.fileName }
                        .map { row ->
                            val content = context.contentResolver.openInputStream(row.uri)
                                ?.bufferedReader(Charsets.UTF_8)
                                ?.use { it.readText() }
                                ?: error("无法读取本地歌词：${row.fileName}")
                            LocalTextMediaAsset(row.fileName, content)
                        }
                } else {
                    legacyAssetFiles(songId, ::isLocalLyricSidecarFileName)
                        .filter { it.name in requestedFileNames }
                        .sortedBy { it.name }
                        .map { LocalTextMediaAsset(it.name, it.readText(Charsets.UTF_8)) }
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
                val context = KoinPlatform.getKoin().get<Context>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    var inserted: Uri? = null
                    val backups = mutableListOf<AndroidMediaAssetBackup>()
                    try {
                        val temporaryName = temporaryMediaStoreAssetName(fileName)
                        inserted = insertPendingMediaStoreAsset(
                            context = context,
                            fileName = temporaryName,
                            mimeType = mimeType.trim(),
                            bytes = bytes,
                        )
                        backupMediaStoreAssets(
                            context = context,
                            songId = songId,
                            predicate = ::isLocalArtworkSidecarFileName,
                            backups = backups,
                        )
                        publishMediaStoreAsset(context, inserted, fileName)
                    } catch (failure: Throwable) {
                        rollbackMediaStoreReplacement(
                            context = context,
                            inserted = listOfNotNull(inserted),
                            backups = backups,
                            failure = failure,
                        )
                        throw failure
                    }
                    deleteMediaStoreBackups(context, backups)
                } else {
                    replaceLegacyArtwork(songId, fileName, bytes)
                }
                fileName
            }
        }
    }

    actual suspend fun inspect(songId: Long): LocalMediaAssetSnapshot {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val context = KoinPlatform.getKoin().get<Context>()
                val names = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    queryMediaStoreAssetRows(context, songId).map { it.fileName }
                } else {
                    legacyAssetFileNames()
                }
                localMediaAssetSnapshot(songId, names)
            }
        }
    }

    actual suspend fun resolveArtworkUri(songId: Long): String? {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val context = KoinPlatform.getKoin().get<Context>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    queryMediaStoreAssetRows(context, songId)
                        .filter { isLocalArtworkSidecarFileName(songId, it.fileName) }
                        .sortedBy { it.fileName }
                        .firstOrNull()
                        ?.uri
                        ?.toString()
                } else {
                    legacyAssetFiles(songId, ::isLocalArtworkSidecarFileName)
                        .sortedBy { it.name }
                        .firstOrNull()
                        ?.toURI()
                        ?.toString()
                }
            }
        }
    }

    actual fun releaseArtworkUri(uri: String) = Unit

    actual suspend fun deleteAssets(songId: Long): Boolean {
        requireLocalMediaSongId(songId)
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val context = KoinPlatform.getKoin().get<Context>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    deleteMediaStoreAssets(context, songId) { id, name ->
                        isLocalLyricSidecarFileName(id, name) ||
                            isLocalArtworkSidecarFileName(id, name) ||
                            isLocalMediaAssetTransactionFileName(id, name)
                    }
                } else {
                    val files = legacyAssetFiles(songId, ::isLocalLyricSidecarFileName) +
                        legacyAssetFiles(songId, ::isLocalArtworkSidecarFileName) +
                        legacyAssetFiles(songId, ::isLocalMediaAssetTransactionFileName)
                    files.forEach(::deleteLegacyAssetOrThrow)
                    files.isNotEmpty()
                }
            }
        }
    }
}

private data class AndroidMediaAssetRow(
    val fileName: String,
    val uri: Uri,
)

private data class AndroidMediaAssetBackup(
    val uri: Uri,
    val originalFileName: String,
)

private fun replaceMediaStoreLyrics(
    context: Context,
    songId: Long,
    files: List<LocalTextMediaAsset>,
) {
    val inserted = mutableListOf<Pair<Uri, String>>()
    val backups = mutableListOf<AndroidMediaAssetBackup>()
    try {
        files.forEach { file ->
            val uri = insertPendingMediaStoreAsset(
                context = context,
                fileName = temporaryMediaStoreAssetName(file.fileName),
                mimeType = lyricMimeType(file.fileName),
                bytes = file.content.encodeToByteArray(),
            )
            inserted += uri to file.fileName
        }
        backupMediaStoreAssets(
            context = context,
            songId = songId,
            predicate = ::isLocalLyricSidecarFileName,
            backups = backups,
        )
        inserted.forEach { (uri, targetName) ->
            publishMediaStoreAsset(context, uri, targetName)
        }
    } catch (failure: Throwable) {
        rollbackMediaStoreReplacement(
            context = context,
            inserted = inserted.map { it.first },
            backups = backups,
            failure = failure,
        )
        throw failure
    }
    deleteMediaStoreBackups(context, backups)
}

private fun backupMediaStoreAssets(
    context: Context,
    songId: Long,
    predicate: (Long, String) -> Boolean,
    backups: MutableList<AndroidMediaAssetBackup>,
) {
    queryAllMediaStoreRows(context)
        .filter { predicate(songId, it.fileName) }
        .forEach { row ->
            val backup = AndroidMediaAssetBackup(
                uri = row.uri,
                originalFileName = row.fileName,
            )
            backups += backup
            val values = ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    backupMediaStoreAssetName(row.fileName),
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            check(context.contentResolver.update(row.uri, values, null, null) == 1) {
                "无法备份本地媒体边车：${row.fileName}"
            }
        }
}

private fun rollbackMediaStoreReplacement(
    context: Context,
    inserted: List<Uri>,
    backups: List<AndroidMediaAssetBackup>,
    failure: Throwable,
) {
    inserted.forEach { uri ->
        runCatching {
            context.contentResolver.delete(uri, null, null)
        }.exceptionOrNull()?.let(failure::addSuppressed)
    }
    backups.forEach { backup ->
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, backup.originalFileName)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            check(context.contentResolver.update(backup.uri, values, null, null) == 1) {
                "无法恢复本地媒体边车：${backup.originalFileName}"
            }
        }.exceptionOrNull()?.let(failure::addSuppressed)
    }
}

private fun deleteMediaStoreBackups(
    context: Context,
    backups: List<AndroidMediaAssetBackup>,
) {
    backups.forEach { backup ->
        runCatching {
            context.contentResolver.delete(backup.uri, null, null)
        }
    }
}

private fun queryMediaStoreAssetRows(
    context: Context,
    songId: Long,
): List<AndroidMediaAssetRow> {
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val cursor = context.contentResolver.query(
        collection,
        arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
        ),
        "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
            "${MediaStore.MediaColumns.IS_PENDING} = 0",
        arrayOf(DOWNLOAD_RELATIVE_PATH),
        "${MediaStore.MediaColumns.DISPLAY_NAME} ASC",
    ) ?: error("MediaStore query returned no cursor")
    return cursor.use {
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        buildList {
            while (cursor.moveToNext()) {
                val fileName = cursor.getString(nameIndex) ?: continue
                if (
                    !isLocalLyricSidecarFileName(songId, fileName) &&
                    !isLocalArtworkSidecarFileName(songId, fileName)
                ) continue
                add(
                    AndroidMediaAssetRow(
                        fileName = fileName,
                        uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex)),
                    )
                )
            }
        }
    }
}

private fun deleteMediaStoreAssets(
    context: Context,
    songId: Long,
    predicate: (Long, String) -> Boolean,
): Boolean {
    val rows = queryAllMediaStoreRows(context)
        .filter { predicate(songId, it.fileName) }
    rows.forEach { row ->
        check(context.contentResolver.delete(row.uri, null, null) > 0) {
            "无法删除本地媒体边车：${row.fileName}"
        }
    }
    return rows.isNotEmpty()
}

private fun queryAllMediaStoreRows(context: Context): List<AndroidMediaAssetRow> {
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val cursor = context.contentResolver.query(
        collection,
        arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
        ),
        "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
        arrayOf(DOWNLOAD_RELATIVE_PATH),
        null,
    ) ?: error("MediaStore query returned no cursor")
    return cursor.use {
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        buildList {
            while (cursor.moveToNext()) {
                val fileName = cursor.getString(nameIndex) ?: continue
                add(
                    AndroidMediaAssetRow(
                        fileName = fileName,
                        uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex)),
                    )
                )
            }
        }
    }
}

private fun insertPendingMediaStoreAsset(
    context: Context,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
): Uri {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOAD_RELATIVE_PATH)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = context.contentResolver.insert(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        values,
    ) ?: error("无法创建本地媒体边车：$fileName")
    try {
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: error("无法写入本地媒体边车：$fileName")
        return uri
    } catch (failure: Throwable) {
        context.contentResolver.delete(uri, null, null)
        throw failure
    }
}

private fun publishMediaStoreAsset(
    context: Context,
    uri: Uri,
    fileName: String,
) {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.IS_PENDING, 0)
    }
    check(context.contentResolver.update(uri, values, null, null) == 1) {
        "无法发布本地媒体边车"
    }
}

private fun temporaryMediaStoreAssetName(targetName: String): String =
    ".$targetName.${UUID.randomUUID()}.asset-pending"

private fun backupMediaStoreAssetName(targetName: String): String =
    ".$targetName.${UUID.randomUUID()}.asset-backup"

private fun lyricMimeType(fileName: String): String =
    if (fileName.endsWith(".ttml", ignoreCase = true)) {
        "application/ttml+xml"
    } else {
        "text/plain"
    }

private fun replaceLegacyLyrics(
    songId: Long,
    files: List<LocalTextMediaAsset>,
) {
    val directory = ensureLegacyAssetDirectory()
    val staged = mutableListOf<Pair<File, File>>()
    val backups = mutableListOf<Pair<File, File>>()
    val published = mutableListOf<File>()
    try {
        files.forEach { file ->
            val target = File(directory, file.fileName)
            val temporary = temporaryLegacyAssetFile(directory, file.fileName)
            staged += temporary to target
            writeLegacyAsset(temporary, file.content.encodeToByteArray())
        }
        legacyAssetFiles(songId, ::isLocalLyricSidecarFileName).forEach { original ->
            val backup = backupLegacyAssetFile(directory, original.name)
            check(original.renameTo(backup)) {
                "无法备份 Android 本地歌词：${original.name}"
            }
            backups += backup to original
        }
        staged.forEach { (temporary, target) ->
            check(temporary.renameTo(target)) {
                "无法发布本地歌词：${target.name}"
            }
            published += target
        }
        backups.forEach { (backup, _) -> backup.delete() }
    } catch (failure: Throwable) {
        staged.forEach { (temporary, _) -> temporary.delete() }
        published.forEach { it.delete() }
        backups.forEach { (backup, original) ->
            if (backup.exists() && !backup.renameTo(original)) {
                failure.addSuppressed(
                    IllegalStateException("无法恢复 Android 本地歌词：${original.name}")
                )
            }
        }
        throw failure
    }
}

private fun replaceLegacyArtwork(
    songId: Long,
    fileName: String,
    bytes: ByteArray,
) {
    val directory = ensureLegacyAssetDirectory()
    val temporary = temporaryLegacyAssetFile(directory, fileName)
    val backups = mutableListOf<Pair<File, File>>()
    var published: File? = null
    try {
        writeLegacyAsset(temporary, bytes)
        legacyAssetFiles(songId, ::isLocalArtworkSidecarFileName).forEach { original ->
            val backup = backupLegacyAssetFile(directory, original.name)
            check(original.renameTo(backup)) {
                "无法备份 Android 本地封面：${original.name}"
            }
            backups += backup to original
        }
        val target = File(directory, fileName)
        check(temporary.renameTo(target)) {
            "无法发布本地封面：$fileName"
        }
        published = target
        backups.forEach { (backup, _) -> backup.delete() }
    } catch (failure: Throwable) {
        temporary.delete()
        published?.delete()
        backups.forEach { (backup, original) ->
            if (backup.exists() && !backup.renameTo(original)) {
                failure.addSuppressed(
                    IllegalStateException("无法恢复 Android 本地封面：${original.name}")
                )
            }
        }
        throw failure
    }
}

private fun legacyAssetFileNames(): List<String> {
    val directory = legacyAssetDirectory()
    if (!directory.exists()) return emptyList()
    check(directory.isDirectory) { "Android 下载路径不是目录：$directory" }
    return checkNotNull(directory.listFiles()) { "无法读取 Android 下载目录：$directory" }
        .asSequence()
        .filter(File::isFile)
        .map { it.name }
        .toList()
}

private fun legacyAssetFiles(
    songId: Long,
    predicate: (Long, String) -> Boolean,
): List<File> {
    val directory = legacyAssetDirectory()
    if (!directory.exists()) return emptyList()
    check(directory.isDirectory) { "Android 下载路径不是目录：$directory" }
    return checkNotNull(directory.listFiles()) { "无法读取 Android 下载目录：$directory" }
        .asSequence()
        .filter(File::isFile)
        .filter { predicate(songId, it.name) }
        .toList()
}

private fun legacyAssetDirectory(): File =
    Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS + "/$DOWNLOAD_SUBDIR",
    )

private fun ensureLegacyAssetDirectory(): File =
    legacyAssetDirectory().also { directory ->
        check(directory.isDirectory || directory.mkdirs()) {
            "无法创建 Android 下载目录：$directory"
        }
    }

private fun temporaryLegacyAssetFile(directory: File, targetName: String): File =
    File(directory, ".$targetName.${UUID.randomUUID()}.asset-pending")

private fun backupLegacyAssetFile(directory: File, targetName: String): File =
    File(directory, ".$targetName.${UUID.randomUUID()}.asset-backup")

private fun writeLegacyAsset(file: File, bytes: ByteArray) {
    FileOutputStream(file, false).use { output ->
        output.write(bytes)
        output.fd.sync()
    }
}

private fun deleteLegacyAssetOrThrow(file: File) {
    check(!file.exists() || file.delete() || !file.exists()) {
        "无法删除本地媒体边车：${file.name}"
    }
}

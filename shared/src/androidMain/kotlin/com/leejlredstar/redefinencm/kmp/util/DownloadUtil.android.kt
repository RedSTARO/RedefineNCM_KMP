package com.leejlredstar.redefinencm.kmp.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.koin.mp.KoinPlatform
import java.io.File

private const val DOWNLOAD_SUBDIR = "RedefineNCM"

/**
 * Android-specific: scan the RedefineNCM download folder and return all downloaded songs.
 * Called once by [DownloadedSongsCache] and cached for O(1) lookups.
 */
actual fun scanDownloadedSongs(): List<DownloadedSongSnapshot> =
    scanDownloadedSongFiles().values
        .sortedWith(
            compareByDescending<DownloadedSongSnapshot> { it.lastModifiedEpochMillis ?: 0L }
                .thenBy { it.id }
        )

actual fun scanDownloadedSongIds(): Set<Long> =
    scanDownloadedSongFiles().keys

fun findDownloadedSongUri(songId: Long): String? =
    scanDownloadedSongFiles(songId)[songId]?.uri

private fun scanDownloadedSongFiles(targetSongId: Long? = null): Map<Long, DownloadedSongSnapshot> {
    val result = linkedMapOf<Long, DownloadedSongSnapshot>()
    val context = runCatching { KoinPlatform.getKoin().get<Context>() }.getOrNull()
    if (context != null) {
        result.putAll(queryMediaStore(context, targetSongId))
    }
    scanLegacyDownloadDir(targetSongId).forEach { (id, snapshot) ->
        result.putIfAbsent(id, snapshot)
    }
    return result
}

private fun queryMediaStore(context: Context, targetSongId: Long?): Map<Long, DownloadedSongSnapshot> {
    val result = linkedMapOf<Long, DownloadedSongSnapshot>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        result.putAll(
            queryMediaCollection(
                context = context,
                collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                targetSongId = targetSongId,
                canFilterRelativePath = true,
            )
        )
    }
    queryMediaCollection(
        context = context,
        collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        targetSongId = targetSongId,
        canFilterRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    ).forEach { (id, snapshot) ->
        result.putIfAbsent(id, snapshot)
    }
    return result
}

private fun queryMediaCollection(
    context: Context,
    collectionUri: Uri,
    targetSongId: Long?,
    canFilterRelativePath: Boolean,
): Map<Long, DownloadedSongSnapshot> = runCatching {
    val result = linkedMapOf<Long, DownloadedSongSnapshot>()
    val projection = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.DATE_MODIFIED)
        if (canFilterRelativePath) {
            add(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            add(MediaStore.MediaColumns.DATA)
        }
    }.toTypedArray()

    val selection = buildList {
        add("${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?")
        if (canFilterRelativePath) {
            add("${MediaStore.MediaColumns.RELATIVE_PATH} = ?")
        }
    }.joinToString(" AND ")
    val selectionArgs = buildList {
        add(targetSongId?.let { "$it.%" } ?: "%.%")
        if (canFilterRelativePath) {
            add(downloadRelativePath())
        }
    }.toTypedArray()

    context.contentResolver.query(collectionUri, projection, selection, selectionArgs, null)
        ?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathIndex = if (canFilterRelativePath) {
                cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            }

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex) ?: continue
                val songId = songIdFromFileName(displayName, targetSongId) ?: continue
                if (pathIndex >= 0) {
                    val path = cursor.getString(pathIndex)
                    if (path != null && !isRedefineDownloadPath(path)) continue
                }
                val mediaId = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(collectionUri, mediaId).toString()
                val sizeBytes = if (sizeIndex >= 0) cursor.getLong(sizeIndex).takeIf { it > 0L } else null
                val modifiedSeconds = if (modifiedIndex >= 0) {
                    cursor.getLong(modifiedIndex).takeIf { it > 0L }
                } else {
                    null
                }
                result[songId] = DownloadedSongSnapshot(
                    id = songId,
                    fileName = displayName,
                    uri = uri,
                    sizeBytes = sizeBytes,
                    lastModifiedEpochMillis = modifiedSeconds?.let { it * 1000L },
                )
            }
        }
    result
}.getOrDefault(emptyMap())

private fun scanLegacyDownloadDir(targetSongId: Long?): Map<Long, DownloadedSongSnapshot> = runCatching {
    val dir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS + "/$DOWNLOAD_SUBDIR"
    )
    if (!dir.exists() || !dir.isDirectory) {
        emptyMap()
    } else {
        dir.listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.mapNotNull { file ->
                val songId = songIdFromFileName(file.name, targetSongId) ?: return@mapNotNull null
                songId to DownloadedSongSnapshot(
                    id = songId,
                    fileName = file.name,
                    uri = file.toURI().toString(),
                    sizeBytes = file.length().takeIf { it > 0L },
                    lastModifiedEpochMillis = file.lastModified().takeIf { it > 0L },
                )
            }
            ?.toMap(linkedMapOf()) ?: emptyMap()
    }
}.getOrDefault(emptyMap())

/**
 * Legacy direct file-system check. Use [DownloadedSongsCache.isDownloaded] from UI code
 * to avoid repeated directory scans.
 */
actual fun isSongDownloaded(songId: Long): Boolean =
    DownloadedSongsCache.isDownloaded(songId)

actual fun deleteDownloadedSongFile(songId: Long): Boolean {
    var deleted = false
    val context = runCatching { KoinPlatform.getKoin().get<Context>() }.getOrNull()
    if (context != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deleted = deleteFromMediaCollection(
                context = context,
                collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                songId = songId,
                canFilterRelativePath = true,
            ) || deleted
        }
        deleted = deleteFromMediaCollection(
            context = context,
            collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songId = songId,
            canFilterRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        ) || deleted
    }
    return deleteLegacyDownloadedSong(songId) || deleted
}

private fun deleteFromMediaCollection(
    context: Context,
    collectionUri: Uri,
    songId: Long,
    canFilterRelativePath: Boolean,
): Boolean = runCatching {
    var deleted = false
    queryMediaCollection(context, collectionUri, songId, canFilterRelativePath)
        .values
        .mapNotNull { it.uri }
        .forEach { uri ->
            deleted = context.contentResolver.delete(Uri.parse(uri), null, null) > 0 || deleted
        }
    deleted
}.getOrDefault(false)

private fun deleteLegacyDownloadedSong(songId: Long): Boolean = runCatching {
    val dir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS + "/$DOWNLOAD_SUBDIR"
    )
    if (!dir.exists() || !dir.isDirectory) return@runCatching false
    var deleted = false
    dir.listFiles()
        ?.asSequence()
        ?.filter(File::isFile)
        ?.filter { file -> file.name.startsWith("$songId.") }
        ?.forEach { file -> deleted = file.delete() || deleted }
    deleted
}.getOrDefault(false)

private fun songIdFromFileName(fileName: String, targetSongId: Long?): Long? {
    val songId = fileName.substringBeforeLast('.', fileName).toLongOrNull() ?: return null
    return if (targetSongId == null || songId == targetSongId) songId else null
}

private fun downloadRelativePath(): String =
    "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_SUBDIR/"

private fun isRedefineDownloadPath(path: String): Boolean {
    val normalized = path.replace('\\', '/')
    val relativePath = downloadRelativePath()
    return normalized == relativePath ||
        normalized.endsWith("/$relativePath") ||
        normalized.contains("/${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_SUBDIR/")
}

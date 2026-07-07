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
 * Android-specific: scan the RedefineNCM download folder and return all downloaded song IDs.
 * Called once by [DownloadedSongsCache] and cached for O(1) lookups.
 */
actual fun scanDownloadedSongIds(): Set<Long> =
    scanDownloadedSongUris().keys

fun findDownloadedSongUri(songId: Long): String? =
    scanDownloadedSongUris(songId)[songId]

private fun scanDownloadedSongUris(targetSongId: Long? = null): Map<Long, String> {
    val result = linkedMapOf<Long, String>()
    val context = runCatching { KoinPlatform.getKoin().get<Context>() }.getOrNull()
    if (context != null) {
        result.putAll(queryMediaStore(context, targetSongId))
    }
    scanLegacyDownloadDir(targetSongId).forEach { (id, uri) ->
        result.putIfAbsent(id, uri)
    }
    return result
}

private fun queryMediaStore(context: Context, targetSongId: Long?): Map<Long, String> {
    val result = linkedMapOf<Long, String>()
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
    ).forEach { (id, uri) ->
        result.putIfAbsent(id, uri)
    }
    return result
}

private fun queryMediaCollection(
    context: Context,
    collectionUri: Uri,
    targetSongId: Long?,
    canFilterRelativePath: Boolean,
): Map<Long, String> = runCatching {
    val result = linkedMapOf<Long, String>()
    val projection = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
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
                result[songId] = ContentUris.withAppendedId(collectionUri, mediaId).toString()
            }
        }
    result
}.getOrDefault(emptyMap())

private fun scanLegacyDownloadDir(targetSongId: Long?): Map<Long, String> = runCatching {
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
                songId to file.toURI().toString()
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

package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.util.LocalLyricFormat
import kotlinx.serialization.Serializable

@Serializable
internal data class PersistedDownloadQueue(
    val version: Int = CURRENT_DOWNLOAD_QUEUE_VERSION,
    val tasks: List<PersistedDownloadTask>,
)

@Serializable
internal data class PersistedDownloadTask(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: String,
    val playlistId: Long? = null,
    val status: String,
    val requestedQuality: String? = null,
    val actualQuality: String? = null,
    val lyricStatus: String,
    val artworkStatus: String = "NotStarted",
    val lyricFormat: String? = null,
    val lyricFileName: String? = null,
    val artworkFileName: String? = null,
    val progressBytes: Long = 0,
    val totalBytes: Long? = null,
    val fileName: String? = null,
    val errorMessage: String? = null,
)

internal fun List<SongDownloadTask>.toPersistedDownloadQueue(): PersistedDownloadQueue =
    PersistedDownloadQueue(
        tasks = map { task ->
            PersistedDownloadTask(
                id = task.id,
                title = task.title,
                artist = task.artist,
                artworkUri = task.artworkUri,
                playlistId = task.playlistId,
                status = task.status.name,
                requestedQuality = task.requestedQuality,
                actualQuality = task.actualQuality,
                lyricStatus = task.lyricStatus.name,
                artworkStatus = task.artworkStatus.name,
                lyricFormat = task.lyricFormat?.name,
                lyricFileName = task.lyricFileName,
                artworkFileName = task.artworkFileName,
                progressBytes = task.progressBytes,
                totalBytes = task.totalBytes,
                fileName = task.fileName,
                errorMessage = task.errorMessage,
            )
        },
    )

internal fun PersistedDownloadQueue.toDownloadTasks(): List<SongDownloadTask> {
    require(version in 1..CURRENT_DOWNLOAD_QUEUE_VERSION) {
        "Unsupported download queue version: $version"
    }
    return tasks.map { task ->
        SongDownloadTask(
            id = task.id,
            title = task.title,
            artist = task.artist,
            artworkUri = task.artworkUri,
            playlistId = task.playlistId,
            status = DownloadTaskStatus.valueOf(task.status),
            requestedQuality = task.requestedQuality,
            actualQuality = task.actualQuality,
            lyricStatus = DownloadLyricStatus.valueOf(task.lyricStatus),
            artworkStatus = runCatching { DownloadArtworkStatus.valueOf(task.artworkStatus) }
                .getOrDefault(DownloadArtworkStatus.NotStarted),
            lyricFormat = task.lyricFormat
                ?.let { stored -> runCatching { LocalLyricFormat.valueOf(stored) }.getOrNull() },
            lyricFileName = task.lyricFileName,
            artworkFileName = task.artworkFileName,
            progressBytes = task.progressBytes.coerceAtLeast(0L),
            totalBytes = task.totalBytes?.takeIf { it > 0L },
            fileName = task.fileName,
            errorMessage = task.errorMessage,
            executionGeneration = 0L,
        )
    }
}

private const val CURRENT_DOWNLOAD_QUEUE_VERSION = 2

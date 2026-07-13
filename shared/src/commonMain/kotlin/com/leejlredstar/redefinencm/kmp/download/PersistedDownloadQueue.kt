package com.leejlredstar.redefinencm.kmp.download

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
                progressBytes = task.progressBytes,
                totalBytes = task.totalBytes,
                fileName = task.fileName,
                errorMessage = task.errorMessage,
            )
        },
    )

internal fun PersistedDownloadQueue.toDownloadTasks(): List<SongDownloadTask> {
    require(version == CURRENT_DOWNLOAD_QUEUE_VERSION) {
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
            progressBytes = task.progressBytes.coerceAtLeast(0L),
            totalBytes = task.totalBytes?.takeIf { it > 0L },
            fileName = task.fileName,
            errorMessage = task.errorMessage,
            executionGeneration = 0L,
        )
    }
}

private const val CURRENT_DOWNLOAD_QUEUE_VERSION = 1

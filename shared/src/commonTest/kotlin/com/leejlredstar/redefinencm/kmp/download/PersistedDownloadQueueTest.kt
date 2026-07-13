package com.leejlredstar.redefinencm.kmp.download

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistedDownloadQueueTest {

    @Test
    fun jsonRoundTripPreservesTaskOrderAndPersistedFields() {
        val tasks = listOf(
            SongDownloadTask(
                id = 2002L,
                title = "Second in source order",
                artist = "Artist B",
                artworkUri = "https://example.test/2002.jpg",
                playlistId = 88L,
                status = DownloadTaskStatus.Downloading,
                requestedQuality = "lossless",
                actualQuality = "exhigh",
                lyricStatus = DownloadLyricStatus.Saving,
                progressBytes = 4_096L,
                totalBytes = 16_384L,
                fileName = "2002.flac.part",
                errorMessage = "transient detail",
                executionGeneration = 41L,
            ),
            SongDownloadTask(
                id = 1001L,
                title = "First by id, second in source order",
                artist = "Artist A",
                artworkUri = "",
                playlistId = null,
                status = DownloadTaskStatus.Paused,
                requestedQuality = null,
                actualQuality = null,
                lyricStatus = DownloadLyricStatus.NoLyric,
                progressBytes = 512L,
                totalBytes = null,
                fileName = null,
                errorMessage = null,
                executionGeneration = 42L,
            ),
        )

        val encoded = Json.encodeToString(tasks.toPersistedDownloadQueue())
        val decoded = Json.decodeFromString<PersistedDownloadQueue>(encoded)
        val restored = decoded.toDownloadTasks()

        assertEquals(listOf(2002L, 1001L), restored.map { it.id })
        assertEquals(
            tasks.map { it.copy(executionGeneration = 0L) },
            restored,
        )
    }

    @Test
    fun recoveryQueuesInterruptedTasksAndClearsEveryExecutionGeneration() {
        val inputStatuses = listOf(
            DownloadTaskStatus.Queued,
            DownloadTaskStatus.Resolving,
            DownloadTaskStatus.Downloading,
            DownloadTaskStatus.SavingLyrics,
            DownloadTaskStatus.Paused,
            DownloadTaskStatus.Failed,
            DownloadTaskStatus.Completed,
        )
        val tasks = inputStatuses.mapIndexed { index, status ->
            SongDownloadTask(
                id = index.toLong() + 1L,
                title = status.name,
                artist = "Artist",
                artworkUri = "",
                status = status,
                lyricStatus = DownloadLyricStatus.Saved,
                progressBytes = 123L,
                totalBytes = 456L,
                errorMessage = "old error",
                executionGeneration = index.toLong() + 10L,
            )
        }

        val recovered = recoverPersistedDownloadTasks(tasks)

        assertEquals(
            listOf(
                DownloadTaskStatus.Queued,
                DownloadTaskStatus.Queued,
                DownloadTaskStatus.Queued,
                DownloadTaskStatus.Queued,
                DownloadTaskStatus.Paused,
                DownloadTaskStatus.Failed,
                DownloadTaskStatus.Completed,
            ),
            recovered.map { it.status },
        )
        assertEquals(List(recovered.size) { 0L }, recovered.map { it.executionGeneration })

        val preservedStatuses = setOf(
            DownloadTaskStatus.Paused,
            DownloadTaskStatus.Failed,
            DownloadTaskStatus.Completed,
        )
        tasks.zip(recovered).forEach { (before, after) ->
            if (before.status in preservedStatuses) {
                assertEquals(before.copy(executionGeneration = 0L), after)
            }
        }
    }
}

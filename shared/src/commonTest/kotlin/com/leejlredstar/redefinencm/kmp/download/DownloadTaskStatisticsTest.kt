package com.leejlredstar.redefinencm.kmp.download

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadTaskStatisticsTest {
    @Test
    fun summarizesStatusesInOnePassAndKeepsFirstActiveTask() {
        val tasks = listOf(
            task(1, DownloadTaskStatus.Completed),
            task(2, DownloadTaskStatus.Resolving),
            task(3, DownloadTaskStatus.Failed),
            task(4, DownloadTaskStatus.Cancelled),
            task(5, DownloadTaskStatus.Paused),
            task(6, DownloadTaskStatus.Deleted),
            task(7, DownloadTaskStatus.Downloading),
        )

        val statistics = analyzeDownloadTasks(tasks)

        assertEquals(
            DownloadQueueSummary(
                total = 7,
                active = 2,
                completed = 1,
                failed = 2,
                deleted = 1,
                paused = 1,
            ),
            statistics.summary,
        )
        assertEquals(2L, statistics.firstActiveTask?.id)
    }

    private fun task(id: Long, status: DownloadTaskStatus) = SongDownloadTask(
        id = id,
        title = "Song $id",
        artist = "Artist",
        artworkUri = "",
        status = status,
    )
}

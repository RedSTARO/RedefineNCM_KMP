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
                failed = 1,
                deleted = 1,
                paused = 1,
                cancelled = 1,
            ),
            statistics.summary,
        )
        assertEquals(2L, statistics.firstActiveTask?.id)
    }

    /** A run reports what it worked on, not what the list kept from earlier runs. */
    @Test
    fun aRunCountsOnlyTheTasksItWorkedOn() {
        val seen = mutableSetOf<Long>()
        val before = listOf(
            task(1, DownloadTaskStatus.Failed),
            task(2, DownloadTaskStatus.Completed),
            task(3, DownloadTaskStatus.Queued),
            task(4, DownloadTaskStatus.Downloading),
        )
        assertEquals(listOf(3L, 4L), tasksOfRun(before, seen).map { it.id })

        // The user cancels both: the run has nothing finished and nothing failed.
        val after = listOf(
            task(1, DownloadTaskStatus.Failed),
            task(2, DownloadTaskStatus.Completed),
            task(3, DownloadTaskStatus.Cancelled),
            task(4, DownloadTaskStatus.Cancelled),
        )
        val summary = analyzeDownloadTasks(tasksOfRun(after, seen)).summary
        assertEquals(0, summary.failed)
        assertEquals(0, summary.completed)
        assertEquals(2, summary.cancelled)
    }

    private fun task(id: Long, status: DownloadTaskStatus) = SongDownloadTask(
        id = id,
        title = "Song $id",
        artist = "Artist",
        artworkUri = "",
        status = status,
    )
}

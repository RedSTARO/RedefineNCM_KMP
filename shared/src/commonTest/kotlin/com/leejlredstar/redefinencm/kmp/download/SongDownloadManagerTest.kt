package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.util.DownloadedSongSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongDownloadManagerTest {

    @Test
    fun completedTaskBecomesDeletedWhenLocalFileIsMissing() {
        val result = reconcileDownloadTasksWithLocalLibrary(
            tasks = listOf(
                SongDownloadTask(
                    id = 1001,
                    title = "Song",
                    artist = "Artist",
                    artworkUri = "",
                    status = DownloadTaskStatus.Completed,
                    requestedQuality = "exhigh",
                    actualQuality = "lossless",
                    lyricStatus = DownloadLyricStatus.Saved,
                    progressBytes = 100,
                    totalBytes = 100,
                    fileName = "1001.flac",
                )
            ),
            localFiles = emptyMap(),
        )

        val task = result.single()
        assertEquals(DownloadTaskStatus.Deleted, task.status)
        assertNull(task.fileName)
        assertNull(task.actualQuality)
        assertEquals("本地文件已删除", task.errorMessage)
    }

    @Test
    fun localOnlyFileIsImportedAsCompletedTask() {
        val result = reconcileDownloadTasksWithLocalLibrary(
            tasks = emptyList(),
            localFiles = mapOf(
                1002L to DownloadedSongSnapshot(
                    id = 1002,
                    fileName = "1002.m4a",
                    uri = "file:///downloads/1002.m4a",
                    sizeBytes = 4096,
                    lastModifiedEpochMillis = 123,
                )
            ),
        )

        val task = result.single()
        assertEquals(1002L, task.id)
        assertEquals(DownloadTaskStatus.Completed, task.status)
        assertEquals("1002.m4a", task.fileName)
        assertEquals(4096L, task.progressBytes)
        assertEquals(4096L, task.totalBytes)
    }
}

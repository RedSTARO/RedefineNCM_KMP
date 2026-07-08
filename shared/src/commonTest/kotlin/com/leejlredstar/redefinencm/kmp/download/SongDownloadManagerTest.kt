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

    @Test
    fun enqueueRequeuesCompletedTaskWhenLocalFileIsMissing() {
        val result = mergeDownloadTasksForEnqueue(
            current = listOf(
                SongDownloadTask(
                    id = 1003,
                    title = "Old Song",
                    artist = "Old Artist",
                    artworkUri = "old.jpg",
                    status = DownloadTaskStatus.Completed,
                    actualQuality = "lossless",
                    lyricStatus = DownloadLyricStatus.Saved,
                    progressBytes = 2048,
                    totalBytes = 2048,
                    fileName = "1003.flac",
                )
            ),
            incoming = listOf(
                SongDownloadTask(
                    id = 1003,
                    title = "New Song",
                    artist = "New Artist",
                    artworkUri = "new.jpg",
                    status = DownloadTaskStatus.Queued,
                )
            ),
            localFiles = emptyMap(),
        )

        val task = result.single()
        assertEquals(DownloadTaskStatus.Queued, task.status)
        assertEquals("New Song", task.title)
        assertEquals("New Artist", task.artist)
        assertEquals("new.jpg", task.artworkUri)
        assertEquals(0L, task.progressBytes)
        assertNull(task.totalBytes)
        assertNull(task.fileName)
        assertNull(task.actualQuality)
        assertNull(task.errorMessage)
    }
}

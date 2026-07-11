package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebDownloadStorageTest {
    @Test
    fun opfsDownloadScanPlaybackUrlAndDeleteRoundTrip() = runTest {
        val songId = 7_777_777L
        WebDownloadStorage.delete(songId)
        try {
            var downloadedBytes = 0L
            val downloaded = WebDownloadStorage.download(
                item = DownloadRequestItem(
                    id = songId,
                    title = "Browser test",
                    artist = "RedefineNCM",
                    artworkUri = "",
                    url = "data:audio/mpeg;base64,AAECAwQF",
                    expectedBytes = 6L,
                ),
                fileName = "$songId.mp3",
                onProgress = { downloaded, _ -> downloadedBytes = downloaded },
            )

            assertEquals("$songId.mp3", downloaded.fileName)
            assertEquals(6L, downloadedBytes)
            val scan = assertIs<DownloadScanResult.Success>(WebDownloadStorage.scan())
            val snapshot = assertNotNull(scan.snapshots.singleOrNull { it.id == songId })
            assertEquals(6L, snapshot.sizeBytes)

            val objectUrl = assertNotNull(WebDownloadStorage.createObjectUrl(snapshot.uri.orEmpty()))
            assertTrue(objectUrl.startsWith("blob:"))
            WebDownloadStorage.revokeObjectUrl(objectUrl)

            assertTrue(WebDownloadStorage.delete(songId))
            val afterDelete = assertIs<DownloadScanResult.Success>(WebDownloadStorage.scan())
            assertFalse(afterDelete.snapshots.any { it.id == songId })
        } finally {
            WebDownloadStorage.delete(songId)
        }
    }
}

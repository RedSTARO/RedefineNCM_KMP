package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalMediaAssetStorageWebTest {
    @Test
    fun opfsSidecarsReplaceReadResolveAndDeleteRoundTrip() = runTest {
        val songId = 7_777_778L
        LocalMediaAssetStorage.deleteAssets(songId)
        try {
            LocalMediaAssetStorage.replaceLyrics(
                songId = songId,
                files = listOf(
                    LocalTextMediaAsset("$songId.lyric.ttml", "<tt></tt>"),
                    LocalTextMediaAsset("$songId.lyric.translation.lrc", "[00:00.00]翻译"),
                ),
            )

            val firstSnapshot = LocalMediaAssetStorage.inspect(songId)
            assertEquals(LocalLyricFormat.TTML, firstSnapshot.lyricFormat)
            assertEquals("$songId.lyric.ttml", firstSnapshot.lyricFileName)
            assertEquals(
                listOf(
                    "$songId.lyric.translation.lrc",
                    "$songId.lyric.ttml",
                ),
                LocalMediaAssetStorage.readLyrics(
                    songId = songId,
                    fileNames = setOf(
                        "$songId.lyric.translation.lrc",
                        "$songId.lyric.ttml",
                    ),
                ).map { it.fileName },
            )
            assertEquals(
                listOf("$songId.lyric.ttml"),
                LocalMediaAssetStorage.readLyrics(
                    songId = songId,
                    fileNames = setOf("$songId.lyric.ttml"),
                ).map { it.fileName },
            )

            LocalMediaAssetStorage.replaceLyrics(
                songId = songId,
                files = listOf(
                    LocalTextMediaAsset("$songId.lyric.yrc", "[0,1000](0,1000,0)word"),
                ),
            )
            val replacedLyrics = LocalMediaAssetStorage.readLyrics(
                songId = songId,
                fileNames = setOf("$songId.lyric.yrc"),
            )
            assertEquals(listOf("$songId.lyric.yrc"), replacedLyrics.map { it.fileName })
            assertEquals(LocalLyricFormat.YRC, LocalMediaAssetStorage.inspect(songId).lyricFormat)

            val artworkName = LocalMediaAssetStorage.replaceArtwork(
                songId = songId,
                fileExtension = "png",
                mimeType = "image/png",
                bytes = byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                ),
            )
            assertEquals("$songId.cover.png", artworkName)
            assertEquals(artworkName, LocalMediaAssetStorage.inspect(songId).artworkFileName)
            val audioScan = assertIs<DownloadScanResult.Success>(WebDownloadStorage.scan())
            assertFalse(audioScan.snapshots.any { it.id == songId })

            val artworkUri = assertNotNull(LocalMediaAssetStorage.resolveArtworkUri(songId))
            assertTrue(artworkUri.startsWith("blob:"))
            LocalMediaAssetStorage.releaseArtworkUri(artworkUri)

            assertTrue(LocalMediaAssetStorage.deleteAssets(songId))
            val deleted = LocalMediaAssetStorage.inspect(songId)
            assertNull(deleted.lyricFormat)
            assertNull(deleted.artworkFileName)
        } finally {
            LocalMediaAssetStorage.deleteAssets(songId)
        }
    }

    @Test
    fun cancelledMutationKeepsSerialLockUntilUnderlyingOperationSettles() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        var finishFirst: (() -> Unit)? = null
        val first = launch {
            runSerializedWebLocalMediaAssetMutation<Unit> { onSuccess, _ ->
                finishFirst = { onSuccess(Unit) }
                firstStarted.complete(Unit)
            }
        }
        firstStarted.await()
        first.cancel()

        val secondStarted = CompletableDeferred<Unit>()
        val second = async {
            runSerializedWebLocalMediaAssetMutation<Unit> { onSuccess, _ ->
                secondStarted.complete(Unit)
                onSuccess(Unit)
            }
        }
        repeat(3) { yield() }
        try {
            assertFalse(first.isCompleted)
            assertFalse(secondStarted.isCompleted)
        } finally {
            checkNotNull(finishFirst).invoke()
        }

        first.join()
        second.await()
        assertTrue(secondStarted.isCompleted)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun artworkBlobIsReleasedWhenCancellationWinsAfterResume() = runTest {
        var succeed: ((String?) -> Unit)? = null
        val released = mutableListOf<String>()
        val job = launch {
            awaitWebArtworkResource(
                start = { onSuccess, _ -> succeed = onSuccess },
                release = released::add,
            )
        }
        runCurrent()

        checkNotNull(succeed).invoke("blob:prompt-cancellation")
        job.cancel()
        runCurrent()
        job.join()

        assertEquals(listOf("blob:prompt-cancellation"), released)
    }
}

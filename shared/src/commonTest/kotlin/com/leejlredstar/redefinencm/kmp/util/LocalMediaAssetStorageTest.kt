package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalMediaAssetStorageTest {
    @Test
    fun sidecarNamesCannotBeMistakenForSingleExtensionAudioNames() {
        val songId = 1234L

        assertTrue(isLocalLyricSidecarFileName(songId, "1234.lyric.ttml"))
        assertTrue(isLocalLyricSidecarFileName(songId, "1234.lyric.translation.lrc"))
        assertTrue(isLocalLyricSidecarFileName(songId, "1234.lyric.yrc"))
        assertTrue(isLocalArtworkSidecarFileName(songId, "1234.cover.webp"))
        assertFalse(isLocalLyricSidecarFileName(songId, "1234.ttml"))
        assertFalse(isLocalLyricSidecarFileName(songId, "1234.lyric.txt"))
        assertFalse(isLocalLyricSidecarFileName(songId, "1234.lyric.json"))
        assertFalse(isLocalArtworkSidecarFileName(songId, "1234.jpg"))
        assertFalse(isLocalLyricSidecarFileName(songId, "../1234.lyric.ttml"))
        assertFalse(isLocalArtworkSidecarFileName(songId, "other/1234.cover.jpg"))
        assertTrue(
            isLocalMediaAssetTransactionFileName(
                songId,
                ".1234.lyric.ttml.token.asset-pending",
            ),
        )
        assertTrue(
            isLocalMediaAssetTransactionFileName(
                songId,
                ".1234.cover.webp.token.asset-backup",
            ),
        )
        assertFalse(
            isLocalMediaAssetTransactionFileName(
                songId,
                ".4321.cover.webp.token.asset-backup",
            ),
        )
    }

    @Test
    fun lyricValidationRejectsWrongSongPathsAndDuplicateTargets() {
        assertFailsWith<IllegalArgumentException> {
            validateLocalLyricAssets(
                songId = 10L,
                files = listOf(LocalTextMediaAsset("11.lyric.lrc", "wrong song")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateLocalLyricAssets(
                songId = 10L,
                files = listOf(LocalTextMediaAsset("10.lyric/../../escape.lrc", "escape")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateLocalLyricAssets(
                songId = 10L,
                files = listOf(
                    LocalTextMediaAsset("10.lyric.lrc", "first"),
                    LocalTextMediaAsset("10.lyric.lrc", "second"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateLocalLyricFileNames(
                songId = 10L,
                fileNames = setOf("11.lyric.ttml"),
            )
        }
    }

    @Test
    fun inspectionSelectsPrimaryFormatAndIgnoresUnrelatedFiles() {
        val snapshot = localMediaAssetSnapshot(
            songId = 42L,
            fileNames = listOf(
                "42.mp3",
                "42.lyric.translation.lrc",
                "42.lyric.yrc",
                "42.cover.webp",
                "43.lyric.ttml",
            ),
        )

        assertEquals(LocalLyricFormat.YRC, snapshot.lyricFormat)
        assertEquals("42.lyric.yrc", snapshot.lyricFileName)
        assertEquals("42.cover.webp", snapshot.artworkFileName)
    }

    @Test
    fun inspectionPrefersTtmlWhenDamagedStorageContainsMultiplePrimaryFiles() {
        val snapshot = localMediaAssetSnapshot(
            songId = 77L,
            fileNames = listOf(
                "77.lyric.lrc",
                "77.lyric.ttml",
                "77.lyric.yrc",
            ),
        )

        assertEquals(LocalLyricFormat.TTML, snapshot.lyricFormat)
        assertEquals("77.lyric.ttml", snapshot.lyricFileName)
        assertNull(snapshot.artworkFileName)
    }

    @Test
    fun artworkValidationNormalizesSafeExtensionAndRejectsNonImages() {
        assertEquals(
            "webp",
            validateLocalArtworkExtension(
                fileExtension = ".WEBP",
                mimeType = "image/webp",
                bytes = byteArrayOf(1),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            validateLocalArtworkExtension(
                fileExtension = "../jpg",
                mimeType = "image/jpeg",
                bytes = byteArrayOf(1),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateLocalArtworkExtension(
                fileExtension = "jpg",
                mimeType = "text/html",
                bytes = byteArrayOf(1),
            )
        }
    }
}

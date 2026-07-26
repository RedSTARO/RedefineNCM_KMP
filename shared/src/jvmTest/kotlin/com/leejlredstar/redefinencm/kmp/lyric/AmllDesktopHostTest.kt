package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AmllDesktopHostTest {
    @Test
    fun fallsBackToParsedLyricsWhenRawPayloadIsBlank() {
        val payload = desktopAmllLyricPayload(
            rawLyric = " \n ",
            lyricMap = linkedMapOf(
                1_500L to "第一句",
                3_000L to "第二句",
            ),
            lyricUiState = LyricUiState.Content(lineCount = 2),
        )

        assertTrue(payload.contains("第一句"))
        assertTrue(payload.contains("第二句"))
        assertTrue(payload.contains("["))
    }

    @Test
    fun doesNotExposeStaleLyricsOutsideContentState() {
        val payload = desktopAmllLyricPayload(
            rawLyric = "[00:01.00]旧歌词",
            lyricMap = linkedMapOf(1_000L to "旧歌词"),
            lyricUiState = LyricUiState.Loading,
        )

        assertEquals("", payload)
    }

    @Test
    fun extractsImmutableContentAddressedAssetSetsAndRepairsCorruption() {
        val root = Files.createTempDirectory("amll-assets-test").toFile()
        try {
            val firstAssets = testAssets(playerHtml = "first")
            val firstDirectory = extractAmllAssets(root, firstAssets)
            val reusedDirectory = extractAmllAssets(root, firstAssets)

            assertEquals(firstDirectory.canonicalFile, reusedDirectory.canonicalFile)
            assertTrue(firstDirectory.name.startsWith("v1-"))

            val playerFile = firstDirectory.resolve("player.html")
            playerFile.writeText("corrupt")
            val repairedDirectory = extractAmllAssets(root, firstAssets)

            assertEquals(firstDirectory.canonicalFile, repairedDirectory.canonicalFile)
            assertContentEquals(firstAssets.getValue("player.html"), playerFile.readBytes())

            val changedDirectory = extractAmllAssets(
                root,
                testAssets(playerHtml = "second"),
            )
            assertNotEquals(firstDirectory.canonicalFile, changedDirectory.canonicalFile)
            assertContentEquals(
                "second".encodeToByteArray(),
                changedDirectory.resolve("player.html").readBytes(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun testAssets(playerHtml: String): Map<String, ByteArray> = mapOf(
        "player.html" to playerHtml.encodeToByteArray(),
        "bundle.js" to "bundle".encodeToByteArray(),
        "style.css" to "style".encodeToByteArray(),
    )
}

package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmllDesktopHostTest {
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

    @Test
    fun bridgesOnlyOwnedArtworkSidecarsToDataUris() {
        val downloadDirectory = Files.createTempDirectory("amll-artwork-test").toFile()
        val outsideDirectory = Files.createTempDirectory("amll-artwork-outside").toFile()
        try {
            val artwork = downloadDirectory.resolve("42.cover.png").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val outside = outsideDirectory.resolve("42.cover.png").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }

            assertEquals(
                "data:image/png;base64,AQIDBA==",
                desktopLocalArtworkDataUri(
                    songId = 42L,
                    uriText = artwork.toURI().toString(),
                    downloadDirectory = downloadDirectory,
                ),
            )
            assertNull(
                desktopLocalArtworkDataUri(
                    songId = 43L,
                    uriText = artwork.toURI().toString(),
                    downloadDirectory = downloadDirectory,
                ),
            )
            assertNull(
                desktopLocalArtworkDataUri(
                    songId = 42L,
                    uriText = outside.toURI().toString(),
                    downloadDirectory = downloadDirectory,
                ),
            )
        } finally {
            downloadDirectory.deleteRecursively()
            outsideDirectory.deleteRecursively()
        }
    }

    private fun testAssets(playerHtml: String): Map<String, ByteArray> = mapOf(
        "player.html" to playerHtml.encodeToByteArray(),
        "bundle.js" to "bundle".encodeToByteArray(),
        "style.css" to "style".encodeToByteArray(),
    )
}

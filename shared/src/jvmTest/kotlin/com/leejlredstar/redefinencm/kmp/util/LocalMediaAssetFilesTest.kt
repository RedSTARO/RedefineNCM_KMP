package com.leejlredstar.redefinencm.kmp.util

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the transaction Desktop and Android now share. Both drive it with a real filesystem, so
 * the test does too; a rename is the operation under test and cannot be faked usefully.
 */
class LocalMediaAssetFilesTest {

    private val directory: File = Files.createTempDirectory("local-media-assets").toFile()

    private fun rename(source: File, target: File) {
        check(source.renameTo(target)) { "rename failed: ${source.name}" }
    }

    private fun write(name: String, text: String): File =
        File(directory, name).apply { writeText(text) }

    private fun visibleNames(): List<String> =
        localMediaAssetFileNames(directory, "test").sorted()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun publishesTheReplacementAndRemovesWhatItDisplaced() {
        val old = write("1.lrc", "old")
        replaceLocalMediaAssetFiles(
            directory = directory,
            replacements = listOf(LocalMediaAssetWrite("1.lrc", "new".encodeToByteArray())),
            displaced = listOf(old),
            move = ::rename,
        )
        assertEquals(listOf("1.lrc"), visibleNames())
        assertEquals("new", File(directory, "1.lrc").readText())
    }

    @Test
    fun leavesNoStagingOrBackupFilesBehindOnSuccess() {
        write("1.lrc", "old")
        replaceLocalMediaAssetFiles(
            directory = directory,
            replacements = listOf(
                LocalMediaAssetWrite("1.lrc", "a".encodeToByteArray()),
                LocalMediaAssetWrite("1.ttml", "b".encodeToByteArray()),
            ),
            displaced = localMediaAssetFiles(directory, 1L, "test") { _, name -> name == "1.lrc" },
            move = ::rename,
        )
        assertEquals(listOf("1.lrc", "1.ttml"), visibleNames())
    }

    @Test
    fun restoresTheOldSidecarWhenPublishingFails() {
        val old = write("1.lrc", "old")
        var moves = 0
        val failOnPublish = { source: File, target: File ->
            // The first move parks the old file; the second publishes, and is the one that fails.
            moves += 1
            if (moves == 2) error("publish failed") else rename(source, target)
        }

        assertFailsWith<IllegalStateException> {
            replaceLocalMediaAssetFiles(
                directory = directory,
                replacements = listOf(LocalMediaAssetWrite("1.lrc", "new".encodeToByteArray())),
                displaced = listOf(old),
                move = failOnPublish,
            )
        }

        assertEquals(listOf("1.lrc"), visibleNames())
        assertEquals("old", File(directory, "1.lrc").readText())
    }

    @Test
    fun removesTheStagedFileWhenParkingTheOldSidecarFails() {
        val old = write("1.lrc", "old")
        // The first move parks the displaced sidecar, and staging has already happened by then.
        val failOnPark = { _: File, _: File -> error("park failed") }

        assertFailsWith<IllegalStateException> {
            replaceLocalMediaAssetFiles(
                directory = directory,
                replacements = listOf(LocalMediaAssetWrite("1.lrc", "new".encodeToByteArray())),
                displaced = listOf(old),
                move = failOnPark,
            )
        }

        assertEquals(listOf("1.lrc"), visibleNames())
        assertEquals("old", File(directory, "1.lrc").readText())
        // Nothing staged is left behind, hidden or not.
        assertTrue(directory.listFiles().orEmpty().none { it.name.contains("asset-pending") })
    }

    @Test
    fun aFailureWhileStagingNeverTouchesWhatIsAlreadyOnDisk() {
        write("1.lrc", "old")
        // A directory where a staged file wants to be makes the write fail.
        val blocked = "1.ttml"

        assertFailsWith<Exception> {
            replaceLocalMediaAssetFiles(
                directory = File(directory, "missing/deeper"),
                replacements = listOf(LocalMediaAssetWrite(blocked, "b".encodeToByteArray())),
                displaced = localMediaAssetFiles(directory, 1L, "test") { _, name -> name == "1.lrc" },
                move = ::rename,
            )
        }

        assertEquals(listOf("1.lrc"), visibleNames())
        assertEquals("old", File(directory, "1.lrc").readText())
    }

    @Test
    fun listingIgnoresAMissingDirectoryAndRejectsAFile() {
        assertTrue(localMediaAssetFileNames(File(directory, "absent"), "test").isEmpty())
        val notADirectory = write("plain.txt", "x")
        assertFailsWith<IllegalStateException> {
            localMediaAssetFileNames(notADirectory, "test")
        }
    }

    @Test
    fun stagingAndBackupNamesAreHiddenAndUnique() {
        val first = temporaryLocalMediaAssetFile(directory, "1.lrc")
        val second = temporaryLocalMediaAssetFile(directory, "1.lrc")
        assertTrue(first.name.startsWith("."))
        assertFalse(first.name == second.name)
        assertTrue(backupLocalMediaAssetFile(directory, "1.lrc").name.startsWith("."))
    }

    @Test
    fun deletingAnAlreadyMissingSidecarIsNotAFailure() {
        deleteLocalMediaAssetOrThrow(File(directory, "never-existed.lrc"))
    }
}

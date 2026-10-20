package com.leejlredstar.redefinencm.kmp.util

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A backup file that cannot be read or written is reported, and a failed write loses nothing. */
class SettingsFileOpsJvmTest {
    private val directory: File = Files.createTempDirectory("settings-file-ops").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun aTypedNameThatDoesNotExistFailsToReadRatherThanThrowing() {
        assertTrue(readSettingsFile(directory.resolve("missing.json")).isFailure)
        assertTrue(readSettingsFile(directory).isFailure)
        directory.resolve("ok.json").writeText("{\"server\":\"x\"}")
        assertEquals("{\"server\":\"x\"}", readSettingsFile(directory.resolve("ok.json")).getOrThrow())
    }

    @Test
    fun aWriteReplacesTheFileWhole() {
        val target = directory.resolve("backup.json").apply { writeText("old") }
        writeSettingsFileAtomically(target, "new").getOrThrow()
        assertEquals("new", target.readText())
        assertEquals(listOf("backup.json"), directory.list()!!.toList())
    }

    @Test
    fun aFailedWriteLeavesNoTemporaryFileBehind() {
        assertTrue(writeSettingsFileAtomically(directory.resolve("missing/backup.json"), "new").isFailure)
        // A target that cannot be replaced: a folder with something in it.
        val folder = directory.resolve("taken.json").apply { mkdir() }
        folder.resolve("inside").writeText("kept")
        assertTrue(writeSettingsFileAtomically(folder, "new").isFailure)
        assertEquals("kept", folder.resolve("inside").readText())
        assertEquals(listOf("taken.json"), directory.list()!!.toList())
    }
}

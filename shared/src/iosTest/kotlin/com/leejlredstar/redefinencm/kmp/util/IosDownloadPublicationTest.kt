@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.CancellationException
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Exercises the actual native publication and scanning code without user files or networking. */
class IosDownloadPublicationTest {
    private val files = NSFileManager.defaultManager
    private val directory = "${NSTemporaryDirectory()}/download-test-${NSUUID.UUID().UUIDString}".also {
        check(files.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null))
    }

    @AfterTest
    fun close() { files.removeItemAtPath(directory, error = null) }

    @Test
    fun progressAndClaimRunBeforeTheCompleteFileBecomesVisible() {
        val temporary = write("system-temp", "complete-audio")
        val events = mutableListOf<String>()
        val file = publishIosDownload(temporary, directory, "1.mp3", 41uL,
            onProgress = { assertEquals(14L, it); assertFalse(exists("1.mp3")); events += "progress" },
            onReadyToPublish = { assertFalse(exists("1.mp3")); events += "claim"; true },
        )
        assertEquals(listOf("progress", "claim"), events)
        assertEquals("1.mp3", file.fileName)
        assertContent("1.mp3", "complete-audio")
        assertFalse(exists("1.mp3.41.part"))
    }

    @Test
    fun rejectedPublicationLeavesAnExistingFileAndAnotherTasksPartUntouched() {
        write("1.mp3", "saved")
        write("1.mp3.42.part", "newer-task")
        val temporary = write("system-temp", "rejected")
        assertFailsWith<CancellationException> {
            publishIosDownload(temporary, directory, "1.mp3", 41uL, {}, { false })
        }
        assertContent("1.mp3", "saved")
        assertContent("1.mp3.42.part", "newer-task")
        assertTrue(exists("system-temp"))
    }

    @Test
    fun aLateBackgroundPublicationDoesNotReplaceAlreadyCompletedAudio() {
        write("1.mp3", "newer-complete-audio")
        write("1.mp3.42.part", "newer-task")
        val temporary = write("system-temp", "old-background-audio")
        publishIosDownload(temporary, directory, "1.mp3", 41uL, {}, { true })
        assertContent("1.mp3", "newer-complete-audio")
        assertContent("1.mp3.42.part", "newer-task")
    }

    @Test
    fun aFailedFinalMoveOnlyRemovesItsOwnPart() {
        check(files.createDirectoryAtPath("$directory/1.mp3", true, null, null))
        write("1.mp3.42.part", "newer-task")
        val temporary = write("system-temp", "audio")
        assertFailsWith<IllegalStateException> {
            publishIosDownload(temporary, directory, "1.mp3", 41uL, {}, { true })
        }
        assertTrue(exists("1.mp3"))
        assertFalse(exists("1.mp3.41.part"))
        assertContent("1.mp3.42.part", "newer-task")
    }

    @Test
    fun anEmptyDownloadCannotClaimOrReplaceSavedAudio() {
        write("1.mp3", "saved")
        val temporary = write("system-temp", "")
        assertFailsWith<IllegalStateException> {
            publishIosDownload(temporary, directory, "1.mp3", 41uL, { error("No progress for an empty file") },
                { error("An empty file cannot claim publication") })
        }
        assertContent("1.mp3", "saved")
    }

    @Test
    fun anOldEmptyFileCanBeReplacedAfterClaimingTheCompleteDownload() {
        write("1.mp3", "")
        val temporary = write("system-temp", "complete")
        publishIosDownload(temporary, directory, "1.mp3", 41uL, {}, { true })
        assertContent("1.mp3", "complete")
    }

    @Test
    fun aNameOutsideTheAudioShapeCannotReachFileOperations() {
        val temporary = write("system-temp", "audio")
        for (name in listOf("../1.mp3", "1.lyric.lrc", "1.mp3.41.part")) {
            assertFailsWith<IllegalArgumentException> {
                publishIosDownload(temporary, directory, name, 41uL, {}, { error("Invalid name") })
            }
        }
        assertContent("system-temp", "audio")
    }

    @Test
    fun theScannerExcludesEmptyFilesDirectoriesAndStagingFiles() {
        write("1.mp3", "audio")
        write("2.mp3", "")
        check(files.createDirectoryAtPath("$directory/3.mp3", true, null, null))
        write("4.mp3.42.part", "partial")
        write("5.lyric.lrc", "sidecar")
        val scan = assertIs<DownloadScanResult.Success>(scanIosDownloadedSongs { directory })
        assertEquals(listOf(1L), scan.snapshots.map { it.id })
        assertEquals(5L, scan.snapshots.single().sizeBytes)
    }

    private fun exists(name: String) = files.fileExistsAtPath("$directory/$name")

    private fun write(name: String, content: String): NSURL {
        val path = "$directory/$name"
        val handle = fopen(path, "wb") ?: error("Cannot create fixture")
        try { check(fputs(content, handle) >= 0) } finally { fclose(handle) }
        return NSURL.fileURLWithPath(path)
    }

    private fun assertContent(name: String, expected: String) {
        val expectedUrl = write("expected-bytes", expected)
        assertEquals(files.contentsAtPath(expectedUrl.path!!), files.contentsAtPath("$directory/$name"))
    }
}

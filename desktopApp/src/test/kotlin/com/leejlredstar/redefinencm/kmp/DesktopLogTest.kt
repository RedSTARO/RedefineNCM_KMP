package com.leejlredstar.redefinencm.kmp

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.io.OutputStream
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLogTest {
    @Test
    fun urlUserInfoAndUppercaseUrlQueriesAreRedacted() {
        val logged = scrubLogLine("GET HTTPS://test-user:SECRET@host/song?token=PRIVATE https://other:PASS@host/status")
        assertFalse("test-user" in logged || "SECRET" in logged || "PRIVATE" in logged || "PASS" in logged)
        assertTrue("host/song?<redacted>" in logged)
        assertTrue("host/status" in logged)
    }


    /** Runs [block] against a log in a directory of its own, and removes the directory after. */
    private fun withLog(maxBytes: Long, existing: String? = null, block: (RotatingLogFile, File, File) -> Unit) {
        val directory = Files.createTempDirectory("redefinencm-log").toFile()
        val file = directory.resolve("app.log")
        val previous = directory.resolve("app.previous.log")
        existing?.let(file::writeText)
        try {
            RotatingLogFile(file, previous, maxBytes).use { log -> block(log, file, previous) }
        } finally {
            // An open file cannot be deleted on Windows, so this also proves the log was closed.
            assertTrue(directory.deleteRecursively(), "the log's directory was left behind")
        }
    }

    @Test
    fun aFullLogStartsAgainAndKeepsWhatItHeld() = withLog(maxBytes = 10) { log, file, previous ->
        log.write("first!".toByteArray(), 0, 6)
        log.write("second".toByteArray(), 0, 6)

        assertEquals("first!", previous.readText())
        assertEquals("second", file.readText())
    }

    @Test
    fun aLogFromAnEarlierRunIsAppendedTo() = withLog(maxBytes = 1024, existing = "earlier\n") { log, file, _ ->
        log.write("now\n".toByteArray(), 0, 4)

        assertEquals("earlier\nnow\n", file.readText())
    }

    @Test
    fun aWriteLargerThanTheFileLimitIsDropped() = withLog(maxBytes = 4) { log, file, _ ->
        log.write("a long line".toByteArray(), 0, 11)
        assertEquals(0L, file.length())
        log.write("okay".toByteArray(), 0, 4)
        assertEquals("okay", file.readText())
    }

    @Test
    fun failedRotationKeepsThePreviousDataWithoutGrowingTheFile() = withLog(maxBytes = 6) { log, file, previous ->
        assertTrue(previous.mkdir())
        val blocker = previous.resolve("blocker").apply { writeText("keep") }
        log.write("first!".toByteArray(), 0, 6)
        log.write("second".toByteArray(), 0, 6)
        assertEquals("first!", file.readText())
        assertEquals("keep", blocker.readText())
        assertTrue(blocker.delete())
        assertTrue(previous.delete())
        log.write("third!".toByteArray(), 0, 6)
        assertEquals("third!", file.readText())
        assertEquals("first!", previous.readText())
    }

    @Test
    fun aStreamAddressLosesItsTokenAndAccountNumber() {
        val printed = "JvmMediaPlayer failed to play audio stream: Could not open input " +
            "\"https://dl.stream.qqmusic.qq.com/C400abc.m4a?guid=1&vkey=SECRET&uin=10001\". (Has start() been called?)"

        val stored = scrubLogLine(printed)

        assertEquals(
            "JvmMediaPlayer failed to play audio stream: Could not open input " +
                "\"https://dl.stream.qqmusic.qq.com/C400abc.m4a?<redacted>\". (Has start() been called?)",
            stored,
        )
    }

    @Test
    fun aRequestLineKeepsItsPathAndLosesWhatWasAsked() {
        val stored = scrubLogLine(
            "REQUEST: https://ncm.example/cloudsearch?keywords=private+words&uid=42&timestamp=1",
        )

        assertEquals("REQUEST: https://ncm.example/cloudsearch?<redacted>", stored)
        assertEquals("RESPONSE: 200 OK", scrubLogLine("RESPONSE: 200 OK"))
    }

    @Test
    fun anOversizedUrlNeverLogsItsContinuationAndTheNextLineIsStillWritten() {
        val console = ByteArrayOutputStream()
        withLog(maxBytes = DesktopLog.MAX_BYTES) { log, file, _ ->
            val stream = PrintStream(TeeStream(log, PrintStream(console, true, Charsets.UTF_8)), true, Charsets.UTF_8)
            val prefix = "GET https://host/audio?token=" + "x".repeat(17_000)
            stream.print(prefix)
            stream.print("SECRET_TOKEN_TAIL&uin=PRIVATE_ACCOUNT\n")
            stream.println("next diagnostic line")
            val stored = file.readText()
            assertFalse("SECRET_TOKEN_TAIL" in stored)
            assertFalse("PRIVATE_ACCOUNT" in stored)
            assertTrue("next diagnostic line" in stored)
            assertTrue(file.length() < 17_000)
            assertTrue(console.toString(Charsets.UTF_8).contains(prefix + "SECRET_TOKEN_TAIL"))
        }
    }

    @Test
    fun aSingleHugeWriteCannotBypassTheLineLimit() = withLog(maxBytes = DesktopLog.MAX_BYTES) { log, file, previous ->
        val stream = TeeStream(log, PrintStream(OutputStream.nullOutputStream()))
        val bytes = ("x".repeat(3 * 1024 * 1024) + "\nnext line\n").toByteArray()
        stream.write(bytes, 0, bytes.size)
        assertTrue(file.length() < 17_000)
        assertTrue(previous.length() < 17_000)
        assertTrue("next line" in file.readText())
    }

    @Test
    fun theFileGetsWholeLinesWithTheTimeAndTheConsoleGetsTheTextAsPrinted() {
        val console = ByteArrayOutputStream()
        val noon = LocalDateTime.of(2026, 10, 2, 12, 0, 0)
        withLog(maxBytes = 1024) { log, file, _ ->
            val stream = PrintStream(TeeStream(log, PrintStream(console, true, Charsets.UTF_8)) { noon }, true, Charsets.UTF_8)

            // One line arriving in two writes, with its URL cut in the middle.
            stream.print("GET http://host/path?to")
            stream.print("ken=abc 歌词\n")
            stream.print("unfinished")

            assertEquals("2026-10-02 12:00:00.000 GET http://host/path?<redacted> 歌词\n", file.readText())
            assertEquals("GET http://host/path?token=abc 歌词\nunfinished", console.toString(Charsets.UTF_8))
            assertFalse("unfinished" in file.readText(), "a line is written once it is complete")
        }
    }
}

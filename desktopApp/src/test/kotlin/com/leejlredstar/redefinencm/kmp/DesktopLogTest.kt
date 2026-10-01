package com.leejlredstar.redefinencm.kmp

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLogTest {

    @Test
    fun aFullLogStartsAgainAndKeepsWhatItHeld() {
        val directory = Files.createTempDirectory("redefinencm-log").toFile()
        val file = directory.resolve("app.log")
        val previous = directory.resolve("app.previous.log")
        val log = RotatingLogFile(file, previous, maxBytes = 10)

        try {
            log.write("first!".toByteArray(), 0, 6)
            log.write("second".toByteArray(), 0, 6)

            assertEquals("first!", previous.readText())
            assertEquals("second", file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aLogFromAnEarlierRunIsAppendedTo() {
        val directory = Files.createTempDirectory("redefinencm-log").toFile()
        val file = directory.resolve("app.log").apply { writeText("earlier\n") }
        val log = RotatingLogFile(file, directory.resolve("app.previous.log"), maxBytes = 1024)

        try {
            log.write("now\n".toByteArray(), 0, 4)

            assertEquals("earlier\nnow\n", file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun oneLineLargerThanTheLimitIsStillWritten() {
        val directory = Files.createTempDirectory("redefinencm-log").toFile()
        val file = directory.resolve("app.log")
        val log = RotatingLogFile(file, directory.resolve("app.previous.log"), maxBytes = 4)

        try {
            log.write("a long line".toByteArray(), 0, 11)

            assertTrue("a long line" in file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}

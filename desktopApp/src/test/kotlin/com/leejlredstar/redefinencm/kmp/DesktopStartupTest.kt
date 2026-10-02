package com.leejlredstar.redefinencm.kmp

import com.leejlredstar.redefinencm.kmp.data.db.DatabaseSchemaTooNewException
import com.leejlredstar.redefinencm.kmp.i18n.strings
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopStartupTest {
    private val noon = LocalDateTime.of(2026, 10, 2, 12, 0, 0)

    private fun temporaryDatabase(): File =
        Files.createTempDirectory("redefinencm-startup").toFile().resolve("redefinencm.db")

    @Test
    fun dataThatOpensNeedsNoQuestion() {
        val database = temporaryDatabase()

        val goesOn = openLocalDataOrExplain(
            databaseFile = database,
            open = {},
            ask = { _, _ -> error("nothing to ask") },
            tell = { _, _ -> error("nothing to tell") },
        )

        assertTrue(goesOn)
        database.parentFile.deleteRecursively()
    }

    @Test
    fun aNewerFileIsNamedAndLeftAloneWhenTheUserQuits() {
        val database = temporaryDatabase().apply { writeText("newer") }
        var asked = ""

        val goesOn = openLocalDataOrExplain(
            databaseFile = database,
            // Koin wraps what a definition throws; the reason is underneath.
            open = { throw IllegalStateException("definition failed", DatabaseSchemaTooNewException(9, 5)) },
            ask = { _, message -> asked = message; LocalDataChoice.Quit },
            tell = { _, _ -> error("nothing to tell") },
        )

        assertFalse(goesOn)
        // The exact text: the other message names the path as well, so "contains the path"
        // would pass with the newer-version case not recognised at all.
        assertEquals(strings.desktopDataTooNew(database.absolutePath), asked)
        assertEquals("newer", database.readText())
        database.parentFile.deleteRecursively()
    }

    @Test
    fun startingOverKeepsTheOldFileUnderAnotherName() {
        val database = temporaryDatabase().apply { writeText("damaged") }
        val journal = File(database.parentFile, database.name + "-wal").apply { writeText("journal") }
        var opens = 0

        val goesOn = openLocalDataOrExplain(
            databaseFile = database,
            open = { if (opens++ == 0) error("file is not a database") },
            ask = { _, message ->
                assertTrue("file is not a database" in message)
                LocalDataChoice.StartOver
            },
            tell = { _, _ -> error("nothing to tell") },
            now = { noon },
        )

        assertTrue(goesOn)
        assertEquals(2, opens)
        assertFalse(database.exists())
        assertFalse(journal.exists())
        val kept = File(database.parentFile, "redefinencm.db.20261002-120000.bak")
        assertEquals("damaged", kept.readText())
        assertEquals("journal", File(database.parentFile, kept.name + "-wal").readText())
        database.parentFile.deleteRecursively()
    }

    @Test
    fun twoFilesPutAsideInOneSecondAreBothKept() {
        val database = temporaryDatabase().apply { writeText("the old data") }

        val first = putAside(database, noon)
        database.writeText("what the failed retry left")
        val second = putAside(database, noon)

        assertEquals("the old data", first?.readText())
        assertEquals("what the failed retry left", second?.readText())
        assertEquals("redefinencm.db.20261002-120000-1.bak", second?.name)
        database.parentFile.deleteRecursively()
    }
}

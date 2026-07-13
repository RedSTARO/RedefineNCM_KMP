package com.leejlredstar.redefinencm.kmp.data.db

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseMigrationTest {
    @Test
    fun unversionedLegacyJvmDatabaseIsAdoptedWithoutLosingCachedData() {
        val directory = Files.createTempDirectory("redefinencm-db-unversioned-")
        val databaseFile = directory.resolve("legacy.db").toFile()
        val jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"

        // The old JVM factory called Schema.create() directly and never assigned user_version.
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE CachedUserDetail (uid INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
                statement.execute("INSERT INTO CachedUserDetail(uid, json) VALUES (7, '{\"name\":\"legacy\"}')")
            }
        }

        val driver = openJvmDatabase(databaseFile)
        try {
            val database = AppDatabase(driver)
            assertEquals("{\"name\":\"legacy\"}", database.cachedUserDetailQueries.selectByUid(7).executeAsOne())
            assertEquals(AppDatabase.Schema.version, readUserVersion(jdbcUrl))
        } finally {
            driver.close()
            databaseFile.toPath().deleteIfExists()
            directory.deleteIfExists()
        }
    }

    @Test
    fun versionOneDatabaseMigratesWithoutLosingCachedData() {
        val directory = Files.createTempDirectory("redefinencm-db-migration-")
        val databaseFile = directory.resolve("legacy.db").toFile()
        val jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"

        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE CachedUserDetail (uid INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
                statement.execute("CREATE TABLE CachedUserPlaylist (uid INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
                statement.execute("CREATE TABLE CachedRecommendResource (singleton INTEGER NOT NULL PRIMARY KEY DEFAULT 0, json TEXT NOT NULL)")
                statement.execute("CREATE TABLE CachedRecommendSongs (singleton INTEGER NOT NULL PRIMARY KEY DEFAULT 0, json TEXT NOT NULL)")
                statement.execute("CREATE TABLE CachedLyric (song_id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
                statement.execute("INSERT INTO CachedUserDetail(uid, json) VALUES (42, '{\"name\":\"kept\"}')")
                statement.execute("PRAGMA user_version = 1")
            }
        }

        val driver = openJvmDatabase(databaseFile)
        try {
            val database = AppDatabase(driver)
            assertEquals("{\"name\":\"kept\"}", database.cachedUserDetailQueries.selectByUid(42).executeAsOne())

            DriverManager.getConnection(jdbcUrl).use { connection ->
                val tables = connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { result ->
                        buildSet {
                            while (result.next()) add(result.getString(1))
                        }
                    }
                }
                assertTrue("CachedPlaylistDetail" in tables)
                assertTrue("CachedPlaylistTrackAll" in tables)
                assertTrue("CachedCommentMusic" in tables)
                assertTrue("PlayerStatus" in tables)
                assertTrue("CachedUserLevel" in tables)

                assertEquals(AppDatabase.Schema.version, readUserVersion(jdbcUrl))
            }
        } finally {
            driver.close()
            databaseFile.toPath().deleteIfExists()
            directory.deleteIfExists()
        }
    }

    @Test
    fun versionTwoDatabaseAddsUserLevelCacheWithoutLosingExistingData() {
        val directory = Files.createTempDirectory("redefinencm-db-level-migration-")
        val databaseFile = directory.resolve("legacy.db").toFile()
        val jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"

        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE CachedUserDetail (uid INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
                statement.execute("INSERT INTO CachedUserDetail(uid, json) VALUES (42, '{\"name\":\"kept\"}')")
                statement.execute("PRAGMA user_version = 2")
            }
        }

        val driver = openJvmDatabase(databaseFile)
        try {
            val database = AppDatabase(driver)
            assertEquals("{\"name\":\"kept\"}", database.cachedUserDetailQueries.selectByUid(42).executeAsOne())
            database.cachedUserLevelQueries.upsert(42, "{\"level\":7}")
            assertEquals(
                "{\"level\":7}",
                database.cachedUserLevelQueries.selectByUid(42).executeAsOne(),
            )
            assertEquals(AppDatabase.Schema.version, readUserVersion(jdbcUrl))
        } finally {
            driver.close()
            databaseFile.toPath().deleteIfExists()
            directory.deleteIfExists()
        }
    }

    @Test
    fun versionThreeDatabaseAddsDownloadQueueWithoutLosingExistingData() {
        val directory = Files.createTempDirectory("redefinencm-db-download-queue-migration-")
        val databaseFile = directory.resolve("legacy.db").toFile()
        val jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"

        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE CachedUserDetail (uid INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
                statement.execute("CREATE TABLE PlayerStatus (id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
                statement.execute("INSERT INTO CachedUserDetail(uid, json) VALUES (42, '{\"name\":\"kept\"}')")
                statement.execute("INSERT INTO PlayerStatus(id, json) VALUES (1, '{\"positionMs\":1234}')")
                statement.execute("PRAGMA user_version = 3")
            }
        }

        val driver = openJvmDatabase(databaseFile)
        try {
            val database = AppDatabase(driver)
            assertEquals("{\"name\":\"kept\"}", database.cachedUserDetailQueries.selectByUid(42).executeAsOne())
            assertEquals("{\"positionMs\":1234}", database.playerStatusQueries.select().executeAsOne())

            database.downloadQueueQueries.upsert("[{\"id\":7,\"status\":\"Queued\"}]")
            assertEquals(
                "[{\"id\":7,\"status\":\"Queued\"}]",
                database.downloadQueueQueries.select().executeAsOne(),
            )
            assertEquals(AppDatabase.Schema.version, readUserVersion(jdbcUrl))
        } finally {
            driver.close()
            databaseFile.toPath().deleteIfExists()
            directory.deleteIfExists()
        }
    }

    private fun readUserVersion(jdbcUrl: String): Long =
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { result ->
                    assertTrue(result.next())
                    result.getLong(1)
                }
            }
        }
}

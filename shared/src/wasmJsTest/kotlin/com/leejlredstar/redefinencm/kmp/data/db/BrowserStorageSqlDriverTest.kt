package com.leejlredstar.redefinencm.kmp.data.db

import kotlinx.browser.localStorage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BrowserStorageSqlDriverTest {
    @BeforeTest
    fun clearBefore() = clearDatabaseStorage()

    @AfterTest
    fun clearAfter() = clearDatabaseStorage()

    @Test
    fun generatedQueriesPersistAcrossDriverInstances() {
        val firstDriver = DatabaseDriverFactory().createDriver()
        AppDatabase(firstDriver).apply {
            cachedUserDetailQueries.upsert(42L, "{\"name\":\"web\"}")
            cachedUserLevelQueries.upsert(42L, "{\"level\":7}")
            cachedRecommendSongsQueries.upsert("{\"songs\":[]}")
            playerStatusQueries.upsert("{\"index\":0}")
            downloadQueueQueries.upsert("{\"tasks\":[]}")
        }
        firstDriver.close()

        val secondDriver = DatabaseDriverFactory().createDriver()
        try {
            val database = AppDatabase(secondDriver)
            assertEquals(
                "{\"name\":\"web\"}",
                database.cachedUserDetailQueries.selectByUid(42L).executeAsOne(),
            )
            assertEquals(
                "{\"level\":7}",
                database.cachedUserLevelQueries.selectByUid(42L).executeAsOne(),
            )
            assertEquals(
                "{\"songs\":[]}",
                database.cachedRecommendSongsQueries.select().executeAsOne(),
            )
            assertEquals(
                "{\"index\":0}",
                database.playerStatusQueries.select().executeAsOne(),
            )
            assertEquals(
                "{\"tasks\":[]}",
                database.downloadQueueQueries.select().executeAsOne(),
            )
        } finally {
            secondDriver.close()
        }
    }

    @Test
    fun versionTwoStorageMigratesToUserLevelCache() {
        localStorage.setItem("redefinencm.db.schemaVersion", "2")

        val driver = DatabaseDriverFactory().createDriver()
        try {
            val database = AppDatabase(driver)
            database.cachedUserLevelQueries.upsert(42L, "{\"level\":7}")

            assertEquals(
                "{\"level\":7}",
                database.cachedUserLevelQueries.selectByUid(42L).executeAsOne(),
            )
            assertEquals(
                AppDatabase.Schema.version.toString(),
                localStorage.getItem("redefinencm.db.schemaVersion"),
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun deleteAllOnlyClearsItsOwnTable() {
        val driver = DatabaseDriverFactory().createDriver()
        try {
            val database = AppDatabase(driver)
            database.cachedRecommendSongsQueries.upsert("songs")
            database.cachedRecommendResourceQueries.upsert("resources")

            database.cachedRecommendSongsQueries.deleteAll()

            assertNull(database.cachedRecommendSongsQueries.select().executeAsOneOrNull())
            assertEquals(
                "resources",
                database.cachedRecommendResourceQueries.select().executeAsOne(),
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun unsupportedConditionalDeleteFailsInsteadOfClearingTheTable() {
        val driver = BrowserStorageSqlDriver()
        try {
            assertFailsWith<IllegalStateException> {
                driver.execute(
                    identifier = null,
                    sql = "DELETE FROM CachedRecommendSongs WHERE singleton = 0",
                    parameters = 0,
                    binders = null,
                )
            }
        } finally {
            driver.close()
        }
    }

    private fun clearDatabaseStorage() {
        val keys = buildList {
            repeat(localStorage.length) { index ->
                localStorage.key(index)?.takeIf { it.startsWith("redefinencm.db.") }?.let(::add)
            }
        }
        keys.forEach(localStorage::removeItem)
    }
}

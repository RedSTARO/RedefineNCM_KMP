package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.db.SqlDriver
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Run the same generated queries and budget policy against SQLite and the browser driver. */
abstract class MetadataCacheContract {
    protected abstract fun createDriver(): SqlDriver

    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val driver = createDriver()
        try {
            AppDatabase.Schema.create(driver)
            block(AppDatabase(driver))
        } finally { driver.close() }
    }

    @Test
    fun clearingAllNineCacheTablesLeavesOwnedDataUntouched() = runTest {
        withDatabase { db ->
            db.localLibraryQueries.upsert("owned-library")
            db.playerStatusQueries.upsert("owned-player")
            db.downloadQueueQueries.upsert("owned-downloads")
            val cache = MetadataCache(db)
            val value = "{\"text\":\"歌词\"}"
            for (table in MetadataCacheTable.entries) {
                val id = if (table.name.startsWith("CachedRecommend")) 0L else 42L
                cache.write(table, id, value)
            }
            assertEquals(9, db.cacheMaintenanceQueries.entries().executeAsList().size)
            assertEquals(value.encodeToByteArray().size.toLong() * 9, cache.bytes.value)
            cache.clear()
            assertTrue(db.cacheMaintenanceQueries.entries().executeAsList().isEmpty())
            assertEquals(0L, cache.bytes.value)
            assertEquals("owned-library", db.localLibraryQueries.select().executeAsOne())
            assertEquals("owned-player", db.playerStatusQueries.select().executeAsOne())
            assertEquals("owned-downloads", db.downloadQueueQueries.select().executeAsOne())
        }
    }

    @Test
    fun theByteBudgetEvictsAnOlderUntouchedEntry() = runTest {
        withDatabase { db ->
            val cache = MetadataCache(db, budgetBytes = { 40L })
            cache.write(MetadataCacheTable.CachedLyric, 1, "a".repeat(20))
            cache.write(MetadataCacheTable.CachedLyric, 2, "b".repeat(20))
            cache.touch(MetadataCacheTable.CachedLyric, 1)
            cache.write(MetadataCacheTable.CachedCommentMusic, 3, "c".repeat(20))
            assertEquals("a".repeat(20), db.cachedLyricQueries.selectBySongId(1).executeAsOne())
            assertNull(db.cachedLyricQueries.selectBySongId(2).executeAsOneOrNull())
            assertEquals("c".repeat(20), db.cachedCommentMusicQueries.selectBySongId(3).executeAsOne())
            assertEquals(40L, cache.bytes.value)
        }
    }

    @Test
    fun anOversizedResponseDoesNotDeleteThePreviousCache() = runTest {
        withDatabase { db ->
            val cache = MetadataCache(db, budgetBytes = { 20L })
            cache.write(MetadataCacheTable.CachedLyric, 1, "old")
            assertFailsWith<IllegalStateException> { cache.write(MetadataCacheTable.CachedLyric, 1, "x".repeat(21)) }
            assertEquals("old", db.cachedLyricQueries.selectBySongId(1).executeAsOne())
            assertEquals(3L, cache.bytes.value)
        }
    }

    @Test
    fun existingCachesAreCountedAndACapChangeTrimsThem() = runTest {
        withDatabase { db ->
            db.cachedLyricQueries.upsert(1, "x".repeat(20))
            db.cachedCommentMusicQueries.upsert(1, "y".repeat(20))
            db.localLibraryQueries.upsert("protected")
            var budget = 40L
            val cache = MetadataCache(db, budgetBytes = { budget })
            cache.refresh()
            assertEquals(40L, cache.bytes.value)
            cache.touch(MetadataCacheTable.CachedLyric, 1)
            budget = 20L
            cache.refresh()
            assertEquals(20L, cache.bytes.value)
            assertEquals("x".repeat(20), db.cachedLyricQueries.selectBySongId(1).executeAsOne())
            assertEquals("protected", db.localLibraryQueries.select().executeAsOne())
        }
    }

    @Test
    fun rowCountAlsoBoundsCachesOfTinyResponses() = runTest {
        withDatabase { db ->
            val cache = MetadataCache(db, budgetBytes = { 1_024L }, maxEntries = 2)
            repeat(3) { cache.write(MetadataCacheTable.CachedLyric, it.toLong(), "x") }
            assertEquals(2, db.cacheMaintenanceQueries.entries().executeAsList().size)
            assertEquals(2L, cache.bytes.value)
        }
    }
}

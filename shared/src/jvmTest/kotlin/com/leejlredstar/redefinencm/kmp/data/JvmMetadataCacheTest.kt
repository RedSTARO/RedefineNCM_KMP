package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JvmMetadataCacheTest : MetadataCacheContract() {
    override fun createDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    @Test
    fun failedUpsertLeavesBytesAndEvictionOrderUnchanged() = runTest {
        createDriver().use { driver ->
            AppDatabase.Schema.create(driver)
            val db = AppDatabase(driver)
            val table = MetadataCacheTable.CachedLyric
            val cache = MetadataCache(db, budgetBytes = { 8L }, maxEntries = 2)
            cache.write(table, 1, "abcd")
            cache.write(table, 2, "efgh")
            cache.touch(table, 1)
            driver.execute(
                identifier = null,
                sql = """CREATE TRIGGER reject_lyric BEFORE INSERT ON CachedLyric
                    WHEN NEW.song_id = 3 BEGIN SELECT RAISE(ABORT, 'test write failure'); END""",
                parameters = 0,
            )

            assertFailsWith<SQLException> { cache.write(table, 3, "ijkl") }
            assertEquals(8L, cache.bytes.value)
            assertEquals("efgh", db.cachedLyricQueries.selectBySongId(2).executeAsOne())
            assertNull(db.cachedLyricQueries.selectBySongId(3).executeAsOneOrNull())

            cache.write(table, 4, "mnop")
            assertEquals(8L, cache.bytes.value)
            assertEquals("abcd", db.cachedLyricQueries.selectBySongId(1).executeAsOne())
            assertNull(db.cachedLyricQueries.selectBySongId(2).executeAsOneOrNull())
            assertEquals("mnop", db.cachedLyricQueries.selectBySongId(4).executeAsOne())
        }
    }
}

package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryLyricCacheBundleTest {
    @Test
    fun addingTtmlPreservesLegacyBackendCache() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val client = HttpClient(OkHttp)
        val repository = Repository(NCMApi(client), database)
        database.cachedLyricQueries.upsert(
            song_id = 42,
            json = """
                {
                  "code": 200,
                  "lrc": {"version": 1, "lyric": "[00:01.00]legacy backend"}
                }
            """.trimIndent(),
        )

        try {
            repository.cacheExternalTtml(
                id = 42,
                ttml = CachedExternalTtml(
                    content = "<tt><body><div><p begin=\"1s\" end=\"2s\">TTML</p></div></body></tt>",
                    providerItemId = "raw-file.ttml",
                    endpoint = "test",
                ),
            )

            assertEquals("raw-file.ttml", repository.cachedExternalTtml(42)?.providerItemId)
            assertEquals(
                "[00:01.00]legacy backend",
                repository.getLyric(42).first()?.lrc?.lyric,
            )
            val stored = database.cachedLyricQueries.selectBySongId(42).executeAsOne()
            assertTrue(stored.contains("\"backend\""))
            assertTrue(stored.contains("\"amllTtml\""))
        } finally {
            client.close()
            driver.close()
        }
    }
}

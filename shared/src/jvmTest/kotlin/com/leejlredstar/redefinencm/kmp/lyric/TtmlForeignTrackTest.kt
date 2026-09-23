package com.leejlredstar.redefinencm.kmp.lyric

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.AmlldbApi
import com.leejlredstar.redefinencm.kmp.data.api.ExternalHttpClient
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderItemId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The AMLL TTML database is indexed by NetEase song id, so another provider's track is never
 * looked up there: no request leaves the app and no cache row is touched.
 */
class TtmlForeignTrackTest {
    @Test
    fun anotherProvidersTrackIsNeverLookedUp() = runBlocking {
        var requests = 0
        val engine = MockEngine {
            requests += 1
            respondError(HttpStatusCode.NotFound)
        }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val repository = Repository(NCMApi(HttpClient(engine)), AppDatabase(driver))
        val provider = TtmlLyricProvider(repository, AmlldbApi(ExternalHttpClient(HttpClient(engine))))

        val results = provider.load(LyricQuery(itemId = ProviderItemId.qq("0039MnYb0qxYhV"))).toList()

        assertEquals(listOf<LyricProviderResult>(LyricProviderResult.NoMatch), results)
        assertEquals(0, requests)
        driver.close()
    }
}

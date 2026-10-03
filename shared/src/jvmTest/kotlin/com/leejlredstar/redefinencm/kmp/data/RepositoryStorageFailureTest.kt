package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class RepositoryStorageFailureTest {
    @Test
    fun uncachedNetworkLevelsAndLyricsStillReachTheScreen() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val client = HttpClient(MockEngine { request ->
            respond(
                if (request.url.encodedPath == "/user/level") {
                    """{"code":200,"data":{"userId":42,"level":3}}"""
                } else {
                    """{"code":200,"lrc":{"lyric":"[00:01.00]line"}}"""
                },
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json(ApiJson) }
            defaultRequest { url("https://ncm.test/") }
        }
        val repository = Repository(NCMApi(client), AppDatabase(driver))
        driver.close()
        try {
            val level = repository.getUserLevel(42).toList().single()
            assertEquals(CacheThenNetworkSource.NETWORK, level.source)
            assertEquals(42L, level.value.data?.userId)
            assertEquals(42L, repository.refreshUserLevel(42)?.data?.userId)
            assertEquals("[00:01.00]line", repository.getLyric(1).toList().single()?.lrc?.lyric)
            // An explicit request to cache for later use must not falsely report durable success.
            assertEquals(LyricCacheStatus.Failed, repository.cacheLyric(1))
        } finally { client.close() }
    }

    @Test
    fun playerStatusReadAndWriteExposeClosedDatabaseFailures() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val client = HttpClient(OkHttp)
        val repository = Repository(NCMApi(client), AppDatabase(driver))
        driver.close()

        try {
            assertTrue(repository.getPlayerStatus().isFailure)
            assertTrue(repository.savePlayerStatus(PlayerStatus()).isFailure)
        } finally {
            client.close()
        }
    }
}

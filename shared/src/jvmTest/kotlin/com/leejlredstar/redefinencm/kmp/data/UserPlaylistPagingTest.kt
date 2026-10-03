package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserPlaylist
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserPlaylistPagingTest {
    private fun page(ids: IntRange, more: Boolean): String =
        """{"code":200,"more":$more,"playlist":[${ids.joinToString { """{"id":$it,"name":"list-$it"}""" }}]}"""

    private suspend fun withRepository(
        response: (Int) -> String,
        verify: suspend (Repository, AppDatabase, List<Int>) -> Unit,
    ) {
        val offsets = mutableListOf<Int>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/user/playlist", request.url.encodedPath)
            assertEquals("42", request.url.parameters["uid"])
            assertEquals("100", request.url.parameters["limit"])
            val offset = request.url.parameters["offset"]!!.toInt()
            offsets += offset
            check(offsets.size <= 3) { "pagination did not terminate" }
            respond(response(offset), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(ApiJson) }
            defaultRequest { url("https://ncm.test/") }
        }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver)
            val db = AppDatabase(driver)
            verify(Repository(NCMApi(client), db), db, offsets)
        } finally {
            client.close()
            driver.close()
        }
    }

    @Test
    fun everyPageIsMergedBeforeTheCompleteListIsCached() = runBlocking {
        withRepository(response = { offset -> if (offset == 0) page(1..30, true) else page(31..45, false) }) { repo, db, offsets ->
            val result = repo.getUserPlaylist(42).toList().single().value
            assertEquals((1L..45L).toList(), result.playlist.map { it.id })
            assertFalse(result.more)
            assertEquals(listOf(0, 30), offsets)
            val saved = ApiJson.decodeFromString<UserPlaylist>(db.cachedUserPlaylistQueries.selectByUid(42).executeAsOne())
            assertEquals(result, saved)
        }
    }

    @Test
    fun failedLaterPageDoesNotReplaceTheLastCompleteCache() = runBlocking {
        withRepository(response = { offset -> if (offset == 0) page(1..30, true) else """{"code":500}""" }) { repo, db, offsets ->
            val old = page(99..100, false)
            db.cachedUserPlaylistQueries.upsert(42, old)
            val result = repo.getUserPlaylist(42).toList().single()
            assertEquals(CacheThenNetworkSource.CACHE, result.source)
            assertEquals(listOf(99L, 100L), result.value.playlist.map { it.id })
            assertEquals(old, db.cachedUserPlaylistQueries.selectByUid(42).executeAsOne())
            assertEquals(listOf(0, 30), offsets)
        }
    }

    @Test
    fun repeatingOrEmptyPagesDoNotLoopOrPublishAnIncompleteList() = runBlocking {
        for (ids in listOf(1..30, IntRange.EMPTY)) {
            withRepository(response = { page(ids, true) }) { repo, _, offsets ->
                assertTrue(repo.getUserPlaylist(42).toList().isEmpty())
                assertEquals(if (ids.isEmpty()) listOf(0) else listOf(0, 30), offsets)
            }
        }
    }
}

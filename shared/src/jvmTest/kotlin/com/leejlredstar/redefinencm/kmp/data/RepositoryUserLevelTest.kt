package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.test.testHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryUserLevelTest {
    @Test
    fun cachedLevelIsEmittedBeforeValidatedNetworkLevelAndCacheIsReplaced() = runBlocking {
        val requestCount = AtomicInteger()
        val server = responseServer(
            response = levelResponse(userId = 42, progress = 0.75, nowPlayCount = 75),
            requestCount = requestCount,
        )
        val client = testHttpClient(server.address.port)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        database.cachedUserLevelQueries.upsert(
            42,
            levelResponse(userId = 42, progress = 0.25, nowPlayCount = 25),
        )
        val repository = Repository(NCMApi(client), database)

        try {
            val emissions = repository.getUserLevel(42).toList()

            assertEquals(2, emissions.size)
            assertEquals(CacheThenNetworkSource.CACHE, emissions[0].source)
            assertEquals(0.25, emissions[0].value.data?.progress)
            assertEquals(CacheThenNetworkSource.NETWORK, emissions[1].source)
            assertEquals(0.75, emissions[1].value.data?.progress)
            assertEquals(1, requestCount.get())
            assertTrue(
                database.cachedUserLevelQueries.selectByUid(42).executeAsOne()
                    .contains("\"progress\":0.75"),
            )
        } finally {
            driver.close()
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun mismatchedCachedAndNetworkAccountsAreRejectedWithoutOverwritingCache() = runBlocking {
        val requestCount = AtomicInteger()
        val server = responseServer(
            response = levelResponse(userId = 99, progress = 0.9, nowPlayCount = 90),
            requestCount = requestCount,
        )
        val client = testHttpClient(server.address.port)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val invalidCached = levelResponse(userId = 99, progress = 0.1, nowPlayCount = 10)
        database.cachedUserLevelQueries.upsert(42, invalidCached)
        val repository = Repository(NCMApi(client), database)

        try {
            assertEquals(emptyList(), repository.getUserLevel(42).toList())
            assertEquals(1, requestCount.get())
            assertEquals(
                invalidCached,
                database.cachedUserLevelQueries.selectByUid(42).executeAsOne(),
            )
        } finally {
            driver.close()
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun nonSuccessfulNetworkResponseIsNotEmittedOrCached() = runBlocking {
        val requestCount = AtomicInteger()
        val server = responseServer(
            response = """{"code":301,"message":null,"msg":"需要登录"}""",
            requestCount = requestCount,
        )
        val client = testHttpClient(server.address.port)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        val repository = Repository(NCMApi(client), database)

        try {
            assertEquals(emptyList(), repository.getUserLevel(42).toList())
            assertEquals(1, requestCount.get())
            assertNull(database.cachedUserLevelQueries.selectByUid(42).executeAsOneOrNull())
        } finally {
            driver.close()
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun invalidUidFailsBeforeAnyNetworkRequest() = runBlocking {
        val requestCount = AtomicInteger()
        val server = responseServer(
            response = """{"code":301,"msg":"需要登录"}""",
            requestCount = requestCount,
        )
        val client = testHttpClient(server.address.port)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val repository = Repository(NCMApi(client), AppDatabase(driver))

        try {
            assertFailsWith<IllegalArgumentException> { repository.getUserLevel(0) }
            assertFailsWith<IllegalArgumentException> { repository.getUserLevel(-1) }
            assertEquals(0, requestCount.get())
        } finally {
            driver.close()
            client.close()
            server.stop(0)
        }
    }

    private fun responseServer(response: String, requestCount: AtomicInteger): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requestCount.incrementAndGet()
                val body = response.encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

    private fun levelResponse(userId: Long, progress: Double, nowPlayCount: Long): String =
        """
        {"code":200,"full":false,"data":{"userId":$userId,"info":"","progress":$progress,"nextPlayCount":100,"nextLoginCount":20,"nowPlayCount":$nowPlayCount,"nowLoginCount":10,"level":3}}
        """.trimIndent()
}

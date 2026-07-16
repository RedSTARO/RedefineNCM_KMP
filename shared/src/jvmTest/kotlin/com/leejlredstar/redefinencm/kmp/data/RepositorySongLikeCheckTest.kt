package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.test.CapturedRequest
import com.leejlredstar.redefinencm.kmp.test.parseQuery
import com.leejlredstar.redefinencm.kmp.test.testHttpClient
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositorySongLikeCheckTest {
    @Test
    fun checkUsesJsonArrayParameterAndDistinguishesUnlikedFromRejected() = runBlocking {
        val fixture = repositoryFixture(
            """{"code":200,"ids":[1497529942,999]}""",
            """{"code":200,"ids":[]}""",
            """{"code":301,"msg":"需要登录"}""",
        )

        try {
            assertEquals(
                setOf(1_497_529_942L),
                fixture.repository.checkLikedSongIds(
                    listOf(2_058_263_032L, 1_497_529_942L, 2_058_263_032L),
                ),
            )
            assertFalse(fixture.repository.isSongLiked(2_058_263_032L)!!)
            assertNull(fixture.repository.isSongLiked(2_058_263_032L))

            assertEquals(
                listOf(
                    CapturedRequest(
                        "GET",
                        "/song/like/check",
                        mapOf("ids" to "[2058263032,1497529942]"),
                    ),
                    CapturedRequest(
                        "GET",
                        "/song/like/check",
                        mapOf("ids" to "[2058263032]"),
                    ),
                    CapturedRequest(
                        "GET",
                        "/song/like/check",
                        mapOf("ids" to "[2058263032]"),
                    ),
                ),
                fixture.requests.toList(),
            )
            assertFailsWith<IllegalArgumentException> {
                fixture.repository.checkLikedSongIds(emptyList())
            }
            assertFailsWith<IllegalArgumentException> {
                fixture.repository.isSongLiked(0)
            }
            assertEquals(3, fixture.requests.size)
            assertTrue(fixture.responses.isEmpty())
        } finally {
            fixture.close()
        }
    }

    private fun repositoryFixture(vararg responseBodies: String): Fixture {
        val requests = ConcurrentLinkedQueue<CapturedRequest>()
        val responses = ConcurrentLinkedQueue(responseBodies.toList())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests += CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    query = parseQuery(exchange.requestURI.rawQuery.orEmpty()),
                )
                val body = checkNotNull(responses.poll()) { "Unexpected request" }.encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = testHttpClient(server.address.port)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return Fixture(
            repository = Repository(NCMApi(client), AppDatabase(driver)),
            requests = requests,
            responses = responses,
            server = server,
            client = client,
            driver = driver,
        )
    }

    private data class Fixture(
        val repository: Repository,
        val requests: ConcurrentLinkedQueue<CapturedRequest>,
        val responses: ConcurrentLinkedQueue<String>,
        val server: HttpServer,
        val client: HttpClient,
        val driver: JdbcSqliteDriver,
    ) {
        fun close() {
            driver.close()
            client.close()
            server.stop(0)
        }
    }
}

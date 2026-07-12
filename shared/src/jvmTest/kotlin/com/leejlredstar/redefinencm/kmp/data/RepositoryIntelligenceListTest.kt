package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RepositoryIntelligenceListTest {
    @Test
    fun likedSongIdsRequireSuccessAndPreserveFirstPositiveOccurrence() = runBlocking {
        val responses = ConcurrentLinkedQueue(
            listOf(
                """{"code":200,"ids":[5,0,-1,5,7,7,9]}""",
                """{"code":301,"ids":[11]}""",
            ),
        )
        val fixture = repositoryFixture(responses)

        try {
            assertEquals(listOf(5L, 7L, 9L), fixture.repository.getLikedSongIds(42))
            assertEquals(emptyList(), fixture.repository.getLikedSongIds(42))
            assertEquals(2, fixture.requestCount.get())
            assertEquals(
                listOf(
                    CapturedRequest("POST", "/likelist", mapOf("uid" to "42")),
                    CapturedRequest("POST", "/likelist", mapOf("uid" to "42")),
                ),
                fixture.requests.toList(),
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun intelligenceListRequiresSuccessfulBodyCode() = runBlocking {
        val responses = ConcurrentLinkedQueue(
            listOf(
                """{"code":200,"data":[{"id":7,"songInfo":{"id":7,"name":"Song"}}]}""",
                """{"code":301,"msg":"需要登录"}""",
            ),
        )
        val fixture = repositoryFixture(responses)

        try {
            val success = fixture.repository.getIntelligenceList(id = 5, pid = 42)
            assertEquals(7L, success?.data?.single()?.songInfo?.id)
            assertNull(fixture.repository.getIntelligenceList(id = 5, pid = 42, sid = 7))
            assertEquals(2, fixture.requestCount.get())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun invalidIdentifiersFailBeforeNetworkRequest() = runBlocking {
        val fixture = repositoryFixture(ConcurrentLinkedQueue())

        try {
            assertFailsWith<IllegalArgumentException> {
                fixture.repository.getLikedSongIds(0)
            }
            assertFailsWith<IllegalArgumentException> {
                fixture.repository.getIntelligenceList(id = 0, pid = 42)
            }
            assertFailsWith<IllegalArgumentException> {
                fixture.repository.getIntelligenceList(id = 5, pid = 0)
            }
            assertFailsWith<IllegalArgumentException> {
                fixture.repository.getIntelligenceList(id = 5, pid = 42, sid = -1)
            }
            assertEquals(0, fixture.requestCount.get())
        } finally {
            fixture.close()
        }
    }

    private fun repositoryFixture(responses: ConcurrentLinkedQueue<String>): Fixture {
        val requestCount = AtomicInteger()
        val requests = ConcurrentLinkedQueue<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requestCount.incrementAndGet()
                requests += CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    query = parseQuery(exchange.requestURI.rawQuery.orEmpty()),
                )
                val response = checkNotNull(responses.poll()) { "Unexpected request" }
                val body = response.encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            defaultRequest {
                url("http://127.0.0.1:${server.address.port}")
            }
        }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return Fixture(
            repository = Repository(NCMApi(client), AppDatabase(driver)),
            requestCount = requestCount,
            requests = requests,
            server = server,
            client = client,
            driver = driver,
        )
    }

    private data class Fixture(
        val repository: Repository,
        val requestCount: AtomicInteger,
        val requests: ConcurrentLinkedQueue<CapturedRequest>,
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

    private fun parseQuery(rawQuery: String): Map<String, String> = rawQuery
        .split('&')
        .filter(String::isNotBlank)
        .associate { part ->
            val separator = part.indexOf('=')
            val name = if (separator >= 0) part.substring(0, separator) else part
            val value = if (separator >= 0) part.substring(separator + 1) else ""
            decode(name) to decode(value)
        }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
    )
}

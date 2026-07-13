package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.test.CapturedRequest
import com.leejlredstar.redefinencm.kmp.test.decodeQueryComponent
import com.leejlredstar.redefinencm.kmp.test.parseQuery
import com.leejlredstar.redefinencm.kmp.test.testHttpClient
import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RepositoryPlaybackReportingTest {
    @Test
    fun htmlNotFoundResponseRetainsHttpMetadata() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val body = "<!doctype html><html><body>Not Found</body></html>".encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(404, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = testHttpClient(server.address.port)

        try {
            val response = NCMApi(client).scrobbleV1(id = 42, timeSeconds = 30)

            assertEquals(404, response.statusCode)
            assertTrue(response.isHtmlNotFound)
            assertEquals(null, response.body)
        } finally {
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun reportingCredentialSnapshotOverridesDynamicQueryCookie() = runBlocking {
        var rawQuery = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                rawQuery = exchange.requestURI.rawQuery.orEmpty()
                val body = """{"code":200,"data":{"accepted":true}}""".encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = HttpClientFactory.create(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            realIP = "127.0.0.1",
            cookieProvider = { "MUSIC_U=dynamic-account" },
            engineFactory = OkHttp,
            cookieTransport = HttpClientFactory.CookieTransport.QUERY_PARAMETER,
        )

        try {
            val response = NCMApi(client).submitPlayState(
                id = 42,
                sessionId = "AB12CD34EF56",
                progressSeconds = 1,
                playMode = "list_loop",
                credentialCookie = "MUSIC_U=session+snapshot==",
            )

            assertEquals(200, response.statusCode)
            assertEquals(200, response.body?.code)
            val cookieValues = rawQuery.split('&')
                .filter(String::isNotBlank)
                .map { part -> part.substringBefore('=') to part.substringAfter('=', "") }
                .filter { (name, _) -> decodeQueryComponent(name) == "cookie" }
                .map { (_, value) -> decodeQueryComponent(value) }
            assertEquals(listOf("MUSIC_U=session+snapshot=="), cookieValues)
        } finally {
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun reportingCredentialSnapshotUsesNativeCookieHeader() = runBlocking {
        var rawQuery = ""
        var cookieHeader: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                rawQuery = exchange.requestURI.rawQuery.orEmpty()
                cookieHeader = exchange.requestHeaders.getFirst("Cookie")
                val body = """{"code":200,"data":{"accepted":true}}""".encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = HttpClientFactory.create(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            realIP = "127.0.0.1",
            cookieProvider = { "MUSIC_U=dynamic-account" },
            engineFactory = OkHttp,
        )

        try {
            NCMApi(client).submitPlayState(
                id = 42,
                sessionId = "AB12CD34EF56",
                progressSeconds = 1,
                playMode = "list_loop",
                credentialCookie = "MUSIC_U=session-snapshot",
            )

            assertEquals("MUSIC_U=session-snapshot", cookieHeader)
            assertFalse(parseQuery(rawQuery).containsKey("cookie"))
        } finally {
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun cookieTransportUsesFreshProviderAndPreservesExplicitCookie() = runBlocking {
        val captured = ConcurrentLinkedQueue<Pair<String?, String>>()
        var providedCookie = "MUSIC_U=first+token=="
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                captured += exchange.requestHeaders.getFirst("Cookie") to
                    exchange.requestURI.rawQuery.orEmpty()
                val body = "{}".encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = HttpClientFactory.create(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            realIP = "127.0.0.1",
            cookieProvider = { providedCookie },
            engineFactory = OkHttp,
        )

        try {
            client.get("/probe")
            providedCookie = "MUSIC_U=second+token=="
            client.get("/probe")
            client.get("/probe") {
                parameter("cookie", "MUSIC_U=explicit+token==")
            }

            val requests = captured.toList()
            assertEquals(
                listOf(
                    "MUSIC_U=first+token==",
                    "MUSIC_U=second+token==",
                    "MUSIC_U=explicit+token==",
                ),
                requests.map { it.first },
            )
            assertTrue(requests.all { (_, rawQuery) -> !parseQuery(rawQuery).containsKey("cookie") })
        } finally {
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun reportingUsesGetQueriesAndOnlyAcceptsBodyCode200() = runBlocking {
        val responses = ConcurrentLinkedQueue(
            listOf(
                """{"code":200,"data":"scrobble_v1 上报成功","details":{"plv":{},"pld":{}}}""",
                """{"code":201,"data":"not accepted"}""",
                """{"code":200,"data":{"accepted":true}}""",
                """{"code":301,"msg":"需要登录"}""",
            ),
        )
        val requests = ConcurrentLinkedQueue<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests += CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    query = parseQuery(exchange.requestURI.rawQuery.orEmpty()),
                )
                val body = checkNotNull(responses.poll()) { "Unexpected request" }
                    .encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = testHttpClient(server.address.port)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val repository = Repository(NCMApi(client), AppDatabase(driver))

        try {
            assertIs<PlaybackReportResult.Accepted>(
                repository.scrobbleV1(
                    id = 518_066_366,
                    timeSeconds = 291,
                    sourceId = "36780169",
                    source = "playlist",
                    name = "Song name",
                    artist = "Artist name",
                    bitrate = 320,
                    level = "exhigh",
                    totalSeconds = 300,
                ),
            )
            assertIs<PlaybackReportResult.Rejected>(repository.scrobbleV1(id = 42, timeSeconds = 30))
            assertIs<PlaybackReportResult.Accepted>(
                repository.submitPlayState(
                    id = 518_066_366,
                    sessionId = "AB12CD34EF56",
                    progressSeconds = 30,
                    playMode = "list_loop",
                ),
            )
            assertIs<PlaybackReportResult.Rejected>(
                repository.submitPlayState(
                    id = 518_066_366,
                    sessionId = "AB12CD34EF56",
                    progressSeconds = 31,
                    playMode = "list_loop",
                ),
            )

            val captured = requests.toList()
            assertEquals(4, captured.size)
            assertTrue(captured.all { it.method == "GET" })
            assertEquals("/scrobble/v1", captured[0].path)
            assertEquals(
                mapOf(
                    "id" to "518066366",
                    "time" to "291",
                    "sourceid" to "36780169",
                    "source" to "playlist",
                    "name" to "Song name",
                    "artist" to "Artist name",
                    "bitrate" to "320",
                    "level" to "exhigh",
                    "total" to "300",
                ),
                captured[0].query,
            )
            assertEquals(mapOf("id" to "42", "time" to "30"), captured[1].query)
            assertEquals("/relay/play/state/submit", captured[2].path)
            assertEquals(
                mapOf(
                    "id" to "518066366",
                    "sessionId" to "AB12CD34EF56",
                    "progress" to "30",
                    "playMode" to "list_loop",
                    "type" to "song",
                ),
                captured[2].query,
            )
        } finally {
            driver.close()
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun scrobble404FallsBackToWeblogAndCachesUnsupportedCapability() = runBlocking {
        val v1Requests = AtomicInteger()
        val weblogBodies = ConcurrentLinkedQueue<String>()
        val weblogCookies = ConcurrentLinkedQueue<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                when (exchange.requestURI.path) {
                    "/scrobble/v1" -> {
                        v1Requests.incrementAndGet()
                        val body = "<!doctype html><html>Not Found</html>".encodeToByteArray()
                        exchange.responseHeaders.add("Content-Type", "text/html")
                        exchange.sendResponseHeaders(404, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    }
                    "/weblog" -> {
                        weblogBodies += exchange.requestBody.use { it.readBytes().decodeToString() }
                        weblogCookies += exchange.requestHeaders.getFirst("Cookie")
                        val body = """{"code":200}""".encodeToByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    }
                    else -> error("Unexpected path ${exchange.requestURI.path}")
                }
            }
            start()
        }
        val client = HttpClientFactory.create(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            realIP = "127.0.0.1",
            cookieProvider = { "" },
            engineFactory = OkHttp,
        )
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val repository = Repository(NCMApi(client), AppDatabase(driver))

        try {
            repeat(2) {
                val result = repository.scrobbleV1(
                    id = 518_066_366,
                    timeSeconds = 100,
                    sourceId = "36780169",
                    credentialCookie = "MUSIC_U=session-snapshot; os=pc",
                )
                assertEquals(PlaybackReportEndpoint.WEBLOG, assertIs<PlaybackReportResult.Accepted>(result).endpoint)
            }
            val invalidFallback = assertIs<PlaybackReportResult.Rejected>(
                repository.scrobbleV1(
                    id = 42,
                    timeSeconds = 30,
                    credentialCookie = "MUSIC_U=session-snapshot",
                ),
            )

            assertEquals(1, v1Requests.get())
            assertEquals(4, weblogBodies.size)
            assertEquals(PlaybackReportRejectionReason.INVALID_INPUT, invalidFallback.reason)
            assertTrue(weblogBodies.elementAt(0).contains("\\\"action\\\":\\\"startplay\\\""))
            assertTrue(weblogBodies.elementAt(1).contains("\\\"action\\\":\\\"play\\\""))
            assertTrue(weblogCookies.all { cookie ->
                cookie?.contains("MUSIC_U=session-snapshot") == true &&
                    cookie.contains("os=osx") &&
                    !cookie.contains("os=pc")
            })
        } finally {
            driver.close()
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun relay404IsCachedWithoutRetryingTheUnsupportedEndpoint() = runBlocking {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests.incrementAndGet()
                val body = "<!doctype html><html>Not Found</html>".encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "text/html")
                exchange.sendResponseHeaders(404, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = testHttpClient(server.address.port)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val repository = Repository(NCMApi(client), AppDatabase(driver))

        try {
            repeat(2) {
                assertIs<PlaybackReportResult.Unsupported>(
                    repository.submitPlayState(
                        id = 42,
                        sessionId = "AB12CD34EF56",
                        progressSeconds = 30,
                        playMode = "list_loop",
                        credentialCookie = "MUSIC_U=session-snapshot",
                    ),
                )
            }
            assertEquals(1, requests.get())
        } finally {
            driver.close()
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun json404IsRejectedWithoutCachingRouteAsUnsupportedOrFallingBack() = runBlocking {
        val scrobbleRequests = AtomicInteger()
        val relayRequests = AtomicInteger()
        val weblogRequests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                when (exchange.requestURI.path) {
                    "/scrobble/v1" -> scrobbleRequests.incrementAndGet()
                    "/relay/play/state/submit" -> relayRequests.incrementAndGet()
                    "/weblog" -> weblogRequests.incrementAndGet()
                    else -> error("Unexpected path ${exchange.requestURI.path}")
                }
                val body = """{"code":404,"msg":"resource not found"}""".encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(404, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = testHttpClient(server.address.port)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val repository = Repository(NCMApi(client), AppDatabase(driver))

        try {
            repeat(2) {
                assertIs<PlaybackReportResult.Rejected>(
                    repository.scrobbleV1(
                        id = 42,
                        timeSeconds = 30,
                        sourceId = "7",
                        credentialCookie = "MUSIC_U=session-snapshot",
                    ),
                )
                assertIs<PlaybackReportResult.Rejected>(
                    repository.submitPlayState(
                        id = 42,
                        sessionId = "AB12CD34EF56",
                        progressSeconds = 30,
                        playMode = "list_loop",
                        credentialCookie = "MUSIC_U=session-snapshot",
                    ),
                )
            }

            assertEquals(2, scrobbleRequests.get())
            assertEquals(2, relayRequests.get())
            assertEquals(0, weblogRequests.get())
        } finally {
            driver.close()
            client.close()
            server.stop(0)
        }
    }

}

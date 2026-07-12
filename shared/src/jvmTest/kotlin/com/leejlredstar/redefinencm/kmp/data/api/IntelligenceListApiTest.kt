package com.leejlredstar.redefinencm.kmp.data.api

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
import kotlin.test.Test
import kotlin.test.assertEquals

class IntelligenceListApiTest {
    @Test
    fun usesGetRouteAndPreservesOptionalSid() = runBlocking {
        val requests = ConcurrentLinkedQueue<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests += CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    query = parseQuery(exchange.requestURI.rawQuery.orEmpty()),
                )
                val body = """{"code":200,"data":[]}""".encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = testClient(server.address.port)

        try {
            NCMApi(client).intelligenceList(
                id = 33_894_312,
                pid = 24_381_616,
            )
            NCMApi(client).intelligenceList(
                id = 33_894_312,
                pid = 24_381_616,
                sid = 36_871_368,
            )

            assertEquals(
                listOf(
                    CapturedRequest(
                        method = "GET",
                        path = "/playmode/intelligence/list",
                        query = mapOf(
                            "id" to "33894312",
                            "pid" to "24381616",
                        ),
                    ),
                    CapturedRequest(
                        method = "GET",
                        path = "/playmode/intelligence/list",
                        query = mapOf(
                            "id" to "33894312",
                            "pid" to "24381616",
                            "sid" to "36871368",
                        ),
                    ),
                ),
                requests.toList(),
            )
        } finally {
            client.close()
            server.stop(0)
        }
    }

    private fun testClient(port: Int): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        defaultRequest {
            url("http://127.0.0.1:$port")
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

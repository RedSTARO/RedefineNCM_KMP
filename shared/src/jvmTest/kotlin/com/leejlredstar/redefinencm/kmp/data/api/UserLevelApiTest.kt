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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserLevelApiTest {
    @Test
    fun userLevelUsesGetWithoutQueryParameters() = runBlocking {
        var method = ""
        var path = ""
        var rawQuery: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                method = exchange.requestMethod
                path = exchange.requestURI.path
                rawQuery = exchange.requestURI.rawQuery
                val body = """
                    {
                      "code":200,
                      "full":false,
                      "data":{
                        "userId":42,
                        "progress":0.5,
                        "nextPlayCount":100,
                        "nextLoginCount":20,
                        "nowPlayCount":50,
                        "nowLoginCount":10,
                        "level":3
                      }
                    }
                """.trimIndent().encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = testClient(server.address.port)

        try {
            val response = NCMApi(client).userLevel()

            assertEquals("GET", method)
            assertEquals("/user/level", path)
            assertTrue(rawQuery.isNullOrEmpty())
            assertEquals(42L, response.data?.userId)
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
}

package com.leejlredstar.redefinencm.kmp.data.api

import com.leejlredstar.redefinencm.kmp.test.testHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
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
        val client = testHttpClient(server.address.port)

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

}

package com.leejlredstar.redefinencm.kmp.data.api

import com.leejlredstar.redefinencm.kmp.test.CapturedRequest
import com.leejlredstar.redefinencm.kmp.test.parseQuery
import com.leejlredstar.redefinencm.kmp.test.testHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
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
        val client = testHttpClient(server.address.port)

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

}

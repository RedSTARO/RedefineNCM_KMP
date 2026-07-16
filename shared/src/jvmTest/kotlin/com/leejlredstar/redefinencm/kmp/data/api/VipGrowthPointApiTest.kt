package com.leejlredstar.redefinencm.kmp.data.api

import com.leejlredstar.redefinencm.kmp.test.parseQuery
import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VipGrowthPointApiTest {
    @Test
    fun getAllUsesDocumentedEndpointAndCredentialSnapshot() = runBlocking {
        var method = ""
        var path = ""
        var query = emptyMap<String, String>()
        var cookieHeader: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                method = exchange.requestMethod
                path = exchange.requestURI.path
                query = parseQuery(exchange.requestURI.rawQuery.orEmpty())
                cookieHeader = exchange.requestHeaders.getFirst("Cookie")
                val body = """{"code":200,"data":{"growthPoint":3}}"""
                    .encodeToByteArray()
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
            val response = NCMApi(client).vipGrowthPointGetAll("MUSIC_U=startup-account")

            assertEquals("GET", method)
            assertEquals("/vip/growthpoint/getall", path)
            assertEquals("MUSIC_U=startup-account", cookieHeader)
            assertTrue("ids" !in query)
            assertTrue("cookie" !in query)
            assertEquals(200, response.code)
        } finally {
            client.close()
            server.stop(0)
        }
    }
}

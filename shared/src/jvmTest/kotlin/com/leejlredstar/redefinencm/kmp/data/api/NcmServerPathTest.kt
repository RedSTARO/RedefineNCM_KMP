package com.leejlredstar.redefinencm.kmp.data.api

import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NcmServerPathTest {
    private data class Request(val path: String, val contentType: String?, val body: String)

    @Test
    fun apiCallsStayUnderTheConfiguredDirectoryWithoutJsonHeadersOnGet() = runBlocking {
        val seen = CopyOnWriteArrayList<Request>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                seen += Request(exchange.requestURI.path, exchange.requestHeaders.getFirst("Content-Type"), exchange.requestBody.readBytes().decodeToString())
                val answer = """{"code":200,"more":false,"playlist":[]}""".encodeToByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, answer.size.toLong())
                exchange.responseBody.use { it.write(answer) }
            }
            start()
        }
        try {
            for (suffix in listOf("/api/ncm", "/api/ncm/")) {
                for (transport in HttpClientFactory.CookieTransport.entries) {
                    val client = HttpClientFactory.create(
                        baseUrl = "http://127.0.0.1:${server.address.port}$suffix",
                        realIP = "127.0.0.1", cookieProvider = { "" }, engineFactory = OkHttp,
                        cookieTransport = transport,
                    )
                    try {
                        assertEquals(200, NCMApi(client).userPlaylist(42).code)
                        assertEquals("/api/ncm/user/playlist", seen.last().path)
                        assertNull(seen.last().contentType)
                        client.post("query-only").bodyAsText()
                        assertNull(seen.last().contentType)
                        client.post("echo") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"check":true}""")
                        }.bodyAsText()
                        assertEquals("/api/ncm/echo", seen.last().path)
                        assertEquals("application/json", seen.last().contentType?.substringBefore(';'))
                        assertEquals("""{"check":true}""", seen.last().body)
                    } finally { client.close() }
                }
            }
        } finally { server.stop(0) }
    }
}

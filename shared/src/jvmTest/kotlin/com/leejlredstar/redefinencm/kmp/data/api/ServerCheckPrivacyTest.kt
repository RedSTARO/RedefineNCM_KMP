package com.leejlredstar.redefinencm.kmp.data.api

import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerCheckPrivacyTest {
    @Test
    fun candidateServerChecksNeverSendTheStoredCredential() = runBlocking {
        for (transport in HttpClientFactory.CookieTransport.entries) {
            val received = CopyOnWriteArrayList<Pair<String?, String>>()
            val candidate = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { request ->
                    received += request.requestHeaders.getFirst("Cookie") to request.requestURI.toString()
                    val body = """{"code":200,"data":{"version":"test"}}""".encodeToByteArray()
                    request.responseHeaders.set("Content-Type", "application/json")
                    request.sendResponseHeaders(200, body.size.toLong())
                    request.responseBody.use { it.write(body) }
                }
                start()
            }
            val client = HttpClientFactory.create(
                baseUrl = "http://127.0.0.1:${candidate.address.port}/",
                realIP = "127.0.0.1",
                cookieProvider = { "MUSIC_U=stored-session" },
                engineFactory = OkHttp,
                cookieTransport = transport,
            )
            try {
                assertEquals(200, NCMApi(client).innerVersion("http://127.0.0.1:${candidate.address.port}/inner/version/").code)
                val check = received.single()
                assertNull(check.first)
                assertFalse("cookie=" in check.second)
                assertFalse("stored-session" in check.second)

                // The anonymous check must not clear the credential for the next account call.
                client.get("/user/account").bodyAsText()
                val account = received.last()
                when (transport) {
                    HttpClientFactory.CookieTransport.HEADER -> assertEquals("MUSIC_U=stored-session", account.first)
                    HttpClientFactory.CookieTransport.QUERY_PARAMETER -> assertTrue("stored-session" in account.second)
                }
            } finally {
                client.close()
                candidate.stop(0)
            }
        }
    }
}

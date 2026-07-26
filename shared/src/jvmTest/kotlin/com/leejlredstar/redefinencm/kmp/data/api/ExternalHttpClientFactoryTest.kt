package com.leejlredstar.redefinencm.kmp.data.api

import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ExternalHttpClientFactoryTest {
    @Test
    fun publicClientDoesNotAttachNcmCredentialsOrParameters() = runBlocking {
        val request = CompletableFuture<Pair<String?, String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                request.complete(
                    exchange.requestHeaders.getFirst(HttpHeaders.Cookie) to
                        exchange.requestURI.rawQuery.orEmpty(),
                )
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            start()
        }
        val external = ExternalHttpClientFactory.create(OkHttp)

        try {
            external.client.get("http://127.0.0.1:${server.address.port}/lyrics")
            val (cookie, query) = request.get(5, TimeUnit.SECONDS)

            assertNull(cookie)
            assertFalse(query.contains("realIP"))
            assertFalse(query.contains("timestamp"))
            assertFalse(query.contains("cookie", ignoreCase = true))
        } finally {
            external.client.close()
            server.stop(0)
        }
    }
}

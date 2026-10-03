package com.leejlredstar.redefinencm.kmp.data.api

import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertTrue

class HttpClientLoggingTest {
    @Test
    fun urlUserInfoAndQrKeysDoNotEnterRequestLogs() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val body = "{}".encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val logged = CopyOnWriteArrayList<String>()
        val client = HttpClientFactory.create(
            baseUrl = "http://proxy-user:ADDRESS_SECRET@127.0.0.1:${server.address.port}/api/",
            realIP = "127.0.0.1", cookieProvider = { "" }, engineFactory = OkHttp,
            logSink = object : Logger { override fun log(message: String) { logged += message } },
        )
        try {
            client.get("login/qr/check") { parameter("key", "PRIVATE_QR_KEY") }.bodyAsText()
            val log = logged.joinToString("\n")
            assertTrue("/api/login/qr/check" in log)
            assertTrue("ADDRESS_SECRET" !in log)
            assertTrue("proxy-user" !in log)
            assertTrue("PRIVATE_QR_KEY" !in log)
        } finally { client.close(); server.stop(0) }
    }

    @Test
    fun aLoginAnswerLeavesNoSessionInTheLog() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                // The login answer carries the session twice: as a header and as a field.
                val body = """{"code":803,"cookie":"MUSIC_U=body-session"}""".encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.responseHeaders.add("Set-Cookie", "MUSIC_U=answer-session; Path=/")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val logged = CopyOnWriteArrayList<String>()
        val client = HttpClientFactory.create(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            realIP = "127.0.0.1",
            cookieProvider = { "MUSIC_U=request-session" },
            engineFactory = OkHttp,
            logSink = object : Logger {
                override fun log(message: String) {
                    logged += message
                }
            },
        )

        try {
            assertTrue("body-session" in client.get("/login/qr/check").bodyAsText())

            val log = logged.joinToString("\n")
            assertTrue("/login/qr/check" in log, "the request line is what the log is for")
            assertTrue("request-session" !in log)
            assertTrue("answer-session" !in log)
            assertTrue("body-session" !in log)
        } finally {
            client.close()
            server.stop(0)
        }
    }
}

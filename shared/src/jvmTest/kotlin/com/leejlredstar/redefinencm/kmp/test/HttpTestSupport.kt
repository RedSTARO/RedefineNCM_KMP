package com.leejlredstar.redefinencm.kmp.test

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal fun testHttpClient(port: Int): HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
    defaultRequest {
        url("http://127.0.0.1:$port")
    }
}

internal fun parseQuery(rawQuery: String): Map<String, String> = rawQuery
    .split('&')
    .filter(String::isNotBlank)
    .associate { part ->
        val separator = part.indexOf('=')
        val name = if (separator >= 0) part.substring(0, separator) else part
        val value = if (separator >= 0) part.substring(separator + 1) else ""
        decodeQueryComponent(name) to decodeQueryComponent(value)
    }

internal fun decodeQueryComponent(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

internal data class CapturedRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
)

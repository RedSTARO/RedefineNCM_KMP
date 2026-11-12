package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import com.leejlredstar.redefinencm.kmp.data.api.NcmCredentialCookieAttribute
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.JsonElement

/** In-process compatibility boundary: no loopback listener and no second runtime. */
internal class NativeNcmEngine(private val nativeDispatcher: NativeNcmDispatcher) : HttpClientEngineBase("native-ncm") {
    override val config = HttpClientEngineConfig()
    override val supportedCapabilities = setOf(HttpTimeoutCapability)

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        // Absolute URLs are used only by the legacy server checker. Native requests must never
        // forward an account to an arbitrary address or fall back silently to a remote server.
        require(data.url.host == NATIVE_NCM_HOST) { "Native NetEase does not use a server address" }
        val query = data.url.parameters.entries().associate { (name, values) -> name to values.last() }
        val rawBody = when (val body = data.body) {
            is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
            is OutgoingContent.NoContent -> ""
            else -> error("Unsupported native NetEase request body")
        }
        val json: JsonElement? = rawBody.takeIf(String::isNotBlank)?.let(ApiJson::parseToJsonElement)
        val result = nativeDispatcher.execute(
            route = data.url.encodedPath.trim('/'),
            query = query,
            jsonBody = json,
            credential = data.attributes.getOrNull(NcmCredentialCookieAttribute)
                ?: data.headers[HttpHeaders.Cookie],
        )
        return HttpResponseData(
            HttpStatusCode.fromValue(result.status),
            GMTDate(),
            Headers.build {
                append(HttpHeaders.ContentType, "application/json; charset=utf-8")
                result.cookies.forEach { append(HttpHeaders.SetCookie, it) }
            },
            HttpProtocolVersion.HTTP_1_1,
            ByteReadChannel(result.body.toString().encodeToByteArray()),
            callContext(),
        )
    }

    override fun close() {
        super.close()
        nativeDispatcher.close()
    }
}

internal const val NATIVE_NCM_HOST = "native-ncm.invalid"

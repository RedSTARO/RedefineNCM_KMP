package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentLength
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable

/** Raw response, deliberately without data-class toString: login bodies contain credentials. */
internal class NcmWireResponse(
    val status: Int,
    val body: ByteArray,
    val headers: Map<String, List<String>> = emptyMap(),
) {
    fun header(name: String): String? = headers.entries.firstOrNull {
        it.key.equals(name, ignoreCase = true)
    }?.value?.firstOrNull()

    val cookies: List<String>
        get() = headers.entries.firstOrNull {
            it.key.equals(HttpHeaders.SetCookie, ignoreCase = true)
        }?.value.orEmpty()
}

internal interface NcmWire {
    suspend fun request(
        url: String,
        method: HttpMethod = HttpMethod.Post,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
    ): NcmWireResponse
}

/** One connection pool, no API-server defaults or cookie jar, and no credential-bearing redirects. */
internal class KtorNcmWire(private val client: HttpClient) : NcmWire {
    override suspend fun request(
        url: String,
        method: HttpMethod,
        headers: Map<String, String>,
        body: ByteArray?,
    ): NcmWireResponse {
        require(isNcmProtocolUrl(url)) { "Untrusted NetEase protocol destination" }
        return client.prepareRequest {
            this.method = method
            this.url.takeFrom(url)
            headers.forEach { (name, value) ->
                // The protocol layer supports bounded gzip, not raw HTTP deflate. The pinned
                // NCBL helper advertises both; narrow negotiation instead of accepting an
                // encoding the native adapter cannot decode on every platform.
                this.headers.append(name, if (name.equals(HttpHeaders.AcceptEncoding, ignoreCase = true)) "gzip" else value)
            }
            if (body != null) setBody(body)
        }.execute { response ->
            val channel = response.bodyAsChannel()
            val limit = 16 * 1024 * 1024
            require((response.contentLength() ?: 0L) <= limit) { "NetEase response is too large" }
            // Read in chunks instead of allocating the entire limit for every small API response.
            val chunks = mutableListOf<ByteArray>()
            var count = 0
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = channel.readAvailable(buffer, 0, minOf(buffer.size, limit + 1 - count))
                if (read < 0) break
                if (read == 0) continue
                count += read
                if (count > limit) {
                    channel.cancel(IllegalStateException("NetEase response is too large"))
                    error("NetEase response is too large")
                }
                chunks += buffer.copyOf(read)
            }
            val bytes = ByteArray(count)
            var offset = 0
            chunks.forEach { chunk -> chunk.copyInto(bytes, offset); offset += chunk.size }
            val decoded = if (response.headers[HttpHeaders.ContentEncoding]?.equals("gzip", ignoreCase = true) == true &&
                bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                NcmCompression.gunzip(bytes, limit)
            } else bytes
            NcmWireResponse(response.status.value, decoded, response.headers.entries().associate { it.toPair() })
        }
    }
}

internal fun isNcmProtocolUrl(url: String): Boolean = runCatching {
    val parsed = io.ktor.http.Url(url)
    parsed.protocol.name == "https" && parsed.user == null && parsed.password == null &&
        parsed.port == 443 && parsed.host in setOf(
            "music.163.com", "interface.music.163.com", "interfacepc.music.163.com",
            "interface3.music.163.com", "clientlog3.music.163.com",
        )
}.getOrDefault(false)

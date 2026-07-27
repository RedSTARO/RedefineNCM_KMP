@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.map.Mapper
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import com.leejlredstar.redefinencm.kmp.download.LocalMediaAssets
import kotlin.JsFun
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Buffer

/**
 * Installs the Web image pipeline before the first [coil3.compose.AsyncImage] request.
 *
 * Coil 3.5's network fetcher deliberately accepts only HTTP(S) URIs. Local downloaded artwork is
 * exposed as a short-lived browser `blob:` URI, so it needs a narrowly scoped fetcher that copies
 * the active blob into Coil's decoder source without changing or persisting that URI.
 */
internal fun configureWebArtworkImageLoader() {
    SingletonImageLoader.setSafe(::createWebArtworkImageLoader)
}

internal fun createWebArtworkImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(WebBlobArtworkMapper)
            add(WebBlobArtworkKeyer)
            add(WebBlobArtworkFetcher.Factory())
            add(KtorNetworkFetcherFactory())
        }
        .build()

internal data class WebBlobArtworkUri(
    val value: String,
)

internal data class WebBlobArtwork(
    val bytes: ByteArray,
    val mimeType: String?,
)

private object WebBlobArtworkMapper : Mapper<String, WebBlobArtworkUri> {
    override fun map(data: String, options: Options): WebBlobArtworkUri? =
        data.takeIf(::isWebBlobArtworkUri)?.let(::WebBlobArtworkUri)
}

private object WebBlobArtworkKeyer : Keyer<WebBlobArtworkUri> {
    override fun key(data: WebBlobArtworkUri, options: Options): String =
        data.value
}

internal class WebBlobArtworkFetcher(
    private val uri: String,
    private val options: Options,
    private val load: suspend (String) -> WebBlobArtwork = ::loadWebBlobArtwork,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val artwork = load(uri)
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().write(artwork.bytes),
                fileSystem = options.fileSystem,
            ),
            mimeType = artwork.mimeType,
            dataSource = DataSource.MEMORY,
        )
    }

    internal class Factory : Fetcher.Factory<WebBlobArtworkUri> {
        override fun create(
            data: WebBlobArtworkUri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            return WebBlobArtworkFetcher(
                uri = data.value,
                options = options,
            )
        }
    }
}

internal fun isWebBlobArtworkUri(uri: String): Boolean =
    uri.startsWith("blob:")

/**
 * Reads one same-document object URL. The URL remains owned by [LocalMediaAssets]; this function
 * never revokes it because other active Compose image requests can share the same current-song
 * URI. Cancellation only aborts this in-flight copy.
 */
internal suspend fun loadWebBlobArtwork(uri: String): WebBlobArtwork {
    require(isWebBlobArtworkUri(uri)) { "Only browser blob artwork URIs are supported" }
    return suspendCancellableCoroutine { continuation ->
        val token = newWebBlobArtworkRequestToken()
        startWebBlobArtworkRead(
            token = token,
            uri = uri,
            maxBytes = MaxWebArtworkBytes,
            onSuccess = success@{ encodedBytes, mimeType ->
                if (!continuation.isActive) return@success
                val decoded = runCatching {
                    val bytes = Base64.Default.decode(encodedBytes)
                    check(bytes.isNotEmpty()) { "Browser artwork blob is empty" }
                    check(bytes.size <= MaxWebArtworkBytes) {
                        "Browser artwork blob exceeds ${MaxWebArtworkBytes / (1024 * 1024)} MiB"
                    }
                    val normalizedMimeType = mimeType
                        ?.substringBefore(';')
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf(String::isNotEmpty)
                    check(normalizedMimeType == null || normalizedMimeType.startsWith("image/")) {
                        "Browser artwork blob is not an image"
                    }
                    WebBlobArtwork(
                        bytes = bytes,
                        mimeType = normalizedMimeType,
                    )
                }
                decoded.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(it) },
                )
            },
            onError = error@{ message ->
                if (!continuation.isActive) return@error
                continuation.resumeWithException(IllegalStateException(message))
            },
        )
        continuation.invokeOnCancellation {
            cancelWebBlobArtworkRead(token)
        }
    }
}

@JsFun(
    "() => globalThis.crypto?.randomUUID?.() ?? 'artwork-' + Date.now() + '-' + Math.random()",
)
private external fun newWebBlobArtworkRequestToken(): String

@JsFun(
    """(token, uri, maxBytes, onSuccess, onError) => {
        const requests = globalThis.__redefineNcmArtworkBlobRequests ??= new Map();
        requests.get(token)?.abort();
        const controller = new AbortController();
        requests.set(token, controller);

        (async () => {
            const response = await fetch(uri, {
                signal: controller.signal,
                credentials: "same-origin",
                cache: "no-store",
            });
            if (!response.ok) {
                throw new Error("Local artwork read failed: HTTP " + response.status);
            }
            const declaredLength = Number(response.headers.get("content-length"));
            if (Number.isFinite(declaredLength) && declaredLength > maxBytes) {
                throw new Error("Local artwork exceeds " + maxBytes + " bytes");
            }
            const buffer = await response.arrayBuffer();
            if (buffer.byteLength === 0) {
                throw new Error("Local artwork blob is empty");
            }
            if (buffer.byteLength > maxBytes) {
                throw new Error("Local artwork exceeds " + maxBytes + " bytes");
            }

            const bytes = new Uint8Array(buffer);
            const chunks = [];
            const chunkSize = 0x8000;
            for (let offset = 0; offset < bytes.length; offset += chunkSize) {
                chunks.push(String.fromCharCode(...bytes.subarray(offset, offset + chunkSize)));
            }
            onSuccess(
                btoa(chunks.join("")),
                response.headers.get("content-type"),
            );
        })().catch(error => {
            if (error?.name !== "AbortError") {
                onError(error?.message || String(error));
            }
        }).finally(() => {
            if (requests.get(token) === controller) requests.delete(token);
        });
    }""",
)
private external fun startWebBlobArtworkRead(
    token: String,
    uri: String,
    maxBytes: Int,
    onSuccess: (String, String?) -> Unit,
    onError: (String) -> Unit,
)

@JsFun(
    """(token) => {
        const requests = globalThis.__redefineNcmArtworkBlobRequests;
        const controller = requests?.get(token);
        if (!controller) return;
        controller.abort();
        requests.delete(token);
    }""",
)
private external fun cancelWebBlobArtworkRead(token: String)

private const val MaxWebArtworkBytes = 16 * 1024 * 1024

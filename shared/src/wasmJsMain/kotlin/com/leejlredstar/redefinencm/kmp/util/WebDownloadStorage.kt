@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val WEB_DOWNLOAD_URI_PREFIX = "opfs://RedefineNCM/"
private val webDownloadJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class WebStoredDownload(
    val id: String,
    val fileName: String,
    val uri: String,
    val sizeBytes: String,
    val lastModifiedEpochMillis: String,
)

internal object WebDownloadStorage {
    suspend fun download(
        item: DownloadRequestItem,
        fileName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): DownloadedSongFile = suspendCancellableCoroutine { continuation ->
        val token = newWebDownloadToken()
        continuation.invokeOnCancellation { cancelWebDownload(token) }
        startWebDownload(
            token = token,
            url = item.url,
            fileName = fileName,
            expectedBytes = item.expectedBytes?.toString(),
            onProgress = progress@{ downloaded, total ->
                if (!continuation.isActive) return@progress
                val downloadedBytes = downloaded.toLongOrNull() ?: return@progress
                onProgress(downloadedBytes, total?.toLongOrNull())
            },
            onComplete = complete@{ storedFileName, uri ->
                if (!continuation.isActive) return@complete
                continuation.resume(DownloadedSongFile(fileName = storedFileName, uri = uri))
            },
            onError = failure@{ message ->
                if (!continuation.isActive) return@failure
                continuation.resumeWithException(IllegalStateException(message))
            },
        )
    }

    suspend fun scan(): DownloadScanResult = suspendCancellableCoroutine { continuation ->
        scanWebDownloads(
            onSuccess = success@{ payload ->
                if (!continuation.isActive) return@success
                val result = runCatching {
                    webDownloadJson.decodeFromString<List<WebStoredDownload>>(payload).map { stored ->
                        DownloadedSongSnapshot(
                            id = stored.id.toLong(),
                            fileName = stored.fileName,
                            uri = stored.uri,
                            sizeBytes = stored.sizeBytes.toLongOrNull(),
                            lastModifiedEpochMillis = stored.lastModifiedEpochMillis.toLongOrNull(),
                        )
                    }
                }.fold(
                    onSuccess = { DownloadScanResult.Success(it) },
                    onFailure = { DownloadScanResult.Failure("无法解析浏览器下载索引", it) },
                )
                continuation.resume(result)
            },
            onError = failure@{ message ->
                if (!continuation.isActive) return@failure
                continuation.resume(DownloadScanResult.Failure(message))
            },
        )
    }

    suspend fun delete(songId: Long): Boolean = suspendCancellableCoroutine { continuation ->
        deleteWebDownloads(
            songId = songId.toString(),
            onSuccess = success@{ deleted ->
                if (continuation.isActive) continuation.resume(deleted)
            },
            onError = failure@{ message ->
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException(message))
                }
            },
        )
    }

    /** Resolve a persisted OPFS URI into a temporary URL consumable by HTMLAudioElement. */
    suspend fun createObjectUrl(uri: String): String? {
        if (!uri.startsWith(WEB_DOWNLOAD_URI_PREFIX)) return null
        return suspendCancellableCoroutine { continuation ->
            createWebDownloadObjectUrl(
                uri = uri,
                onSuccess = success@{ objectUrl ->
                    if (!continuation.isActive) {
                        objectUrl?.let(::revokeObjectUrl)
                        return@success
                    }
                    continuation.resume(objectUrl)
                },
                onError = failure@{ message ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException(message))
                    }
                },
            )
        }
    }

    fun revokeObjectUrl(url: String) {
        revokeWebObjectUrl(url)
    }
}

private fun newWebDownloadToken(): String = js(
    "crypto.randomUUID ? crypto.randomUUID() : 'download-' + Date.now() + '-' + Math.random()",
)

private fun startWebDownload(
    token: String,
    url: String,
    fileName: String,
    expectedBytes: String?,
    onProgress: (String, String?) -> Unit,
    onComplete: (String, String) -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
        const controllers = globalThis.__redefineNcmDownloadControllers ??= new Map();
        const controller = new AbortController();
        controllers.set(token, controller);
        (async () => {
            if (!globalThis.isSecureContext || !navigator.storage?.getDirectory) {
                throw new Error("浏览器内部下载需要 HTTPS 或 localhost，并要求支持 OPFS");
            }
            navigator.storage.persist?.().catch(() => false);
            const root = await navigator.storage.getDirectory();
            const directory = await root.getDirectoryHandle("RedefineNCM", { create: true });

            try {
                const existingHandle = await directory.getFileHandle(fileName);
                const existing = await existingHandle.getFile();
                const expected = expectedBytes && Number(expectedBytes) > 0 ? Number(expectedBytes) : null;
                if (existing.size > 0 && (expected === null || existing.size === expected)) {
                    onProgress(existing.size.toString(), existing.size.toString());
                    onComplete(fileName, "opfs://RedefineNCM/" + encodeURIComponent(fileName));
                    return;
                }
                await directory.removeEntry(fileName);
            } catch (error) {
                if (error?.name !== "NotFoundError") throw error;
            }

            const partialName = fileName + ".part";
            const partialHandle = await directory.getFileHandle(partialName, { create: true });
            let writable = null;
            let finalFileCreated = false;
            try {
                const response = await fetch(url, { signal: controller.signal, credentials: "omit" });
                if (!response.ok) throw new Error("下载失败：HTTP " + response.status);
                const headerLength = response.headers.get("content-length");
                const total = headerLength && Number(headerLength) > 0
                    ? headerLength
                    : (expectedBytes || null);
                writable = await partialHandle.createWritable();
                let downloaded = 0;
                onProgress("0", total);
                if (response.body) {
                    const reader = response.body.getReader();
                    while (true) {
                        const { done, value } = await reader.read();
                        if (done) break;
                        await writable.write(value);
                        downloaded += value.byteLength;
                        onProgress(downloaded.toString(), total);
                    }
                } else {
                    const bytes = await response.arrayBuffer();
                    await writable.write(bytes);
                    downloaded = bytes.byteLength;
                    onProgress(downloaded.toString(), total);
                }
                await writable.close();
                writable = null;

                const completedPart = await partialHandle.getFile();
                const finalHandle = await directory.getFileHandle(fileName, { create: true });
                finalFileCreated = true;
                const finalWriter = await finalHandle.createWritable();
                try {
                    await finalWriter.write(completedPart);
                    await finalWriter.close();
                } catch (error) {
                    await finalWriter.abort?.();
                    throw error;
                }
                await directory.removeEntry(partialName);
                const completed = await finalHandle.getFile();
                onProgress(completed.size.toString(), total || completed.size.toString());
                onComplete(fileName, "opfs://RedefineNCM/" + encodeURIComponent(fileName));
            } catch (error) {
                if (writable) await writable.abort?.();
                await directory.removeEntry(partialName).catch(() => {});
                if (finalFileCreated) await directory.removeEntry(fileName).catch(() => {});
                throw error;
            }
        })().catch(error => {
            const message = error?.name === "AbortError"
                ? "下载已取消"
                : (error?.message || String(error));
            onError(message);
        }).finally(() => controllers.delete(token));
    }""",
)

private fun cancelWebDownload(token: String): Unit = js(
    "globalThis.__redefineNcmDownloadControllers?.get(token)?.abort()",
)

private fun scanWebDownloads(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
        (async () => {
            if (!globalThis.isSecureContext || !navigator.storage?.getDirectory) {
                throw new Error("浏览器内部下载需要 HTTPS 或 localhost，并要求支持 OPFS");
            }
            const root = await navigator.storage.getDirectory();
            let directory;
            try {
                directory = await root.getDirectoryHandle("RedefineNCM");
            } catch (error) {
                if (error?.name === "NotFoundError") {
                    onSuccess("[]");
                    return;
                }
                throw error;
            }
            const files = [];
            for await (const [name, handle] of directory.entries()) {
                if (handle.kind !== "file" || name.endsWith(".part")) continue;
                const match = /^(\d+)\./.exec(name);
                if (!match) continue;
                const file = await handle.getFile();
                files.push({
                    id: match[1],
                    fileName: name,
                    uri: "opfs://RedefineNCM/" + encodeURIComponent(name),
                    sizeBytes: file.size.toString(),
                    lastModifiedEpochMillis: file.lastModified.toString(),
                });
            }
            files.sort((left, right) => Number(left.id) - Number(right.id));
            onSuccess(JSON.stringify(files));
        })().catch(error => onError(error?.message || String(error)));
    }""",
)

private fun deleteWebDownloads(
    songId: String,
    onSuccess: (Boolean) -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
        (async () => {
            if (!globalThis.isSecureContext || !navigator.storage?.getDirectory) {
                throw new Error("浏览器内部下载需要 HTTPS 或 localhost，并要求支持 OPFS");
            }
            const root = await navigator.storage.getDirectory();
            let directory;
            try {
                directory = await root.getDirectoryHandle("RedefineNCM");
            } catch (error) {
                if (error?.name === "NotFoundError") {
                    onSuccess(false);
                    return;
                }
                throw error;
            }
            let deleted = false;
            for await (const name of directory.keys()) {
                if (name.startsWith(songId + ".")) {
                    await directory.removeEntry(name);
                    deleted = true;
                }
            }
            onSuccess(deleted);
        })().catch(error => onError(error?.message || String(error)));
    }""",
)

private fun createWebDownloadObjectUrl(
    uri: String,
    onSuccess: (String?) -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
        (async () => {
            const prefix = "opfs://RedefineNCM/";
            if (!uri.startsWith(prefix)) {
                onSuccess(null);
                return;
            }
            const fileName = decodeURIComponent(uri.slice(prefix.length));
            if (!fileName || fileName.includes("/") || fileName.includes("\\")) {
                throw new Error("无效的浏览器下载 URI");
            }
            const root = await navigator.storage.getDirectory();
            const directory = await root.getDirectoryHandle("RedefineNCM");
            const handle = await directory.getFileHandle(fileName);
            onSuccess(URL.createObjectURL(await handle.getFile()));
        })().catch(error => onError(error?.message || String(error)));
    }""",
)

private fun revokeWebObjectUrl(url: String): Unit = js("URL.revokeObjectURL(url)")

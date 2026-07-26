@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.util

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.io.encoding.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val localMediaAssetJson = Json
private val webLocalMediaAssetMutex = Mutex()

actual object LocalMediaAssetStorage {
    actual suspend fun replaceLyrics(
        songId: Long,
        files: List<LocalTextMediaAsset>,
    ) {
        val validated = validateLocalLyricAssets(songId, files)
        val payload = buildJsonArray {
            validated.forEach { asset ->
                add(
                    buildJsonObject {
                        put("fileName", asset.fileName)
                        put("content", asset.content)
                    }
                )
            }
        }.toString()
        runSerializedWebLocalMediaAssetMutation<Unit> { onSuccess, onError ->
            replaceWebLyrics(
                songId = songId.toString(),
                payload = payload,
                onSuccess = { onSuccess(Unit) },
                onError = onError,
            )
        }
    }

    actual suspend fun readLyrics(
        songId: Long,
        fileNames: Set<String>,
    ): List<LocalTextMediaAsset> {
        val requestedFileNames = validateLocalLyricFileNames(songId, fileNames)
        if (requestedFileNames.isEmpty()) return emptyList()
        val requestedPayload = buildJsonArray {
            requestedFileNames.sorted().forEach(::add)
        }.toString()
        return webLocalMediaAssetMutex.withLock {
            suspendCancellableCoroutine { continuation ->
                readWebLyrics(
                    songId = songId.toString(),
                    requestedPayload = requestedPayload,
                    onSuccess = success@{ payload ->
                        if (!continuation.isActive) return@success
                        val decoded = runCatching {
                            localMediaAssetJson.parseToJsonElement(payload)
                                .jsonArray
                                .map { element ->
                                    val value = element.jsonObject
                                    LocalTextMediaAsset(
                                        fileName = value.getValue("fileName").jsonPrimitive.content,
                                        content = value.getValue("content").jsonPrimitive.content,
                                    )
                                }
                        }
                        decoded.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { continuation.resumeWithException(it) },
                        )
                    },
                    onError = { message ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException(message))
                        }
                    },
                )
            }
        }
    }

    actual suspend fun replaceArtwork(
        songId: Long,
        fileExtension: String,
        mimeType: String,
        bytes: ByteArray,
    ): String {
        requireLocalMediaSongId(songId)
        val extension = validateLocalArtworkExtension(fileExtension, mimeType, bytes)
        val fileName = localArtworkFileName(songId, extension)
        val base64 = Base64.Default.encode(bytes)
        return runSerializedWebLocalMediaAssetMutation { onSuccess, onError ->
            replaceWebArtwork(
                songId = songId.toString(),
                fileName = fileName,
                mimeType = mimeType.trim(),
                base64 = base64,
                onSuccess = { onSuccess(fileName) },
                onError = onError,
            )
        }
    }

    actual suspend fun inspect(songId: Long): LocalMediaAssetSnapshot {
        requireLocalMediaSongId(songId)
        return webLocalMediaAssetMutex.withLock {
            val names = listWebAssetNames(songId)
            localMediaAssetSnapshot(songId, names)
        }
    }

    actual suspend fun resolveArtworkUri(songId: Long): String? {
        requireLocalMediaSongId(songId)
        return webLocalMediaAssetMutex.withLock {
            awaitWebArtworkResource(
                start = { onSuccess, onError ->
                    resolveWebArtworkUri(
                        songId = songId.toString(),
                        onSuccess = onSuccess,
                        onError = onError,
                    )
                },
                release = ::releaseWebArtworkUri,
            )
        }
    }

    actual fun releaseArtworkUri(uri: String) {
        if (uri.startsWith("blob:")) releaseWebArtworkUri(uri)
    }

    actual suspend fun deleteAssets(songId: Long): Boolean {
        requireLocalMediaSongId(songId)
        return runSerializedWebLocalMediaAssetMutation { onSuccess, onError ->
            deleteWebAssets(
                songId = songId.toString(),
                onSuccess = onSuccess,
                onError = onError,
            )
        }
    }
}

/**
 * OPFS promises cannot be cancelled once started. Keep the shared storage mutex held until the
 * callback settles even when the caller is cancelled, otherwise a late replace can commit after a
 * later delete or race another replace.
 */
internal suspend fun <T> runSerializedWebLocalMediaAssetMutation(
    start: (onSuccess: (T) -> Unit, onError: (String) -> Unit) -> Unit,
): T = webLocalMediaAssetMutex.withLock {
    suspendCoroutine { continuation ->
        start(
            { value -> continuation.resume(value) },
            { message ->
                continuation.resumeWithException(IllegalStateException(message))
            },
        )
    }
}

internal suspend fun awaitWebArtworkResource(
    start: (onSuccess: (String?) -> Unit, onError: (String) -> Unit) -> Unit,
    release: (String) -> Unit,
): String? = suspendCancellableCoroutine { continuation ->
    start(
        { uri ->
            if (!continuation.isActive) {
                uri?.let(release)
            } else {
                continuation.resume(
                    uri,
                    onCancellation = { _, value, _ ->
                        value?.let(release)
                    },
                )
            }
        },
        { message ->
            if (continuation.isActive) {
                continuation.resumeWithException(IllegalStateException(message))
            }
        },
    )
}

private suspend fun listWebAssetNames(songId: Long): List<String> =
    suspendCancellableCoroutine { continuation ->
        inspectWebAssets(
            songId = songId.toString(),
            onSuccess = success@{ payload ->
                if (!continuation.isActive) return@success
                val names = runCatching {
                    localMediaAssetJson.parseToJsonElement(payload)
                        .jsonArray
                        .map { it.jsonPrimitive.content }
                }
                names.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(it) },
                )
            },
            onError = { message ->
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException(message))
                }
            },
        )
    }

private fun replaceWebLyrics(
    songId: String,
    payload: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
        (async () => {
            if (!globalThis.isSecureContext || !navigator.storage?.getDirectory) {
                throw new Error("浏览器本地媒体边车需要 HTTPS 或 localhost，并要求支持 OPFS");
            }
            const assets = JSON.parse(payload);
            const lyricPattern = new RegExp("^" + songId + "\\.lyric\\.[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
            if (!Array.isArray(assets) || assets.some(asset =>
                !asset || typeof asset.fileName !== "string" ||
                typeof asset.content !== "string" || !lyricPattern.test(asset.fileName)
            )) {
                throw new Error("无效的浏览器本地歌词边车");
            }
            const root = await navigator.storage.getDirectory();
            const directory = await root.getDirectoryHandle("RedefineNCM", { create: true });
            const token = crypto.randomUUID ? crypto.randomUUID() : Date.now() + "-" + Math.random();
            const staged = [];
            const backups = [];
            const published = [];
            const copyFile = async (sourceName, targetName) => {
                const source = await (await directory.getFileHandle(sourceName)).getFile();
                const target = await directory.getFileHandle(targetName, { create: true });
                const writer = await target.createWritable();
                try {
                    await writer.write(source);
                    await writer.close();
                } catch (error) {
                    await writer.abort?.();
                    throw error;
                }
            };
            try {
                for (const asset of assets) {
                    const temporaryName = "." + asset.fileName + "." + token + ".asset-pending";
                    staged.push([temporaryName, asset.fileName]);
                    const handle = await directory.getFileHandle(temporaryName, { create: true });
                    const writer = await handle.createWritable();
                    try {
                        await writer.write(asset.content);
                        await writer.close();
                    } catch (error) {
                        await writer.abort?.();
                        throw error;
                    }
                }
                for await (const [name, handle] of directory.entries()) {
                    if (handle.kind !== "file" || !lyricPattern.test(name)) continue;
                    const backupName = "." + name + "." + token + ".asset-backup";
                    await copyFile(name, backupName);
                    backups.push([backupName, name]);
                    await directory.removeEntry(name);
                }
                for (const [temporaryName, targetName] of staged) {
                    await copyFile(temporaryName, targetName);
                    published.push(targetName);
                    await directory.removeEntry(temporaryName).catch(() => {});
                }
                for (const [backupName] of backups) {
                    await directory.removeEntry(backupName).catch(() => {});
                }
                onSuccess();
            } catch (error) {
                for (const name of published) await directory.removeEntry(name).catch(() => {});
                const rollbackErrors = [];
                for (const [backupName, targetName] of backups) {
                    try {
                        await copyFile(backupName, targetName);
                        await directory.removeEntry(backupName).catch(() => {});
                    } catch (restoreError) {
                        rollbackErrors.push(
                            targetName + ": " + (restoreError?.message || String(restoreError))
                        );
                    }
                }
                for (const [temporaryName] of staged) {
                    await directory.removeEntry(temporaryName).catch(() => {});
                }
                if (rollbackErrors.length > 0) {
                    throw new Error(
                        (error?.message || String(error)) +
                        "; local lyric rollback failed: " + rollbackErrors.join("; ")
                    );
                }
                throw error;
            }
        })().catch(error => onError(error?.message || String(error)));
    }""",
)

private fun readWebLyrics(
    songId: String,
    requestedPayload: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
        (async () => {
            if (!globalThis.isSecureContext || !navigator.storage?.getDirectory) {
                throw new Error("浏览器本地媒体边车需要 HTTPS 或 localhost，并要求支持 OPFS");
            }
            const requestedNames = JSON.parse(requestedPayload);
            const lyricPattern = new RegExp("^" + songId + "\\.lyric\\.[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
            if (
                !Array.isArray(requestedNames) ||
                requestedNames.some(name => typeof name !== "string" || !lyricPattern.test(name))
            ) {
                throw new Error("无效的浏览器本地歌词读取范围");
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
            const result = [];
            for (const name of requestedNames) {
                try {
                    const handle = await directory.getFileHandle(name);
                    result.push({ fileName: name, content: await (await handle.getFile()).text() });
                } catch (error) {
                    if (error?.name !== "NotFoundError") throw error;
                }
            }
            result.sort((left, right) => left.fileName.localeCompare(right.fileName));
            onSuccess(JSON.stringify(result));
        })().catch(error => onError(error?.message || String(error)));
    }""",
)

private fun replaceWebArtwork(
    songId: String,
    fileName: String,
    mimeType: String,
    base64: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
        (async () => {
            if (!globalThis.isSecureContext || !navigator.storage?.getDirectory) {
                throw new Error("浏览器本地媒体边车需要 HTTPS 或 localhost，并要求支持 OPFS");
            }
            const artworkPattern = new RegExp("^" + songId + "\\.cover\\.[A-Za-z0-9]{1,12}$");
            if (!artworkPattern.test(fileName) || !mimeType.toLowerCase().startsWith("image/")) {
                throw new Error("无效的浏览器本地封面边车");
            }
            const binary = atob(base64);
            const bytes = new Uint8Array(binary.length);
            for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
            const root = await navigator.storage.getDirectory();
            const directory = await root.getDirectoryHandle("RedefineNCM", { create: true });
            const token = crypto.randomUUID ? crypto.randomUUID() : Date.now() + "-" + Math.random();
            const temporaryName = "." + fileName + "." + token + ".asset-pending";
            const backups = [];
            let published = false;
            const copyFile = async (sourceName, targetName) => {
                const source = await (await directory.getFileHandle(sourceName)).getFile();
                const target = await directory.getFileHandle(targetName, { create: true });
                const writer = await target.createWritable();
                try {
                    await writer.write(source);
                    await writer.close();
                } catch (error) {
                    await writer.abort?.();
                    throw error;
                }
            };
            try {
                const temporary = await directory.getFileHandle(temporaryName, { create: true });
                const writer = await temporary.createWritable();
                try {
                    await writer.write(new Blob([bytes], { type: mimeType }));
                    await writer.close();
                } catch (error) {
                    await writer.abort?.();
                    throw error;
                }
                for await (const [name, handle] of directory.entries()) {
                    if (handle.kind !== "file" || !artworkPattern.test(name)) continue;
                    const backupName = "." + name + "." + token + ".asset-backup";
                    await copyFile(name, backupName);
                    backups.push([backupName, name]);
                    await directory.removeEntry(name);
                }
                await copyFile(temporaryName, fileName);
                published = true;
                await directory.removeEntry(temporaryName).catch(() => {});
                for (const [backupName] of backups) {
                    await directory.removeEntry(backupName).catch(() => {});
                }
                onSuccess();
            } catch (error) {
                if (published) await directory.removeEntry(fileName).catch(() => {});
                const rollbackErrors = [];
                for (const [backupName, targetName] of backups) {
                    try {
                        await copyFile(backupName, targetName);
                        await directory.removeEntry(backupName).catch(() => {});
                    } catch (restoreError) {
                        rollbackErrors.push(
                            targetName + ": " + (restoreError?.message || String(restoreError))
                        );
                    }
                }
                await directory.removeEntry(temporaryName).catch(() => {});
                if (rollbackErrors.length > 0) {
                    throw new Error(
                        (error?.message || String(error)) +
                        "; local artwork rollback failed: " + rollbackErrors.join("; ")
                    );
                }
                throw error;
            }
        })().catch(error => onError(error?.message || String(error)));
    }""",
)

private fun inspectWebAssets(
    songId: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
        (async () => {
            if (!globalThis.isSecureContext || !navigator.storage?.getDirectory) {
                throw new Error("浏览器本地媒体边车需要 HTTPS 或 localhost，并要求支持 OPFS");
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
            const lyricPattern = new RegExp("^" + songId + "\\.lyric\\.[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
            const artworkPattern = new RegExp("^" + songId + "\\.cover\\.[A-Za-z0-9]{1,12}$");
            const names = [];
            for await (const [name, handle] of directory.entries()) {
                if (handle.kind === "file" && (lyricPattern.test(name) || artworkPattern.test(name))) {
                    names.push(name);
                }
            }
            names.sort();
            onSuccess(JSON.stringify(names));
        })().catch(error => onError(error?.message || String(error)));
    }""",
)

private fun resolveWebArtworkUri(
    songId: String,
    onSuccess: (String?) -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
        (async () => {
            if (!globalThis.isSecureContext || !navigator.storage?.getDirectory) {
                throw new Error("浏览器本地媒体边车需要 HTTPS 或 localhost，并要求支持 OPFS");
            }
            const root = await navigator.storage.getDirectory();
            let directory;
            try {
                directory = await root.getDirectoryHandle("RedefineNCM");
            } catch (error) {
                if (error?.name === "NotFoundError") {
                    onSuccess(null);
                    return;
                }
                throw error;
            }
            const artworkPattern = new RegExp("^" + songId + "\\.cover\\.[A-Za-z0-9]{1,12}$");
            const names = [];
            for await (const [name, handle] of directory.entries()) {
                if (handle.kind === "file" && artworkPattern.test(name)) names.push(name);
            }
            names.sort();
            if (names.length === 0) {
                onSuccess(null);
                return;
            }
            const file = await (await directory.getFileHandle(names[0])).getFile();
            onSuccess(URL.createObjectURL(file));
        })().catch(error => onError(error?.message || String(error)));
    }""",
)

private fun releaseWebArtworkUri(uri: String): Unit = js("URL.revokeObjectURL(uri)")

private fun deleteWebAssets(
    songId: String,
    onSuccess: (Boolean) -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
        (async () => {
            if (!globalThis.isSecureContext || !navigator.storage?.getDirectory) {
                throw new Error("浏览器本地媒体边车需要 HTTPS 或 localhost，并要求支持 OPFS");
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
            const lyricPattern = new RegExp("^" + songId + "\\.lyric\\.[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
            const artworkPattern = new RegExp("^" + songId + "\\.cover\\.[A-Za-z0-9]{1,12}$");
            const transactionPattern = new RegExp(
                "^\\." + songId + "\\.(?:lyric|cover)\\..+\\.asset-(?:pending|backup)$"
            );
            let deleted = false;
            for await (const [name, handle] of directory.entries()) {
                if (
                    handle.kind !== "file" ||
                    (!lyricPattern.test(name) &&
                        !artworkPattern.test(name) &&
                        !transactionPattern.test(name))
                ) {
                    continue;
                }
                await directory.removeEntry(name);
                deleted = true;
            }
            onSuccess(deleted);
        })().catch(error => onError(error?.message || String(error)));
    }""",
)

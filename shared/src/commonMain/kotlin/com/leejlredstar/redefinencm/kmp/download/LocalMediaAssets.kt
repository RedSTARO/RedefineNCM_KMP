package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.data.api.ExternalHttpClient
import com.leejlredstar.redefinencm.kmp.data.api.secureNeteaseCdnUrl
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.lyric.LyricDocument
import com.leejlredstar.redefinencm.kmp.lyric.LyricQuery
import com.leejlredstar.redefinencm.kmp.lyric.LyricSource
import com.leejlredstar.redefinencm.kmp.lyric.backendLyricDocument
import com.leejlredstar.redefinencm.kmp.lyric.localTtmlDocument
import com.leejlredstar.redefinencm.kmp.util.LocalMediaAssetSnapshot
import com.leejlredstar.redefinencm.kmp.util.LocalMediaAssetStorage
import com.leejlredstar.redefinencm.kmp.util.LocalTextMediaAsset
import com.leejlredstar.redefinencm.kmp.util.LyricSidecar
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable

/**
 * Owns the durable lyric and artwork sidecars associated with downloaded audio.
 *
 * Persisted data is always keyed by song ID. Platform-specific runtime URIs (notably Web blob
 * URLs) are resolved only for the active consumer and are never written into the play queue.
 */
class LocalMediaAssets(
    private val externalHttpClient: ExternalHttpClient,
) {
    suspend fun saveLyrics(
        songId: Long,
        document: LyricDocument,
    ): LocalMediaAssetSnapshot {
        require(songId > 0L) { "songId must be positive" }
        val files = document.toOriginalLyricSidecars(songId)
        require(files.isNotEmpty()) { strings.lyricsNoOriginalContent }
        LocalMediaAssetStorage.replaceLyrics(songId, files)
        val snapshot = LocalMediaAssetStorage.inspect(songId)
        check(snapshot.lyricFormat != null && snapshot.lyricFileName != null) {
            strings.lyricsFileUnreadableAfterWrite
        }
        return snapshot
    }

    /**
     * Loads a local lyric only when its persisted source matches [source].
     *
     * Keeping the source boundary here preserves the user's TTML-only/backend-only privacy choice.
     */
    suspend fun loadLyrics(
        query: LyricQuery,
        source: LyricSource,
    ): LyricDocument? {
        val songId = query.songId
        val requestedFiles = when (source) {
            LyricSource.AMLL_TTML -> setOf(LyricSidecar.TTML.fileName(songId))
            LyricSource.NCM_BACKEND -> setOf(
                LyricSidecar.YRC.fileName(songId),
                LyricSidecar.LRC.fileName(songId),
                LyricSidecar.LINE_LRC.fileName(songId),
                LyricSidecar.TRANSLATION.fileName(songId),
                LyricSidecar.ROMANIZATION.fileName(songId),
            )
        }
        val files = LocalMediaAssetStorage.readLyrics(songId, requestedFiles)
            .associateBy(LocalTextMediaAsset::fileName)
        if (files.isEmpty()) return null

        fun content(sidecar: LyricSidecar): String = files[sidecar.fileName(songId)]?.content.orEmpty()

        return when (source) {
            LyricSource.AMLL_TTML -> {
                val ttml = content(LyricSidecar.TTML).takeIf(String::isNotBlank) ?: return null
                localTtmlDocument(ttml)
            }
            LyricSource.NCM_BACKEND -> {
                val yrc = content(LyricSidecar.YRC)
                val primaryLrc = content(LyricSidecar.LRC)
                val lineLrc = content(LyricSidecar.LINE_LRC)
                if (yrc.isBlank() && primaryLrc.isBlank()) return null
                backendLyricDocument(
                    query = query,
                    lrcText = lineLrc.ifBlank { primaryLrc },
                    yrcText = yrc,
                    translatedText = content(LyricSidecar.TRANSLATION),
                    romanText = content(LyricSidecar.ROMANIZATION),
                    endpoint = "local-sidecar",
                )
            }
        }
    }

    suspend fun saveArtwork(
        songId: Long,
        remoteUri: String,
    ): String? {
        require(songId > 0L) { "songId must be positive" }
        val uri = secureNeteaseCdnUrl(remoteUri.trim())
        if (uri.isEmpty()) return null

        // Streamed, not get(): Ktor's get() reads the whole body into memory before it returns,
        // so the size limit below would only have measured a body already downloaded.
        val (bytes, contentType) = externalHttpClient.client.prepareGet(uri).execute { response ->
            check(response.status.isSuccess()) {
                strings.coverDownloadHttpError(response.status.value)
            }
            val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            check(declaredLength == null || declaredLength in 1..MAX_ARTWORK_BYTES) {
                strings.coverTooLarge(MAX_ARTWORK_BYTES / (1024 * 1024))
            }
            response.readArtworkBytesAtMost(MAX_ARTWORK_BYTES.toInt()) to response.headers[HttpHeaders.ContentType]
        }
        check(bytes.isNotEmpty()) { strings.coverDownloadEmpty }
        check(bytes.size.toLong() <= MAX_ARTWORK_BYTES) {
            strings.coverTooLarge(MAX_ARTWORK_BYTES / (1024 * 1024))
        }

        val rawContentType = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        check(
            rawContentType.isBlank() ||
                rawContentType.startsWith("image/") ||
                rawContentType == "application/octet-stream",
        ) {
            strings.coverNotImage(rawContentType)
        }
        val format = detectArtworkFormat(bytes)
            ?: error(strings.coverFormatUnsupported)
        val fileName = LocalMediaAssetStorage.replaceArtwork(
            songId = songId,
            fileExtension = format.extension,
            // The response header can be stale or generic. The byte signature is the durable
            // source of truth for both the extension and the MediaStore/Blob MIME type.
            mimeType = format.mimeType,
            bytes = bytes,
        )
        val snapshot = LocalMediaAssetStorage.inspect(songId)
        check(snapshot.artworkFileName == fileName) { strings.coverFileUnreadableAfterWrite }
        return fileName
    }

    suspend fun inspect(songId: Long): LocalMediaAssetSnapshot =
        LocalMediaAssetStorage.inspect(songId)

    suspend fun inspectAll(songIds: Collection<Long>): Map<Long, LocalMediaAssetSnapshot> =
        LocalMediaAssetStorage.inspectAll(songIds)

    suspend fun resolveArtworkUri(songId: Long): String? =
        LocalMediaAssetStorage.resolveArtworkUri(songId)

    fun releaseArtworkUri(uri: String) {
        LocalMediaAssetStorage.releaseArtworkUri(uri)
    }

    suspend fun delete(songId: Long): Boolean =
        LocalMediaAssetStorage.deleteAssets(songId)
}

internal fun LyricDocument.toOriginalLyricSidecars(songId: Long): List<LocalTextMediaAsset> {
    fun asset(sidecar: LyricSidecar, content: String) = LocalTextMediaAsset(sidecar.fileName(songId), content)

    val primary = when {
        rawTtml.isNotBlank() -> asset(LyricSidecar.TTML, rawTtml)
        rawWordLyric.isNotBlank() -> asset(LyricSidecar.YRC, rawWordLyric)
        rawLineLyric.isNotBlank() -> asset(LyricSidecar.LRC, rawLineLyric)
        else -> return emptyList()
    }
    return buildList {
        add(primary)
        if (source == LyricSource.NCM_BACKEND) {
            if (rawWordLyric.isNotBlank() && rawLineLyric.isNotBlank()) {
                add(asset(LyricSidecar.LINE_LRC, rawLineLyric))
            }
            rawTranslatedLyric.takeIf(String::isNotBlank)?.let {
                add(asset(LyricSidecar.TRANSLATION, it))
            }
            rawRomanLyric.takeIf(String::isNotBlank)?.let {
                add(asset(LyricSidecar.ROMANIZATION, it))
            }
        }
    }
}

private data class ArtworkFormat(
    val extension: String,
    val mimeType: String,
)

private suspend fun HttpResponse.readArtworkBytesAtMost(maxBytes: Int): ByteArray {
    val channel = bodyAsChannel()
    if (headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { it > maxBytes } == true) {
        channel.cancel(IllegalStateException("Artwork response exceeded $maxBytes bytes"))
        error(strings.coverTooLarge(maxBytes / (1024 * 1024)))
    }
    val chunks = mutableListOf<ByteArray>()
    var total = 0
    while (true) {
        val buffer = ByteArray(minOf(ARTWORK_READ_BUFFER_BYTES, maxBytes - total + 1))
        val read = channel.readAvailable(buffer, 0, buffer.size)
        if (read < 0) {
            return ByteArray(total).also { result ->
                var offset = 0
                chunks.forEach { chunk ->
                    chunk.copyInto(result, destinationOffset = offset)
                    offset += chunk.size
                }
            }
        }
        if (read == 0) continue
        total += read
        if (total > maxBytes) break
        chunks += if (read == buffer.size) buffer else buffer.copyOf(read)
    }
    channel.cancel(IllegalStateException("Artwork response exceeded $maxBytes bytes"))
    error(strings.coverTooLarge(maxBytes / (1024 * 1024)))
}

private fun detectArtworkFormat(bytes: ByteArray): ArtworkFormat? = when {
    bytes.startsWith(0xff, 0xd8, 0xff) -> ArtworkFormat("jpg", "image/jpeg")
    bytes.startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) ->
        ArtworkFormat("png", "image/png")
    bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a") ->
        ArtworkFormat("gif", "image/gif")
    bytes.startsWithAscii("RIFF") && bytes.asciiAt(8, 4) == "WEBP" ->
        ArtworkFormat("webp", "image/webp")
    bytes.startsWithAscii("BM") -> ArtworkFormat("bmp", "image/bmp")
    bytes.asciiAt(4, 4) == "ftyp" -> when (bytes.asciiAt(8, 4)) {
        "avif", "avis" -> ArtworkFormat("avif", "image/avif")
        "heic", "heix", "hevc", "hevx" -> ArtworkFormat("heic", "image/heic")
        "mif1", "msf1" -> ArtworkFormat("heif", "image/heif")
        else -> null
    }
    else -> null
}

private fun ByteArray.startsWith(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { index ->
        this[index].toInt() and 0xff == expected[index]
    }

private fun ByteArray.startsWithAscii(expected: String): Boolean =
    asciiAt(0, expected.length) == expected

private fun ByteArray.asciiAt(offset: Int, length: Int): String? {
    if (offset < 0 || length < 0 || offset + length > size) return null
    return buildString(length) {
        repeat(length) { index ->
            append((this@asciiAt[offset + index].toInt() and 0xff).toChar())
        }
    }
}

private const val MAX_ARTWORK_BYTES = 16L * 1024L * 1024L
private const val ARTWORK_READ_BUFFER_BYTES = 64 * 1024

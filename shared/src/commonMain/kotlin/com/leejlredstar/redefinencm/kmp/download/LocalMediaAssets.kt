package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.data.api.ExternalHttpClient
import com.leejlredstar.redefinencm.kmp.lyric.LyricDocument
import com.leejlredstar.redefinencm.kmp.lyric.LyricQuery
import com.leejlredstar.redefinencm.kmp.lyric.LyricSource
import com.leejlredstar.redefinencm.kmp.lyric.backendLyricDocument
import com.leejlredstar.redefinencm.kmp.lyric.localTtmlDocument
import com.leejlredstar.redefinencm.kmp.util.LocalLyricFormat
import com.leejlredstar.redefinencm.kmp.util.LocalMediaAssetSnapshot
import com.leejlredstar.redefinencm.kmp.util.LocalMediaAssetStorage
import com.leejlredstar.redefinencm.kmp.util.LocalTextMediaAsset
import io.ktor.client.request.get
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
        require(files.isNotEmpty()) { "歌词源没有可持久化的原始内容" }
        LocalMediaAssetStorage.replaceLyrics(songId, files)
        val snapshot = LocalMediaAssetStorage.inspect(songId)
        check(snapshot.lyricFormat != null && snapshot.lyricFileName != null) {
            "歌词文件写入后无法回读"
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
        val requestedFiles = when (source) {
            LyricSource.AMLL_TTML -> setOf("${query.songId}.lyric.ttml")
            LyricSource.NCM_BACKEND -> setOf(
                "${query.songId}.lyric.yrc",
                "${query.songId}.lyric.lrc",
                "${query.songId}.lyric.line.lrc",
                "${query.songId}.lyric.translation.lrc",
                "${query.songId}.lyric.romanization.lrc",
            )
        }
        val files = LocalMediaAssetStorage.readLyrics(query.songId, requestedFiles)
            .associateBy(LocalTextMediaAsset::fileName)
        if (files.isEmpty()) return null

        return when (source) {
            LyricSource.AMLL_TTML -> {
                val ttml = files["${query.songId}.lyric.ttml"]?.content
                    ?.takeIf(String::isNotBlank)
                    ?: return null
                localTtmlDocument(ttml)
            }
            LyricSource.NCM_BACKEND -> {
                val yrc = files["${query.songId}.lyric.yrc"]?.content.orEmpty()
                val primaryLrc = files["${query.songId}.lyric.lrc"]?.content.orEmpty()
                val lineLrc = files["${query.songId}.lyric.line.lrc"]?.content.orEmpty()
                if (yrc.isBlank() && primaryLrc.isBlank()) return null
                backendLyricDocument(
                    query = query,
                    lrcText = lineLrc.ifBlank { primaryLrc },
                    yrcText = yrc,
                    translatedText = files["${query.songId}.lyric.translation.lrc"]
                        ?.content
                        .orEmpty(),
                    romanText = files["${query.songId}.lyric.romanization.lrc"]
                        ?.content
                        .orEmpty(),
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
        val uri = remoteUri.trim()
        if (uri.isEmpty()) return null

        val response = externalHttpClient.client.get(uri)
        check(response.status.isSuccess()) {
            "封面下载失败：HTTP ${response.status.value}"
        }
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        check(declaredLength == null || declaredLength in 1..MAX_ARTWORK_BYTES) {
            "封面文件超过 ${MAX_ARTWORK_BYTES / (1024 * 1024)} MiB 限制"
        }
        val bytes = response.readArtworkBytesAtMost(MAX_ARTWORK_BYTES.toInt())
        check(bytes.isNotEmpty()) { "封面响应为空" }
        check(bytes.size.toLong() <= MAX_ARTWORK_BYTES) {
            "封面文件超过 ${MAX_ARTWORK_BYTES / (1024 * 1024)} MiB 限制"
        }

        val rawContentType = response.headers[HttpHeaders.ContentType]
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        check(
            rawContentType.isBlank() ||
                rawContentType.startsWith("image/") ||
                rawContentType == "application/octet-stream",
        ) {
            "封面响应不是图片：$rawContentType"
        }
        val format = detectArtworkFormat(bytes)
            ?: error("封面文件签名不受支持")
        val fileName = LocalMediaAssetStorage.replaceArtwork(
            songId = songId,
            fileExtension = format.extension,
            // The response header can be stale or generic. The byte signature is the durable
            // source of truth for both the extension and the MediaStore/Blob MIME type.
            mimeType = format.mimeType,
            bytes = bytes,
        )
        val snapshot = LocalMediaAssetStorage.inspect(songId)
        check(snapshot.artworkFileName == fileName) { "封面文件写入后无法回读" }
        return fileName
    }

    suspend fun inspect(songId: Long): LocalMediaAssetSnapshot =
        LocalMediaAssetStorage.inspect(songId)

    suspend fun resolveArtworkUri(songId: Long): String? =
        LocalMediaAssetStorage.resolveArtworkUri(songId)

    fun releaseArtworkUri(uri: String) {
        LocalMediaAssetStorage.releaseArtworkUri(uri)
    }

    suspend fun delete(songId: Long): Boolean =
        LocalMediaAssetStorage.deleteAssets(songId)
}

internal fun LyricDocument.toOriginalLyricSidecars(songId: Long): List<LocalTextMediaAsset> {
    val primary = when {
        rawTtml.isNotBlank() -> LocalTextMediaAsset("$songId.lyric.ttml", rawTtml)
        rawWordLyric.isNotBlank() -> LocalTextMediaAsset("$songId.lyric.yrc", rawWordLyric)
        rawLineLyric.isNotBlank() -> LocalTextMediaAsset("$songId.lyric.lrc", rawLineLyric)
        else -> return emptyList()
    }
    return buildList {
        add(primary)
        if (source == LyricSource.NCM_BACKEND) {
            if (rawWordLyric.isNotBlank() && rawLineLyric.isNotBlank()) {
                add(LocalTextMediaAsset("$songId.lyric.line.lrc", rawLineLyric))
            }
            rawTranslatedLyric.takeIf(String::isNotBlank)?.let {
                add(LocalTextMediaAsset("$songId.lyric.translation.lrc", it))
            }
            rawRomanLyric.takeIf(String::isNotBlank)?.let {
                add(LocalTextMediaAsset("$songId.lyric.romanization.lrc", it))
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
        error("封面文件超过 ${maxBytes / (1024 * 1024)} MiB 限制")
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
    error("封面文件超过 ${maxBytes / (1024 * 1024)} MiB 限制")
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

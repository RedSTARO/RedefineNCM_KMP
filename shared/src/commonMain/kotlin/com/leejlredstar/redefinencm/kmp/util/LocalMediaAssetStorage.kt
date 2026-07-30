package com.leejlredstar.redefinencm.kmp.util

enum class LocalLyricFormat {
    TTML,
    YRC,
    LRC,
}

data class LocalTextMediaAsset(
    val fileName: String,
    val content: String,
)

data class LocalMediaAssetSnapshot(
    val lyricFormat: LocalLyricFormat? = null,
    val lyricFileName: String? = null,
    val artworkFileName: String? = null,
)

/**
 * Stores durable lyrics and artwork associated with downloaded audio.
 *
 * Audio uses `<songId>.<extension>`, while assets deliberately use an extra name segment:
 * `<songId>.lyric.*` and `<songId>.cover.*`. Android keeps artwork in app-specific storage so
 * image-aware gallery scanners cannot publish it as a user photo; other targets keep sidecars
 * beside the audio where their platform storage contract permits it.
 */
expect object LocalMediaAssetStorage {
    suspend fun replaceLyrics(songId: Long, files: List<LocalTextMediaAsset>)

    suspend fun readLyrics(
        songId: Long,
        fileNames: Set<String>,
    ): List<LocalTextMediaAsset>

    suspend fun replaceArtwork(
        songId: Long,
        fileExtension: String,
        mimeType: String,
        bytes: ByteArray,
    ): String

    suspend fun inspect(songId: Long): LocalMediaAssetSnapshot

    suspend fun resolveArtworkUri(songId: Long): String?

    fun releaseArtworkUri(uri: String)

    suspend fun deleteAssets(songId: Long): Boolean
}

internal fun requireLocalMediaSongId(songId: Long) {
    require(songId > 0L) { "songId must be positive" }
}

internal fun validateLocalLyricAssets(
    songId: Long,
    files: List<LocalTextMediaAsset>,
): List<LocalTextMediaAsset> {
    requireLocalMediaSongId(songId)
    require(files.map { it.fileName }.distinct().size == files.size) {
        "Local lyric sidecar file names must be unique"
    }
    files.forEach { file ->
        require(isLocalLyricSidecarFileName(songId, file.fileName)) {
            "Invalid local lyric sidecar file name: ${file.fileName}"
        }
    }
    return files.sortedBy { it.fileName }
}

internal fun validateLocalLyricFileNames(
    songId: Long,
    fileNames: Set<String>,
): Set<String> {
    requireLocalMediaSongId(songId)
    fileNames.forEach { fileName ->
        require(isLocalLyricSidecarFileName(songId, fileName)) {
            "Invalid local lyric sidecar file name: $fileName"
        }
    }
    return fileNames
}

internal fun validateLocalArtworkExtension(
    fileExtension: String,
    mimeType: String,
    bytes: ByteArray,
): String {
    val normalized = fileExtension.trim().lowercase().removePrefix(".")
    require(normalized.matches(Regex("[a-z0-9]{1,12}"))) {
        "Invalid local artwork extension"
    }
    val normalizedMimeType = mimeType.trim().lowercase()
    require(normalizedMimeType.matches(Regex("image/[a-z0-9][a-z0-9.+-]*"))) {
        "Local artwork MIME type must be an image"
    }
    require(bytes.isNotEmpty()) { "Local artwork bytes must not be empty" }
    return normalized
}

internal fun isLocalLyricSidecarFileName(songId: Long, fileName: String): Boolean {
    if (songId <= 0L || fileName != fileName.substringAfterLast('/').substringAfterLast('\\')) {
        return false
    }
    val suffix = fileName.removePrefix("$songId.lyric.")
    return suffix != fileName && suffix in LOCAL_LYRIC_SUFFIXES
}

internal fun isLocalArtworkSidecarFileName(songId: Long, fileName: String): Boolean {
    if (songId <= 0L || fileName != fileName.substringAfterLast('/').substringAfterLast('\\')) {
        return false
    }
    val extension = fileName.removePrefix("$songId.cover.")
    return extension != fileName &&
        extension.length in 1..12 &&
        extension.matches(Regex("[A-Za-z0-9]+"))
}

internal fun isLocalMediaAssetTransactionFileName(songId: Long, fileName: String): Boolean {
    if (songId <= 0L || fileName != fileName.substringAfterLast('/').substringAfterLast('\\')) {
        return false
    }
    val lyricPrefix = ".$songId.lyric."
    val artworkPrefix = ".$songId.cover."
    return (fileName.startsWith(lyricPrefix) || fileName.startsWith(artworkPrefix)) &&
        fileName.length <= 256 &&
        (fileName.endsWith(".asset-pending") || fileName.endsWith(".asset-backup"))
}

internal fun localArtworkFileName(songId: Long, extension: String): String =
    "$songId.cover.$extension"

internal fun localMediaAssetSnapshot(
    songId: Long,
    fileNames: Iterable<String>,
): LocalMediaAssetSnapshot {
    requireLocalMediaSongId(songId)
    val names = fileNames.toSet()
    val primaryLyric = listOf(
        LocalLyricFormat.TTML to "$songId.lyric.ttml",
        LocalLyricFormat.YRC to "$songId.lyric.yrc",
        LocalLyricFormat.LRC to "$songId.lyric.lrc",
    ).firstOrNull { (_, name) -> name in names }
    val artwork = names
        .asSequence()
        .filter { isLocalArtworkSidecarFileName(songId, it) }
        .sorted()
        .firstOrNull()
    return LocalMediaAssetSnapshot(
        lyricFormat = primaryLyric?.first,
        lyricFileName = primaryLyric?.second,
        artworkFileName = artwork,
    )
}

private val LOCAL_LYRIC_SUFFIXES = setOf(
    "ttml",
    "yrc",
    "lrc",
    "line.lrc",
    "translation.lrc",
    "romanization.lrc",
)

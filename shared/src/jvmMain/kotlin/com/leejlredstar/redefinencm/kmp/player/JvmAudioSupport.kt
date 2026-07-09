package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import java.net.URI

private val jvmPlayableAudioExtensions = setOf("mp3", "wav", "aif", "aiff", "au")

internal fun jvmPlaybackQualityLevel(requested: SoundQuality): String {
    return when (requested) {
        SoundQuality.STANDARD,
        SoundQuality.HIGHER,
        SoundQuality.EXHIGH -> requested.name.lowercase()
        // Java Sound + mp3spi does not decode FLAC/M4A. Ask NCM for the highest MP3 level.
        SoundQuality.LOSSLESS,
        SoundQuality.HIRES,
        SoundQuality.JYEFFECT,
        SoundQuality.SKY,
        SoundQuality.DOLBY,
        SoundQuality.JYMASTER -> SoundQuality.EXHIGH.name.lowercase()
    }
}

internal fun isJvmPlayableAudioUri(uri: String): Boolean {
    val path = runCatching { URI.create(uri).path }
        .getOrNull()
        ?: uri.substringBefore('?').substringBefore('#')
    val extension = path.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
    return extension in jvmPlayableAudioExtensions
}

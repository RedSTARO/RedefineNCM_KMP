package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import java.net.URI

// What the two providers serve, plus the uncompressed containers Java Sound accepts on its own.
// FFmpeg opens far more than this. The list only keeps a stray non-audio file out of the queue;
// it does not describe the decoder's reach.
private val jvmPlayableAudioExtensions =
    setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "aif", "aiff", "au")

/**
 * The quality level to request, unchanged from what the user chose.
 *
 * FFmpeg decodes the whole ladder, so the tier the user picked is the tier that gets requested,
 * and a downloaded FLAC passes [isJvmPlayableAudioUri] instead of being skipped for the CDN.
 * Collapsing lossless tiers to `exhigh`, as an MP3-only decoder like Java Sound's requires,
 * would make 超清母带 silently play a 320k MP3.
 */
internal fun jvmPlaybackQualityLevel(requested: SoundQuality): String = requested.name.lowercase()

internal fun isJvmPlayableAudioUri(uri: String): Boolean {
    val path = runCatching { URI.create(uri).path }
        .getOrNull()
        ?: uri.substringBefore('?').substringBefore('#')
    val extension = path.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
    return extension in jvmPlayableAudioExtensions
}

package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import java.net.URI

// What the two providers actually serve, plus the uncompressed containers Java Sound used to
// accept on its own. FFmpeg opens far more than this; the point of the list is to keep a stray
// non-audio file out of the queue, not to describe the decoder's reach.
private val jvmPlayableAudioExtensions =
    setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "aif", "aiff", "au")

/**
 * The quality level to request, unchanged from what the user chose.
 *
 * This used to collapse every lossless tier to `exhigh`, because Java Sound could only decode
 * MP3: asking for 超清母带 silently played a 320k MP3, and a downloaded FLAC was skipped by
 * [isJvmPlayableAudioUri] on the way to the CDN. FFmpeg decodes the whole ladder, so the tier
 * the user picked is the tier that gets requested.
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

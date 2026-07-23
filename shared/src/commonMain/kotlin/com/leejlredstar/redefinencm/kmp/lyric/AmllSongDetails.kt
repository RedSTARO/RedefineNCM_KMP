package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import kotlinx.serialization.Serializable

/**
 * Stable display-metadata payload consumed by the bundled AMLL details surface.
 *
 * Playback-only fields such as placeholderUri and sourceId deliberately stay outside this bridge.
 */
@Serializable
internal data class AmllSongDetails(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val albumTitle: String = "",
    val artworkUri: String = "",
    val durationMs: Long = 0,
)

internal fun MediaInfo?.toAmllSongDetails(): AmllSongDetails =
    this?.let { media ->
        AmllSongDetails(
            mediaId = media.id,
            title = media.title,
            artist = media.artist,
            albumTitle = media.albumTitle,
            artworkUri = media.artworkUri,
            durationMs = media.duration.coerceAtLeast(0),
        )
    } ?: AmllSongDetails()

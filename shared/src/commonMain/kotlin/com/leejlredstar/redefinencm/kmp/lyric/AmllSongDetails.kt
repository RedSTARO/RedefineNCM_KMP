package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import kotlinx.serialization.Serializable

/** Stable, deliberately small metadata payload consumed by the bundled AMLL details surface. */
@Serializable
internal data class AmllSongDetails(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val albumTitle: String = "",
    val artworkUri: String = "",
)

internal fun MediaInfo?.toAmllSongDetails(): AmllSongDetails =
    this?.let { media ->
        AmllSongDetails(
            mediaId = media.id,
            title = media.title,
            artist = media.artist,
            albumTitle = media.albumTitle,
            artworkUri = media.artworkUri,
        )
    } ?: AmllSongDetails()

package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderTrack
import com.leejlredstar.redefinencm.kmp.data.provider.mediaId
import com.leejlredstar.redefinencm.kmp.player.MediaInfo

internal fun SongDetailSongs.toPlayerMediaInfo(sourceId: String = ""): MediaInfo = MediaInfo(
    id = id.toString(),
    title = name,
    artist = ar.joinToString(", ") { it.name },
    albumTitle = al.name,
    artworkUri = al.picUrl,
    placeholderUri = "redefinencm://playbackPlaceHolder?id=$id",
    duration = dt,
    sourceId = sourceId,
)

/**
 * Maps a provider-neutral track to the player's [MediaInfo].
 *
 * The player never learns what a provider is: it carries the id opaquely and hands it back to
 * [com.leejlredstar.redefinencm.kmp.player.StreamUrlResolver] at play time, which is where the
 * provider dispatch happens.
 */
internal fun ProviderTrack.toPlayerMediaInfo(sourceId: String = ""): MediaInfo {
    val mediaId = id.mediaId
    return MediaInfo(
        id = mediaId,
        title = title,
        artist = artistLine,
        albumTitle = album?.name.orEmpty(),
        artworkUri = artworkUrl,
        placeholderUri = "redefinencm://playbackPlaceHolder?id=$mediaId",
        duration = durationMillis,
        sourceId = sourceId,
    )
}

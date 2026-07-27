package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import kotlinx.serialization.Serializable

@Serializable
data class AmllSongDetails(
    val mediaId: String,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val durationMs: Long,
    val artworkUri: String,
)

fun MediaInfo.toAmllSongDetails(): AmllSongDetails = AmllSongDetails(
    mediaId = id,
    title = title,
    artist = artist,
    albumTitle = albumTitle,
    durationMs = duration,
    artworkUri = artworkUri,
)

package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
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

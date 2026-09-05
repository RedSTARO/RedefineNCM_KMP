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

/**
 * Low-frequency playback state for the Legacy page's in-page transport console.
 *
 * Position is deliberately absent: the page already receives it through `AmllBridge.setTime`,
 * which the host coalesces with `evalLatest`. Pushing it again here would send a JSON payload
 * through the queued `eval` path at position-update rate.
 *
 * [mediaId] scopes the payload the same way every other `AmllPage.*` push is scoped, so a late
 * favorite check belonging to the previous track cannot paint the new one.
 */
@Serializable
data class AmllPlaybackState(
    val mediaId: String,
    val hasMedia: Boolean,
    val isPlaying: Boolean,
    val durationMs: Long,
    val isFavorite: Boolean,
    val shuffleEnabled: Boolean,
)

fun MediaInfo.toAmllSongDetails(): AmllSongDetails = AmllSongDetails(
    mediaId = id,
    title = title,
    artist = artist,
    albumTitle = albumTitle,
    durationMs = duration,
    artworkUri = artworkUri,
)

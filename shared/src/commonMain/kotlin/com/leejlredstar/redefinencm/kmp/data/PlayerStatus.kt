package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.player.PlayerQueueSnapshot
import kotlinx.serialization.Serializable

/**
 * 播放状态持久化模型（对应原版 Room 的 PlayerStatusEntity + MediaItemData）。
 * 只存元数据与占位 URI 所需的 id；真实流 URL 永不持久化（播放时重新解析）。
 */
@Serializable
data class PersistedMediaItem(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val albumTitle: String = "",
    val artworkUri: String = "",
    val duration: Long = 0,
    val sourceId: String = "",
)

@Serializable
data class PlayerStatus(
    val playlist: List<PersistedMediaItem> = emptyList(),
    val index: Int = 0,
    val position: Long = 0,
    val isPlaying: Boolean = false,
    val isShuffling: Boolean = false,
    /** Null in legacy saves whose playlist is already in visible play order. */
    val playOrder: List<Int>? = null,
)

/** Persist original item order and its permutation together; duplicate songs keep their slots. */
internal fun PlayerQueueSnapshot.toPlayerStatus(position: Long, isPlaying: Boolean): PlayerStatus =
    PlayerStatus(
        playlist = originalItems.map {
            PersistedMediaItem(it.id, it.title, it.artist, it.albumTitle, it.artworkUri, it.duration, it.sourceId)
        },
        index = playOrder.getOrNull(currentIndex)?.coerceAtLeast(0) ?: 0,
        position = position.coerceAtLeast(0),
        isPlaying = isPlaying,
        isShuffling = shuffleEnabled,
        playOrder = playOrder.takeIf { shuffleEnabled },
    )

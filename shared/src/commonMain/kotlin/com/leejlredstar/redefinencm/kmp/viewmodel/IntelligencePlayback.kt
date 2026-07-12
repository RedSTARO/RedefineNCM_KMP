package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import kotlin.random.Random

internal fun selectIntelligenceSeed(
    ids: Iterable<Long>,
    indexSelector: (candidateCount: Int) -> Int = { candidateCount ->
        Random.nextInt(candidateCount)
    },
): Long? {
    val candidates = ids.asSequence()
        .filter { it > 0L }
        .distinct()
        .toList()
    if (candidates.isEmpty()) return null

    val selectedIndex = indexSelector(candidates.size)
    require(selectedIndex in candidates.indices) {
        "Intelligence seed index $selectedIndex is outside 0..${candidates.lastIndex}"
    }
    return candidates[selectedIndex]
}

internal fun buildIntelligenceQueue(
    songInfos: Iterable<SongDetailSongs?>,
    playlistId: Long,
): List<MediaInfo> {
    require(playlistId > 0L) { "Intelligence playlist ID must be positive" }
    val seenIds = mutableSetOf<Long>()
    return buildList {
        songInfos.forEach { song ->
            if (song == null || song.id <= 0L || !seenIds.add(song.id)) return@forEach
            add(song.toIntelligenceMediaInfo(playlistId))
        }
    }
}

private fun SongDetailSongs.toIntelligenceMediaInfo(playlistId: Long): MediaInfo = MediaInfo(
    id = id.toString(),
    title = name,
    artist = ar.joinToString(", ") { it.name },
    albumTitle = al.name,
    artworkUri = al.picUrl,
    placeholderUri = "redefinencm://playbackPlaceHolder?id=$id",
    duration = dt,
    sourceId = playlistId.toString(),
)

internal fun replaceQueueWithIntelligenceList(
    player: PlatformPlayer,
    queue: List<MediaInfo>,
) {
    require(queue.isNotEmpty()) { "Intelligence queue must not be empty" }
    player.setShuffleEnabled(false)
    player.setQueue(queue, startIndex = 0)
    player.play()
}

package com.leejlredstar.redefinencm.kmp.data.provider

/**
 * One row of merged search results: a track, and the same song from the other providers.
 *
 * [track] is what a tap plays; [alternates] can be played instead from the row's menu.
 */
data class MergedSearchEntry(
    val track: ProviderTrack,
    val alternates: List<ProviderTrack> = emptyList(),
) {
    /** Every provider the row stands for, the played one first. */
    val providers: List<MusicProviderId> get() = listOf(track.provider) + alternates.map { it.provider }
}

/**
 * Folds the same song from different providers into one row, keeping the order of first
 * appearance.
 *
 * The match is strict because a wrong merge hides a track: the title and the first artist must
 * be equal once case, spaces and punctuation are ignored, and both durations must be known and
 * within [DurationToleranceMillis]. "晴天" and "晴天 (Live)" stay apart. Two tracks of one
 * provider are never merged, since that provider listed them separately for a reason. The rows
 * stay labelled with every provider they stand for, and the setting that turns merging off shows
 * them one by one again.
 */
fun List<ProviderTrack>.mergeSameSongs(): List<MergedSearchEntry> {
    val entries = ArrayList<MergedSearchEntry>(size)
    val rowsByKey = HashMap<Pair<String, String>, MutableList<Int>>()
    for (track in this) {
        val key = track.sameSongKey()
        // Each bucket keeps the first-appearance order of its primary rows.
        val matchingRows = key?.let { rowsByKey[it] }
        val matchingIndex = matchingRows?.firstOrNull { index ->
            val entry = entries[index]
            entry.track.provider != track.provider &&
                entry.alternates.none { it.provider == track.provider } &&
                durationsAgree(entry.track.durationMillis, track.durationMillis)
        }
        if (matchingIndex == null) {
            if (key != null) rowsByKey.getOrPut(key) { mutableListOf() }.add(entries.size)
            entries += MergedSearchEntry(track)
        } else {
            val entry = entries[matchingIndex]
            entries[matchingIndex] = entry.copy(alternates = entry.alternates + track)
        }
    }
    return entries
}

/**
 * Whether [other] is this song from another provider, by the same strict rule the merged search
 * rows use. Switching source (换源) relies on it, so a wrong match plays a different recording.
 */
fun ProviderTrack.isSameSongAs(other: ProviderTrack): Boolean {
    if (provider == other.provider) return false
    val key = sameSongKey() ?: return false
    return other.sameSongKey() == key && durationsAgree(durationMillis, other.durationMillis)
}

private fun ProviderTrack.sameSongKey(): Pair<String, String>? {
    val title = title.comparable()
    val artist = artists.firstOrNull()?.name?.comparable().orEmpty()
    if (title.isEmpty() || artist.isEmpty()) return null
    return title to artist
}

private fun String.comparable(): String = lowercase().filter { it.isLetterOrDigit() }

private fun durationsAgree(a: Long, b: Long): Boolean =
    a > 0 && b > 0 && kotlin.math.abs(a - b) <= DurationToleranceMillis

/** Services round a track's length differently; a remaster or a live cut differs by far more. */
internal const val DurationToleranceMillis = 3_000L

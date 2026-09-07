package com.leejlredstar.redefinencm.kmp.ui.component

/**
 * The transport clocks. Both read the same decomposition of a millisecond position; they differ
 * only in whether an hour field appears, so keep them here together rather than letting each
 * surface grow its own copy.
 */

/**
 * `m:ss`, with the minute field running past 60 (`61:01`).
 *
 * The compact clock for surfaces that show `position / duration` on one line, where a third field
 * would shift the layout for the few tracks long enough to need it.
 */
internal fun formatPlaybackDuration(millis: Long): String =
    formatClock(millis, rollOverToHours = false)

/**
 * `m:ss`, or `h:mm:ss` past an hour.
 *
 * The full clock for surfaces with room for the hour field: the now-playing screen and the song
 * wiki, where durations come from metadata and can be arbitrarily long.
 */
internal fun formatPlaybackClock(millis: Long): String =
    formatClock(millis, rollOverToHours = true)

/**
 * Negative positions clamp to zero: some backends report one between selecting and opening a
 * track. Milliseconds truncate rather than round, so a clock never shows a second the position
 * has not reached.
 */
private fun formatClock(millis: Long, rollOverToHours: Boolean): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000L
    val seconds = totalSeconds % 60L
    val paddedSeconds = seconds.toString().padStart(2, '0')
    val hours = totalSeconds / 3_600L
    return if (rollOverToHours && hours > 0L) {
        val minutes = (totalSeconds % 3_600L) / 60L
        "$hours:${minutes.toString().padStart(2, '0')}:$paddedSeconds"
    } else {
        "${totalSeconds / 60L}:$paddedSeconds"
    }
}

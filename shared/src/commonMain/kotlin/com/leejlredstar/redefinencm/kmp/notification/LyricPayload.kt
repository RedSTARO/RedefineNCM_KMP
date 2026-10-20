package com.leejlredstar.redefinencm.kmp.notification

/**
 * What a lyric surface was handed, in the form every surface wants it.
 *
 * [LyricSurface.updateLyric] takes eight nullable, untrimmed arguments straight from the view
 * model. Every target needs the same non-null, trimmed shape, so it is built once here rather
 * than in a private data class per target. Per-target copies drift apart: one skips trimming and
 * lets a negative position through to the DOM, another has no fallback to the title when the
 * lyric line is blank.
 */
data class LyricPayload(
    val title: String,
    val artist: String,
    val currentLyric: String,
    val nextLyric: String,
    val artworkUri: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
) {
    /**
     * The one line to show where a surface has room for exactly one: the lyric, or the title
     * when the track has no lyric at this moment.
     */
    val headline: String get() = currentLyric.ifEmpty { title }
}

/**
 * Trims every text field and clamps the position.
 *
 * A negative position arrives between selecting a track and opening it; a surface that renders
 * it draws a backwards progress bar. Duration is left alone: -1 is how the contract says
 * "unknown", and clamping it would claim a zero-length track.
 */
internal fun lyricPayloadOf(
    title: String?,
    artist: String?,
    currentLyric: String?,
    nextLyric: String?,
    artworkUri: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
): LyricPayload = LyricPayload(
    title = title?.trim().orEmpty(),
    artist = artist?.trim().orEmpty(),
    currentLyric = currentLyric?.trim().orEmpty(),
    nextLyric = nextLyric?.trim().orEmpty(),
    artworkUri = artworkUri?.trim().orEmpty(),
    isPlaying = isPlaying,
    positionMs = positionMs.coerceAtLeast(0L),
    durationMs = durationMs,
)

/**
 * The payload for a surface whose whole point is one line: a notification whose title is the
 * lyric, or a Live Activity's leading text.
 *
 * [LyricPayload.currentLyric] is replaced by [LyricPayload.headline], so the surface has nothing
 * left to decide and two updates that resolve to the same line compare equal. Returns null when
 * there is no line to show, which is the signal to leave the surface as it is rather than blank
 * it: a track between lyric lines still has a notification worth keeping on screen.
 *
 * The desktop floating window does not use this. It draws the title, the artist and the lyric as
 * separate lines, so substituting the title into the lyric line would show it twice.
 */
internal fun LyricPayload.asSingleLineSurface(): LyricPayload? =
    headline.takeIf { it.isNotEmpty() }?.let { copy(currentLyric = it) }

/** While playing, a surface that moves its own progress bar still gets an update this often. */
internal const val TIMED_SURFACE_KEEP_ALIVE_MS = 60_000L

/** How far the position may be from where the bar has moved before it is sent again. */
internal const val TIMED_SURFACE_DRIFT_MS = 2_000L

/**
 * Whether [next] adds nothing for a surface that moves its own progress bar from the last update
 * it was sent, [last], [elapsedMs] ago: the same track, line and play state, and a position where
 * the bar already is. A seek, a new line, a pause or another track is sent, and so is one update
 * per [TIMED_SURFACE_KEEP_ALIVE_MS] while playing, which tells the surface the app still runs.
 *
 * The iOS Live Activity is that surface. Sending every whole-second position re-rendered its
 * widget once a second for the whole time the music played.
 */
internal fun isRedundantTimedUpdate(last: LyricPayload, next: LyricPayload, elapsedMs: Long): Boolean {
    if (last.copy(positionMs = 0L) != next.copy(positionMs = 0L)) return false
    if (!next.isPlaying) return next.positionMs == last.positionMs
    if (elapsedMs >= TIMED_SURFACE_KEEP_ALIVE_MS) return false
    val expectedMs = last.positionMs + elapsedMs
    return next.positionMs in (expectedMs - TIMED_SURFACE_DRIFT_MS)..(expectedMs + TIMED_SURFACE_DRIFT_MS)
}

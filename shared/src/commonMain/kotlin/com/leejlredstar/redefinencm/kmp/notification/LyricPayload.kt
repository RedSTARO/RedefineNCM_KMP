package com.leejlredstar.redefinencm.kmp.notification

/**
 * What a lyric surface was handed, in the form every surface actually wants it.
 *
 * [LyricNotificationController.updateLyric] takes eight nullable, untrimmed arguments straight
 * from the view model. Each of the four targets turned them into the same non-null trimmed shape
 * before doing anything with them, and Android and iOS had grown a private data class for it that
 * was identical field for field. The copies had drifted: Web did not trim at all and let a
 * negative position through to the DOM, and only two of the four fell back to the title when the
 * lyric line was blank.
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
 * The payload for a surface whose whole point is one line — a notification whose title is the
 * lyric, a Live Activity's leading text.
 *
 * [LyricPayload.currentLyric] is replaced by [LyricPayload.headline], so the surface has nothing
 * left to decide and two updates that resolve to the same line compare equal. Returns null when
 * there is no line to show, which is the signal to leave the surface as it is rather than blank
 * it: a track between lyric lines still has a notification worth keeping on screen.
 *
 * The desktop floating window deliberately does not use this. It draws the title, the artist and
 * the lyric as separate lines, so substituting the title into the lyric line would show it twice.
 */
internal fun LyricPayload.asSingleLineSurface(): LyricPayload? =
    headline.takeIf { it.isNotEmpty() }?.let { copy(currentLyric = it) }

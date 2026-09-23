package com.leejlredstar.redefinencm.kmp.transition

/**
 * How one song hands over to the next, as Settings persists it.
 *
 * [CROSSFADE] is Apple Music's older "Crossfade": a fixed-length equal-power blend. [SMART] is
 * the AutoMix-shaped one: each track's head and tail are analysed, the blend is placed on the
 * beat grid, and when the tempi are close the outgoing track is time-stretched onto the incoming
 * one's tempo so their beats land together. [SMART] degrades to a trimmed crossfade whenever the
 * analysis, the accelerator or the backend's tempo control is missing; it never degrades to a
 * cut in the middle of a phrase.
 */
enum class SongTransitionMode(val wireValue: String) {
    OFF("off"),
    CROSSFADE("crossfade"),
    SMART("smart"),
    ;

    companion object {
        /** Off: a transition changes what the listener hears, so it is opted into, not imposed. */
        val DEFAULT: SongTransitionMode = OFF

        fun fromWireValue(value: String): SongTransitionMode =
            entries.firstOrNull { it.wireValue == value } ?: DEFAULT

        fun fromWireValueOrNull(value: String): SongTransitionMode? =
            entries.firstOrNull { it.wireValue == value }
    }
}

/** Crossfade lengths the Settings slider offers, the range Apple Music's own uses. */
const val MIN_CROSSFADE_SECONDS: Long = 1L
const val MAX_CROSSFADE_SECONDS: Long = 12L
const val DEFAULT_CROSSFADE_SECONDS: Long = 6L

fun normalizeCrossfadeSeconds(seconds: Long): Long =
    seconds.coerceIn(MIN_CROSSFADE_SECONDS, MAX_CROSSFADE_SECONDS)

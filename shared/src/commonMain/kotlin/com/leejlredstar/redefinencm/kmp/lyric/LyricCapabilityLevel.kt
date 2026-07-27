package com.leejlredstar.redefinencm.kmp.lyric

/**
 * The timing and source-format capability that the current lyric document actually exposes.
 *
 * This is not a quality score. In particular, [TTML_FULL] means that the upstream TTML document
 * was parsed through the AMLL TTML path; it does not assert that every document uses every TTML
 * extension.
 */
enum class LyricCapabilityLevel {
    /** Primary lyric text exists, but no usable timestamp was parsed. */
    UNSYNCED,

    /** Ordinary line-level timestamps were parsed from LRC. */
    LINE_SYNCED,

    /** NetEase YRC word timings were parsed and selected as the primary representation. */
    NCM_YRC,

    /** An AMLL TTML document was parsed and retained for the full TTML rendering path. */
    TTML_FULL,
}

package com.leejlredstar.redefinencm.kmp.util

/**
 * The exact set of code points ECMAScript's `String.prototype.trim` treats as whitespace.
 *
 * The lyric pipeline reproduces AMLL's browser-side text handling, so trimming has to match
 * JavaScript rather than [Char.isWhitespace] — the two disagree on U+00A0, U+200B..U+200A and
 * U+FEFF, and a mismatch shifts word boundaries in per-syllable lyrics. Declared once here
 * because the TTML parser, the LRC parser and the native AMLL lyric model all need it.
 */
internal fun Char.isEcmaScriptWhitespace(): Boolean = when (code) {
    in 0x0009..0x000D,
    0x0020,
    0x00A0,
    0x1680,
    in 0x2000..0x200A,
    0x2028,
    0x2029,
    0x202F,
    0x205F,
    0x3000,
    0xFEFF,
    -> true

    else -> false
}

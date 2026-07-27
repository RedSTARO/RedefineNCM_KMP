/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * Platform adaptation of Apple Music-like Lyrics text segmentation.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import android.icu.text.BreakIterator as IcuBreakIterator
import java.text.BreakIterator as JavaBreakIterator
import java.util.Locale
import java.util.regex.Pattern

internal actual object AmllPlatformTextSegmenter {
    actual fun segmentWords(text: String): List<AmllWordSegment>? =
        segmentWordsWithIcu(text) ?: segmentWordsWithJava(text)

    actual fun segmentGraphemes(text: String): List<String>? =
        segmentGraphemesWithIcu(text) ?: segmentGraphemesWithJavaRegex(text)

    private fun segmentWordsWithIcu(text: String): List<AmllWordSegment>? = runCatching {
        val iterator = IcuBreakIterator.getWordInstance(Locale.getDefault())
        iterator.setText(text)
        buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != IcuBreakIterator.DONE) {
                val segment = text.substring(start, end)
                if (segment.isNotEmpty()) {
                    add(
                        AmllWordSegment(
                            text = segment,
                            isWordLike = isAmllWordLikeSegment(segment),
                        ),
                    )
                }
                start = end
                end = iterator.next()
            }
        }
    }.getOrNull()

    private fun segmentWordsWithJava(text: String): List<AmllWordSegment>? = runCatching {
        val iterator = JavaBreakIterator.getWordInstance(Locale.getDefault())
        iterator.setText(text)
        buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != JavaBreakIterator.DONE) {
                val segment = text.substring(start, end)
                if (segment.isNotEmpty()) {
                    add(
                        AmllWordSegment(
                            text = segment,
                            isWordLike = isAmllWordLikeSegment(segment),
                        ),
                    )
                }
                start = end
                end = iterator.next()
            }
        }
    }.getOrNull()

    private fun segmentGraphemesWithIcu(text: String): List<String>? = runCatching {
        val iterator = IcuBreakIterator.getCharacterInstance(Locale.getDefault())
        iterator.setText(text)
        buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != IcuBreakIterator.DONE) {
                add(text.substring(start, end))
                start = end
                end = iterator.next()
            }
        }
    }.getOrNull()

    /**
     * Local Android host tests execute against SDK stub classes, whose android.icu methods throw.
     * Keep the real API-24 ICU implementation as the device path, while Java's extended-grapheme
     * regex provides the same lossless contract when that API is genuinely unavailable.
     *
     * Compile `\X` lazily: Android ICU normally succeeds first, so an older runtime whose regex
     * engine does not support this construct can still use the supported device implementation.
     */
    private fun segmentGraphemesWithJavaRegex(text: String): List<String>? = runCatching {
        val matcher = Pattern.compile("\\X").matcher(text)
        buildList {
            while (matcher.find()) add(matcher.group())
        }
    }.getOrNull()
}

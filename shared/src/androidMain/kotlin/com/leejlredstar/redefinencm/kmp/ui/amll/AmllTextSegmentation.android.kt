package com.leejlredstar.redefinencm.kmp.ui.amll

import android.icu.text.BreakIterator
import java.util.Locale

internal actual object AmllPlatformTextSegmenter {
    actual fun segmentWords(text: String): List<AmllWordSegment>? = runCatching {
        val iterator = BreakIterator.getWordInstance(Locale.getDefault())
        iterator.setText(text)
        buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
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

    actual fun segmentGraphemes(text: String): List<String>? = runCatching {
        val iterator = BreakIterator.getCharacterInstance(Locale.getDefault())
        iterator.setText(text)
        buildList {
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                add(text.substring(start, end))
                start = end
                end = iterator.next()
            }
        }
    }.getOrNull()
}

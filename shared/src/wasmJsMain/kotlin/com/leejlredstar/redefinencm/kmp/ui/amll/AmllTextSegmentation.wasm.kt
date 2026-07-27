package com.leejlredstar.redefinencm.kmp.ui.amll

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal actual object AmllPlatformTextSegmenter {
    actual fun segmentWords(text: String): List<AmllWordSegment>? = runCatching {
        segmentWithIntl(text, "word")?.let { encoded ->
            Json.parseToJsonElement(encoded).jsonArray.map { item ->
                val value = item.jsonObject
                AmllWordSegment(
                    text = value.getValue("segment").jsonPrimitive.content,
                    isWordLike = value["isWordLike"]?.jsonPrimitive?.booleanOrNull == true,
                )
            }
        }
    }.getOrNull()

    actual fun segmentGraphemes(text: String): List<String>? = runCatching {
        segmentWithIntl(text, "grapheme")?.let { encoded ->
            Json.parseToJsonElement(encoded).jsonArray.map { item ->
                item.jsonObject.getValue("segment").jsonPrimitive.content
            }
        }
    }.getOrNull()
}

/**
 * Uses the same browser API and default locale selection as AMLL 0.5.2.
 */
private fun segmentWithIntl(
    text: String,
    granularity: String,
): String? = js(
    """(() => {
        if (typeof Intl === "undefined" || typeof Intl.Segmenter !== "function") return null;
        const segmenter = new Intl.Segmenter(undefined, { granularity });
        return JSON.stringify(Array.from(segmenter.segment(text), (entry) => ({
            segment: entry.segment,
            isWordLike: entry.isWordLike === true
        })));
    })()""",
)

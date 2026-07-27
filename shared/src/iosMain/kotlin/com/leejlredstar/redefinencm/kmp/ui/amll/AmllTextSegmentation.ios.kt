/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * Platform adaptation of Apple Music-like Lyrics text segmentation.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.cinterop.useContents
import platform.CoreFoundation.CFRangeMake
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCharacters
import platform.CoreFoundation.CFStringGetRangeOfComposedCharactersAtIndex
import platform.CoreFoundation.CFStringTokenizerAdvanceToNextToken
import platform.CoreFoundation.CFStringTokenizerCreate
import platform.CoreFoundation.CFStringTokenizerGetCurrentTokenRange
import platform.CoreFoundation.kCFStringTokenizerTokenNone
import platform.CoreFoundation.kCFStringTokenizerUnitWord

/**
 * CoreFoundation is used instead of NSString block enumeration here. Kotlin/Native 2.4 maps
 * NSString to kotlin.String at the call site, which hides enumerateSubstringsInRange even though
 * the Objective-C declaration is present in the platform klib. The C tokenizer and composed-range
 * functions expose the same Foundation boundary engine without that bridge ambiguity.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual object AmllPlatformTextSegmenter {
    actual fun segmentWords(text: String): List<AmllWordSegment>? = runCatching {
        if (text.isEmpty()) return@runCatching emptyList()
        withCoreFoundationString(text) { cfString ->
            val tokenizer = CFStringTokenizerCreate(
                alloc = null,
                string = cfString,
                range = CFRangeMake(0, text.length.toLong()),
                options = kCFStringTokenizerUnitWord,
                locale = null,
            ) ?: return@withCoreFoundationString null
            try {
                val result = mutableListOf<AmllWordSegment>()
                var cursor = 0
                while (
                    CFStringTokenizerAdvanceToNextToken(tokenizer) !=
                    kCFStringTokenizerTokenNone
                ) {
                    val (start, length) =
                        CFStringTokenizerGetCurrentTokenRange(tokenizer).useContents {
                            location.toInt() to this.length.toInt()
                        }
                    val end = start + length
                    if (start < cursor || length <= 0 || end > text.length) {
                        return@withCoreFoundationString null
                    }
                    if (start > cursor) {
                        result += AmllWordSegment(
                            text = text.substring(cursor, start),
                            isWordLike = false,
                        )
                    }
                    val token = text.substring(start, end)
                    result += AmllWordSegment(
                        text = token,
                        isWordLike = isAmllWordLikeSegment(token),
                    )
                    cursor = end
                }
                if (cursor < text.length) {
                    result += AmllWordSegment(
                        text = text.substring(cursor),
                        isWordLike = false,
                    )
                }
                result
            } finally {
                CFRelease(tokenizer)
            }
        }
    }.getOrNull()

    actual fun segmentGraphemes(text: String): List<String>? = runCatching {
        if (text.isEmpty()) return@runCatching emptyList()
        withCoreFoundationString(text) { cfString ->
            val result = mutableListOf<String>()
            var cursor = 0
            while (cursor < text.length) {
                val (start, length) =
                    CFStringGetRangeOfComposedCharactersAtIndex(
                        theString = cfString,
                        theIndex = cursor.toLong(),
                    ).useContents {
                        location.toInt() to this.length.toInt()
                    }
                val end = start + length
                if (start != cursor || length <= 0 || end > text.length) {
                    return@withCoreFoundationString null
                }
                result += text.substring(start, end)
                cursor = end
            }
            result
        }
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
private inline fun <T> withCoreFoundationString(
    text: String,
    block: (platform.CoreFoundation.CFStringRef) -> T?,
): T? = memScoped {
    val characters = allocArray<UShortVar>(text.length)
    text.forEachIndexed { index, character ->
        characters[index] = character.code.toUShort()
    }
    val cfString = CFStringCreateWithCharacters(
        alloc = null,
        chars = characters,
        numChars = text.length.toLong(),
    ) ?: return@memScoped null
    try {
        block(cfString)
    } finally {
        CFRelease(cfString)
    }
}

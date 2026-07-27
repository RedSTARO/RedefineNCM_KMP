/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * Kotlin Multiplatform / Compose translation of @applemusic-like-lyrics/core 0.5.2:
 * packages/core/src/lyric-player/dom/lyric-line.ts
 * packages/core/src/utils/lyric-split-words.ts
 * packages/core/src/utils/lyric-line-break.ts
 * packages/core/src/utils/line-balancer.ts
 *
 * Modified for RedefineNCM KMP on 2026-07-26.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val AmllAnimationFrameQuantity = 32
private const val AmllWordFadeWidthEm = 0.5
private const val AmllOverflowPenaltyMultiplier = 1_000.0
private const val AmllCjkBreakPenaltyRatio = 0.15
private const val AmllNormalBreakPenaltyRatio = 0.5
private const val AmllSpaceBreakRewardRatio = 0.4
private const val AmllPunctuationBreakRewardRatio = 0.6

private val AmllPunctuationEndings =
    setOf(',', '.', ';', ':', '!', '?', '，', '。', '；', '：', '！', '？', '、', '）', '】',
        '》', '」', '』', '’', '”', ')', '[', ']', '}', '>', '~', '…')

internal data class AmllEmphasisFrame(
    val scale: Double,
    val offsetXEm: Double,
    val offsetYEm: Double,
    val floatOffsetYEm: Double,
    val glowAlpha: Double,
    val glowRadiusEm: Double,
    val underlyingStyleWeight: Double,
    val firstAuthoredGlowAlpha: Double,
)

internal data class AmllTransformPoint(
    val x: Double,
    val y: Double,
)

/**
 * Maps a point through the transform stack authored by `initEmphasizeAnimation()`.
 *
 * The parent `float-word` translation is outside the grapheme. On the grapheme itself, the
 * replace animation is `matrix3d(scale) translate(glowOffset)` and the later additive animation
 * appends `translate(floatOffset)`. CSS therefore scales both child translations, while the
 * parent's translation stays unscaled. The grapheme's default transform origin is its center.
 */
internal fun transformAmllEmphasisPoint(
    point: AmllTransformPoint,
    origin: AmllTransformPoint,
    parentFloatYEm: Double,
    frame: AmllEmphasisFrame,
): AmllTransformPoint {
    val childX = point.x + frame.offsetXEm
    val childY = point.y + frame.offsetYEm + frame.floatOffsetYEm
    return AmllTransformPoint(
        x = origin.x + (childX - origin.x) * frame.scale,
        y = parentFloatYEm + origin.y + (childY - origin.y) * frame.scale,
    )
}

internal data class AmllShadowSpec(
    val red: Double,
    val green: Double,
    val blue: Double,
    val alpha: Double,
    val offsetXpx: Double,
    val offsetYpx: Double,
    val blurRadiusPx: Double,
)

internal data class AmllWordBodyGlowSpec(
    val foregroundAlpha: Double,
    val strokeWidthPx: Double,
    val strokeAlpha: Double,
    val textShadows: List<AmllShadowSpec>,
    val dropShadow: AmllShadowSpec,
)

/**
 * Literal values from the fixed host's `.emphasize .wordBody` selectors.
 *
 * The selector is reachable only when AMLL creates `wordWithRuby > wordBody`; it intentionally
 * differs between the desktop/default and Android presentations.
 */
internal fun amllWordBodyGlowSpec(androidPresentation: Boolean): AmllWordBodyGlowSpec =
    if (androidPresentation) {
        AmllWordBodyGlowSpec(
            foregroundAlpha = 0.96,
            strokeWidthPx = 0.45,
            strokeAlpha = 0.52,
            textShadows = listOf(
                AmllShadowSpec(1.0, 1.0, 1.0, 0.98, 0.0, 0.0, 3.0),
                AmllShadowSpec(226.0 / 255.0, 214.0 / 255.0, 1.0, 0.82, 0.0, 0.0, 10.0),
                AmllShadowSpec(165.0 / 255.0, 126.0 / 255.0, 1.0, 0.50, 0.0, 0.0, 22.0),
            ),
            dropShadow = AmllShadowSpec(
                190.0 / 255.0,
                160.0 / 255.0,
                1.0,
                0.42,
                0.0,
                0.0,
                7.0,
            ),
        )
    } else {
        AmllWordBodyGlowSpec(
            foregroundAlpha = 0.98,
            strokeWidthPx = 0.55,
            strokeAlpha = 0.58,
            textShadows = listOf(
                AmllShadowSpec(1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 3.0),
                AmllShadowSpec(226.0 / 255.0, 214.0 / 255.0, 1.0, 0.95, 0.0, 0.0, 14.0),
                AmllShadowSpec(165.0 / 255.0, 126.0 / 255.0, 1.0, 0.62, 0.0, 0.0, 30.0),
            ),
            dropShadow = AmllShadowSpec(
                190.0 / 255.0,
                160.0 / 255.0,
                1.0,
                0.52,
                0.0,
                0.0,
                10.0,
            ),
        )
    }

private fun AmllWordBodyGlowSpec.toDevicePixels(density: Double): AmllWordBodyGlowSpec {
    fun AmllShadowSpec.scaled(): AmllShadowSpec = copy(
        offsetXpx = offsetXpx * density,
        offsetYpx = offsetYpx * density,
        blurRadiusPx = blurRadiusPx * density,
    )
    return copy(
        strokeWidthPx = strokeWidthPx * density,
        textShadows = textShadows.map { it.scaled() },
        dropShadow = dropShadow.scaled(),
    )
}

/**
 * Resolves the synthetic neutral keyframe Chromium inserts before AMLL's first authored `1 / 32`
 * keyframe. The neutral value is the inherited host `text-shadow`; the authored animation replaces
 * it, interpolating shadow lists in premultiplied sRGB until the first explicit frame.
 */
internal fun computeAmllEmphasisShadows(
    frame: AmllEmphasisFrame,
    active: Boolean,
    androidPresentation: Boolean,
    fontSizePx: Double,
    wordBodyGlow: AmllWordBodyGlowSpec? = null,
): List<AmllShadowSpec> {
    val authoredCurrent = AmllShadowSpec(
        red = 1.0,
        green = 1.0,
        blue = 1.0,
        alpha = frame.glowAlpha.coerceIn(0.0, 1.0),
        offsetXpx = 0.0,
        offsetYpx = 0.0,
        blurRadiusPx = (frame.glowRadiusEm * fontSizePx).coerceAtLeast(0.0),
    )
    val underlyingWeight = frame.underlyingStyleWeight.coerceIn(0.0, 1.0)
    if (underlyingWeight <= 0.0) return listOf(authoredCurrent)

    val underlying = when {
        wordBodyGlow != null -> wordBodyGlow.textShadows
        active -> listOf(
            AmllShadowSpec(
                red = 1.0,
                green = 1.0,
                blue = 1.0,
                alpha = if (androidPresentation) 0.38 else 0.35,
                offsetXpx = 0.0,
                offsetYpx = 0.0,
                blurRadiusPx = 10.0,
            ),
            AmllShadowSpec(
                red = 0.0,
                green = 0.0,
                blue = 0.0,
                alpha = 0.35,
                offsetXpx = 0.0,
                offsetYpx = 4.0,
                blurRadiusPx = 24.0,
            ),
        )
        androidPresentation -> listOf(
            AmllShadowSpec(
                red = 0.0,
                green = 0.0,
                blue = 0.0,
                alpha = 0.55,
                offsetXpx = 0.0,
                offsetYpx = 2.0,
                blurRadiusPx = 18.0,
            ),
        )
        else -> emptyList()
    }
    val firstAuthored = authoredCurrent.copy(
        alpha = frame.firstAuthoredGlowAlpha.coerceIn(0.0, 1.0),
    )
    val transparent = AmllShadowSpec(
        red = 0.0,
        green = 0.0,
        blue = 0.0,
        alpha = 0.0,
        offsetXpx = 0.0,
        offsetYpx = 0.0,
        blurRadiusPx = 0.0,
    )
    val count = max(underlying.size, 1)
    val authoredWeight = 1.0 - underlyingWeight
    return List(count) { index ->
        interpolateAmllCssShadow(
            from = underlying.getOrElse(index) { transparent },
            to = if (index == 0) firstAuthored else transparent,
            fraction = authoredWeight,
        )
    }
}

private fun interpolateAmllCssShadow(
    from: AmllShadowSpec,
    to: AmllShadowSpec,
    fraction: Double,
): AmllShadowSpec {
    val amount = fraction.coerceIn(0.0, 1.0)
    val alpha = lerp(from.alpha, to.alpha, amount)
    fun channel(fromValue: Double, toValue: Double): Double {
        if (alpha <= 0.0) return 0.0
        val premultiplied = lerp(
            fromValue * from.alpha,
            toValue * to.alpha,
            amount,
        )
        return (premultiplied / alpha).coerceIn(0.0, 1.0)
    }
    return AmllShadowSpec(
        red = channel(from.red, to.red),
        green = channel(from.green, to.green),
        blue = channel(from.blue, to.blue),
        alpha = alpha.coerceIn(0.0, 1.0),
        offsetXpx = lerp(from.offsetXpx, to.offsetXpx, amount),
        offsetYpx = lerp(from.offsetYpx, to.offsetYpx, amount),
        blurRadiusPx = lerp(from.blurRadiusPx, to.blurRadiusPx, amount).coerceAtLeast(0.0),
    )
}

/**
 * The calc()-based fallback mask retained as a pure regression surface. The native renderer uses
 * [buildAmllWebMaskTimeline], matching Chromium's `supportMaskImage = true` path.
 */
internal data class AmllMaskFrame(
    val brightBoundaryPx: Double,
    val fadeWidthPx: Double,
)

internal data class AmllMaskWordMetrics(
    val widthPx: Double,
    val heightPx: Double,
    val paddingPx: Double,
    val startTimeMs: Double,
    val endTimeMs: Double,
    val rubySegments: List<AmllMaskRubySegment> = emptyList(),
)

internal data class AmllMaskRubySegment(
    val text: String,
    val startTimeMs: Double,
    val endTimeMs: Double,
)

internal data class AmllMaskKeyframe(
    val offset: Double,
    val positionPx: Double,
)

internal data class AmllWebMaskTimeline(
    val keyframes: List<AmllMaskKeyframe>,
    val totalDurationMs: Double,
    val minPositionPx: Double,
    val fadeWidthPx: Double,
    val contentWidthPx: Double,
    val paddingPx: Double,
)

internal data class AmllWebMaskFrame(
    val maskPositionPx: Double,
    val brightBoundaryPx: Double,
    val fadeWidthPx: Double,
)

internal data class AmllMaskAlphaState(
    val brightAlpha: Double = 1.0,
    val darkAlpha: Double = 0.2,
)

internal fun targetAmllMaskAlpha(
    scale: Double,
    renderMode: AmllLineRenderMode,
): AmllMaskAlphaState {
    val factor = ((scale - 0.97) / 0.03).coerceIn(0.0, 1.0)
    val dynamicDarkAlpha = factor * 0.2 + 0.2
    val dynamicBrightAlpha = factor * 0.8 + 0.2
    return if (renderMode == AmllLineRenderMode.SOLID) {
        AmllMaskAlphaState(
            brightAlpha = dynamicDarkAlpha,
            darkAlpha = dynamicDarkAlpha,
        )
    } else {
        AmllMaskAlphaState(
            brightAlpha = dynamicBrightAlpha,
            darkAlpha = dynamicDarkAlpha,
        )
    }
}

internal fun advanceAmllMaskAlpha(
    current: AmllMaskAlphaState,
    target: AmllMaskAlphaState,
    deltaSeconds: Double,
): AmllMaskAlphaState {
    // JavaScript's `delta || 0.016` also treats NaN as falsy.
    val delta = if (deltaSeconds == 0.0 || deltaSeconds.isNaN()) 0.016 else deltaSeconds
    fun advance(value: Double, targetValue: Double): Double {
        if (abs(targetValue - value) < 0.001) return targetValue
        val speed = if (targetValue > value) 50.0 else 7.0
        val factor = 1.0 - kotlin.math.exp(-speed * delta)
        return value + (targetValue - value) * factor
    }
    return AmllMaskAlphaState(
        brightAlpha = advance(current.brightAlpha, target.brightAlpha),
        darkAlpha = advance(current.darkAlpha, target.darkAlpha),
    )
}

/**
 * Applies the former host page's active-line `!important` custom-property overrides after the
 * core spring value has been calculated. Inactive values retain core's three-decimal DOM output.
 */
internal fun visibleAmllMaskAlpha(
    animated: AmllMaskAlphaState,
    active: Boolean,
    androidPresentation: Boolean,
): AmllMaskAlphaState =
    if (active) {
        AmllMaskAlphaState(
            brightAlpha = 1.0,
            darkAlpha = if (androidPresentation) 0.12 else 0.14,
        )
    } else {
        AmllMaskAlphaState(
            brightAlpha = round(animated.brightAlpha * 1_000.0) / 1_000.0,
            darkAlpha = round(animated.darkAlpha * 1_000.0) / 1_000.0,
        )
    }

internal data class AmllSplitWord(
    val sourceId: String,
    val text: String,
    val startTimeMs: Double,
    val endTimeMs: Double,
    val romanWord: String = "",
    val obscene: Boolean = false,
    val ruby: List<AmllLyricRubySegment> = emptyList(),
)

internal data class AmllWordChunk(
    val words: List<AmllSplitWord>,
) {
    val text: String
        get() = words.joinToString(separator = "") { it.text }
}

internal enum class AmllMaskObsceneWordsMode {
    DISABLED,
    FULL_MASK,
    PARTIAL_MASK,
}

/**
 * Direct translation of `LyricPlayerBase.processObsceneWord()`.
 *
 * The fixed RedefineNCM host uses AMLL's default [AmllMaskObsceneWordsMode.DISABLED], but keeping
 * the branch here prevents the model flag from becoming lossy if the common UI later exposes the
 * existing core option.
 */
internal fun processAmllObsceneWord(
    text: String,
    obscene: Boolean,
    mode: AmllMaskObsceneWordsMode = AmllMaskObsceneWordsMode.DISABLED,
    maskCharacter: String = "*",
): String {
    if (!obscene || mode == AmllMaskObsceneWordsMode.DISABLED) return text
    val replacement = maskCharacter.firstOrNull()?.toString() ?: "*"
    fun mask(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (character.toString().isEcmaWhitespaceOnly()) {
                append(character)
            } else {
                append(replacement)
            }
        }
    }
    if (mode == AmllMaskObsceneWordsMode.FULL_MASK) return mask(text)

    val trimmed = text.trimEcmaWhitespace()
    if (trimmed.length <= 2) return mask(text)
    val startPosition = text.indexOf(trimmed)
    val endPosition = startPosition + trimmed.length - 1
    return text.substring(0, startPosition + 1) +
        mask(text.substring(startPosition + 1, endPosition)) +
        text.substring(endPosition)
}

/** `createWord()` inserts NBSP for missing entries whenever any word has per-word romanization. */
internal fun amllRomanWordDisplayText(
    romanWord: String,
    hasRomanLine: Boolean,
): String? = if (hasRomanLine) {
    romanWord.trimEcmaWhitespace().ifEmpty { "\u00A0" }
} else {
    null
}

internal data class AmllBalanceNode(
    val widthPx: Double,
    val text: String,
    val isSpace: Boolean,
)

internal enum class AmllLineBreakStrategy {
    BALANCED,
    NATIVE_FLOW,
}

internal fun amllPhysicalRowStartX(
    containerWidthPx: Int,
    rowWidthPx: Int,
    isDuet: Boolean,
): Int = if (isDuet) containerWidthPx - rowWidthPx else 0

internal fun amllLineBreakStrategy(
    segmentation: AmllSegmentationResult<AmllWordSegment>,
): AmllLineBreakStrategy =
    if (segmentation.backend == AmllSegmentationBackend.PLATFORM) {
        AmllLineBreakStrategy.BALANCED
    } else {
        AmllLineBreakStrategy.NATIVE_FLOW
    }

/**
 * Direct translation of the non-Web-Animation fallback in `generateCalcBasedMaskImage`.
 */
internal fun computeAmllMaskFrame(
    wordWidthPx: Double,
    wordHeightPx: Double,
    progress: Double,
    fadeWidthEm: Double = AmllWordFadeWidthEm,
): AmllMaskFrame {
    val fadeWidthPx = max(0.0, wordHeightPx * fadeWidthEm)
    return AmllMaskFrame(
        brightBoundaryPx = -fadeWidthPx +
            progress.coerceIn(0.0, 1.0) * (max(0.0, wordWidthPx) + fadeWidthPx),
        fadeWidthPx = fadeWidthPx,
    )
}

/**
 * Builds the exact no-easing, whole-line keyframe sequence from
 * `LyricLineEl.generateWebAnimationBasedMaskImage()`.
 *
 * Every rendered word receives its own clamped mask position, but all positions are driven by the
 * complete line timeline. In particular, the first movement adds `fadeWidth * 1.5` and the final
 * movement adds `fadeWidth * 0.5`; replacing this with per-word progress creates the visible seams
 * that the upstream implementation deliberately avoids.
 *
 * Ruby timing follows the source's per-segment/per-UTF-16-code-unit branch. Blank ruby segments
 * are ignored for mask generation, exactly like `getRubySegments()`, while emphasis anchoring uses
 * the unfiltered ruby character count at the chunk level.
 */
internal fun buildAmllWebMaskTimeline(
    words: List<AmllMaskWordMetrics>,
    targetWordIndex: Int,
    lineStartTimeMs: Double,
    lineEndTimeMs: Double,
    fadeWidthEm: Double = AmllWordFadeWidthEm,
): AmllWebMaskTimeline {
    require(targetWordIndex in words.indices)
    val target = words[targetWordIndex]
    val fadeWidth = max(0.0, target.heightPx * fadeWidthEm)
    val totalFadeDuration = (
        max(
            lineEndTimeMs,
            words.maxOfOrNull(AmllMaskWordMetrics::endTimeMs) ?: lineStartTimeMs,
        ) - lineStartTimeMs
        ).coerceAtLeast(0.0)
    val durationDivisor = totalFadeDuration.takeIf { it > 0.0 } ?: 1.0
    val widthBeforeSelf = words
        .take(targetWordIndex)
        .sumOf(AmllMaskWordMetrics::widthPx) + if (words.isNotEmpty()) fadeWidth else 0.0
    val minOffset = -(target.widthPx + target.paddingPx * 2.0 + fadeWidth)
    fun clampOffset(value: Double): Double = value.coerceIn(minOffset, 0.0)

    var currentPosition =
        -widthBeforeSelf - target.widthPx - target.paddingPx - fadeWidth
    var timeOffset = 0.0
    var lastPosition = currentPosition
    var lastTime = 0.0
    val keyframes = mutableListOf<AmllMaskKeyframe>()

    fun pushFrame() {
        val moveOffset = currentPosition - lastPosition
        val time = timeOffset.coerceIn(0.0, 1.0)
        val duration = time - lastTime
        if (moveOffset != 0.0) {
            val distanceToTime = abs(duration / moveOffset)
            if (currentPosition > minOffset && lastPosition < minOffset) {
                keyframes += AmllMaskKeyframe(
                    offset = lastTime + abs(lastPosition - minOffset) * distanceToTime,
                    positionPx = clampOffset(lastPosition),
                )
            }
            if (currentPosition > 0.0 && lastPosition < 0.0) {
                keyframes += AmllMaskKeyframe(
                    offset = lastTime + abs(lastPosition) * distanceToTime,
                    // The upstream code intentionally uses curPos here.
                    positionPx = clampOffset(currentPosition),
                )
            }
        }
        keyframes += AmllMaskKeyframe(
            offset = time,
            positionPx = clampOffset(currentPosition),
        )
        lastPosition = currentPosition
        lastTime = time
    }

    pushFrame()
    var lastTimestamp = 0.0
    words.forEachIndexed { wordIndex, word ->
        val currentTimestamp = word.startTimeMs - lineStartTimeMs
        val staticDuration = currentTimestamp - lastTimestamp
        timeOffset += staticDuration / durationDivisor
        if (staticDuration > 0.0) pushFrame()
        lastTimestamp = currentTimestamp

        val fadeDuration = max(0.0, word.endTimeMs - word.startTimeMs)
        val rubySegments = word.rubySegments.filter { !it.text.isEcmaWhitespaceOnly() }
        val rubyCharacterCount = rubySegments.sumOf { it.text.length }
        if (rubyCharacterCount > 0) {
            val widthPerCharacter = word.widthPx / rubyCharacterCount
            var characterIndex = 0
            rubySegments.forEach { ruby ->
                val rubyStartTime = if (ruby.startTimeMs.isFinite()) {
                    ruby.startTimeMs
                } else {
                    word.startTimeMs
                }
                val rubyEndTime = if (ruby.endTimeMs.isFinite()) {
                    ruby.endTimeMs
                } else {
                    word.endTimeMs
                }
                val rubyStart = max(rubyStartTime, word.startTimeMs)
                val rubyEnd = min(max(rubyEndTime, rubyStart), word.endTimeMs)
                val rubyStartTimestamp = rubyStart - lineStartTimeMs
                val rubyStaticDuration = rubyStartTimestamp - lastTimestamp
                timeOffset += rubyStaticDuration / durationDivisor
                if (rubyStaticDuration > 0.0) pushFrame()
                lastTimestamp = rubyStartTimestamp

                val rubyDuration = max(0.0, rubyEnd - rubyStart)
                val perCharacterDuration = rubyDuration / ruby.text.length
                repeat(ruby.text.length) {
                    timeOffset += perCharacterDuration / durationDivisor
                    currentPosition += widthPerCharacter
                    if (wordIndex == 0 && characterIndex == 0) {
                        currentPosition += fadeWidth * 1.5
                    }
                    if (
                        wordIndex == words.lastIndex &&
                        characterIndex == rubyCharacterCount - 1
                    ) {
                        currentPosition += fadeWidth * 0.5
                    }
                    if (perCharacterDuration > 0.0) pushFrame()
                    lastTimestamp += perCharacterDuration
                    characterIndex += 1
                }
            }
            val wordEndTimestamp = max(
                word.endTimeMs - lineStartTimeMs,
                lastTimestamp,
            )
            val wordTailDuration = wordEndTimestamp - lastTimestamp
            timeOffset += wordTailDuration / durationDivisor
            if (wordTailDuration > 0.0) pushFrame()
            lastTimestamp = wordEndTimestamp
        } else {
            // The no-ruby branch has one segment per real word.
            timeOffset += fadeDuration / durationDivisor
            currentPosition += word.widthPx
            if (wordIndex == 0) currentPosition += fadeWidth * 1.5
            if (wordIndex == words.lastIndex) currentPosition += fadeWidth * 0.5
            if (fadeDuration > 0.0) pushFrame()
            lastTimestamp += fadeDuration
        }
    }

    return AmllWebMaskTimeline(
        keyframes = keyframes,
        totalDurationMs = totalFadeDuration,
        minPositionPx = minOffset,
        fadeWidthPx = fadeWidth,
        contentWidthPx = target.widthPx,
        paddingPx = target.paddingPx,
    )
}

internal fun sampleAmllWebMaskTimeline(
    timeline: AmllWebMaskTimeline,
    positionMs: Double,
    lineStartTimeMs: Double,
): AmllWebMaskFrame {
    val normalizedTime = if (timeline.totalDurationMs > 0.0) {
        ((positionMs - lineStartTimeMs) / timeline.totalDurationMs).coerceIn(0.0, 1.0)
    } else {
        if (positionMs < lineStartTimeMs) 0.0 else 1.0
    }
    val frames = timeline.keyframes
    val position = when {
        frames.isEmpty() -> timeline.minPositionPx
        normalizedTime <= frames.first().offset -> frames.first().positionPx
        normalizedTime >= frames.last().offset -> frames.last().positionPx
        else -> {
            var lowerIndex = 0
            for (index in frames.indices) {
                if (frames[index].offset <= normalizedTime) lowerIndex = index else break
            }
            var upperIndex = lowerIndex + 1
            while (
                upperIndex < frames.size &&
                frames[upperIndex].offset <= frames[lowerIndex].offset
            ) {
                upperIndex++
            }
            if (upperIndex >= frames.size) {
                frames[lowerIndex].positionPx
            } else {
                val lower = frames[lowerIndex]
                val upper = frames[upperIndex]
                val fraction = (
                    (normalizedTime - lower.offset) /
                        (upper.offset - lower.offset)
                    ).coerceIn(0.0, 1.0)
                lerp(lower.positionPx, upper.positionPx, fraction)
            }
        }
    }
    return AmllWebMaskFrame(
        maskPositionPx = position,
        // The gradient's bright stop is one element-width from its image origin. Our Canvas starts
        // at the content box, while AMLL's DOM element also has `padding` on each side.
        brightBoundaryPx = position + timeline.contentWidthPx + timeline.paddingPx,
        fadeWidthPx = timeline.fadeWidthPx,
    )
}

/**
 * Samples the same 32 explicitly authored Web Animation keyframes as
 * `initEmphasizeAnimation()`. The first authored offset is `1 / 32`; offset zero is the neutral
 * underlying transform inserted by Web Animations. Interpolating the sampled values (rather than
 * evaluating the easing continuously) is required for parity.
 */
internal fun computeAmllEmphasisFrame(
    positionMs: Double,
    mergedStartTimeMs: Double,
    mergedEndTimeMs: Double,
    characterIndex: Int,
    characterCount: Int,
    anchorCharacterCount: Int = characterCount,
    isLastWordChunk: Boolean,
    isBackground: Boolean,
    lineStartTimeMs: Double = mergedStartTimeMs,
): AmllEmphasisFrame {
    val originalDuration = max(1_000.0, mergedEndTimeMs - mergedStartTimeMs)
    var duration = originalDuration
    var amount = duration / 2_000.0
    amount = if (amount > 1.0) sqrt(amount) else amount.pow(3.0)
    var blur = duration / 3_000.0
    blur = if (blur > 1.0) sqrt(blur) else blur.pow(3.0)
    amount *= 0.6
    blur *= 0.5
    if (isLastWordChunk) {
        amount *= 1.6
        blur *= 1.5
        duration *= 1.2
    }
    amount = min(1.2, amount)
    blur = min(0.8, blur)

    val anchorCount = max(1, anchorCharacterCount)
    val baseDelay = max(0.0, mergedStartTimeMs - lineStartTimeMs)
    val characterDelay = baseDelay + (duration / 2.5 / anchorCount) * characterIndex
    val glowProgress = (positionMs - lineStartTimeMs - characterDelay) / duration
    val glowSample = sampleAmllAuthoredKeyframes(glowProgress) { x ->
        amllEmphasisEasing(x)
    }
    val firstAuthoredOffset = 1.0 / AmllAnimationFrameQuantity
    val underlyingStyleWeight =
        (1.0 - glowProgress / firstAuthoredOffset).coerceIn(0.0, 1.0)
    val firstAuthoredGlowAlpha = amllEmphasisEasing(firstAuthoredOffset) * blur
    val floatDuration = duration * 1.4
    val floatProgress =
        (positionMs - lineStartTimeMs - (characterDelay - 400.0)) / floatDuration
    var floatSample = sampleAmllAuthoredKeyframes(floatProgress) { x ->
        sin(x * PI)
    }
    if (isBackground) floatSample *= 2.0

    return AmllEmphasisFrame(
        scale = 1.0 + glowSample * 0.1 * amount,
        offsetXEm = -glowSample * 0.03 * amount *
            (characterCount / 2.0 - characterIndex),
        offsetYEm = -glowSample * 0.025 * amount,
        floatOffsetYEm = -floatSample * 0.05,
        glowAlpha = glowSample * blur,
        glowRadiusEm = min(0.3, blur * 0.3),
        underlyingStyleWeight = underlyingStyleWeight,
        firstAuthoredGlowAlpha = firstAuthoredGlowAlpha,
    )
}

private fun sampleAmllAuthoredKeyframes(
    rawProgress: Double,
    valueAtAuthoredOffset: (Double) -> Double,
): Double {
    if (rawProgress <= 0.0) return 0.0
    if (rawProgress >= 1.0) return valueAtAuthoredOffset(1.0)
    val scaled = rawProgress * AmllAnimationFrameQuantity
    val lowerIndex = floor(scaled).toInt().coerceIn(0, AmllAnimationFrameQuantity)
    val upperIndex = (lowerIndex + 1).coerceAtMost(AmllAnimationFrameQuantity)
    val lowerValue = if (lowerIndex == 0) {
        0.0
    } else {
        valueAtAuthoredOffset(lowerIndex.toDouble() / AmllAnimationFrameQuantity)
    }
    val upperValue =
        valueAtAuthoredOffset(upperIndex.toDouble() / AmllAnimationFrameQuantity)
    return lerp(lowerValue, upperValue, scaled - lowerIndex)
}

private fun amllEmphasisEasing(value: Double): Double {
    val x = value.coerceIn(0.0, 1.0)
    return if (x < 0.5) {
        cubicBezierYForX(x / 0.5, 0.2, 0.4, 0.58, 1.0)
    } else {
        1.0 - cubicBezierYForX((x - 0.5) / 0.5, 0.3, 0.0, 0.58, 1.0)
    }
}

private fun cubicBezierYForX(
    x: Double,
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
): Double {
    fun coordinate(parameter: Double, first: Double, second: Double): Double {
        val inverse = 1.0 - parameter
        return 3.0 * inverse * inverse * parameter * first +
            3.0 * inverse * parameter * parameter * second +
            parameter * parameter * parameter
    }

    fun derivative(parameter: Double): Double {
        val inverse = 1.0 - parameter
        return 3.0 * inverse * inverse * x1 +
            6.0 * inverse * parameter * (x2 - x1) +
            3.0 * parameter * parameter * (1.0 - x2)
    }

    var parameter = x.coerceIn(0.0, 1.0)
    repeat(8) {
        val error = coordinate(parameter, x1, x2) - x
        val slope = derivative(parameter)
        if (abs(error) >= 1e-7 && abs(slope) >= 1e-7) {
            parameter = (parameter - error / slope).coerceIn(0.0, 1.0)
        }
    }
    var low = 0.0
    var high = 1.0
    repeat(12) {
        val candidate = (low + high) / 2.0
        if (coordinate(candidate, x1, x2) < x) low = candidate else high = candidate
    }
    parameter = (low + high) / 2.0
    return coordinate(parameter, y1, y2)
}

/**
 * Direct translation of `chunkAndSplitLyricWords()`.
 *
 * Ruby atoms bypass whitespace/CJK splitting and are never merged. A nonblank per-word
 * romanization suppresses the CJK UTF-16 split. The CJK branch deliberately follows JavaScript
 * `split("")`: supplementary ideographs become two UTF-16 timing units.
 */
internal fun chunkAndSplitAmllLyricWords(
    words: List<AmllLyricWord>,
): List<AmllWordChunk> {
    val result = mutableListOf<AmllWordChunk>()
    var currentGroup = mutableListOf<AmllSplitWord>()

    fun flushGroup() {
        if (currentGroup.isNotEmpty()) {
            result += AmllWordChunk(currentGroup.toList())
            currentGroup = mutableListOf()
        }
    }

    fun processAtom(atom: AmllSplitWord) {
        val isSpace = atom.text.isEcmaWhitespaceOnly()
        val hasRuby = atom.ruby.isNotEmpty()
        val isCjk = isAmllCjk(atom.text)
        val isMergeable = !isSpace && !hasRuby && !isCjk
        if (isMergeable) {
            currentGroup += atom
        } else {
            flushGroup()
            result += AmllWordChunk(listOf(atom))
        }
    }

    words.forEach { word ->
        val romanWord = word.romanWord.orEmpty()
        val hasRuby = word.ruby.isNotEmpty()
        if (word.text.isEcmaWhitespaceOnly() || hasRuby) {
            processAtom(
                AmllSplitWord(
                    sourceId = word.id,
                    text = word.text,
                    startTimeMs = word.exactStartTimeMs,
                    endTimeMs = word.exactEndTimeMs,
                    romanWord = romanWord,
                    obscene = word.obscene,
                    ruby = word.ruby,
                ),
            )
            return@forEach
        }

        val parts = splitAmllWhitespaceParts(word.text)
        val totalUnits = countNonWhitespaceUtf16Units(word.text).coerceAtLeast(1)
        val timeSpan = word.exactEndTimeMs - word.exactStartTimeMs
        val timePerUnit = timeSpan / totalUnits
        var currentOffset = 0

        parts.forEachIndexed { partIndex, part ->
            if (part.isEcmaWhitespaceOnly()) {
                val start = word.exactStartTimeMs + currentOffset * timePerUnit
                processAtom(
                    AmllSplitWord(
                        sourceId = "${word.id}:space:$partIndex",
                        text = part,
                        startTimeMs = start,
                        endTimeMs = start,
                        romanWord = "",
                        obscene = word.obscene,
                    ),
                )
            } else {
                val utf16Units = splitAmllUtf16CodeUnits(part)
                if (
                    isAmllCjk(part) &&
                    utf16Units.size > 1 &&
                    romanWord.trimEcmaWhitespace().isEmpty()
                ) {
                    utf16Units.forEachIndexed { unitIndex, unit ->
                        val start = word.exactStartTimeMs + currentOffset * timePerUnit
                        val end = start + timePerUnit
                        processAtom(
                            AmllSplitWord(
                                sourceId = "${word.id}:cjk:$unitIndex",
                                text = unit,
                                startTimeMs = start,
                                endTimeMs = end,
                                romanWord = "",
                                obscene = word.obscene,
                            ),
                        )
                        currentOffset += 1
                    }
                } else {
                    val unitCount = part.length.coerceAtLeast(1)
                    val start = word.exactStartTimeMs + currentOffset * timePerUnit
                    processAtom(
                        AmllSplitWord(
                            sourceId = "${word.id}:part:$partIndex",
                            text = part,
                            startTimeMs = start,
                            endTimeMs = start + unitCount * timePerUnit,
                            romanWord = romanWord,
                            obscene = word.obscene,
                        ),
                    )
                    currentOffset += unitCount
                }
            }
        }
    }
    flushGroup()
    return result
}

internal fun calculateAmllBalancedBreaks(
    nodes: List<AmllBalanceNode>,
    containerWidthPx: Double,
    cjkBoundaries: Set<Int> = inferAmllCjkBoundaries(
        nodes.joinToString(separator = "") { it.text },
    ),
): Set<Int> {
    val count = nodes.size
    if (count == 0 || containerWidthPx <= 0.0) return emptySet()
    val charOffsets = IntArray(count + 1)
    val prefixWidth = DoubleArray(count + 1)
    nodes.indices.forEach { index ->
        // JS String.length and Kotlin String.length are both UTF-16 code-unit counts.
        charOffsets[index + 1] = charOffsets[index] + nodes[index].text.length
        prefixWidth[index + 1] = prefixWidth[index] + nodes[index].widthPx
    }
    if (prefixWidth[count] <= containerWidthPx) return emptySet()

    val costs = DoubleArray(count + 1) { Double.POSITIVE_INFINITY }
    val nextBreak = IntArray(count + 1) { -1 }
    costs[count] = 0.0
    val cjkPenalty = (containerWidthPx * AmllCjkBreakPenaltyRatio).pow(2.0)
    val normalPenalty = (containerWidthPx * AmllNormalBreakPenaltyRatio).pow(2.0)

    for (start in count - 1 downTo 0) {
        for (end in start + 1..count) {
            val width = prefixWidth[end] - prefixWidth[start]
            val lineCost = if (width > containerWidthPx) {
                if (end == start + 1) {
                    (width - containerWidthPx).pow(2.0) * AmllOverflowPenaltyMultiplier
                } else {
                    continue
                }
            } else {
                (containerWidthPx - width).pow(2.0)
            }
            val breakPenalty = if (end >= count) {
                0.0
            } else {
                val previous = nodes[end - 1]
                when {
                    previous.text.lastOrNull() in AmllPunctuationEndings ->
                        -(containerWidthPx * AmllPunctuationBreakRewardRatio).pow(2.0)
                    previous.isSpace ->
                        -(containerWidthPx * AmllSpaceBreakRewardRatio).pow(2.0)
                    charOffsets[end] in cjkBoundaries -> cjkPenalty
                    else -> normalPenalty
                }
            }
            val total = lineCost + breakPenalty + costs[end]
            if (total < costs[start]) {
                costs[start] = total
                nextBreak[start] = end
            }
        }
    }

    return buildSet {
        var cursor = 0
        while (cursor in 0 until count) {
            cursor = nextBreak[cursor]
            if (cursor <= 0) break
            if (cursor < count) add(cursor)
        }
    }
}

/**
 * Structural equivalent of leaving LineBalancer unconstructed: dynamic word wrappers remain
 * atomic inline boxes and wrap in source order without the dynamic-programming balance pass.
 */
internal fun calculateAmllNativeFlowBreaks(
    nodes: List<AmllBalanceNode>,
    containerWidthPx: Double,
): Set<Int> {
    if (nodes.isEmpty() || containerWidthPx <= 0.0) return emptySet()
    return buildSet {
        var rowWidth = 0.0
        var rowHasContent = false
        var pendingSpaceWidth = 0.0
        nodes.forEachIndexed { index, node ->
            val nodeWidth = node.widthPx.coerceAtLeast(0.0)
            if (node.isSpace) {
                // A normal-flow text node collapses at a line edge. Keep it pending until the next
                // inline box so it can never create a whitespace-only row.
                if (rowHasContent && pendingSpaceWidth == 0.0) {
                    pendingSpaceWidth = nodeWidth
                }
                return@forEachIndexed
            }
            val candidateWidth = rowWidth + pendingSpaceWidth + nodeWidth
            if (rowHasContent && candidateWidth > containerWidthPx) {
                add(index)
                rowWidth = nodeWidth
            } else {
                rowWidth = candidateWidth
            }
            rowHasContent = true
            pendingSpaceWidth = 0.0
        }
    }
}

private fun inferAmllCjkBoundaries(fullText: String): Set<Int> = buildSet {
    var offset = 0
    segmentAmllWords(fullText).segments.forEach { segment ->
        if (offset > 0 && segment.isWordLike && splitAmllCodePoints(segment.text).any(::isAmllCjk)) {
            add(offset)
        }
        offset += segment.text.length
    }
}

internal data class AmllRenderAtom(
    val text: String,
    val romanWord: String,
    val obscene: Boolean,
    val ruby: List<AmllLyricRubySegment>,
    val hasRubyLine: Boolean,
    val hasRomanLine: Boolean,
    val chunkId: Int,
    val isSpace: Boolean,
    val isDynamic: Boolean,
    val emphasize: Boolean,
    val startTimeMs: Double,
    val endTimeMs: Double,
    val chunkStartTimeMs: Double,
    val chunkEndTimeMs: Double,
    val lineStartTimeMs: Double,
    val characterOffset: Int,
    val characterCount: Int,
    val emphasisAnchorCount: Int,
    val isLastWordChunk: Boolean,
    val isBackground: Boolean,
    val maskWordIndex: Int?,
)

private data class AmllGlyphLayout(
    val layoutResult: TextLayoutResult,
    val xPx: Float,
    val yPx: Float,
)

private data class AmllPositionedTextLayout(
    val layoutResult: TextLayoutResult,
    val xPx: Float,
    val yPx: Float,
)

/**
 * `createWord()` creates one inline span per grapheme for emphasized words. Measuring the entire
 * string and clipping it back into character ranges preserves kerning that does not exist in the
 * DOM tree and clips each character's animated shadow. Keep the two layout shapes distinct.
 */
private data class AmllAtomTextLayout(
    val wholeWord: TextLayoutResult?,
    val glyphs: List<AmllGlyphLayout>,
    val baseXpx: Float,
    val baseYpx: Float,
    val rubySegments: List<AmllPositionedTextLayout>,
    val romanWord: AmllPositionedTextLayout?,
    val widthPx: Int,
    val heightPx: Int,
)

private data class AmllAtomParentData(
    val chunkId: Int,
    val text: String,
    val isSpace: Boolean,
    val overflowPaddingPx: Int,
)

private data class AmllAtomParentDataModifier(
    val data: AmllAtomParentData,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = data
}

@Composable
internal fun AmllTimedWordLine(
    line: AmllLyricLine,
    positionState: State<Long>,
    active: Boolean,
    baseFontSizeSp: Float,
    androidPresentation: Boolean,
    isNonDynamic: Boolean = line.words.size <= 1,
    maskScale: Double = if (active) 1.0 else 0.97,
    maskRenderMode: AmllLineRenderMode = if (active) {
        AmllLineRenderMode.GRADIENT
    } else {
        AmllLineRenderMode.SOLID
    },
    animateMaskAlpha: Boolean = true,
    maskObsceneWordsMode: AmllMaskObsceneWordsMode = AmllMaskObsceneWordsMode.DISABLED,
    maskObsceneWordCharacter: String = "*",
    modifier: Modifier = Modifier,
) {
    val displayLineText = remember(
        line,
        isNonDynamic,
        maskObsceneWordsMode,
        maskObsceneWordCharacter,
    ) {
        if (line.words.isEmpty()) {
            line.mainText
        } else {
            val displayWords = if (isNonDynamic) {
                line.words.map { word ->
                    AmllSplitWord(
                        sourceId = word.id,
                        text = word.text,
                        startTimeMs = word.exactStartTimeMs,
                        endTimeMs = word.exactEndTimeMs,
                        romanWord = word.romanWord.orEmpty(),
                        obscene = word.obscene,
                        ruby = word.ruby,
                    )
                }
            } else {
                chunkAndSplitAmllLyricWords(line.words).flatMap(AmllWordChunk::words)
            }
            displayWords.joinToString(separator = "") { word ->
                processAmllObsceneWord(
                    text = word.text,
                    obscene = word.obscene,
                    mode = maskObsceneWordsMode,
                    maskCharacter = maskObsceneWordCharacter,
                )
            }
        }
    }
    val wordSegmentation = remember(displayLineText) {
        segmentAmllWords(displayLineText)
    }
    val lineBreakStrategy = amllLineBreakStrategy(wordSegmentation)
    val atoms = remember(line, isNonDynamic, wordSegmentation) {
        buildAmllRenderAtoms(
            line = line,
            isNonDynamic = isNonDynamic,
            staticSegments = wordSegmentation.segments,
            maskObsceneWordsMode = maskObsceneWordsMode,
            maskObsceneWordCharacter = maskObsceneWordCharacter,
        )
    }
    val density = LocalDensity.current
    val inheritedTextStyle = LocalTextStyle.current
    val textMeasurer = rememberTextMeasurer()
    val lineHeightMultiplier = if (androidPresentation) 1.08f else 1.2f
    val baseFontSize = with(density) { (baseFontSizeSp / fontScale).sp }
    val lineHeight = with(density) {
        (baseFontSizeSp * lineHeightMultiplier / fontScale).sp
    }
    val style = remember(inheritedTextStyle, baseFontSize, lineHeight) {
        inheritedTextStyle.merge(
            TextStyle(
                fontSize = baseFontSize,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
    val annotationFontSize = with(density) { (baseFontSizeSp * 0.5f / fontScale).sp }
    val annotationStyle = remember(inheritedTextStyle, annotationFontSize) {
        inheritedTextStyle.merge(
            TextStyle(
                fontSize = annotationFontSize,
                // `.rubyWord` and `.romanWord` both use `font-size: .5em; line-height: 1em`.
                lineHeight = annotationFontSize,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
    val fontSizePx = with(density) { baseFontSizeSp.dp.toPx() }
    if (isNonDynamic && lineBreakStrategy == AmllLineBreakStrategy.NATIVE_FLOW) {
        AmllStaticUnsegmentedLine(
            text = displayLineText,
            style = style,
            active = active,
            isDuet = line.isDuet,
            androidPresentation = androidPresentation,
            modifier = modifier,
        )
        return
    }
    val layouts = atoms.map { atom ->
        remember(atom, style, annotationStyle, fontSizePx, textMeasurer) {
            val displayText = if (atom.isDynamic && !atom.isSpace) {
                // `createWord()` renders `processObsceneWord(word).trim()`. The fixed host never
                // changes AMLL's default Disabled obscene-mask mode, so the text itself is intact.
                atom.text.trimEcmaWhitespace()
            } else {
                atom.text
            }
            val graphemes = if (atom.emphasize && atom.isDynamic && !atom.isSpace) {
                segmentAmllGraphemes(displayText).segments
            } else {
                emptyList()
            }
            val wholeWord = if (graphemes.isEmpty()) {
                textMeasurer.measure(
                    text = AnnotatedString(displayText),
                    style = style,
                    softWrap = false,
                    constraints = Constraints(),
                )
            } else {
                null
            }
            val measuredGlyphs = if (graphemes.isEmpty()) {
                emptyList()
            } else {
                graphemes.map { grapheme ->
                    textMeasurer.measure(
                        text = AnnotatedString(grapheme),
                        style = style,
                        softWrap = false,
                        constraints = Constraints(),
                    )
                }
            }
            val baseWidthPx = wholeWord?.size?.width ?: measuredGlyphs.sumOf { it.size.width }
            val baseHeightPx = wholeWord?.size?.height
                ?: (measuredGlyphs.maxOfOrNull { it.size.height } ?: 0)
            val structured = !atom.isSpace && (atom.hasRubyLine || atom.hasRomanLine)
            val measuredRuby = if (structured && atom.hasRubyLine) {
                atom.ruby
                    .filter { !it.text.isEcmaWhitespaceOnly() }
                    .map { ruby ->
                        textMeasurer.measure(
                            text = AnnotatedString(ruby.text),
                            style = annotationStyle,
                            softWrap = false,
                            constraints = Constraints(),
                        )
                    }
            } else {
                emptyList()
            }
            val romanLayout = if (structured && atom.hasRomanLine) {
                textMeasurer.measure(
                    text = AnnotatedString(
                        amllRomanWordDisplayText(
                            romanWord = atom.romanWord,
                            hasRomanLine = true,
                        ).orEmpty(),
                    ),
                    style = annotationStyle,
                    softWrap = false,
                    constraints = Constraints(),
                )
            } else {
                null
            }
            val rubyHeightPx = if (structured && atom.hasRubyLine) {
                max(
                    fontSizePx * 0.5f,
                    (measuredRuby.maxOfOrNull { it.size.height } ?: 0).toFloat(),
                )
            } else {
                0f
            }
            // `padding-inline-end: .3em` is resolved against the annotation's .5em font size.
            val romanPaddingEndPx = if (romanLayout != null) fontSizePx * 0.15f else 0f
            val romanWidthPx = (romanLayout?.size?.width ?: 0) + romanPaddingEndPx
            val rubyWidthPx = measuredRuby.sumOf { it.size.width }.toFloat()
            val bodyWidthPx = max(baseWidthPx.toFloat(), romanWidthPx)
            val contentWidthPx = if (structured && atom.hasRubyLine) {
                max(bodyWidthPx, rubyWidthPx)
            } else {
                bodyWidthPx
            }
            val baseXpx = if (structured && atom.hasRubyLine) {
                (contentWidthPx - baseWidthPx) / 2f
            } else {
                0f
            }
            val baseYpx = rubyHeightPx
            val romanXpx = if (structured && atom.hasRubyLine) {
                (contentWidthPx - romanWidthPx) / 2f
            } else {
                0f
            }
            val romanYpx = baseYpx + baseHeightPx
            var rubyXpx = (contentWidthPx - rubyWidthPx) / 2f
            val positionedRuby = measuredRuby.map { rubyLayout ->
                AmllPositionedTextLayout(
                    layoutResult = rubyLayout,
                    xPx = rubyXpx,
                    yPx = (rubyHeightPx - rubyLayout.size.height) / 2f,
                ).also {
                    rubyXpx += rubyLayout.size.width
                }
            }
            var glyphXpx = baseXpx
            val positionedGlyphs = measuredGlyphs.map { glyph ->
                AmllGlyphLayout(
                    layoutResult = glyph,
                    xPx = glyphXpx,
                    yPx = baseYpx + baseHeightPx - glyph.size.height,
                ).also {
                    glyphXpx += glyph.size.width
                }
            }
            AmllAtomTextLayout(
                wholeWord = wholeWord,
                glyphs = positionedGlyphs,
                baseXpx = baseXpx,
                baseYpx = baseYpx,
                rubySegments = positionedRuby,
                romanWord = romanLayout?.let {
                    AmllPositionedTextLayout(
                        layoutResult = it,
                        xPx = romanXpx,
                        yPx = romanYpx,
                    )
                },
                widthPx = ceil(contentWidthPx.toDouble()).toInt(),
                heightPx = ceil(
                    (
                        rubyHeightPx +
                            baseHeightPx +
                            (romanLayout?.size?.height ?: 0)
                        ).toDouble(),
                ).toInt(),
            )
        }
    }
    val staticBalanceCalibration = remember(isNonDynamic, displayLineText, style, layouts) {
        if (!isNonDynamic) {
            1.0
        } else {
            val measuredPartsWidth = layouts.sumOf(AmllAtomTextLayout::widthPx).toDouble()
            val visualWidth = textMeasurer.measure(
                text = AnnotatedString(displayLineText),
                style = style,
                softWrap = false,
                constraints = Constraints(),
            ).size.width.toDouble()
            if (measuredPartsWidth > 0.0 && visualWidth > 0.0) {
                visualWidth / measuredPartsWidth
            } else {
                1.0
            }
        }
    }
    val latestMaskScale by rememberUpdatedState(maskScale)
    val latestMaskRenderMode by rememberUpdatedState(maskRenderMode)
    var maskAlphaState by remember(line.id) { mutableStateOf(AmllMaskAlphaState()) }
    if (animateMaskAlpha) {
        LaunchedEffect(line.id) {
            var previousFrameNanos = withFrameNanos { it }
            while (true) {
                val frameNanos = withFrameNanos { it }
                val deltaSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000.0
                previousFrameNanos = frameNanos
                maskAlphaState = advanceAmllMaskAlpha(
                    current = maskAlphaState,
                    target = targetAmllMaskAlpha(
                        scale = latestMaskScale,
                        renderMode = latestMaskRenderMode,
                    ),
                    deltaSeconds = deltaSeconds,
                )
            }
        }
    } else {
        LaunchedEffect(line.id, maskScale, maskRenderMode) {
            maskAlphaState = targetAmllMaskAlpha(
                scale = maskScale,
                renderMode = maskRenderMode,
            )
        }
    }
    val renderedMaskAlphaState = if (animateMaskAlpha) {
        maskAlphaState
    } else {
        targetAmllMaskAlpha(maskScale, maskRenderMode)
    }
    val visibleMaskAlpha = visibleAmllMaskAlpha(
        animated = renderedMaskAlphaState,
        active = active,
        androidPresentation = androidPresentation,
    )
    val visibleBrightMaskAlpha = visibleMaskAlpha.brightAlpha.toFloat()
    val visibleDarkMaskAlpha = visibleMaskAlpha.darkAlpha.toFloat()
    val maskMetrics = remember(atoms, layouts, fontSizePx) {
        atoms.mapIndexedNotNull { index, atom ->
            atom.maskWordIndex?.let { maskIndex ->
                maskIndex to AmllMaskWordMetrics(
                    widthPx = layouts[index].widthPx.toDouble(),
                    heightPx = layouts[index].heightPx.toDouble(),
                    paddingPx = if (atom.emphasize) fontSizePx.toDouble() else 0.0,
                    startTimeMs = atom.startTimeMs,
                    endTimeMs = atom.endTimeMs,
                    rubySegments = atom.ruby
                        .filter { !it.text.isEcmaWhitespaceOnly() }
                        .map { ruby ->
                            AmllMaskRubySegment(
                                text = ruby.text,
                                startTimeMs = ruby.exactStartTimeMs,
                                endTimeMs = ruby.exactEndTimeMs,
                            )
                        },
                )
            }
        }.sortedBy { it.first }.map { it.second }
    }
    val maskTimelines = remember(maskMetrics, line.exactStartTimeMs, line.exactEndTimeMs) {
        maskMetrics.indices.map { index ->
            buildAmllWebMaskTimeline(
                words = maskMetrics,
                targetWordIndex = index,
                lineStartTimeMs = line.exactStartTimeMs,
                lineEndTimeMs = line.exactEndTimeMs,
            )
        }
    }

    Layout(
        modifier = modifier,
        content = {
            atoms.forEachIndexed { index, atom ->
                AmllWordAtom(
                    atom = atom,
                    textLayout = layouts[index],
                    maskTimeline = atom.maskWordIndex?.let(maskTimelines::getOrNull),
                    positionState = positionState,
                    active = active,
                    baseFontSizeSp = baseFontSizeSp,
                    androidPresentation = androidPresentation,
                    brightMaskAlpha = visibleBrightMaskAlpha,
                    darkMaskAlpha = visibleDarkMaskAlpha,
                    modifier = Modifier.then(
                        AmllAtomParentDataModifier(
                            AmllAtomParentData(
                                chunkId = atom.chunkId,
                                text = atom.text,
                                isSpace = atom.isSpace,
                                overflowPaddingPx = if (atom.emphasize) {
                                    amllEmphasisVisualOverflowPx(fontSizePx).roundToInt()
                                } else {
                                    0
                                },
                            ),
                        ),
                    ),
                )
            }
        },
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            layout(constraints.minWidth, 0) {}
        } else {
            val maxWidth = constraints.maxWidth.coerceAtLeast(1)
            val childData = measurables.mapIndexed { index, measurable ->
                (measurable.parentData as? AmllAtomParentData) ?: AmllAtomParentData(
                    chunkId = index,
                    text = "",
                    isSpace = false,
                    overflowPaddingPx = 0,
                )
            }
            val placeables = measurables.mapIndexed { index, measurable ->
                val overflow = childData[index].overflowPaddingPx * 2
                measurable.measure(
                    Constraints(
                        maxWidth = addConstraintSpace(maxWidth, overflow),
                        maxHeight = addConstraintSpace(constraints.maxHeight, overflow),
                    ),
                )
            }
            val chunks = buildList {
                var start = 0
                while (start < placeables.size) {
                    val chunkId = childData[start].chunkId
                    var end = start + 1
                    while (end < placeables.size && childData[end].chunkId == chunkId) end++
                    add(
                        AmllMeasuredChunk(
                            firstChild = start,
                            lastChildExclusive = end,
                            width = (start until end).sumOf {
                                placeables[it].width - childData[it].overflowPaddingPx * 2
                            },
                            height = (start until end).maxOf {
                                placeables[it].height - childData[it].overflowPaddingPx * 2
                            },
                            text = (start until end).joinToString(separator = "") {
                                childData[it].text
                            },
                            isSpace = (start until end).all { childData[it].isSpace },
                        ),
                    )
                    start = end
                }
            }
            val balanceNodes = chunks.map {
                AmllBalanceNode(
                    // LineBalancer calibrates Canvas segment measurements against the full
                    // text Range width only for non-dynamic lyrics.
                    widthPx = it.width * staticBalanceCalibration,
                    text = it.text,
                    isSpace = it.isSpace,
                )
            }
            val breakBefore = when (lineBreakStrategy) {
                AmllLineBreakStrategy.BALANCED -> calculateAmllBalancedBreaks(
                    nodes = balanceNodes,
                    containerWidthPx = maxWidth.toDouble(),
                )
                AmllLineBreakStrategy.NATIVE_FLOW -> calculateAmllNativeFlowBreaks(
                    nodes = balanceNodes,
                    containerWidthPx = maxWidth.toDouble(),
                )
            }
            val measureRow: (Int, Int) -> AmllMeasuredRow = if (
                lineBreakStrategy == AmllLineBreakStrategy.NATIVE_FLOW
            ) {
                { first, end -> measuredNativeFlowRow(chunks, first, end) }
            } else {
                { first, end -> measuredRow(chunks, first, end) }
            }
            val rows = mutableListOf<AmllMeasuredRow>()
            var rowStart = 0
            chunks.indices.forEach { chunkIndex ->
                if (chunkIndex > rowStart && chunkIndex in breakBefore) {
                    rows += measureRow(rowStart, chunkIndex)
                    rowStart = chunkIndex
                }
            }
            rows += measureRow(rowStart, chunks.size)
            val measuredHeight = rows.sumOf(AmllMeasuredRow::height)
            val width = maxWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = measuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

            layout(width, height) {
                var y = 0
                rows.forEach { row ->
                    var x = amllPhysicalRowStartX(
                        containerWidthPx = width,
                        rowWidthPx = row.width,
                        isDuet = line.isDuet,
                    )
                    for (chunkIndex in row.firstChunk until row.lastChunkExclusive) {
                        val chunk = chunks[chunkIndex]
                        for (childIndex in chunk.firstChild until chunk.lastChildExclusive) {
                            val placeable = placeables[childIndex]
                            val overflow = childData[childIndex].overflowPaddingPx
                            val contentWidth = placeable.width - overflow * 2
                            val contentHeight = placeable.height - overflow * 2
                            // CSS uses physical left/right and the host document has LTR geometry.
                            // `placeRelative` would mirror duet rows under an RTL system locale.
                            placeable.place(
                                x - overflow,
                                y + row.height - contentHeight - overflow,
                            )
                            x += contentWidth
                        }
                    }
                    y += row.height
                }
            }
        }
    }
}

/**
 * `rebuildElement()` writes one `textContent` node for non-dynamic lyrics. If no word segmenter
 * exists, LineBalancer is never constructed, so the browser's ordinary text flow performs wrapping.
 */
@Composable
private fun AmllStaticUnsegmentedLine(
    text: String,
    style: TextStyle,
    active: Boolean,
    isDuet: Boolean,
    androidPresentation: Boolean,
    modifier: Modifier,
) {
    val blackShadowAlpha = when {
        active -> 0.35f
        androidPresentation -> 0.55f
        else -> 0f
    }
    val flowStyle = style.copy(
        // Upstream uses physical `text-align: right`, not logical `end`.
        textAlign = if (isDuet) TextAlign.Right else TextAlign.Start,
    )
    val semanticsFreeTextModifier = Modifier
        .fillMaxWidth()
        .clearAndSetSemantics {}
    Box(modifier = modifier) {
        // This first layer owns measurement. BasicText keeps the full source string and uses the
        // platform's ordinary wrapping instead of a synthesized dictionary-free balance pass.
        BasicText(
            text = text,
            modifier = semanticsFreeTextModifier,
            style = flowStyle.copy(
                color = Color.Transparent,
                shadow = if (blackShadowAlpha > 0f) {
                    Shadow(
                        color = Color.Black.copy(alpha = blackShadowAlpha),
                        offset = Offset(0f, if (active) 4f else 2f),
                        blurRadius = if (active) 24f else 18f,
                    )
                } else {
                    null
                },
            ),
            softWrap = true,
        )
        if (active) {
            BasicText(
                text = text,
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {},
                style = flowStyle.copy(
                    color = Color.Transparent,
                    shadow = Shadow(
                        color = Color.White.copy(
                            alpha = if (androidPresentation) 0.38f else 0.35f,
                        ),
                        offset = Offset.Zero,
                        blurRadius = 10f,
                    ),
                ),
                softWrap = true,
            )
        }
        BasicText(
            text = text,
            modifier = Modifier
                .matchParentSize()
                .clearAndSetSemantics {},
            style = flowStyle.copy(
                color = if (active) {
                    Color.White.copy(alpha = 0.98f)
                } else {
                    Color(0xFFCDCDCD)
                },
                shadow = null,
            ),
            softWrap = true,
        )
    }
}

private fun addConstraintSpace(value: Int, extra: Int): Int =
    if (value == Constraints.Infinity) {
        Constraints.Infinity
    } else {
        min(Constraints.Infinity.toLong(), value.toLong() + extra.toLong()).toInt()
    }

/**
 * The upstream emphasized word and grapheme spans use `margin: -1em; padding: 1em`. The padding
 * expands paint bounds while the negative margin keeps layout width unchanged.
 */
private fun amllEmphasisVisualOverflowPx(fontSizePx: Float): Float = fontSizePx

private data class AmllMeasuredChunk(
    val firstChild: Int,
    val lastChildExclusive: Int,
    val width: Int,
    val height: Int,
    val text: String,
    val isSpace: Boolean,
)

private data class AmllMeasuredRow(
    val firstChunk: Int,
    val lastChunkExclusive: Int,
    val width: Int,
    val height: Int,
)

private fun measuredRow(
    chunks: List<AmllMeasuredChunk>,
    first: Int,
    end: Int,
): AmllMeasuredRow = AmllMeasuredRow(
    firstChunk = first,
    lastChunkExclusive = end,
    width = (first until end).sumOf { chunks[it].width },
    height = (first until end).maxOfOrNull { chunks[it].height } ?: 0,
)

/**
 * CSS normal-flow whitespace at the beginning or end of a line is not painted and contributes no
 * inline advance. Pure-space top-level text nodes therefore stay out of each placed row boundary.
 */
private fun measuredNativeFlowRow(
    chunks: List<AmllMeasuredChunk>,
    first: Int,
    end: Int,
): AmllMeasuredRow {
    var contentFirst = first
    while (contentFirst < end && chunks[contentFirst].isSpace) contentFirst++
    var contentEnd = end
    while (contentEnd > contentFirst && chunks[contentEnd - 1].isSpace) contentEnd--
    return measuredRow(chunks, contentFirst, contentEnd)
}

@Composable
private fun AmllWordAtom(
    atom: AmllRenderAtom,
    textLayout: AmllAtomTextLayout,
    maskTimeline: AmllWebMaskTimeline?,
    positionState: State<Long>,
    active: Boolean,
    baseFontSizeSp: Float,
    androidPresentation: Boolean,
    brightMaskAlpha: Float,
    darkMaskAlpha: Float,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val fontSizePx = with(density) { baseFontSizeSp.dp.toPx() }
    val overflowPaddingPx = if (atom.emphasize) {
        amllEmphasisVisualOverflowPx(fontSizePx)
    } else {
        0f
    }
    val widthDp = with(density) {
        (textLayout.widthPx + overflowPaddingPx * 2f).toDp()
    }
    val heightDp = with(density) {
        (textLayout.heightPx + overflowPaddingPx * 2f).toDp()
    }
    val reverseNormalFloatProgress = remember(
        atom.startTimeMs,
        atom.endTimeMs,
        atom.isDynamic,
    ) {
        Animatable(0f)
    }
    var previousActive by remember(
        atom.startTimeMs,
        atom.endTimeMs,
        atom.isDynamic,
    ) {
        mutableStateOf(active)
    }
    var hasEverBeenActive by remember(
        atom.lineStartTimeMs,
        atom.startTimeMs,
        atom.endTimeMs,
    ) {
        mutableStateOf(active)
    }
    var frozenMaskPositionMs by remember(
        atom.lineStartTimeMs,
        atom.startTimeMs,
        atom.endTimeMs,
    ) {
        mutableStateOf(atom.lineStartTimeMs)
    }
    LaunchedEffect(active, atom.startTimeMs, atom.endTimeMs, atom.isDynamic) {
        val wasActive = previousActive
        if (wasActive && !active && atom.isDynamic) {
            val snapshotTimeMs = positionState.value.toDouble()
            val progress = computeAmllNormalFloatProgress(
                positionMs = snapshotTimeMs,
                startTimeMs = atom.startTimeMs,
                endTimeMs = atom.endTimeMs,
            )
            frozenMaskPositionMs = snapshotTimeMs
            reverseNormalFloatProgress.snapTo(progress.toFloat())
            previousActive = false
            val reverseDurationMs = (
                progress * max(1_000.0, atom.endTimeMs - atom.startTimeMs)
                ).coerceIn(0.0, Int.MAX_VALUE.toDouble()).roundToInt()
            if (reverseDurationMs > 0) {
                reverseNormalFloatProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = reverseDurationMs,
                        easing = LinearEasing,
                    ),
                )
            }
        } else {
            previousActive = active
            if (active || !atom.isDynamic) {
                if (active) hasEverBeenActive = true
                reverseNormalFloatProgress.snapTo(0f)
            }
        }
    }

    Canvas(
        modifier = modifier
            .width(widthDp)
            .height(heightDp),
    ) {
        if (atom.isSpace || atom.text.isEmpty()) return@Canvas
        val now = positionState.value.toDouble()
        val normalFloat = when {
            !atom.isDynamic -> 0.0
            active -> computeAmllNormalFloatOffsetEm(
                positionMs = now,
                startTimeMs = atom.startTimeMs,
                endTimeMs = atom.endTimeMs,
                isBackground = atom.isBackground,
            )
            previousActive -> computeAmllNormalFloatOffsetEm(
                positionMs = now,
                startTimeMs = atom.startTimeMs,
                endTimeMs = atom.endTimeMs,
                isBackground = atom.isBackground,
            )
            else -> computeAmllNormalFloatOffsetFromProgressEm(
                progress = reverseNormalFloatProgress.value.toDouble(),
                isBackground = atom.isBackground,
            )
        }
        val maskPositionMs = if (active || previousActive) now else frozenMaskPositionMs
        val elementAnimationPositionMs =
            if (active || hasEverBeenActive) now else atom.lineStartTimeMs
        val maskFrame = if (atom.isDynamic && maskTimeline != null) {
            sampleAmllWebMaskTimeline(
                timeline = maskTimeline,
                positionMs = maskPositionMs,
                lineStartTimeMs = atom.lineStartTimeMs,
            )
        } else {
            null
        }

        withTransform({
            translate(
                left = overflowPaddingPx,
                top = overflowPaddingPx,
            )
        }) {
            if (
                atom.isDynamic &&
                !atom.isSpace &&
                (atom.hasRubyLine || atom.hasRomanLine)
            ) {
                drawStructuredAmllAtom(
                    atom = atom,
                    textLayout = textLayout,
                    maskFrame = maskFrame,
                    nowMs = elementAnimationPositionMs,
                    normalFloatEm = normalFloat,
                    fontSizePx = fontSizePx,
                    brightMaskAlpha = brightMaskAlpha,
                    darkMaskAlpha = darkMaskAlpha,
                    active = active,
                    androidPresentation = androidPresentation,
                    overflowPaddingPx = overflowPaddingPx,
                )
            } else if (atom.emphasize && atom.isDynamic) {
                drawEmphasizedAtom(
                    atom = atom,
                    textLayout = textLayout,
                    maskFrame = maskFrame,
                    nowMs = elementAnimationPositionMs,
                    normalFloatEm = normalFloat,
                    fontSizePx = fontSizePx,
                    brightMaskAlpha = brightMaskAlpha,
                    darkMaskAlpha = darkMaskAlpha,
                    active = active,
                    androidPresentation = androidPresentation,
                    overflowPaddingPx = overflowPaddingPx,
                )
            } else {
                textLayout.wholeWord?.let { wholeWord ->
                    withTransform({
                        translate(top = (normalFloat * fontSizePx).toFloat())
                    }) {
                        when {
                            !atom.isDynamic -> drawHostUnmaskedText(
                                layoutResult = wholeWord,
                                active = active,
                                androidPresentation = androidPresentation,
                                topLeft = Offset(textLayout.baseXpx, textLayout.baseYpx),
                            )
                            maskFrame != null -> drawHostMaskedText(
                                layoutResult = wholeWord,
                                maskFrame = maskFrame,
                                textColor = if (active) {
                                    Color.White.copy(alpha = 0.98f)
                                } else {
                                    Color(0xFFCDCDCD)
                                },
                                brightMaskAlpha = brightMaskAlpha,
                                darkMaskAlpha = darkMaskAlpha,
                                whiteGlowAlpha = if (active) {
                                    if (androidPresentation) 0.38f else 0.35f
                                } else {
                                    0f
                                },
                                blackShadowAlpha = when {
                                    active -> 0.35f
                                    androidPresentation -> 0.55f
                                    else -> 0f
                                },
                                blackShadowOffsetY = if (active) 4f else 2f,
                                blackShadowRadiusPx = if (active) 24f else 18f,
                                overflowPaddingPx = overflowPaddingPx,
                                topLeft = Offset(textLayout.baseXpx, textLayout.baseYpx),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStructuredAmllAtom(
    atom: AmllRenderAtom,
    textLayout: AmllAtomTextLayout,
    maskFrame: AmllWebMaskFrame?,
    nowMs: Double,
    normalFloatEm: Double,
    fontSizePx: Float,
    brightMaskAlpha: Float,
    darkMaskAlpha: Float,
    active: Boolean,
    androidPresentation: Boolean,
    overflowPaddingPx: Float,
) {
    val wordBodyGlow = if (atom.hasRubyLine && atom.emphasize) {
        amllWordBodyGlowSpec(androidPresentation).toDevicePixels(density.toDouble())
    } else {
        null
    }
    withTransform({
        translate(top = (normalFloatEm * fontSizePx).toFloat())
    }) {
        val drawContents: DrawScope.() -> Unit = {
            textLayout.rubySegments.forEach { ruby ->
                drawHostUnmaskedText(
                    layoutResult = ruby.layoutResult,
                    active = active,
                    androidPresentation = androidPresentation,
                    topLeft = Offset(ruby.xPx, ruby.yPx),
                )
            }
            if (atom.emphasize && textLayout.glyphs.isNotEmpty()) {
                drawEmphasizedGlyphs(
                    atom = atom,
                    textLayout = textLayout,
                    nowMs = nowMs,
                    fontSizePx = fontSizePx,
                    active = active,
                    androidPresentation = androidPresentation,
                    wordBodyGlow = wordBodyGlow,
                )
            } else {
                textLayout.wholeWord?.let { wholeWord ->
                    val topLeft = Offset(textLayout.baseXpx, textLayout.baseYpx)
                    if (wordBodyGlow != null) {
                        drawWordBodyGlowText(wholeWord, topLeft, wordBodyGlow)
                    } else {
                        drawHostUnmaskedText(
                            layoutResult = wholeWord,
                            active = active,
                            androidPresentation = androidPresentation,
                            topLeft = topLeft,
                        )
                    }
                }
            }
            textLayout.romanWord?.let { roman ->
                val topLeft = Offset(roman.xPx, roman.yPx)
                if (wordBodyGlow != null) {
                    drawWordBodyGlowText(roman.layoutResult, topLeft, wordBodyGlow)
                } else {
                    drawHostUnmaskedText(
                        layoutResult = roman.layoutResult,
                        active = active,
                        androidPresentation = androidPresentation,
                        topLeft = topLeft,
                    )
                }
            }
        }
        if (maskFrame != null) {
            withAmllMaskLayer(
                maskFrame = maskFrame,
                brightMaskAlpha = brightMaskAlpha,
                darkMaskAlpha = darkMaskAlpha,
                contentSize = Size(
                    width = textLayout.widthPx.toFloat(),
                    height = textLayout.heightPx.toFloat(),
                ),
                overflowPaddingPx = overflowPaddingPx,
                drawContent = drawContents,
            )
        } else {
            drawContents()
        }
    }
}

private fun DrawScope.drawHostUnmaskedText(
    layoutResult: TextLayoutResult,
    active: Boolean,
    androidPresentation: Boolean,
    topLeft: Offset = Offset.Zero,
) {
    val blackShadowAlpha = when {
        active -> 0.35f
        androidPresentation -> 0.55f
        else -> 0f
    }
    if (blackShadowAlpha > 0f) {
        drawText(
            textLayoutResult = layoutResult,
            topLeft = topLeft,
            color = Color.Transparent,
            shadow = Shadow(
                color = Color.Black.copy(alpha = blackShadowAlpha),
                offset = Offset(0f, if (active) 4f else 2f),
                blurRadius = if (active) 24f else 18f,
            ),
        )
    }
    if (active) {
        drawText(
            textLayoutResult = layoutResult,
            topLeft = topLeft,
            color = Color.Transparent,
            shadow = Shadow(
                color = Color.White.copy(
                    alpha = if (androidPresentation) 0.38f else 0.35f,
                ),
                offset = Offset.Zero,
                blurRadius = 10f,
            ),
        )
    }
    drawText(
        textLayoutResult = layoutResult,
        topLeft = topLeft,
        color = if (active) Color.White.copy(alpha = 0.98f) else Color(0xFFCDCDCD),
    )
}

private fun DrawScope.drawEmphasizedAtom(
    atom: AmllRenderAtom,
    textLayout: AmllAtomTextLayout,
    maskFrame: AmllWebMaskFrame?,
    nowMs: Double,
    normalFloatEm: Double,
    fontSizePx: Float,
    brightMaskAlpha: Float,
    darkMaskAlpha: Float,
    active: Boolean,
    androidPresentation: Boolean,
    overflowPaddingPx: Float,
) {
    if (textLayout.glyphs.isEmpty() || maskFrame == null) return
    // `float-word` transforms the masked main word element, so its translation must move the mask
    // together with the text. The two grapheme transforms remain inside that parent mask.
    withTransform({
        translate(top = (normalFloatEm * fontSizePx).toFloat())
    }) {
        withAmllMaskLayer(
            maskFrame = maskFrame,
            brightMaskAlpha = brightMaskAlpha,
            darkMaskAlpha = darkMaskAlpha,
            contentSize = Size(
                width = textLayout.widthPx.toFloat(),
                height = textLayout.heightPx.toFloat(),
            ),
            overflowPaddingPx = overflowPaddingPx,
        ) {
            drawEmphasizedGlyphs(
                atom = atom,
                textLayout = textLayout,
                nowMs = nowMs,
                fontSizePx = fontSizePx,
                active = active,
                androidPresentation = androidPresentation,
                wordBodyGlow = null,
            )
        }
    }
}

private fun DrawScope.drawEmphasizedGlyphs(
    atom: AmllRenderAtom,
    textLayout: AmllAtomTextLayout,
    nowMs: Double,
    fontSizePx: Float,
    active: Boolean,
    androidPresentation: Boolean,
    wordBodyGlow: AmllWordBodyGlowSpec?,
) {
    textLayout.glyphs.forEachIndexed { localIndex, glyph ->
        val bounds = Rect(
            left = glyph.xPx,
            top = glyph.yPx,
            right = glyph.xPx + glyph.layoutResult.size.width,
            bottom = glyph.yPx + glyph.layoutResult.size.height,
        )
        val frame = computeAmllEmphasisFrame(
            positionMs = nowMs,
            mergedStartTimeMs = atom.chunkStartTimeMs,
            mergedEndTimeMs = atom.chunkEndTimeMs,
            characterIndex = atom.characterOffset + localIndex,
            characterCount = atom.characterCount,
            anchorCharacterCount = atom.emphasisAnchorCount,
            isLastWordChunk = atom.isLastWordChunk,
            isBackground = atom.isBackground,
            lineStartTimeMs = atom.lineStartTimeMs,
        )
        withTransform({
            // Compose/Skia concatenates in call order. This yields
            // origin * scale * childTranslate * -origin, matching CSS
            // `matrix3d(scale) translate(...)` plus the appended additive float.
            scale(
                scaleX = frame.scale.toFloat(),
                scaleY = frame.scale.toFloat(),
                pivot = bounds.center,
            )
            translate(
                left = (frame.offsetXEm * fontSizePx).toFloat(),
                top = (
                    (frame.offsetYEm + frame.floatOffsetYEm) * fontSizePx
                    ).toFloat(),
            )
        }) {
            val glyphTopLeft = Offset(glyph.xPx, glyph.yPx)
            if (wordBodyGlow != null) {
                drawShadowText(
                    layoutResult = glyph.layoutResult,
                    topLeft = glyphTopLeft,
                    shadow = wordBodyGlow.dropShadow,
                )
            }
            computeAmllEmphasisShadows(
                frame = frame,
                active = active,
                androidPresentation = androidPresentation,
                fontSizePx = fontSizePx.toDouble(),
                wordBodyGlow = wordBodyGlow,
            ).asReversed().forEach { shadow ->
                drawShadowText(
                    layoutResult = glyph.layoutResult,
                    topLeft = glyphTopLeft,
                    shadow = shadow,
                )
            }
            if (wordBodyGlow != null) {
                drawText(
                    textLayoutResult = glyph.layoutResult,
                    topLeft = glyphTopLeft,
                    color = Color.White.copy(alpha = wordBodyGlow.strokeAlpha.toFloat()),
                    drawStyle = Stroke(width = wordBodyGlow.strokeWidthPx.toFloat()),
                )
            }
            drawText(
                textLayoutResult = glyph.layoutResult,
                topLeft = glyphTopLeft,
                color = if (wordBodyGlow != null) {
                    Color.White.copy(alpha = wordBodyGlow.foregroundAlpha.toFloat())
                } else if (active) {
                    Color.White.copy(alpha = 0.98f)
                } else {
                    Color(0xFFCDCDCD)
                },
            )
        }
    }
}

private fun DrawScope.drawWordBodyGlowText(
    layoutResult: TextLayoutResult,
    topLeft: Offset,
    spec: AmllWordBodyGlowSpec,
) {
    drawShadowText(layoutResult, topLeft, spec.dropShadow)
    spec.textShadows.asReversed().forEach { shadow ->
        drawShadowText(layoutResult, topLeft, shadow)
    }
    drawText(
        textLayoutResult = layoutResult,
        topLeft = topLeft,
        color = Color.White.copy(alpha = spec.strokeAlpha.toFloat()),
        drawStyle = Stroke(width = spec.strokeWidthPx.toFloat()),
    )
    drawText(
        textLayoutResult = layoutResult,
        topLeft = topLeft,
        color = Color.White.copy(alpha = spec.foregroundAlpha.toFloat()),
    )
}

private fun DrawScope.drawShadowText(
    layoutResult: TextLayoutResult,
    topLeft: Offset,
    shadow: AmllShadowSpec,
) {
    if (shadow.alpha <= 0.0) return
    drawText(
        textLayoutResult = layoutResult,
        topLeft = topLeft,
        color = Color.Transparent,
        shadow = Shadow(
            color = Color(
                red = shadow.red.toFloat(),
                green = shadow.green.toFloat(),
                blue = shadow.blue.toFloat(),
                alpha = shadow.alpha.toFloat(),
            ),
            offset = Offset(
                x = shadow.offsetXpx.toFloat(),
                y = shadow.offsetYpx.toFloat(),
            ),
            blurRadius = shadow.blurRadiusPx.toFloat(),
        ),
    )
}

private fun DrawScope.drawHostMaskedText(
    layoutResult: TextLayoutResult,
    maskFrame: AmllWebMaskFrame,
    textColor: Color,
    brightMaskAlpha: Float,
    darkMaskAlpha: Float,
    whiteGlowAlpha: Float,
    whiteGlowRadiusPx: Float = 10f,
    blackShadowAlpha: Float,
    blackShadowOffsetY: Float,
    blackShadowRadiusPx: Float,
    overflowPaddingPx: Float,
    topLeft: Offset = Offset.Zero,
) {
    withAmllMaskLayer(
        maskFrame = maskFrame,
        brightMaskAlpha = brightMaskAlpha,
        darkMaskAlpha = darkMaskAlpha,
        contentSize = Size(
            width = topLeft.x + layoutResult.size.width,
            height = topLeft.y + layoutResult.size.height,
        ),
        overflowPaddingPx = overflowPaddingPx,
    ) {
        // The second CSS shadow is behind the first. Android's inactive root shadow is a separate
        // inherited declaration, hence its different y-offset, radius and alpha.
        if (blackShadowAlpha > 0f) {
            drawText(
                textLayoutResult = layoutResult,
                topLeft = topLeft,
                color = Color.Transparent,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = blackShadowAlpha.coerceIn(0f, 1f)),
                    offset = Offset(0f, blackShadowOffsetY),
                    blurRadius = blackShadowRadiusPx.coerceAtLeast(0f),
                ),
            )
        }
        if (whiteGlowAlpha > 0f) {
            drawText(
                textLayoutResult = layoutResult,
                topLeft = topLeft,
                color = Color.Transparent,
                shadow = Shadow(
                    color = Color.White.copy(alpha = whiteGlowAlpha.coerceIn(0f, 1f)),
                    offset = Offset.Zero,
                    blurRadius = whiteGlowRadiusPx.coerceAtLeast(0f),
                ),
            )
        }
        drawText(
            textLayoutResult = layoutResult,
            topLeft = topLeft,
            color = textColor,
        )
    }
}

/**
 * CSS mask-image applies after text color, every text-shadow and filter output have been composed.
 * A saved layer followed by DstIn reproduces that ordering; drawing a dark text then a bright
 * overlay does not, because the two shadow stacks would retain different alpha.
 */
private fun DrawScope.withAmllMaskLayer(
    maskFrame: AmllWebMaskFrame,
    brightMaskAlpha: Float,
    darkMaskAlpha: Float,
    contentSize: Size,
    overflowPaddingPx: Float,
    drawContent: DrawScope.() -> Unit,
) {
    val layerBounds = Rect(
        left = -overflowPaddingPx,
        top = -overflowPaddingPx,
        right = contentSize.width + overflowPaddingPx,
        bottom = contentSize.height + overflowPaddingPx,
    )
    drawIntoCanvas { canvas ->
        canvas.saveLayer(layerBounds, Paint())
    }
    drawContent()
    val start = maskFrame.brightBoundaryPx.toFloat()
    val end = start + maskFrame.fadeWidthPx.toFloat().coerceAtLeast(0.001f)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Black.copy(alpha = brightMaskAlpha.coerceIn(0f, 1f)),
                Color.Black.copy(alpha = darkMaskAlpha.coerceIn(0f, 1f)),
            ),
            startX = start,
            endX = end,
        ),
        topLeft = layerBounds.topLeft,
        size = layerBounds.size,
        blendMode = BlendMode.DstIn,
    )
    drawIntoCanvas { canvas ->
        canvas.restore()
    }
}

private fun computeAmllNormalFloatOffsetEm(
    positionMs: Double,
    startTimeMs: Double,
    endTimeMs: Double,
    isBackground: Boolean,
): Double = computeAmllNormalFloatOffsetFromProgressEm(
    progress = computeAmllNormalFloatProgress(positionMs, startTimeMs, endTimeMs),
    isBackground = isBackground,
)

internal fun computeAmllNormalFloatProgress(
    positionMs: Double,
    startTimeMs: Double,
    endTimeMs: Double,
): Double {
    val duration = max(1_000.0, endTimeMs - startTimeMs)
    return ((positionMs - startTimeMs) / duration).coerceIn(0.0, 1.0)
}

internal fun computeAmllNormalFloatOffsetFromProgressEm(
    progress: Double,
    isBackground: Boolean,
): Double {
    val amount = if (isBackground) 0.10 else 0.05
    return -cubicBezierYForX(
        progress.coerceIn(0.0, 1.0),
        0.0,
        0.0,
        0.58,
        1.0,
    ) * amount
}

internal fun buildAmllRenderAtoms(
    line: AmllLyricLine,
    isNonDynamic: Boolean,
    staticSegments: List<AmllWordSegment>,
    maskObsceneWordsMode: AmllMaskObsceneWordsMode = AmllMaskObsceneWordsMode.DISABLED,
    maskObsceneWordCharacter: String = "*",
): List<AmllRenderAtom> {
    val dynamic = !isNonDynamic
    if (!dynamic) {
        return staticSegments.mapIndexed { index, segment ->
            AmllRenderAtom(
                text = segment.text,
                romanWord = "",
                obscene = false,
                ruby = emptyList(),
                hasRubyLine = false,
                hasRomanLine = false,
                chunkId = index,
                isSpace = segment.text.isEcmaWhitespaceOnly(),
                isDynamic = false,
                emphasize = false,
                startTimeMs = line.exactStartTimeMs,
                endTimeMs = line.exactEndTimeMs,
                chunkStartTimeMs = line.exactStartTimeMs,
                chunkEndTimeMs = line.exactEndTimeMs,
                lineStartTimeMs = line.exactStartTimeMs,
                characterOffset = 0,
                characterCount = 1,
                emphasisAnchorCount = 1,
                isLastWordChunk = false,
                isBackground = line.isBackground,
                maskWordIndex = null,
            )
        }
    }

    val chunks = chunkAndSplitAmllLyricWords(line.words)
    val hasRubyLine = line.words.any { it.ruby.isNotEmpty() }
    val hasRomanLine = line.words.any {
        it.romanWord.orEmpty().trimEcmaWhitespace().isNotEmpty()
    }
    val lastSourceWord = line.words.lastOrNull()?.text.orEmpty()
    var maskWordIndex = 0
    return buildList {
        chunks.forEachIndexed { chunkIndex, chunk ->
            val nonSpaceWords = chunk.words.filterNot { it.text.isEcmaWhitespaceOnly() }
            val mergedText = chunk.text
            val chunkStart = nonSpaceWords.minOfOrNull(AmllSplitWord::startTimeMs)
                ?: line.exactStartTimeMs
            val chunkEnd = nonSpaceWords.maxOfOrNull(AmllSplitWord::endTimeMs)
                ?: chunkStart
            val mergedWord = AmllSplitWord(
                sourceId = "merged:$chunkIndex",
                text = mergedText,
                startTimeMs = chunkStart,
                endTimeMs = chunkEnd,
                romanWord = "",
                obscene = false,
            )
            val emphasize = nonSpaceWords.any(::shouldEmphasizeAmllWord) ||
                (!isAmllCjk(mergedText) && shouldEmphasizeAmllWord(mergedWord))
            val characterCount = nonSpaceWords.sumOf {
                segmentAmllGraphemes(it.text.trimEcmaWhitespace()).segments.size
            }.coerceAtLeast(1)
            // `getRubyCharCount()` deliberately does not filter blank ruby segments.
            val rubyCharacterCount = chunk.words.sumOf { word ->
                word.ruby.sumOf { ruby -> ruby.text.length }
            }
            val emphasisAnchorCount = if (rubyCharacterCount > 0) {
                rubyCharacterCount
            } else {
                characterCount
            }
            var characterOffset = 0

            chunk.words.forEach { word ->
                val isSpace = word.text.isEcmaWhitespaceOnly()
                val graphemeCount = if (isSpace) {
                    0
                } else {
                    segmentAmllGraphemes(word.text.trimEcmaWhitespace()).segments.size
                }
                add(
                    AmllRenderAtom(
                        text = processAmllObsceneWord(
                            text = word.text,
                            obscene = word.obscene,
                            mode = maskObsceneWordsMode,
                            maskCharacter = maskObsceneWordCharacter,
                        ),
                        romanWord = word.romanWord,
                        obscene = word.obscene,
                        ruby = word.ruby,
                        hasRubyLine = hasRubyLine,
                        hasRomanLine = hasRomanLine,
                        chunkId = chunkIndex,
                        isSpace = isSpace,
                        isDynamic = true,
                        emphasize = emphasize,
                        startTimeMs = word.startTimeMs,
                        endTimeMs = word.endTimeMs,
                        chunkStartTimeMs = chunkStart,
                        chunkEndTimeMs = chunkEnd,
                        lineStartTimeMs = line.exactStartTimeMs,
                        characterOffset = characterOffset,
                        characterCount = characterCount,
                        emphasisAnchorCount = emphasisAnchorCount,
                        // JavaScript `String.includes("")` is true; retain that edge case too.
                        isLastWordChunk = mergedText.contains(lastSourceWord),
                        isBackground = line.isBackground,
                        maskWordIndex = if (isSpace) null else maskWordIndex++,
                    ),
                )
                characterOffset += graphemeCount
            }
        }
    }
}

private fun shouldEmphasizeAmllWord(word: AmllSplitWord): Boolean {
    val duration = word.endTimeMs - word.startTimeMs
    val trimmedLength = word.text.trimEcmaWhitespace().length
    return if (isAmllCjk(word.text)) {
        duration >= 1_000.0
    } else {
        duration >= 1_000.0 && trimmedLength in 2..7
    }
}

private fun lerp(start: Double, end: Double, fraction: Double): Double =
    start + (end - start) * fraction

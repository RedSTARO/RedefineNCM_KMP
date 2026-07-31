/*
 * Derived from @applemusic-like-lyrics/core 0.5.2:
 * packages/core/src/lyric-player/base/layout.ts,
 * packages/core/src/lyric-player/base/timeline.ts,
 * packages/core/src/lyric-player/base/group.ts, and
 * packages/core/src/lyric-player/base/index.ts.
 * Kotlin Multiplatform translation and modifications dated 2026-07-26.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * This derivative file is licensed under the GNU Affero General Public License v3.0 only.
 * Upstream source: https://github.com/amll-dev/applemusic-like-lyrics
 */

package com.leejlredstar.redefinencm.kmp.ui.amll

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal enum class AmllLayoutAlignAnchor {
    TOP,
    CENTER,
    BOTTOM,
}

/**
 * The data pass owns grouping; the player engine consumes that exact optimized group directly.
 */
internal typealias AmllEngineLineGroup = AmllLyricGroup

internal data class AmllTimelineState(
    var currentTimeMs: Double = 0.0,
    var lastCurrentTimeMs: Double = 0.0,
    var hotGroups: MutableSet<Int> = linkedSetOf(),
    var bufferedGroups: MutableSet<Int> = linkedSetOf(),
    var scrollToIndex: Int = 0,
    var isSeeking: Boolean = false,
    var isPlaying: Boolean = true,
    var initialLayoutFinished: Boolean = false,
)

internal data class AmllPlayerTimeStateResult(
    val nextHotGroups: Set<Int>,
    val addedIds: Set<Int>,
    val removedHotIds: Set<Int>,
    val removedBufferedIds: Set<Int>,
)

/**
 * Returns whether a monotonically advancing clock crossed a lyric start/end boundary.
 *
 * The lower bound is exclusive because [AmllTimelineState.currentTimeMs] has already been
 * committed for that instant. The upper bound is inclusive because AMLL starts groups at
 * `startTime <= currentTime` and ends them at `endTime <= currentTime`.
 */
internal fun crossesAmllTimelineBoundary(
    boundariesMs: DoubleArray,
    fromExclusiveMs: Double,
    toInclusiveMs: Double,
): Boolean {
    if (
        !fromExclusiveMs.isFinite() ||
        !toInclusiveMs.isFinite() ||
        toInclusiveMs < fromExclusiveMs
    ) {
        return true
    }

    var low = 0
    var high = boundariesMs.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (boundariesMs[middle] <= fromExclusiveMs) {
            low = middle + 1
        } else {
            high = middle
        }
    }
    return low < boundariesMs.size && boundariesMs[low] <= toInclusiveMs
}

/**
 * Direct translation of `computePlayerTimeState`.
 */
internal fun computeAmllPlayerTimeState(
    timeMs: Double,
    currentGroups: List<AmllEngineLineGroup>,
    timelineState: AmllTimelineState,
): AmllPlayerTimeStateResult {
    val nextHotGroups = LinkedHashSet(timelineState.hotGroups)
    val addedIds = linkedSetOf<Int>()
    val removedHotIds = linkedSetOf<Int>()
    val removedBufferedIds = linkedSetOf<Int>()

    for (lastHotId in timelineState.hotGroups) {
        val group = currentGroups.getOrNull(lastHotId)
        if (
            group == null ||
            timeMs < group.exactStartTimeMs ||
            group.exactEndTimeMs <= timeMs
        ) {
            nextHotGroups.remove(lastHotId)
            removedHotIds += lastHotId
        }
    }

    for (id in currentGroups.indices) {
        val group = currentGroups[id]
        if (
            group.exactStartTimeMs <= timeMs &&
            group.exactEndTimeMs > timeMs &&
            id !in nextHotGroups
        ) {
            nextHotGroups += id
            addedIds += id
        }
    }

    for (id in timelineState.bufferedGroups) {
        if (id !in nextHotGroups) {
            removedBufferedIds += id
        }
    }

    return AmllPlayerTimeStateResult(
        nextHotGroups = nextHotGroups,
        addedIds = addedIds,
        removedHotIds = removedHotIds,
        removedBufferedIds = removedBufferedIds,
    )
}

/**
 * Direct translation of `pickScrollToIndexForSeek`.
 */
internal fun pickAmllScrollToIndexForSeek(
    timeMs: Double,
    currentGroups: List<AmllEngineLineGroup>,
    bufferedGroups: Set<Int>,
): Int {
    if (bufferedGroups.isNotEmpty()) {
        return bufferedGroups.min()
    }
    val foundIndex = currentGroups.indexOfFirst { group ->
        group.exactStartTimeMs >= timeMs
    }
    return if (foundIndex == -1) currentGroups.size else foundIndex
}

internal data class AmllTimelineCommitResult(
    val shouldLayout: Boolean,
    val shouldResetScroll: Boolean,
    val groupsToEnable: List<Int>,
    val groupsToDisable: List<Int>,
)

/**
 * Direct translation of `commitPlayerTimeState`. The supplied timeline state is updated in place.
 */
internal fun commitAmllPlayerTimeState(
    timelineState: AmllTimelineState,
    timeMs: Double,
    currentGroups: List<AmllEngineLineGroup>,
    hasBottomContent: Boolean,
    stateResult: AmllPlayerTimeStateResult,
): AmllTimelineCommitResult {
    val addedIds = stateResult.addedIds
    val removedHotIds = stateResult.removedHotIds
    val removedBufferedIds = stateResult.removedBufferedIds
    val isSeeking = timelineState.isSeeking

    timelineState.currentTimeMs = timeMs
    timelineState.hotGroups = LinkedHashSet(stateResult.nextHotGroups)

    var shouldLayout = false
    var shouldResetScroll = false
    val groupsToEnable = mutableListOf<Int>()
    val groupsToDisable = linkedSetOf<Int>()

    if (isSeeking) {
        timelineState.bufferedGroups = LinkedHashSet(timelineState.hotGroups)
        timelineState.scrollToIndex = pickAmllScrollToIndexForSeek(
            timeMs = timeMs,
            currentGroups = currentGroups,
            bufferedGroups = timelineState.bufferedGroups,
        )
        groupsToDisable += removedHotIds
        groupsToEnable += timelineState.hotGroups
        groupsToDisable += removedBufferedIds

        shouldResetScroll = true
        shouldLayout = true
    } else if (addedIds.isNotEmpty()) {
        for (id in addedIds) {
            timelineState.bufferedGroups += id
            groupsToEnable += id
        }
        for (id in removedBufferedIds) {
            timelineState.bufferedGroups -= id
            groupsToDisable += id
        }
        if (timelineState.bufferedGroups.isNotEmpty()) {
            timelineState.scrollToIndex = timelineState.bufferedGroups.min()
        }
        shouldLayout = true
    } else if (
        removedBufferedIds.isNotEmpty() &&
        amllSetsEqual(removedBufferedIds, timelineState.bufferedGroups)
    ) {
        for (id in timelineState.bufferedGroups.toList()) {
            if (id in timelineState.hotGroups) continue
            timelineState.bufferedGroups -= id
            groupsToDisable += id
        }
        shouldLayout = true
    }

    if (timelineState.bufferedGroups.isEmpty() && currentGroups.isNotEmpty()) {
        val lastGroup = currentGroups.last()
        if (timeMs >= lastGroup.exactEndTimeMs) {
            val targetIndex = if (hasBottomContent) {
                currentGroups.size
            } else {
                currentGroups.lastIndex
            }
            if (timelineState.scrollToIndex != targetIndex) {
                timelineState.scrollToIndex = targetIndex
                shouldLayout = true
            }
        }
    }

    timelineState.lastCurrentTimeMs = timeMs

    return AmllTimelineCommitResult(
        shouldLayout = shouldLayout,
        shouldResetScroll = shouldResetScroll,
        groupsToEnable = groupsToEnable,
        groupsToDisable = groupsToDisable.toList(),
    )
}

internal data class AmllPlayerInterlude(
    val startTimeMs: Double,
    val endTimeMs: Double,
    val anchorLineIndex: Int,
    val isNextDuet: Boolean,
)

/**
 * Direct translation of `computeCurrentInterlude`.
 */
internal fun computeAmllCurrentInterlude(
    currentTimeMs: Double,
    scrollToIndex: Int,
    currentGroups: List<AmllEngineLineGroup>,
): AmllPlayerInterlude? {
    val adjustedCurrentTimeMs = currentTimeMs + INTERLUDE_TIME_BIAS_MS

    fun checkGap(anchorIndex: Int): AmllPlayerInterlude? {
        if (anchorIndex < -1 || anchorIndex >= currentGroups.size - 1) return null

        val previousGroup = if (anchorIndex == -1) null else currentGroups[anchorIndex]
        val nextGroup = currentGroups[anchorIndex + 1]
        val gapStartMs = previousGroup?.exactEndTimeMs ?: 0.0
        val gapEndMs = max(gapStartMs, nextGroup.exactStartTimeMs - INTERLUDE_LEAD_IN_MS)

        if (gapEndMs - gapStartMs < INTERLUDE_MIN_DURATION_MS) return null

        if (gapEndMs > adjustedCurrentTimeMs && gapStartMs < adjustedCurrentTimeMs) {
            return AmllPlayerInterlude(
                startTimeMs = max(gapStartMs, adjustedCurrentTimeMs),
                endTimeMs = gapEndMs,
                anchorLineIndex = anchorIndex,
                isNextDuet = nextGroup.mainLine.isDuet,
            )
        }
        return null
    }

    return checkGap(scrollToIndex - 1)
        ?: checkGap(scrollToIndex)
        ?: checkGap(scrollToIndex + 1)
}

internal data class AmllLinePosYSpringParametersResult(
    val shouldUpdate: Boolean,
    val parameters: AmllSpringParameters? = null,
)

/**
 * Direct translation of `computeLinePosYSpringParams`.
 */
internal fun computeAmllLinePosYSpringParameters(
    enabled: Boolean,
    currentGroups: List<AmllEngineLineGroup>,
    scrollToIndex: Int,
    isSeeking: Boolean,
    isInterludeActive: Boolean,
): AmllLinePosYSpringParametersResult {
    if (!enabled || currentGroups.isEmpty()) {
        return AmllLinePosYSpringParametersResult(shouldUpdate = false)
    }

    if (isSeeking || isInterludeActive) {
        return AmllLinePosYSpringParametersResult(
            shouldUpdate = true,
            parameters = AmllSpringParameters(
                stiffness = 90.0,
                damping = 15.0,
            ),
        )
    }

    val currentGroup = currentGroups.getOrNull(scrollToIndex)
    val previousGroup = currentGroups.getOrNull(scrollToIndex - 1)
    if (currentGroup == null || previousGroup == null) {
        return AmllLinePosYSpringParametersResult(shouldUpdate = false)
    }

    val intervalMs = currentGroup.exactStartTimeMs - previousGroup.exactStartTimeMs
    val clampedIntervalMs = intervalMs.coerceIn(
        MIN_LINE_INTERVAL_MS,
        MAX_LINE_INTERVAL_MS,
    )
    var ratio =
        1.0 -
            (clampedIntervalMs - MIN_LINE_INTERVAL_MS) /
            (MAX_LINE_INTERVAL_MS - MIN_LINE_INTERVAL_MS)
    ratio = ratio.pow(LINE_INTERVAL_POWER)

    val targetStiffness =
        MIN_LINE_STIFFNESS +
            ratio * (MAX_LINE_STIFFNESS - MIN_LINE_STIFFNESS)
    val targetDamping = sqrt(targetStiffness) * LINE_DAMPING_MULTIPLIER

    return AmllLinePosYSpringParametersResult(
        shouldUpdate = true,
        parameters = AmllSpringParameters(
            stiffness = targetStiffness,
            damping = targetDamping,
        ),
    )
}

internal data class AmllGroupPresentation(
    val isActive: Boolean,
    val targetOpacity: Double,
    val blurLevel: Double,
)

/**
 * Direct translation of `computeGroupPresentation`.
 */
internal fun computeAmllGroupPresentation(
    groupIndex: Int,
    scrollToIndex: Int,
    latestIndex: Int,
    hasBuffered: Boolean,
    hidePassedLines: Boolean,
    isPlaying: Boolean,
    isNonDynamic: Boolean,
    enableBlur: Boolean,
    isUserScrolling: Boolean,
    isCompact: Boolean,
    interlude: AmllPlayerInterlude? = null,
): AmllGroupPresentation {
    val isActive =
        hasBuffered ||
            (groupIndex >= scrollToIndex && groupIndex < latestIndex)
    val blurLevel = computeAmllLineBlur(
        enableBlur = enableBlur,
        isUserScrolling = isUserScrolling,
        isActive = isActive,
        itemIndex = groupIndex,
        scrollToIndex = scrollToIndex,
        latestIndex = latestIndex,
        isCompact = isCompact,
    )

    val targetOpacity = if (hidePassedLines) {
        if (
            groupIndex < (interlude?.let { it.anchorLineIndex + 1 } ?: scrollToIndex) &&
            isPlaying
        ) {
            1e-4
        } else if (hasBuffered) {
            0.85
        } else {
            if (isNonDynamic) 0.2 else 1.0
        }
    } else if (hasBuffered) {
        0.85
    } else {
        if (isNonDynamic) 0.2 else 1.0
    }

    return AmllGroupPresentation(
        isActive = isActive,
        targetOpacity = targetOpacity,
        blurLevel = blurLevel,
    )
}

/**
 * Direct translation of `computeLineBlur`.
 */
internal fun computeAmllLineBlur(
    enableBlur: Boolean,
    isUserScrolling: Boolean,
    isActive: Boolean,
    itemIndex: Int,
    scrollToIndex: Int,
    latestIndex: Int,
    isCompact: Boolean,
): Double {
    if (!enableBlur || isUserScrolling || isActive) {
        return 0.0
    }

    var blurLevel = 1.0
    blurLevel += if (itemIndex < scrollToIndex) {
        abs(scrollToIndex - itemIndex).toDouble() + 1.0
    } else {
        abs(itemIndex - max(scrollToIndex, latestIndex)).toDouble()
    }
    return if (isCompact) blurLevel * COMPACT_BLUR_MULTIPLIER else blurLevel
}

internal data class AmllPlayerLayoutState(
    var targetAlignIndex: Int = 0,
    var lastInterludeState: Boolean = false,
    var alignAnchor: AmllLayoutAlignAnchor = AmllLayoutAlignAnchor.CENTER,
    var alignPosition: Double = 0.35,
    var overscanPx: Double = 300.0,
)

internal data class AmllLayoutInput(
    val viewportWidthPx: Double,
    val viewportHeightPx: Double,
    /**
     * Mirrors core's `lyricGroupSize` WeakMap: a null entry means that the DOM group has not
     * entered overscan and therefore has not been measured yet. Layout uses the exact
     * `viewportHeight / 5` fallback in that case.
     */
    val groupHeightsPx: List<Double?>,
    val interludeDotsWidthPx: Double,
    val interludeDotsHeightPx: Double,
    val scrollOffsetPx: Double = 0.0,
    val fontSizePx: Double = 24.0,
    val bottomLineHeightPx: Double = 0.0,
    val hidePassedLines: Boolean = false,
    val isNonDynamic: Boolean = false,
    val enableSpring: Boolean = true,
    val enableScale: Boolean = true,
    val enableBlur: Boolean = true,
    val isUserScrolling: Boolean = false,
    val isCompact: Boolean = viewportWidthPx <= COMPACT_VIEWPORT_WIDTH_PX,
    val isPlaying: Boolean = true,
    val sync: Boolean = false,
    val force: Boolean = false,
    val alwaysPostpositionBackground: Boolean = false,
)

internal data class AmllInterludeDotsTarget(
    val xPx: Double,
    val yPx: Double,
    val startTimeMs: Double,
    val endTimeMs: Double,
    /**
     * DomLyricPlayer calls `setInterlude()` on every layout, even for an unchanged tuple.
     * This revision lets the Compose leaf reproduce that internal-clock reset.
     */
    val layoutRevision: Long = 0L,
)

internal enum class AmllLineRenderMode {
    SOLID,
    GRADIENT,
}

internal data class AmllGroupTarget(
    val index: Int,
    val topPx: Double,
    val delaySeconds: Double,
    val isActive: Boolean,
    val opacity: Double,
    val blurLevel: Double,
    val renderMode: AmllLineRenderMode,
    val mainScalePercent: Double,
    val backgroundScalePercent: Double,
    val backgroundSlideYPercent: Double,
)

internal data class AmllBottomLineTarget(
    val xPx: Double,
    val yPx: Double,
    val blurLevel: Double,
    val delaySeconds: Double,
    val isFocused: Boolean,
)

internal data class AmllLayoutResult(
    val interlude: AmllPlayerInterlude?,
    val ySpringParameterUpdate: AmllSpringParameters?,
    val groupTargets: List<AmllGroupTarget>,
    val interludeDotsTarget: AmllInterludeDotsTarget?,
    val bottomLineTarget: AmllBottomLineTarget,
    val scrollMinOffsetPx: Double,
    val scrollMaxOffsetPx: Double,
)

/**
 * Pure layout-target translation of `LyricPlayerBase.calcLayout`.
 *
 * It mutates only [layoutState], matching AMLL's cached target/interlude bookkeeping. Rendering
 * and spring advancement remain the responsibility of [AmllPlayerEngine].
 */
internal fun calculateAmllLayoutTargets(
    currentGroups: List<AmllEngineLineGroup>,
    timelineState: AmllTimelineState,
    layoutState: AmllPlayerLayoutState,
    input: AmllLayoutInput,
): AmllLayoutResult {
    val interlude = computeAmllCurrentInterlude(
        currentTimeMs = timelineState.currentTimeMs,
        scrollToIndex = timelineState.scrollToIndex,
        currentGroups = currentGroups,
    )
    val isInterludeActive = interlude != null

    var ySpringParameterUpdate: AmllSpringParameters? = null
    if (
        layoutState.targetAlignIndex != timelineState.scrollToIndex ||
        layoutState.lastInterludeState != isInterludeActive
    ) {
        layoutState.lastInterludeState = isInterludeActive
        val springParameters = computeAmllLinePosYSpringParameters(
            enabled = input.enableSpring,
            currentGroups = currentGroups,
            scrollToIndex = timelineState.scrollToIndex,
            isSeeking = timelineState.isSeeking,
            isInterludeActive = isInterludeActive,
        )
        if (springParameters.shouldUpdate) {
            ySpringParameterUpdate = springParameters.parameters
        }
    }

    var currentPositionPx = -input.scrollOffsetPx
    val targetAlignIndex = timelineState.scrollToIndex
    val isNextDuet = interlude?.isNextDuet ?: false
    // JavaScript's `baseFontSize || 24` falls back for +0, -0, and NaN.
    val effectiveFontSizePx = if (
        input.fontSizePx == 0.0 ||
        input.fontSizePx.isNaN()
    ) {
        DEFAULT_FONT_SIZE_PX
    } else {
        input.fontSizePx
    }
    val dotMarginPx = effectiveFontSizePx * INTERLUDE_DOT_MARGIN_FONT_RATIO
    val totalInterludeHeightPx =
        input.interludeDotsHeightPx + dotMarginPx * 2.0

    if (interlude != null && interlude.anchorLineIndex != -1) {
        currentPositionPx -= totalInterludeHeightPx
    }

    val lineHeightFallbackPx = input.viewportHeightPx / LINE_HEIGHT_FALLBACK_DIVISOR
    val sliceEnd = jsSliceEndIndex(targetAlignIndex, currentGroups.size)
    val lineScrollOffsetPx = (0 until sliceEnd).sumOf { index ->
        input.groupHeightsPx.getOrNull(index) ?: lineHeightFallbackPx
    }

    val scrollMinOffsetPx = -lineScrollOffsetPx
    currentPositionPx -= lineScrollOffsetPx
    currentPositionPx += input.viewportHeightPx * layoutState.alignPosition

    val currentGroup = currentGroups.getOrNull(targetAlignIndex)
    layoutState.targetAlignIndex = targetAlignIndex

    val isBottomFocused = targetAlignIndex == currentGroups.size
    val targetLineHeightPx = when {
        currentGroup != null ->
            input.groupHeightsPx.getOrNull(targetAlignIndex) ?: lineHeightFallbackPx
        isBottomFocused -> input.bottomLineHeightPx
        else -> 0.0
    }

    if (targetLineHeightPx > 0.0) {
        currentPositionPx -= when (layoutState.alignAnchor) {
            AmllLayoutAlignAnchor.BOTTOM -> targetLineHeightPx
            AmllLayoutAlignAnchor.CENTER -> targetLineHeightPx / 2.0
            AmllLayoutAlignAnchor.TOP -> 0.0
        }
    }

    val latestIndex = timelineState.bufferedGroups.maxOrNull() ?: Int.MIN_VALUE
    var delaySeconds = 0.0
    var baseDelaySeconds = if (input.sync) 0.0 else BASE_LAYOUT_DELAY_SECONDS
    var dotsTarget: AmllInterludeDotsTarget? = null
    val groupTargets = ArrayList<AmllGroupTarget>(currentGroups.size)

    currentGroups.forEachIndexed { index, group ->
        val hasBuffered = index in timelineState.bufferedGroups
        val shouldShowDots =
            dotsTarget == null &&
                interlude != null &&
                index == interlude.anchorLineIndex + 1

        if (shouldShowDots) {
            val activeInterlude = checkNotNull(interlude)
            currentPositionPx += dotMarginPx
            val targetX = if (isNextDuet) {
                input.viewportWidthPx - input.interludeDotsWidthPx
            } else {
                0.0
            }
            dotsTarget = AmllInterludeDotsTarget(
                xPx = targetX,
                yPx = currentPositionPx,
                startTimeMs = activeInterlude.startTimeMs,
                endTimeMs = activeInterlude.endTimeMs,
            )
            currentPositionPx += input.interludeDotsHeightPx
            currentPositionPx += dotMarginPx
        }

        val presentation = computeAmllGroupPresentation(
            groupIndex = index,
            scrollToIndex = timelineState.scrollToIndex,
            latestIndex = latestIndex,
            hasBuffered = hasBuffered,
            hidePassedLines = input.hidePassedLines,
            isPlaying = input.isPlaying,
            isNonDynamic = input.isNonDynamic,
            enableBlur = input.enableBlur,
            isUserScrolling = input.isUserScrolling,
            isCompact = input.isCompact,
            interlude = interlude,
        )
        groupTargets += calculateAmllGroupTarget(
            index = index,
            topPx = currentPositionPx,
            delaySeconds = delaySeconds,
            group = group,
            presentation = presentation,
            isPlaying = input.isPlaying,
            enableScale = input.enableScale,
            alwaysPostpositionBackground = input.alwaysPostpositionBackground,
        )

        currentPositionPx += input.groupHeightsPx.getOrNull(index)
            ?: lineHeightFallbackPx

        if (currentPositionPx >= 0.0 && !timelineState.isSeeking) {
            delaySeconds += baseDelaySeconds
            if (index >= timelineState.scrollToIndex) {
                baseDelaySeconds /= BASE_LAYOUT_DELAY_DECAY
            }
        }
    }

    val scrollMaxOffsetPx =
        currentPositionPx + input.scrollOffsetPx - input.viewportHeightPx / 2.0
    val bottomIndex = currentGroups.size
    val bottomBlur = computeAmllLineBlur(
        enableBlur = input.enableBlur,
        isUserScrolling = input.isUserScrolling,
        isActive = isBottomFocused,
        itemIndex = bottomIndex,
        scrollToIndex = timelineState.scrollToIndex,
        latestIndex = latestIndex,
        isCompact = input.isCompact,
    )

    return AmllLayoutResult(
        interlude = interlude,
        ySpringParameterUpdate = ySpringParameterUpdate,
        groupTargets = groupTargets,
        interludeDotsTarget = dotsTarget,
        bottomLineTarget = AmllBottomLineTarget(
            xPx = 0.0,
            yPx = currentPositionPx,
            blurLevel = bottomBlur,
            delaySeconds = delaySeconds,
            isFocused = isBottomFocused,
        ),
        scrollMinOffsetPx = scrollMinOffsetPx,
        scrollMaxOffsetPx = scrollMaxOffsetPx,
    )
}

/**
 * Direct translation of `LyricLineGroupBase.setLineTransformations` and its background slide
 * target selection in `setTransform`.
 */
internal fun calculateAmllGroupTarget(
    index: Int,
    topPx: Double,
    delaySeconds: Double,
    group: AmllEngineLineGroup,
    presentation: AmllGroupPresentation,
    isPlaying: Boolean,
    enableScale: Boolean,
    alwaysPostpositionBackground: Boolean,
): AmllGroupTarget {
    val scaleAspect = if (enableScale) 97.0 else 100.0
    val mainScale = if (!presentation.isActive && isPlaying) {
        scaleAspect
    } else {
        100.0
    }
    val backgroundScale = if (!presentation.isActive && isPlaying) {
        75.0
    } else {
        100.0
    }
    val shouldBackgroundBeFirst =
        !alwaysPostpositionBackground && group.isBackgroundFirst
    val hiddenSlideY = if (shouldBackgroundBeFirst) 80.0 else -80.0
    val targetBackgroundSlideY =
        if (presentation.isActive || !isPlaying) 0.0 else hiddenSlideY

    return AmllGroupTarget(
        index = index,
        topPx = topPx,
        delaySeconds = delaySeconds,
        isActive = presentation.isActive,
        opacity = presentation.targetOpacity,
        blurLevel = presentation.blurLevel,
        renderMode = if (presentation.isActive) {
            AmllLineRenderMode.GRADIENT
        } else {
            AmllLineRenderMode.SOLID
        },
        mainScalePercent = mainScale,
        backgroundScalePercent = backgroundScale,
        backgroundSlideYPercent = targetBackgroundSlideY,
    )
}

internal data class AmllGroupFrame(
    val index: Int,
    val topPx: Double,
    val backgroundSlideYPercent: Double,
    val backgroundWrapperScale: Double,
    val mainScalePercent: Double,
    val backgroundScalePercent: Double,
    val isActive: Boolean,
    val opacity: Double,
    val blurPx: Double,
    val renderMode: AmllLineRenderMode,
    /** Mirrors LyricLineEl's `force || !enableSpring` mask-alpha snap branch. */
    val animateMaskAlpha: Boolean,
)

internal data class AmllEngineFrame(
    val groups: List<AmllGroupFrame>,
    val bottomLineYPx: Double,
    val bottomLineBlurPx: Double,
    val interludeDotsTarget: AmllInterludeDotsTarget?,
)

internal data class AmllTimeUpdateResult(
    val applied: Boolean,
    val shouldLayout: Boolean,
    val shouldResetScroll: Boolean,
    val groupsToEnable: List<Int>,
    val groupsToDisable: List<Int>,
    /**
     * Inactive word canvases use a coarse playback snapshot instead of observing the 60 Hz clock.
     * Refresh it only when the clock crosses lyric state or moves discontinuously.
     */
    val shouldRefreshInactiveWords: Boolean = false,
)

private val AmllUnchangedTimeUpdateResult = AmllTimeUpdateResult(
    applied = true,
    shouldLayout = false,
    shouldResetScroll = false,
    groupsToEnable = emptyList(),
    groupsToDisable = emptyList(),
)

/**
 * Stateful, platform-free AMLL timeline and layout engine.
 *
 * Parsing, lyric optimization, measurement, drawing, pointer input, and platform animation clocks
 * intentionally remain outside this class.
 */
internal class AmllPlayerEngine(
    lines: List<AmllLyricLine> = emptyList(),
    initialTimeMs: Double = 0.0,
    /**
     * `LyricLineGroup.addBgLine()` resolves the initial ±80% background position when the group is
     * created, before the first layout. Keep that construction-time flag separate from later
     * layout targets so an always-postposition player never animates a BG-first row from +80%.
     */
    private val initialAlwaysPostpositionBackground: Boolean = false,
) {
    private var currentGroups: List<AmllEngineLineGroup> = emptyList()
    private var currentDocumentIsNonDynamic = true
    private var groupMotionStates: List<GroupMotionState> = emptyList()
    private var ySpringParameters = DEFAULT_Y_SPRING_PARAMETERS
    private val bottomLineYSpring = AmllSpring(0.0)
    private var enableSpring = true
    private var lastLayoutResult: AmllLayoutResult? = null
    private var layoutRevision = 0L
    private var cachedFrame: AmllEngineFrame? = null
    private var frameDirty = true
    private var timelineBoundariesMs: DoubleArray = doubleArrayOf()
    private var lastHasBottomContent = false

    val timelineState = AmllTimelineState()
    val layoutState = AmllPlayerLayoutState()

    val groups: List<AmllEngineLineGroup>
        get() = currentGroups

    val groupTargets: List<AmllGroupTarget>
        get() = lastLayoutResult?.groupTargets.orEmpty()

    val linePresentations: List<AmllGroupPresentation>
        get() = groupTargets.map { target ->
            AmllGroupPresentation(
                isActive = target.isActive,
                targetOpacity = target.opacity,
                blurLevel = target.blurLevel,
            )
        }

    val isNonDynamic: Boolean
        get() = currentDocumentIsNonDynamic

    init {
        setLines(lines = lines, initialTimeMs = initialTimeMs)
    }

    fun setLines(
        lines: List<AmllLyricLine>,
        initialTimeMs: Double = 0.0,
    ) {
        setGroups(
            groups = groupAmllLyricLines(lines),
            initialTimeMs = initialTimeMs,
            isNonDynamic = lines.all { it.words.size <= 1 },
        )
    }

    fun setGroups(
        groups: List<AmllEngineLineGroup>,
        initialTimeMs: Double = 0.0,
        isNonDynamic: Boolean = groups.all { group ->
            group.mainLine.words.size <= 1 &&
                (group.backgroundLine?.words?.size ?: 0) <= 1
        },
    ) {
        timelineState.initialLayoutFinished = true
        timelineState.lastCurrentTimeMs = initialTimeMs
        timelineState.currentTimeMs = initialTimeMs
        timelineState.hotGroups.clear()
        timelineState.bufferedGroups.clear()
        timelineState.scrollToIndex = 0

        currentGroups = groups.toList()
        timelineBoundariesMs = currentGroups
            .flatMap { group -> listOf(group.exactStartTimeMs, group.exactEndTimeMs) }
            .filter(Double::isFinite)
            .distinct()
            .sorted()
            .toDoubleArray()
        lastHasBottomContent = false
        currentDocumentIsNonDynamic = isNonDynamic
        groupMotionStates = currentGroups.map { group ->
            GroupMotionState(
                group = group,
                alwaysPostpositionBackgroundAtCreation =
                    initialAlwaysPostpositionBackground,
            ).also { state ->
                state.updateYSpringParameters(ySpringParameters)
            }
        }
        lastLayoutResult = null
        cachedFrame = null
        frameDirty = true
        setTime(
            timeMs = initialTimeMs,
            isSeek = true,
            hasBottomContent = false,
        )
    }

    fun setTime(
        timeMs: Double,
        isSeek: Boolean = false,
        hasBottomContent: Boolean = false,
    ): AmllTimeUpdateResult {
        val roundedTimeMs = floor(timeMs + 0.5)
        val previousTimeMs = timelineState.currentTimeMs
        val crossedTimelineBoundary = crossesAmllTimelineBoundary(
            boundariesMs = timelineBoundariesMs,
            fromExclusiveMs = previousTimeMs,
            toInclusiveMs = roundedTimeMs,
        )
        val playbackMovedBackward = roundedTimeMs < previousTimeMs
        timelineState.isSeeking = isSeek
        timelineState.currentTimeMs = roundedTimeMs

        if (!timelineState.initialLayoutFinished && !timelineState.isSeeking) {
            return AmllTimeUpdateResult(
                applied = false,
                shouldLayout = false,
                shouldResetScroll = false,
                groupsToEnable = emptyList(),
                groupsToDisable = emptyList(),
            )
        }

        if (
            !isSeek &&
            hasBottomContent == lastHasBottomContent &&
            !playbackMovedBackward &&
            !crossedTimelineBoundary
        ) {
            timelineState.lastCurrentTimeMs = roundedTimeMs
            return AmllUnchangedTimeUpdateResult
        }

        val stateResult = computeAmllPlayerTimeState(
            timeMs = roundedTimeMs,
            currentGroups = currentGroups,
            timelineState = timelineState,
        )
        val commitResult = commitAmllPlayerTimeState(
            timelineState = timelineState,
            timeMs = roundedTimeMs,
            currentGroups = currentGroups,
            hasBottomContent = hasBottomContent,
            stateResult = stateResult,
        )
        lastHasBottomContent = hasBottomContent
        return AmllTimeUpdateResult(
            applied = true,
            shouldLayout = commitResult.shouldLayout,
            shouldResetScroll = commitResult.shouldResetScroll,
            groupsToEnable = commitResult.groupsToEnable,
            groupsToDisable = commitResult.groupsToDisable,
            shouldRefreshInactiveWords =
                isSeek || playbackMovedBackward || crossedTimelineBoundary,
        )
    }

    fun setPlaying(isPlaying: Boolean): Boolean {
        if (timelineState.isPlaying == isPlaying) return false
        timelineState.isPlaying = isPlaying
        return true
    }

    fun layout(input: AmllLayoutInput): AmllLayoutResult {
        enableSpring = input.enableSpring
        timelineState.isPlaying = input.isPlaying

        /*
         * `LyricLineGroup` starts every DOM group at `window.innerHeight * 2` before the first
         * synchronous, non-forced layout. Compose knows the viewport only at measure time, so the
         * literal initial position is installed here, immediately before applying the first target.
         */
        val initialGroupPositionPx = input.viewportHeightPx * 2.0
        groupMotionStates.forEach { state ->
            state.initializePositionIfNeeded(initialGroupPositionPx)
        }

        val calculatedResult = calculateAmllLayoutTargets(
            currentGroups = currentGroups,
            timelineState = timelineState,
            layoutState = layoutState,
            input = input,
        )
        if (calculatedResult.interludeDotsTarget != null) {
            layoutRevision += 1L
        }
        val result = calculatedResult.copy(
            interludeDotsTarget = calculatedResult.interludeDotsTarget?.copy(
                layoutRevision = layoutRevision,
            ),
        )
        result.ySpringParameterUpdate?.let(::updateYSpringParameters)

        for (target in result.groupTargets) {
            groupMotionStates.getOrNull(target.index)?.applyTarget(
                target = target,
                force = input.force,
                enableSpring = input.enableSpring,
            )
        }
        if (input.force || !input.enableSpring) {
            bottomLineYSpring.setPosition(result.bottomLineTarget.yPx)
        } else {
            bottomLineYSpring.setTargetPosition(
                position = result.bottomLineTarget.yPx,
                delaySeconds = result.bottomLineTarget.delaySeconds,
            )
        }

        lastLayoutResult = result
        timelineState.initialLayoutFinished = true
        // A seek changes the parameters of one layout pass. Previously the per-frame host
        // cleared this on its next setTime(false); the decoupled 60 Hz timeline must also work
        // while paused or at end-of-track, when no later position emission is guaranteed.
        timelineState.isSeeking = false
        frameDirty = true
        return result
    }

    /**
     * Advances the AMLL springs. The public clock uses milliseconds, as does AMLL's player update;
     * each group receives seconds exactly as the DOM implementation does.
     */
    fun update(
        deltaMs: Double = 0.0,
        isPageVisible: Boolean = true,
    ): AmllEngineFrame {
        var frameChanged = false
        if (enableSpring) {
            val deltaSeconds = deltaMs / 1_000.0
            // DomLyricPlayer.update() advances BaseLyricPlayer's bottom line before checking
            // isPageVisible, then skips every LyricLineGroup update while pagehide/hidden.
            if (isPageVisible) {
                groupMotionStates.forEach { motion ->
                    frameChanged = motion.update(deltaSeconds) || frameChanged
                }
            }
            frameChanged = bottomLineYSpring.update(deltaSeconds) || frameChanged
        }
        frameDirty = frameDirty || frameChanged
        return currentFrame()
    }

    fun currentFrame(): AmllEngineFrame {
        cachedFrame?.takeUnless { frameDirty }?.let { return it }
        val targets = lastLayoutResult?.groupTargets.orEmpty()
        val groupFrames = groupMotionStates.mapIndexed { index, motion ->
            val target = targets.getOrNull(index)
                ?.takeIf { candidate -> candidate.index == index }
                ?: targets.firstOrNull { candidate -> candidate.index == index }
                ?: DEFAULT_GROUP_TARGET.copy(index = index)
            motion.frame(index = index, target = target)
        }
        return AmllEngineFrame(
            groups = groupFrames,
            bottomLineYPx = bottomLineYSpring.currentPosition,
            bottomLineBlurPx = min(
                MAX_RENDERED_BLUR_PX,
                lastLayoutResult?.bottomLineTarget?.blurLevel ?: 0.0,
            ),
            interludeDotsTarget = lastLayoutResult?.interludeDotsTarget,
        ).also { builtFrame ->
            cachedFrame = builtFrame
            frameDirty = false
        }
    }

    private fun updateYSpringParameters(parameters: AmllSpringParameters) {
        ySpringParameters = ySpringParameters.mergedWithEngineParameters(parameters)
        bottomLineYSpring.updateParameters(ySpringParameters)
        groupMotionStates.forEach { state ->
            state.updateYSpringParameters(ySpringParameters)
        }
    }

    private class GroupMotionState(
        group: AmllEngineLineGroup,
        alwaysPostpositionBackgroundAtCreation: Boolean,
    ) {
        private val positionY = AmllSpring(0.0)
        private var positionInitialized = false
        private val backgroundSlideY = AmllSpring(
            if (
                !alwaysPostpositionBackgroundAtCreation &&
                group.isBackgroundFirst
            ) {
                80.0
            } else {
                -80.0
            },
        )
        private val mainScale = AmllSpring(100.0).also { spring ->
            spring.updateParameters(DEFAULT_MAIN_SCALE_SPRING_PARAMETERS)
        }
        private val backgroundScale = AmllSpring(100.0).also { spring ->
            spring.updateParameters(DEFAULT_BACKGROUND_SCALE_SPRING_PARAMETERS)
        }
        private var animateMaskAlpha = true

        fun updateYSpringParameters(parameters: AmllSpringParameters) {
            positionY.updateParameters(parameters)
            backgroundSlideY.updateParameters(parameters)
        }

        fun initializePositionIfNeeded(positionPx: Double) {
            if (positionInitialized) return
            positionInitialized = true
            positionY.setPosition(positionPx)
        }

        fun applyTarget(
            target: AmllGroupTarget,
            force: Boolean,
            enableSpring: Boolean,
        ) {
            animateMaskAlpha = enableSpring && !force
            if (force || !enableSpring) {
                positionY.setPosition(target.topPx)
                backgroundSlideY.setPosition(target.backgroundSlideYPercent)
                mainScale.setPosition(target.mainScalePercent)
                backgroundScale.setPosition(target.backgroundScalePercent)
            } else {
                positionY.setTargetPosition(
                    position = target.topPx,
                    delaySeconds = target.delaySeconds,
                )
                backgroundSlideY.setTargetPosition(
                    position = target.backgroundSlideYPercent,
                    delaySeconds = target.delaySeconds,
                )
                // LyricLineEl.setTransform does not pass the group delay to its scale spring.
                mainScale.setTargetPosition(target.mainScalePercent)
                backgroundScale.setTargetPosition(target.backgroundScalePercent)
            }
        }

        fun update(deltaSeconds: Double): Boolean =
            positionY.update(deltaSeconds) or
                backgroundSlideY.update(deltaSeconds) or
                mainScale.update(deltaSeconds) or
                backgroundScale.update(deltaSeconds)

        fun frame(
            index: Int,
            target: AmllGroupTarget,
        ): AmllGroupFrame {
            val slideY = backgroundSlideY.currentPosition
            val activeProgress =
                (1.0 - abs(slideY) / BACKGROUND_HIDDEN_SLIDE_PERCENT)
                    .coerceIn(0.0, 1.0)
            return AmllGroupFrame(
                index = index,
                topPx = positionY.currentPosition,
                backgroundSlideYPercent = slideY,
                backgroundWrapperScale =
                    BACKGROUND_WRAPPER_MIN_SCALE +
                        activeProgress * BACKGROUND_WRAPPER_SCALE_RANGE,
                mainScalePercent = mainScale.currentPosition,
                backgroundScalePercent = backgroundScale.currentPosition,
                isActive = target.isActive,
                opacity = target.opacity,
                blurPx = min(MAX_RENDERED_BLUR_PX, target.blurLevel),
                renderMode = target.renderMode,
                animateMaskAlpha = animateMaskAlpha,
            )
        }
    }

    private companion object {
        val DEFAULT_Y_SPRING_PARAMETERS = AmllSpringParameters(
            mass = 0.9,
            damping = 15.0,
            stiffness = 90.0,
        )
        val DEFAULT_MAIN_SCALE_SPRING_PARAMETERS = AmllSpringParameters(
            mass = 2.0,
            damping = 25.0,
            stiffness = 100.0,
        )
        val DEFAULT_BACKGROUND_SCALE_SPRING_PARAMETERS = AmllSpringParameters(
            mass = 1.0,
            damping = 20.0,
            stiffness = 50.0,
        )
        val DEFAULT_GROUP_TARGET = AmllGroupTarget(
            index = 0,
            topPx = 0.0,
            delaySeconds = 0.0,
            isActive = false,
            opacity = 1.0,
            blurLevel = 0.0,
            renderMode = AmllLineRenderMode.SOLID,
            mainScalePercent = 100.0,
            backgroundScalePercent = 100.0,
            backgroundSlideYPercent = -80.0,
        )
    }
}

private fun amllSetsEqual(
    first: Set<Int>,
    second: Set<Int>,
): Boolean =
    first.size == second.size && first.all(second::contains)

private fun jsSliceEndIndex(
    requestedEnd: Int,
    size: Int,
): Int = if (requestedEnd >= 0) {
    min(requestedEnd, size)
} else {
    max(size + requestedEnd, 0)
}

private fun AmllSpringParameters.mergedWithEngineParameters(
    other: AmllSpringParameters,
): AmllSpringParameters = AmllSpringParameters(
    mass = other.mass ?: mass,
    damping = other.damping ?: damping,
    stiffness = other.stiffness ?: stiffness,
    soft = other.soft ?: soft,
)

private const val INTERLUDE_TIME_BIAS_MS = 20.0
private const val INTERLUDE_LEAD_IN_MS = 250.0
private const val INTERLUDE_MIN_DURATION_MS = 4_000.0
private const val MIN_LINE_INTERVAL_MS = 100.0
private const val MAX_LINE_INTERVAL_MS = 800.0
private const val MIN_LINE_STIFFNESS = 170.0
private const val MAX_LINE_STIFFNESS = 220.0
private const val LINE_INTERVAL_POWER = 0.2
private const val LINE_DAMPING_MULTIPLIER = 2.2
private const val COMPACT_BLUR_MULTIPLIER = 0.8
private const val COMPACT_VIEWPORT_WIDTH_PX = 1_024.0
private const val DEFAULT_FONT_SIZE_PX = 24.0
private const val INTERLUDE_DOT_MARGIN_FONT_RATIO = 0.4
private const val LINE_HEIGHT_FALLBACK_DIVISOR = 5.0
private const val BASE_LAYOUT_DELAY_SECONDS = 0.05
private const val BASE_LAYOUT_DELAY_DECAY = 1.05
private const val MAX_RENDERED_BLUR_PX = 5.0
private const val BACKGROUND_HIDDEN_SLIDE_PERCENT = 80.0
private const val BACKGROUND_WRAPPER_MIN_SCALE = 0.8
private const val BACKGROUND_WRAPPER_SCALE_RANGE = 0.2

/*
 * Native Compose translation of @applemusic-like-lyrics/core 0.5.2:
 * packages/core/src/lyric-player/base/{index,scroll,group}.ts,
 * packages/core/src/lyric-player/dom/{index,lyric-group,interlude-dots}.ts, and
 * packages/core/src/styles/lyric-player.module.css.
 *
 * Modified for RedefineNCM KMP on 2026-07-26.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.leejlredstar.redefinencm.kmp.getPlatform
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private const val AmllScrollResetMillis = 5_000L
private const val AmllMinimumInertiaSpeedPxPerMs = 0.1
private const val AmllStopInertiaSpeedPxPerMs = 0.05
private const val AmllDomTouchClickThresholdCssPx = 10.0
private val AmllCssEase = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.00f)

internal class AmllViewportRuntime(
    val engine: AmllPlayerEngine,
) {
    var scrollOffsetPx = 0.0
    var scrollMinPx = 0.0
    var scrollMaxPx = 0.0
    var allowScroll = true
    var isScrolled = false
    var isUserScrolling = false
    var needsLayout = true
    var layoutRevision by mutableLongStateOf(0L)
        private set
    var syncNextLayout = true
    var forceNextLayout = false
    var hasPerformedInitialLayout = false
    var knownGroupHeightsPx: MutableList<Int?> = mutableListOf()
    var knownGroupWidthsPx: MutableList<Int?> = mutableListOf()
    var renderedGroupHeightsPx: MutableList<Int> = mutableListOf()
    var interludeDotsMeasurementAvailable = false
    var lastViewportWidthPx = -1
    var lastViewportHeightPx = -1
    var lastInterludeDotsWidthPx = -1
    var lastInterludeDotsHeightPx = -1
    var lastFontSizePx = Double.NaN
    var lastCompactMode: Boolean? = null
    var lastSpringEnabled: Boolean? = null
    var lastAlwaysPostpositionBackground: Boolean? = null
    var lastVisibleInterludeDotsTarget: AmllInterludeDotsTarget? = null

    fun requestLayout(sync: Boolean = false, force: Boolean = false) {
        needsLayout = true
        syncNextLayout = syncNextLayout || sync
        forceNextLayout = forceNextLayout || force
        layoutRevision += 1L
    }

    /**
     * Read from the Compose measure phase so [requestLayout] schedules a remeasure even when the
     * engine frame is structurally unchanged before the pending layout applies its new targets.
     */
    fun hasPendingLayoutForMeasure(): Boolean {
        @Suppress("UNUSED_EXPRESSION")
        layoutRevision
        return needsLayout
    }

    fun clampScroll() {
        scrollOffsetPx = clampAmllScrollOffsetLikeJs(
            value = scrollOffsetPx,
            minimum = scrollMinPx,
            maximum = scrollMaxPx,
        )
    }

    fun resetScrollState() {
        isScrolled = false
        isUserScrolling = false
        scrollOffsetPx = 0.0
    }

    fun ensureGroupMeasurementSlots(count: Int) {
        if (
            knownGroupHeightsPx.size == count &&
            knownGroupWidthsPx.size == count &&
            renderedGroupHeightsPx.size == count
        ) {
            return
        }
        knownGroupHeightsPx = MutableList(count) { null }
        knownGroupWidthsPx = MutableList(count) { null }
        renderedGroupHeightsPx = MutableList(count) { 0 }
        hasPerformedInitialLayout = false
        interludeDotsMeasurementAvailable = false
    }
}

/**
 * Literal `Math.min(Math.max(x, min), max)` used by AMLL core's `clamp.ts`.
 *
 * Kotlin's `coerceIn` rejects an inverted interval, while JavaScript intentionally returns
 * `max` in that case. A short one-line document routinely has `minOffset > maxOffset`.
 */
internal fun clampAmllScrollOffsetLikeJs(
    value: Double,
    minimum: Double,
    maximum: Double,
): Double = min(max(value, minimum), maximum)

internal enum class AmllWheelDeltaMode {
    PIXEL,
    LINE,
    PAGE,
}

internal data class AmllWheelTranslation(
    val deltaPx: Double,
    val sync: Boolean,
)

/**
 * Direct translation of the two `WheelEvent.deltaMode` branches in
 * `attachPlayerScrollHandlers`.
 */
internal fun translateAmllWheelDelta(
    deltaY: Double,
    mode: AmllWheelDeltaMode,
): AmllWheelTranslation = when (mode) {
    AmllWheelDeltaMode.PIXEL -> AmllWheelTranslation(
        deltaPx = deltaY,
        sync = true,
    )
    AmllWheelDeltaMode.LINE,
    AmllWheelDeltaMode.PAGE,
    -> AmllWheelTranslation(
        deltaPx = deltaY * 50.0,
        sync = false,
    )
}

/**
 * The DOM implementation compares each final screen-axis displacement independently with `< 10`.
 * It intentionally does not use total path length or Euclidean distance.
 */
internal fun isAmllTouchClickCandidate(
    start: Offset,
    end: Offset,
    thresholdPx: Double,
): Boolean =
    abs((end.x - start.x).toDouble()) < thresholdPx &&
        abs((end.y - start.y).toDouble()) < thresholdPx

internal fun computeAmllTouchScrollOffset(
    startScrollOffsetPx: Double,
    startTouchYPx: Double,
    currentTouchYPx: Double,
    minimumPx: Double,
    maximumPx: Double,
): Double = clampAmllScrollOffsetLikeJs(
    value = startScrollOffsetPx - (currentTouchYPx - startTouchYPx),
    minimum = minimumPx,
    maximum = maximumPx,
)

internal data class AmllInertiaFrameResult(
    val scrollOffsetPx: Double,
    val speedPxPerMs: Double,
    val shouldLayout: Boolean,
    val finished: Boolean,
)

/**
 * One requestAnimationFrame step from `attachPlayerScrollHandlers`.
 */
internal fun advanceAmllInertiaFrame(
    scrollOffsetPx: Double,
    speedPxPerMs: Double,
    deltaMs: Double,
    minimumPx: Double,
    maximumPx: Double,
): AmllInertiaFrameResult {
    if (deltaMs <= 0.0 || deltaMs > 100.0) {
        return AmllInertiaFrameResult(
            scrollOffsetPx = scrollOffsetPx,
            speedPxPerMs = speedPxPerMs,
            shouldLayout = false,
            finished = false,
        )
    }
    if (abs(speedPxPerMs) <= AmllStopInertiaSpeedPxPerMs) {
        return AmllInertiaFrameResult(
            scrollOffsetPx = scrollOffsetPx,
            speedPxPerMs = speedPxPerMs,
            shouldLayout = false,
            finished = true,
        )
    }
    return AmllInertiaFrameResult(
        scrollOffsetPx = clampAmllScrollOffsetLikeJs(
            value = scrollOffsetPx - speedPxPerMs * deltaMs,
            minimum = minimumPx,
            maximum = maximumPx,
        ),
        speedPxPerMs = speedPxPerMs * 0.95.pow(deltaMs / 16.0),
        shouldLayout = true,
        finished = false,
    )
}

/**
 * Compose has no lyric-line DOM nodes for `document.elementFromPoint()`. In CanvasKit,
 * `elementFromPoint()` returns the one app canvas rather than a rendered lyric wrapper. This
 * resolves the topmost rendered Compose lyric wrapper at the released pointer position; later
 * siblings win, matching normal DOM paint order.
 */
internal fun pickAmllTouchClickGroupIndex(
    position: Offset,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    groupTopsPx: List<Double>,
    groupHeightsPx: List<Int>,
): Int? {
    val xPx = position.x.toDouble()
    val yPx = position.y.toDouble()
    if (
        xPx < 0.0 ||
        yPx < 0.0 ||
        xPx >= viewportWidthPx.toDouble() ||
        yPx >= viewportHeightPx.toDouble()
    ) {
        return null
    }
    val lastIndex = min(groupTopsPx.lastIndex, groupHeightsPx.lastIndex)
    for (index in lastIndex downTo 0) {
        val top = roundToAmllPrecision(groupTopsPx[index], scale = 10.0)
        val bottom = top + groupHeightsPx[index]
        if (yPx >= top && yPx < bottom) return index
    }
    return null
}

/**
 * Resolves the DOM-style roving lyric focus.
 *
 * `tagRenderedLyricLines()` gives the first active line `tabIndex=0` until the user owns a
 * concrete lyric-line focus. Once keyboard focus is inside the lyric player, playback advancing
 * must not move that focus out from under the user.
 */
internal fun resolveAmllRovingKeyboardIndex(
    groupCount: Int,
    currentIndex: Int,
    activeIndex: Int?,
    retainCurrentFocus: Boolean,
): Int {
    if (groupCount <= 0) return 0
    if (retainCurrentFocus) return currentIndex.coerceIn(0, groupCount - 1)
    return activeIndex
        ?.takeIf { it in 0 until groupCount }
        ?: 0
}

internal fun moveAmllRovingKeyboardIndex(
    groupCount: Int,
    currentIndex: Int,
    activeIndex: Int?,
    retainCurrentFocus: Boolean,
    delta: Int,
): Int {
    if (groupCount <= 0) return 0
    return (
        resolveAmllRovingKeyboardIndex(
            groupCount = groupCount,
            currentIndex = currentIndex,
            activeIndex = activeIndex,
            retainCurrentFocus = retainCurrentFocus,
        ) + delta
        ).coerceIn(0, groupCount - 1)
}

/**
 * Chooses the only lyric group that participates in Compose focus traversal.
 *
 * A requested destination must win while its `FocusRequester` is waiting for recomposition; an
 * already focused line wins over playback progression; otherwise this follows AMLL's active-line
 * `tabIndex=0` rule.
 */
internal fun resolveAmllRovingFocusTargetIndex(
    groupCount: Int,
    keyboardIndex: Int,
    focusedIndex: Int?,
    pendingFocusIndex: Int?,
    activeIndex: Int?,
): Int = resolveAmllRovingKeyboardIndex(
    groupCount = groupCount,
    currentIndex = pendingFocusIndex ?: focusedIndex ?: keyboardIndex,
    activeIndex = activeIndex,
    retainCurrentFocus = focusedIndex != null || pendingFocusIndex != null,
)

/**
 * Reproduces the host's `element.textContent.trim()` label for a rendered lyric group.
 *
 * Dynamic AMLL lines place ruby text before each word body and per-word romanization after it.
 * Translation and whole-line romanization are the two following DOM children. The background
 * wrapper is inserted before or after the main line according to the source timestamps.
 */
internal fun amllLyricGroupAccessibilityLabel(
    group: AmllLyricGroup,
    isNonDynamic: Boolean,
): String {
    fun AmllLyricLine.domTextContent(): String {
        val renderedMain = if (isNonDynamic) {
            words.takeIf(List<AmllLyricWord>::isNotEmpty)
                ?.joinToString(separator = "") { word -> word.text }
                ?: mainText
        } else {
            val hasRubyLine = words.any { word ->
                word.ruby.any { ruby -> ruby.text.trim().isNotEmpty() }
            }
            val hasRomanLine = words.any { word -> !word.romanWord.isNullOrBlank() }
            words.takeIf(List<AmllLyricWord>::isNotEmpty)
                ?.joinToString(separator = "") { word ->
                    buildString {
                        if (hasRubyLine) {
                            word.ruby
                                .filter { ruby -> ruby.text.trim().isNotEmpty() }
                                .forEach { ruby -> append(ruby.text) }
                        }
                        append(word.text.trim())
                        if (hasRomanLine) {
                            val romanWord = word.romanWord?.trim().orEmpty()
                            append(romanWord.ifEmpty { "\u00A0" })
                        }
                    }
                }
                ?: mainText
        }
        return buildString {
            append(renderedMain)
            append(translatedText.orEmpty())
            append(romanText.orEmpty())
        }
    }

    val readableText = buildString {
        if (group.isBackgroundFirst) {
            append(group.backgroundLine?.domTextContent().orEmpty())
            append(group.mainLine.domTextContent())
        } else {
            append(group.mainLine.domTextContent())
            append(group.backgroundLine?.domTextContent().orEmpty())
        }
    }.trim { character -> character.isWhitespace() || character == '\u00A0' }

    return if (readableText.isEmpty()) {
        "跳转到这句歌词"
    } else {
        "跳转到歌词：$readableText"
    }
}

internal fun shouldShowAmllFocusOutline(
    renderedIndex: Int,
    focusedIndex: Int?,
    focusVisible: Boolean,
): Boolean = focusVisible && renderedIndex == focusedIndex

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AmllLyricViewport(
    document: AmllLyricDocument,
    mediaId: String,
    positionState: State<Long>,
    isPlaying: Boolean,
    parameters: AmllLyricVisualParameters,
    androidPresentation: Boolean,
    alwaysPostpositionBackground: Boolean = false,
    onSeek: (mediaId: String, positionMs: Long) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val platform = remember { getPlatform() }
    /*
     * Compose documents scrollDelta as platform-specific wheel ticks. Native targets therefore map
     * to AMLL's non-pixel branch. Web installs [amllPlatformEvents] before CanvasKit so the browser
     * path receives the real DOM PIXEL/LINE/PAGE value. This remains the fallback for a browser
     * event emitted before its canvas/viewport bounds are available.
     */
    val composeWheelDeltaMode = if (platform.isDesktop || platform.isMobile) {
        AmllWheelDeltaMode.LINE
    } else {
        AmllWheelDeltaMode.PIXEL
    }
    val touchClickThresholdPx = with(density) {
        (AmllDomTouchClickThresholdCssPx.dp).toPx().toDouble()
    }
    val engine = remember(
        document.groups,
        mediaId,
        alwaysPostpositionBackground,
    ) {
        AmllPlayerEngine(
            initialAlwaysPostpositionBackground = alwaysPostpositionBackground,
        ).also {
            it.setGroups(
                groups = document.groups,
                initialTimeMs = positionState.value.toDouble(),
                // Core computes this over every optimized line before grouping can replace BG rows.
                isNonDynamic = document.lines.all { line -> line.words.size <= 1 },
            )
        }
    }
    val runtime = remember(engine) { AmllViewportRuntime(engine) }
    val hasDuetLine = remember(document.lines) { document.lines.any(AmllLyricLine::isDuet) }
    var frame by remember(engine) { mutableStateOf(engine.currentFrame()) }
    var renderPulse by remember { mutableLongStateOf(0L) }
    var keyboardIndex by remember(document.groups) { mutableIntStateOf(0) }
    var focusedKeyboardIndex by remember(document.groups) { mutableStateOf<Int?>(null) }
    var pendingKeyboardFocusIndex by remember(document.groups) { mutableStateOf<Int?>(null) }
    var pendingFocusVisible by remember(document.groups) { mutableStateOf<Boolean?>(null) }
    var keyboardFocusVisible by remember(document.groups) { mutableStateOf(false) }
    val keyboardFocusRequesters = remember(document.groups) {
        List(document.groups.size) { FocusRequester() }
    }
    val viewportHoverSource = remember { MutableInteractionSource() }
    val isViewportHovered by viewportHoverSource.collectIsHoveredAsState()
    val scope = rememberCoroutineScope()
    var resetScrollJob by remember { mutableStateOf<Job?>(null) }
    var inertiaGeneration by remember { mutableIntStateOf(0) }
    var isPageVisible by remember(engine) { mutableStateOf(true) }

    fun invalidateLayout(sync: Boolean = false, force: Boolean = false) {
        runtime.requestLayout(sync = sync, force = force)
        renderPulse++
    }

    fun beginScrollHandler(): Boolean {
        if (!runtime.allowScroll) return false
        runtime.isScrolled = true
        resetScrollJob?.cancel()
        resetScrollJob = scope.launch {
            delay(AmllScrollResetMillis)
            // BaseLyricPlayer.beginScrollHandler intentionally does not layout and does not
            // clear isUserScrolling when this timeout elapses.
            runtime.isScrolled = false
            runtime.scrollOffsetPx = 0.0
        }
        return true
    }

    fun resetScroll() {
        runtime.resetScrollState()
        resetScrollJob?.cancel()
        resetScrollJob = null
    }

    fun requestGroupFocus(
        target: Int,
        showFocusOutline: Boolean,
    ) {
        if (document.groups.isEmpty()) return
        val boundedTarget = target.coerceIn(document.groups.indices)
        keyboardIndex = boundedTarget
        if (boundedTarget == focusedKeyboardIndex) {
            pendingKeyboardFocusIndex = null
            pendingFocusVisible = null
            keyboardFocusVisible = showFocusOutline
        } else {
            pendingKeyboardFocusIndex = boundedTarget
            pendingFocusVisible = showFocusOutline
        }
    }

    fun clickGroup(
        index: Int,
        requestPointerFocus: Boolean,
    ) {
        val line = document.groups.getOrNull(index)?.mainLine ?: return
        if (requestPointerFocus) {
            // A non-null tabindex makes the old DOM line click-focusable even when its value is -1.
            // Pointer focus does not match :focus-visible, so keep the real focus without a ring.
            requestGroupFocus(target = index, showFocusOutline = false)
        }
        onSeek(mediaId, line.sourceStartTimeMs)
        resetScroll()
        invalidateLayout(sync = true, force = false)
        onInteraction()
    }

    fun launchInertia(rawInitialSpeedPxPerMs: Double) {
        val scrollId = ++inertiaGeneration
        val initialSpeed = if (
            abs(rawInitialSpeedPxPerMs) < AmllMinimumInertiaSpeedPxPerMs
        ) {
            0.0
        } else {
            rawInitialSpeedPxPerMs
        }
        scope.launch {
            var speed = initialSpeed
            var previous = TimeSource.Monotonic.markNow()
            while (true) {
                withFrameNanos { }
                if (scrollId != inertiaGeneration) return@launch
                val deltaMs = previous.elapsedNow().toDouble(DurationUnit.MILLISECONDS)
                previous = TimeSource.Monotonic.markNow()
                val step = advanceAmllInertiaFrame(
                    scrollOffsetPx = runtime.scrollOffsetPx,
                    speedPxPerMs = speed,
                    deltaMs = deltaMs,
                    minimumPx = runtime.scrollMinPx,
                    maximumPx = runtime.scrollMaxPx,
                )
                if (step.finished) {
                    runtime.isUserScrolling = false
                    return@launch
                }
                runtime.scrollOffsetPx = step.scrollOffsetPx
                speed = step.speedPxPerMs
                if (step.shouldLayout) {
                    invalidateLayout(sync = true, force = true)
                }
            }
        }
    }

    LaunchedEffect(engine, positionState, isPlaying, parameters.springEnabled) {
        var previousFrame = TimeSource.Monotonic.markNow()
        while (true) {
            withFrameNanos { }
            val deltaMs = previousFrame
                .elapsedNow()
                .toDouble(DurationUnit.MILLISECONDS)
                .coerceAtLeast(0.0)
            previousFrame = TimeSource.Monotonic.markNow()

            val timeUpdate = engine.setTime(positionState.value.toDouble())
            if (timeUpdate.shouldResetScroll) {
                resetScroll()
            }
            if (timeUpdate.shouldLayout) {
                runtime.requestLayout()
            }
            if (engine.setPlaying(isPlaying)) {
                runtime.requestLayout()
            }
            frame = engine.update(
                deltaMs = deltaMs,
                isPageVisible = isPageVisible,
            )
            renderPulse++
        }
    }

    fun moveKeyboardFocus(target: Int) {
        if (document.groups.isEmpty()) return
        requestGroupFocus(target = target, showFocusOutline = true)
        // player.html only moves the roving tabindex and calls focus(). It does not mutate
        // AMLL's private scrollOffset, so keyboard navigation must not invent a second timeline.
        onInteraction()
    }

    val activeKeyboardIndex = frame.groups
        .indexOfFirst { it.isActive }
        .takeIf { it >= 0 }
    val resolvedKeyboardIndex = resolveAmllRovingFocusTargetIndex(
        groupCount = document.groups.size,
        keyboardIndex = keyboardIndex,
        focusedIndex = focusedKeyboardIndex,
        pendingFocusIndex = pendingKeyboardFocusIndex,
        activeIndex = activeKeyboardIndex,
    )
    LaunchedEffect(
        document.groups,
        activeKeyboardIndex,
        focusedKeyboardIndex,
        pendingKeyboardFocusIndex,
    ) {
        if (focusedKeyboardIndex == null && pendingKeyboardFocusIndex == null) {
            keyboardIndex = resolvedKeyboardIndex
        }
    }
    LaunchedEffect(
        keyboardFocusRequesters,
        pendingKeyboardFocusIndex,
    ) {
        val target = pendingKeyboardFocusIndex ?: return@LaunchedEffect
        if (target !in keyboardFocusRequesters.indices) {
            pendingKeyboardFocusIndex = null
            pendingFocusVisible = null
            return@LaunchedEffect
        }
        if (!keyboardFocusRequesters[target].requestFocus()) {
            pendingKeyboardFocusIndex = null
            pendingFocusVisible = null
            keyboardIndex = resolveAmllRovingKeyboardIndex(
                groupCount = document.groups.size,
                currentIndex = keyboardIndex,
                activeIndex = activeKeyboardIndex,
                retainCurrentFocus = focusedKeyboardIndex != null,
            )
        }
    }

    val rootModifier = modifier
        .amllPlatformEvents(
            onWheel = { deltaY, mode ->
                // A DOM wheel listener on the original lyric element does not receive events
                // whose topmost target belongs to an overlay sibling. Compose has one canvas,
                // so its hit-tested hover state is the available equivalent gate.
                if (!isViewportHovered || !beginScrollHandler()) {
                    false
                } else {
                    val translated = translateAmllWheelDelta(
                        deltaY = deltaY,
                        mode = mode,
                    )
                    runtime.scrollOffsetPx += translated.deltaPx
                    runtime.clampScroll()
                    invalidateLayout(sync = translated.sync, force = false)
                    onInteraction()
                    true
                }
            },
            onPageVisibilityChanged = { visible, forceResync ->
                isPageVisible = visible
                if (visible && forceResync) {
                    // LyricPlayerBase.onPageShow calls setCurrentTime(currentTime, true).
                    val timeUpdate = engine.setTime(
                        timeMs = positionState.value.toDouble(),
                        isSeek = true,
                    )
                    if (timeUpdate.shouldResetScroll) {
                        resetScroll()
                    }
                    if (timeUpdate.shouldLayout) {
                        runtime.requestLayout()
                    }
                    frame = engine.currentFrame()
                    renderPulse++
                }
            },
        )
        .clipToBounds()
        .then(
            if (androidPresentation) {
                Modifier
            } else {
                // Core applies `mix-blend-mode: plus-lighter` to the complete lyric player.
                // player.html explicitly overrides that declaration to `normal !important`
                // only on Android. Isolating the viewport into one offscreen layer makes the
                // Compose blend happen once after all glyphs, shadows, masks, and interlude
                // dots have been composed, matching the CSS group-compositing boundary.
                Modifier.graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    blendMode = BlendMode.Plus
                }
            },
        )
        .hoverable(viewportHoverSource)
        .pointerInput(onInteraction) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        // player.html reveals the controller on capture-phase pointerdown.
                        onInteraction()
                    }
                }
            }
        }
        .pointerInput(document.groups, mediaId, composeWheelDeltaMode) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type != PointerEventType.Scroll) continue
                    if (!beginScrollHandler()) continue
                    val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    val translated = translateAmllWheelDelta(
                        deltaY = deltaY.toDouble(),
                        mode = composeWheelDeltaMode,
                    )
                    runtime.scrollOffsetPx += translated.deltaPx
                    runtime.clampScroll()
                    invalidateLayout(sync = translated.sync, force = false)
                    onInteraction()
                    event.changes.forEach { it.consume() }
                }
            }
        }
        .pointerInput(document.groups, mediaId, touchClickThresholdPx) {
            var activeTouchId: PointerId? = null
            var startScrollOffsetPx = 0.0
            var startTouchPosition = Offset.Zero
            var lastMoveYPx = 0.0
            var lastMoveTimeMs = 0L
            var scrollSpeedPxPerMs = 0.0

            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (activeTouchId == null) {
                        val down = event.changes.firstOrNull { change ->
                            change.type == PointerType.Touch &&
                                change.pressed &&
                                !change.previousPressed
                        }
                        if (down != null && beginScrollHandler()) {
                            activeTouchId = down.id
                            runtime.isUserScrolling = true
                            startScrollOffsetPx = runtime.scrollOffsetPx
                            startTouchPosition = down.position
                            lastMoveYPx = down.position.y.toDouble()
                            lastMoveTimeMs = down.uptimeMillis
                            scrollSpeedPxPerMs = 0.0
                            down.consume()
                            invalidateLayout(sync = true, force = true)
                        }
                        continue
                    }

                    val change = event.changes.firstOrNull { it.id == activeTouchId }
                        ?: continue
                    if (event.type == PointerEventType.Move && change.pressed) {
                        if (beginScrollHandler()) {
                            change.consume()
                            val currentY = change.position.y.toDouble()
                            runtime.scrollOffsetPx = computeAmllTouchScrollOffset(
                                startScrollOffsetPx = startScrollOffsetPx,
                                startTouchYPx = startTouchPosition.y.toDouble(),
                                currentTouchYPx = currentY,
                                minimumPx = runtime.scrollMinPx,
                                maximumPx = runtime.scrollMaxPx,
                            )
                            val deltaMs = change.uptimeMillis - lastMoveTimeMs
                            if (deltaMs > 0L) {
                                scrollSpeedPxPerMs =
                                    (currentY - lastMoveYPx) / deltaMs.toDouble()
                            }
                            lastMoveYPx = currentY
                            lastMoveTimeMs = change.uptimeMillis
                            invalidateLayout(sync = true, force = true)
                        }
                        continue
                    }

                    if (!change.pressed && change.previousPressed) {
                        val allowed = beginScrollHandler()
                        if (allowed) {
                            change.consume()
                            if (
                                isAmllTouchClickCandidate(
                                    start = startTouchPosition,
                                    end = change.position,
                                    thresholdPx = touchClickThresholdPx,
                                )
                            ) {
                                val clickedIndex = pickAmllTouchClickGroupIndex(
                                    position = change.position,
                                    viewportWidthPx = runtime.lastViewportWidthPx,
                                    viewportHeightPx = runtime.lastViewportHeightPx,
                                    groupTopsPx = runtime.engine.currentFrame().groups.map {
                                        it.topPx
                                    },
                                    groupHeightsPx = runtime.renderedGroupHeightsPx,
                                )
                                runtime.isUserScrolling = false
                                clickedIndex?.let { index ->
                                    clickGroup(index, requestPointerFocus = true)
                                }
                            } else {
                                launchInertia(scrollSpeedPxPerMs)
                            }
                        } else {
                            runtime.isUserScrolling = false
                        }
                        activeTouchId = null
                    }
                }
            }
        }
        .semantics { contentDescription = "歌词" }
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                // player.html's capture listener reveals controls for every keydown.
                onInteraction()
            }
            if (event.type != KeyEventType.KeyDown || document.groups.isEmpty()) {
                return@onPreviewKeyEvent false
            }
            when (event.key) {
                Key.DirectionUp -> {
                    moveKeyboardFocus(
                        moveAmllRovingKeyboardIndex(
                            groupCount = document.groups.size,
                            currentIndex = focusedKeyboardIndex ?: keyboardIndex,
                            activeIndex = activeKeyboardIndex,
                            retainCurrentFocus = focusedKeyboardIndex != null,
                            delta = -1,
                        ),
                    )
                    true
                }
                Key.DirectionDown -> {
                    moveKeyboardFocus(
                        moveAmllRovingKeyboardIndex(
                            groupCount = document.groups.size,
                            currentIndex = focusedKeyboardIndex ?: keyboardIndex,
                            activeIndex = activeKeyboardIndex,
                            retainCurrentFocus = focusedKeyboardIndex != null,
                            delta = 1,
                        ),
                    )
                    true
                }
                Key.MoveHome -> {
                    moveKeyboardFocus(0)
                    true
                }
                Key.MoveEnd -> {
                    moveKeyboardFocus(document.groups.lastIndex)
                    true
                }
                Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                    focusedKeyboardIndex?.let { focusedIndex ->
                        clickGroup(focusedIndex, requestPointerFocus = false)
                        true
                    } ?: false
                }
                else -> false
            }
        }

    @Suppress("UNUSED_EXPRESSION")
    renderPulse

    Layout(
        modifier = rootModifier,
        content = {
            /*
             * LyricPlayerBase appends interludeDots before lyric groups. Positioned siblings with
             * no explicit z-index paint in DOM order, so groups must remain above the dots where
             * their boxes overlap.
             */
            AmllInterludeDotsVisual(
                target = frame.interludeDotsTarget,
                isPlaying = isPlaying,
                baseFontSizeDp = parameters.baseFontSizeSp,
                transitionDurationMs = parameters.transitionDurationMs,
            )
            document.groups.forEachIndexed { index, group ->
                val shouldBackgroundBeFirst =
                    !alwaysPostpositionBackground && group.isBackgroundFirst
                val groupFrame = frame.groups.getOrNull(index) ?: AmllGroupFrame(
                    index = index,
                    topPx = 0.0,
                    backgroundSlideYPercent = if (shouldBackgroundBeFirst) 80.0 else -80.0,
                    backgroundWrapperScale = 0.8,
                    mainScalePercent = 100.0,
                    backgroundScalePercent = 100.0,
                    isActive = false,
                    opacity = 1.0,
                    blurPx = 0.0,
                    renderMode = AmllLineRenderMode.SOLID,
                    animateMaskAlpha = parameters.springEnabled,
                )
                AmllLyricGroupContent(
                    group = group,
                    frame = groupFrame,
                    positionState = positionState,
                    isPlaying = isPlaying,
                    parameters = parameters,
                    androidPresentation = androidPresentation,
                    alwaysPostpositionBackground = alwaysPostpositionBackground,
                    isNonDynamic = engine.isNonDynamic,
                    hasDuetLine = hasDuetLine,
                    viewportHovered = isViewportHovered,
                    focusRequester = keyboardFocusRequesters[index],
                    canFocus = resolvedKeyboardIndex == index,
                    keyboardFocused = shouldShowAmllFocusOutline(
                        renderedIndex = index,
                        focusedIndex = focusedKeyboardIndex,
                        focusVisible = keyboardFocusVisible,
                    ),
                    accessibilityLabel = amllLyricGroupAccessibilityLabel(
                        group = group,
                        isNonDynamic = engine.isNonDynamic,
                    ),
                    onFocusChanged = { isFocused ->
                        if (isFocused) {
                            focusedKeyboardIndex = index
                            keyboardIndex = index
                            keyboardFocusVisible = pendingFocusVisible ?: true
                            pendingKeyboardFocusIndex = null
                            pendingFocusVisible = null
                        } else if (focusedKeyboardIndex == index) {
                            focusedKeyboardIndex = null
                            keyboardFocusVisible = false
                            if (pendingKeyboardFocusIndex == null) {
                                keyboardIndex = resolveAmllRovingKeyboardIndex(
                                    groupCount = document.groups.size,
                                    currentIndex = keyboardIndex,
                                    activeIndex = activeKeyboardIndex,
                                    retainCurrentFocus = false,
                                )
                            }
                        }
                    },
                    onClick = {
                        clickGroup(index, requestPointerFocus = true)
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(constraints.minWidth)
        val height = constraints.maxHeight.coerceAtLeast(constraints.minHeight)
        runtime.ensureGroupMeasurementSlots(document.groups.size)
        val dotMeasurable = measurables.first()
        val groupMeasurables = measurables.drop(1)
        val groupPlaceables = groupMeasurables.map { measurable ->
            measurable.measure(
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    minHeight = 0,
                    // DOM lyric wrappers use fit-content and are not capped to one viewport.
                    maxHeight = Constraints.Infinity,
                ),
            )
        }
        groupPlaceables.forEachIndexed { index, placeable ->
            runtime.renderedGroupHeightsPx[index] = placeable.height
        }
        val dotPlaceable = dotMeasurable.measure(
            Constraints(minWidth = 0, maxWidth = width, minHeight = 0, maxHeight = height),
        )
        if (runtime.hasPerformedInitialLayout && !runtime.interludeDotsMeasurementAvailable) {
            // ResizeObserver publishes the always-mounted dots after the first fallback layout.
            runtime.interludeDotsMeasurementAvailable = true
            runtime.requestLayout(sync = true, force = false)
        }

        val fontSizePx = with(density) { parameters.baseFontSizeSp.dp.toPx() }.toDouble()
        val compactMode = parameters.viewportClass != AmllLyricViewportClass.WIDE
        val rootOrDotsChanged =
            runtime.lastViewportWidthPx != width ||
                runtime.lastViewportHeightPx != height ||
                runtime.lastInterludeDotsWidthPx != dotPlaceable.width ||
                runtime.lastInterludeDotsHeightPx != dotPlaceable.height ||
                runtime.lastFontSizePx != fontSizePx ||
                runtime.lastCompactMode != compactMode ||
                runtime.lastSpringEnabled != parameters.springEnabled ||
                runtime.lastAlwaysPostpositionBackground != alwaysPostpositionBackground
        if (rootOrDotsChanged) {
            runtime.lastViewportWidthPx = width
            runtime.lastViewportHeightPx = height
            runtime.lastInterludeDotsWidthPx = dotPlaceable.width
            runtime.lastInterludeDotsHeightPx = dotPlaceable.height
            runtime.lastFontSizePx = fontSizePx
            runtime.lastCompactMode = compactMode
            runtime.lastSpringEnabled = parameters.springEnabled
            runtime.lastAlwaysPostpositionBackground = alwaysPostpositionBackground
            runtime.requestLayout(sync = true, force = false)
        }

        /*
         * DOM groups are detached outside overscan. Their WeakMap size is absent until they first
         * enter sight, so the base layout keeps using viewportHeight / 5. Compose must not leak its
         * eager measurement into that initial trajectory.
         */
        if (runtime.hasPerformedInitialLayout) {
            val overscanPx = engine.layoutState.overscanPx
            groupPlaceables.forEachIndexed { index, placeable ->
                val knownHeight = runtime.knownGroupHeightsPx[index]
                val heightForSight = knownHeight ?: 0
                val topPx = frame.groups.getOrNull(index)?.topPx ?: 0.0
                val isInSight =
                    topPx <= height + heightForSight + overscanPx &&
                        topPx >= -heightForSight - overscanPx
                if (knownHeight == null && isInSight) {
                    runtime.knownGroupHeightsPx[index] = placeable.height
                    runtime.knownGroupWidthsPx[index] = placeable.width
                    runtime.requestLayout(sync = true, force = false)
                } else if (
                    knownHeight != null &&
                    (
                        knownHeight != placeable.height ||
                            runtime.knownGroupWidthsPx[index] != placeable.width
                        )
                ) {
                    runtime.knownGroupHeightsPx[index] = placeable.height
                    runtime.knownGroupWidthsPx[index] = placeable.width
                    runtime.requestLayout(sync = true, force = false)
                }
            }
        }

        var localFrame = frame
        if (runtime.hasPendingLayoutForMeasure()) {
            val result = engine.layout(
                AmllLayoutInput(
                    viewportWidthPx = width.toDouble(),
                    viewportHeightPx = height.toDouble(),
                    groupHeightsPx = runtime.knownGroupHeightsPx.map { it?.toDouble() },
                    interludeDotsWidthPx = if (runtime.interludeDotsMeasurementAvailable) {
                        dotPlaceable.width.toDouble()
                    } else {
                        0.0
                    },
                    interludeDotsHeightPx = if (runtime.interludeDotsMeasurementAvailable) {
                        dotPlaceable.height.toDouble()
                    } else {
                        0.0
                    },
                    scrollOffsetPx = runtime.scrollOffsetPx,
                    fontSizePx = fontSizePx,
                    bottomLineHeightPx = 0.0,
                    hidePassedLines = false,
                    isNonDynamic = engine.isNonDynamic,
                    enableSpring = parameters.springEnabled,
                    enableScale = true,
                    enableBlur = true,
                    isUserScrolling = runtime.isUserScrolling,
                    // Core checks window.innerWidth, not the width of a split lyric pane.
                    isCompact = compactMode,
                    isPlaying = isPlaying,
                    sync = runtime.syncNextLayout,
                    // `setEnableSpring(false)` snaps Spring values through the separate
                    // `!enableSpring` branch; it does not set calcLayout's `force` argument.
                    force = runtime.forceNextLayout,
                    alwaysPostpositionBackground = alwaysPostpositionBackground,
                ),
            )
            runtime.scrollMinPx = result.scrollMinOffsetPx
            runtime.scrollMaxPx = result.scrollMaxOffsetPx
            runtime.needsLayout = false
            runtime.syncNextLayout = false
            runtime.forceNextLayout = false
            runtime.hasPerformedInitialLayout = true
            localFrame = engine.currentFrame()
        }

        layout(width, height) {
            val dotsTarget = localFrame.interludeDotsTarget?.also {
                runtime.lastVisibleInterludeDotsTarget = it
            } ?: runtime.lastVisibleInterludeDotsTarget
            if (dotsTarget != null) {
                // InterludeDots.setTransform serializes both axes with toFixed(2).
                val x = roundToAmllPrecision(dotsTarget.xPx, scale = 100.0)
                val y = roundToAmllPrecision(dotsTarget.yPx, scale = 100.0)
                val integralX = floor(x).toInt()
                val integralY = floor(y).toInt()
                // Core writes a physical CSS `left`, independent of the host locale direction.
                dotPlaceable.placeWithLayer(integralX, integralY) {
                    translationX = (x - integralX).toFloat()
                    translationY = (y - integralY).toFloat()
                }
            } else {
                dotPlaceable.place(0, height + dotPlaceable.height)
            }
            groupPlaceables.forEachIndexed { index, placeable ->
                // LyricLineGroup.renderStyles serializes Y with toFixed(1).
                val top = roundToAmllPrecision(
                    localFrame.groups.getOrNull(index)?.topPx ?: 0.0,
                    scale = 10.0,
                )
                val integralTop = floor(top).toInt()
                placeable.placeRelativeWithLayer(0, integralTop) {
                    translationY = (top - integralTop).toFloat()
                }
            }
        }
    }

    engine.timelineState.hotGroups.minOrNull()?.let { activeGroupIndex ->
        document.groups.getOrNull(activeGroupIndex)?.mainLine?.let { activeLine ->
            val announcement = listOfNotNull(
                activeLine.mainText,
                activeLine.translatedText?.takeIf(String::isNotBlank),
                activeLine.romanText?.takeIf(String::isNotBlank),
            ).joinToString("，")
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints.fixed(1, 1))
                        layout(1, 1) { placeable.place(IntOffset.Zero) }
                    }
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "当前歌词：$announcement"
                    },
            )
        }
    }
}

/**
 * Numeric counterpart of the CSS values emitted through `Number.toFixed()`.
 *
 * `kotlin.math.round` uses ties-to-even, while ECMAScript `toFixed()` rounds a mathematical
 * half away from zero (before the source number's ordinary binary-representation effects).
 */
internal fun roundToAmllPrecision(value: Double, scale: Double): Double {
    if (!value.isFinite()) return value
    val scaled = value * scale
    val rounded = if (scaled >= 0.0) {
        floor(scaled + 0.5)
    } else {
        ceil(scaled - 0.5)
    }
    return rounded / scale
}

@Composable
private fun AmllLyricGroupContent(
    group: AmllLyricGroup,
    frame: AmllGroupFrame,
    positionState: State<Long>,
    isPlaying: Boolean,
    parameters: AmllLyricVisualParameters,
    androidPresentation: Boolean,
    alwaysPostpositionBackground: Boolean,
    isNonDynamic: Boolean,
    hasDuetLine: Boolean,
    viewportHovered: Boolean,
    focusRequester: FocusRequester,
    canFocus: Boolean,
    keyboardFocused: Boolean,
    accessibilityLabel: String,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val baseFontSizeDp = parameters.baseFontSizeSp
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var pressed by remember { mutableStateOf(false) }
    val wrapperHorizontalPadding = when {
        // This host override appears after core's <=500px rule and therefore wins on Android.
        androidPresentation -> (baseFontSizeDp * 0.75f).dp
        else -> parameters.horizontalPaddingDp.dp
    }
    val wrapperVerticalPadding = (baseFontSizeDp * 0.4f).dp
    val gapPx = with(density) { (baseFontSizeDp * 0.3f).dp.roundToPx() }
    val shouldBackgroundBeFirst =
        !alwaysPostpositionBackground && group.isBackgroundFirst
    val shape = RoundedCornerShape((baseFontSizeDp * 0.25f).dp)
    val transitionSpec: FiniteAnimationSpec<Float> =
        if (parameters.transitionDurationMs == 0) {
        snap()
    } else {
        tween(parameters.transitionDurationMs, easing = AmllCssEase)
    }
    val renderedOpacity by animateFloatAsState(
        targetValue = frame.opacity.toFloat().coerceIn(0f, 1f),
        animationSpec = transitionSpec,
        label = "amll-group-opacity-${group.id}",
    )
    val renderedBlurPx by animateFloatAsState(
        targetValue = if (viewportHovered) 0f else frame.blurPx.toFloat(),
        animationSpec = transitionSpec,
        label = "amll-group-blur-${group.id}",
    )
    val blurModifier = if (renderedBlurPx > 0f) {
        Modifier.blur(
            radius = with(density) { renderedBlurPx.toDp() },
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )
    } else {
        Modifier
    }
    val targetBackgroundColor = when {
        pressed -> Color.White.copy(alpha = 0.019607844f)
        hovered -> Color.White.copy(alpha = 0.06666667f)
        else -> Color.Transparent
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = if (parameters.transitionDurationMs == 0) {
            snap()
        } else {
            tween(250, easing = AmllCssEase)
        },
        label = "amll-group-background-${group.id}",
    )
    val renderedBackgroundWrapperOpacity by animateFloatAsState(
        targetValue = if (frame.isActive || !isPlaying) 1f else 0f,
        animationSpec = if (parameters.transitionDurationMs == 0) {
            snap()
        } else {
            tween(300, easing = AmllCssEase)
        },
        label = "amll-background-wrapper-opacity-${group.id}",
    )
    val backgroundWrapperHidden = isAmllBackgroundWrapperHidden(
        slideYPercent = frame.backgroundSlideYPercent,
        isActive = frame.isActive,
        shouldBackgroundBeFirst = shouldBackgroundBeFirst,
    )
    val wrapperModifier = Modifier
        .fillMaxWidth()
        .graphicsLayer { alpha = renderedOpacity }
        .then(blurModifier)
        .background(backgroundColor, shape)
        .drawWithContent {
            drawContent()
            if (keyboardFocused) {
                val strokeWidth = 3.dp.toPx()
                val outlineOffset = 2.dp.toPx()
                val pathExpansion = outlineOffset + strokeWidth / 2f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.92f),
                    topLeft = Offset(-pathExpansion, -pathExpansion),
                    size = Size(
                        width = size.width + pathExpansion * 2f,
                        height = size.height + pathExpansion * 2f,
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )
            }
        }
        .hoverable(interactionSource)
        .pointerInput(group.id) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                    }
                },
                onTap = { onClick() },
            )
        }
        .focusProperties { this.canFocus = canFocus }
        .focusRequester(focusRequester)
        .onFocusChanged { state -> onFocusChanged(state.isFocused) }
        .focusable()
        .semantics {
            role = Role.Button
            contentDescription = accessibilityLabel
            selected = frame.isActive
            stateDescription = if (frame.isActive) "当前歌词" else "歌词"
            onClick(label = "跳转到这句歌词") {
                onClick()
                true
            }
        }
        .padding(
            horizontal = wrapperHorizontalPadding,
            vertical = wrapperVerticalPadding,
        )

    Layout(
        modifier = wrapperModifier,
        content = {
            AmllLyricLineContent(
                line = group.mainLine,
                positionState = positionState,
                active = frame.renderMode == AmllLineRenderMode.GRADIENT,
                maskScale = frame.mainScalePercent / 100.0,
                maskRenderMode = frame.renderMode,
                animateMaskAlpha = frame.animateMaskAlpha,
                baseFontSizeDp = baseFontSizeDp,
                androidPresentation = androidPresentation,
                isNonDynamic = isNonDynamic,
                hasDuetLine = hasDuetLine,
                effectiveDuet = group.mainLine.isDuet,
                isBackgroundLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val scale = roundToAmllPrecision(
                                value = frame.mainScalePercent / 100.0,
                                scale = 10_000.0,
                            ).toFloat()
                            scaleX = scale
                            scaleY = scale
                        transformOrigin = if (group.mainLine.isDuet) {
                            TransformOrigin(1f, 0.5f)
                        } else {
                            TransformOrigin(0f, 0.5f)
                        }
                    },
            )
            group.backgroundLine?.let { backgroundLine ->
                /*
                 * Core has two distinct transform nodes:
                 *   bgWrapper: translateY(slide%) scale(0.8..1), origin at the wrapper edge
                 *   lyricBgLine: scale(lineSpring), origin at the line's horizontal edge/center Y
                 * Multiplying both scales into one layer changes the translation and Y pivot while either
                 * spring is moving, so preserve the DOM nesting literally.
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // `.bgWrapper` has z-index:-1, including while its slide overlaps main.
                        .zIndex(-1f)
                        .graphicsLayer {
                            val wrapperScale = roundToAmllPrecision(
                                value = frame.backgroundWrapperScale,
                                scale = 1_000.0,
                            ).toFloat()
                            val serializedSlide = roundToAmllPrecision(
                                value = frame.backgroundSlideYPercent,
                                scale = 10.0,
                            ).toFloat()
                            scaleX = wrapperScale
                            scaleY = wrapperScale
                            alpha = if (backgroundWrapperHidden) {
                                0f
                            } else {
                                parameters.backgroundLineOpacity *
                                    renderedBackgroundWrapperOpacity
                            }
                            translationY = size.height * serializedSlide / 100f
                            transformOrigin = when {
                                group.mainLine.isDuet && shouldBackgroundBeFirst ->
                                    TransformOrigin(1f, 1f)
                                group.mainLine.isDuet -> TransformOrigin(1f, 0f)
                                shouldBackgroundBeFirst -> TransformOrigin(0f, 1f)
                                else -> TransformOrigin(0f, 0f)
                            }
                        },
                ) {
                    AmllLyricLineContent(
                        line = backgroundLine,
                        positionState = positionState,
                        active = frame.renderMode == AmllLineRenderMode.GRADIENT,
                        maskScale = frame.backgroundScalePercent / 100.0,
                        maskRenderMode = frame.renderMode,
                        animateMaskAlpha = frame.animateMaskAlpha,
                        baseFontSizeDp = max(baseFontSizeDp * 0.7f, 10f),
                        androidPresentation = androidPresentation,
                        isNonDynamic = isNonDynamic,
                        hasDuetLine = hasDuetLine,
                        // LyricLineGroup.addBgLine() mirrors the main line's duet class.
                        effectiveDuet = group.mainLine.isDuet,
                        isBackgroundLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val lineScale = roundToAmllPrecision(
                                    value = frame.backgroundScalePercent / 100.0,
                                    scale = 10_000.0,
                                ).toFloat()
                                scaleX = lineScale
                                scaleY = lineScale
                                transformOrigin = if (group.mainLine.isDuet) {
                                    TransformOrigin(1f, 0.5f)
                                } else {
                                    TransformOrigin(0f, 0.5f)
                                }
                            },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val main = measurables.first().measure(constraints)
        val postpositionIsActive = frame.isActive || !isPlaying
        val inactivePostpositionInsetPx = if (
            !shouldBackgroundBeFirst && !postpositionIsActive
        ) {
            with(density) { wrapperHorizontalPadding.roundToPx() }
        } else {
            0
        }
        val backgroundWidth =
            (constraints.maxWidth - inactivePostpositionInsetPx * 2).coerceAtLeast(0)
        val background = measurables.getOrNull(1)?.measure(
            constraints.copy(
                minWidth = backgroundWidth,
                maxWidth = backgroundWidth,
                minHeight = 0,
            ),
        )
        val activeProgress =
            (1.0 - abs(frame.backgroundSlideYPercent) / 80.0).coerceIn(0.0, 1.0)
        val postpositionBackgroundInFlow =
            background != null && !shouldBackgroundBeFirst && postpositionIsActive
        val measuredHeight = when {
            background == null -> main.height
            shouldBackgroundBeFirst ->
                main.height + gapPx + (background.height * activeProgress).roundToInt()
            postpositionBackgroundInFlow -> main.height + gapPx + background.height
            else -> main.height
        }
        layout(constraints.maxWidth, measuredHeight) {
            if (background == null) {
                main.placeRelative(0, 0)
            } else if (shouldBackgroundBeFirst) {
                val visibleBackgroundHeight = (background.height * activeProgress).roundToInt()
                background.placeRelative(0, visibleBackgroundHeight - background.height)
                main.placeRelative(0, visibleBackgroundHeight + gapPx)
            } else {
                main.placeRelative(0, 0)
                background.placeRelative(
                    inactivePostpositionInsetPx,
                    if (postpositionBackgroundInFlow) main.height + gapPx else main.height,
                )
            }
        }
    }
}

/**
 * `LyricLineGroup.renderStyles()` compares the serialized one-decimal slide against ±80.0 before
 * applying `.bgWrapperHidden`.
 */
internal fun isAmllBackgroundWrapperHidden(
    slideYPercent: Double,
    isActive: Boolean,
    shouldBackgroundBeFirst: Boolean,
): Boolean {
    if (isActive) return false
    val serializedSlide = roundToAmllPrecision(slideYPercent, scale = 10.0)
    val hiddenTarget = if (shouldBackgroundBeFirst) 80.0 else -80.0
    return serializedSlide == hiddenTarget
}

@Composable
private fun AmllLyricLineContent(
    line: AmllLyricLine,
    positionState: State<Long>,
    active: Boolean,
    maskScale: Double,
    maskRenderMode: AmllLineRenderMode,
    animateMaskAlpha: Boolean,
    baseFontSizeDp: Float,
    androidPresentation: Boolean,
    isNonDynamic: Boolean,
    hasDuetLine: Boolean,
    effectiveDuet: Boolean,
    isBackgroundLine: Boolean,
    modifier: Modifier,
) {
    // Core uses the physical CSS values `text-align:right` / `text-align:start`.
    val textAlign = if (effectiveDuet) TextAlign.Right else TextAlign.Start
    // `.lyricLine` has margin:-.2em/padding:.2em, so its default net layout inset is zero.
    // Android changes only the padding to .42em, leaving a visible .22em on each edge.
    // Background lines add another .2em on each edge through main-line 1.2em/-1em.
    val netVerticalInsetDp =
        (if (androidPresentation) baseFontSizeDp * 0.22f else 0f) +
            (if (isBackgroundLine) baseFontSizeDp * 0.2f else 0f)

    Layout(
        modifier = modifier,
        content = {
            Column(
                modifier = Modifier.padding(vertical = netVerticalInsetDp.dp),
            ) {
                AmllTimedWordLine(
                    line = line,
                    positionState = positionState,
                    active = active,
                    baseFontSizeSp = baseFontSizeDp,
                    androidPresentation = androidPresentation,
                    isNonDynamic = isNonDynamic,
                    maskScale = maskScale,
                    maskRenderMode = maskRenderMode,
                    animateMaskAlpha = animateMaskAlpha,
                    modifier = Modifier.fillMaxWidth(),
                )
                line.translatedText?.takeIf(String::isNotBlank)?.let { translated ->
                    AmllSubLine(translated, baseFontSizeDp, textAlign)
                }
                line.romanText?.takeIf(String::isNotBlank)?.let { roman ->
                    AmllSubLine(roman, baseFontSizeDp, textAlign)
                }
            }
        },
    ) { measurables, constraints ->
        val horizontalInset = if (hasDuetLine) {
            (constraints.maxWidth * 0.15f).roundToInt()
        } else {
            0
        }
        val contentWidth = (constraints.maxWidth - horizontalInset).coerceAtLeast(0)
        val child = measurables.single().measure(
            constraints.copy(
                minWidth = contentWidth,
                maxWidth = contentWidth,
                minHeight = 0,
            ),
        )
        layout(constraints.maxWidth, child.height) {
            // `.hasDuetLine .lyricDuetLine` uses physical `padding-left:15%`.
            child.place(if (effectiveDuet) horizontalInset else 0, 0)
        }
    }
}

@Composable
private fun AmllSubLine(
    text: String,
    baseFontSizeDp: Float,
    textAlign: TextAlign,
) {
    val density = LocalDensity.current
    val sizeDp = max(baseFontSizeDp * 0.5f, 10f)
    val fontSize = with(density) { sizeDp.dp.toSp() }
    val lineHeight = with(density) { (sizeDp * 1.5f).dp.toSp() }
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFE1E1E1),
        fontSize = fontSize,
        fontWeight = FontWeight.Normal,
        lineHeight = lineHeight,
        textAlign = textAlign,
    )
}

@Composable
private fun AmllInterludeDotsVisual(
    target: AmllInterludeDotsTarget?,
    isPlaying: Boolean,
    baseFontSizeDp: Float,
    transitionDurationMs: Int,
) {
    val density = LocalDensity.current
    val visualRuntime = remember { AmllInterludeVisualRuntime() }
    if (visualRuntime.target != target) {
        /*
         * InterludeDots.setInterlude() resets its private clock to startTime but deliberately leaves
         * the last inline transform/dot opacity untouched until the next rAF update.
         */
        visualRuntime.setInterlude(target)
    }
    LaunchedEffect(visualRuntime, target, isPlaying) {
        if (!isPlaying || target == null) return@LaunchedEffect
        var previousFrame = TimeSource.Monotonic.markNow()
        while (true) {
            withFrameNanos { }
            val deltaMs = previousFrame
                .elapsedNow()
                .toDouble(DurationUnit.MILLISECONDS)
                .coerceAtLeast(0.0)
            previousFrame = TimeSource.Monotonic.markNow()
            visualRuntime.update(deltaMs)
        }
    }
    val visual = visualRuntime.visual
    val wrapperOpacity by animateFloatAsState(
        targetValue = if (target == null) 0f else 1f,
        animationSpec = if (transitionDurationMs == 0) {
            snap()
        } else {
            tween(250, easing = AmllCssEase)
        },
        label = "amll-interlude-enabled-opacity",
    )

    Layout(
        modifier = Modifier
            .graphicsLayer {
                // globalOpacity is already multiplied into each dot exactly once.
                alpha = wrapperOpacity
                scaleX = visual.scale.toFloat()
                scaleY = visual.scale.toFloat()
                transformOrigin = TransformOrigin.Center
            },
        content = {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = visual.dotAlphas[index].toFloat() }
                        .background(Color.White, CircleShape),
                )
            }
        },
    ) { measurables, constraints ->
        val fontSizePx = with(density) { baseFontSizeDp.dp.toPx() }
        val oneViewportHeightPercent = constraints.maxHeight * 0.01f
        val dotSizePx = oneViewportHeightPercent
            .coerceIn(fontSizePx * 0.5f, fontSizePx * 3f)
            .roundToInt()
            .coerceAtLeast(1)
        val gapPx = (fontSizePx * 0.25f).roundToInt()
        val paddingHorizontalPx = (fontSizePx * 0.75f).roundToInt()
        // CSS percentage padding, including the vertical axis, resolves against containing width.
        val paddingVerticalPx = (constraints.maxWidth * 0.025f).roundToInt()
        val marginRightPx = with(density) { 4.dp.roundToPx() }
        val widthPx = paddingHorizontalPx * 2 +
            dotSizePx * 3 +
            marginRightPx * 3 +
            gapPx * 2
        val heightPx = paddingVerticalPx * 2 + dotSizePx
        val placeables = measurables.map {
            it.measure(Constraints.fixed(dotSizePx, dotSizePx))
        }
        layout(widthPx, heightPx) {
            var x = paddingHorizontalPx
            placeables.forEach { placeable ->
                placeable.place(x, paddingVerticalPx)
                x += dotSizePx + marginRightPx + gapPx
            }
        }
    }
}

internal data class AmllInterludeVisual(
    val scale: Double,
    val dotAlphas: List<Double>,
)

private class AmllInterludeVisualRuntime {
    var target: AmllInterludeDotsTarget? = null
        private set
    private var currentTimeMs = 0.0
    var visual by mutableStateOf(
        AmllInterludeVisual(0.0, listOf(0.0, 0.0, 0.0)),
    )
        private set

    fun setInterlude(newTarget: AmllInterludeDotsTarget?) {
        target = newTarget
        currentTimeMs = newTarget?.startTimeMs ?: 0.0
    }

    fun update(deltaMs: Double) {
        val currentTarget = target ?: return
        currentTimeMs += deltaMs
        visual = computeAmllInterludeVisual(currentTarget, currentTimeMs)
    }
}

internal fun computeAmllInterludeVisual(
    target: AmllInterludeDotsTarget?,
    nowMs: Double,
): AmllInterludeVisual {
    if (target == null) {
        return AmllInterludeVisual(0.0, listOf(0.0, 0.0, 0.0))
    }
    val interludeDuration = target.endTimeMs - target.startTimeMs
    val currentDuration = max(0.0, nowMs - target.startTimeMs)
    if (interludeDuration <= 0.0 || currentDuration > interludeDuration) {
        return AmllInterludeVisual(0.0, listOf(0.0, 0.0, 0.0))
    }
    val breatheDuration = interludeDuration / ceil(interludeDuration / 1_500.0)
    var scale = sin(1.5 * PI - (currentDuration / breatheDuration) * 2.0) / 20.0 + 1.0
    var globalOpacity = 1.0
    if (currentDuration < 2_000.0) {
        scale *= easeOutExpo(currentDuration / 2_000.0)
    }
    if (currentDuration < 500.0) {
        globalOpacity = 0.0
    } else if (currentDuration < 1_000.0) {
        globalOpacity *= (currentDuration - 500.0) / 500.0
    }
    val remaining = interludeDuration - currentDuration
    if (remaining < 750.0) {
        scale *= 1.0 - easeInOutBack((750.0 - remaining) / 750.0 / 2.0)
    }
    if (remaining < 375.0) {
        globalOpacity *= (remaining / 375.0).coerceIn(0.0, 1.0)
    }
    val dotsDuration = max(0.0, interludeDuration - 750.0)
    fun dotOpacity(offset: Double): Double {
        if (dotsDuration <= 0.0) return 1.0
        return max(
            0.25,
            (((currentDuration - offset) * 3.0) / dotsDuration) * 0.75,
        ).coerceAtMost(1.0)
    }
    return AmllInterludeVisual(
        scale = max(0.0, scale) * 0.7,
        dotAlphas = listOf(
            (globalOpacity * dotOpacity(0.0)).coerceIn(0.0, 1.0),
            (globalOpacity * dotOpacity(dotsDuration / 3.0)).coerceIn(0.0, 1.0),
            (globalOpacity * dotOpacity(dotsDuration / 3.0 * 2.0)).coerceIn(0.0, 1.0),
        ),
    )
}

private fun easeInOutBack(value: Double): Double {
    val x = value.coerceIn(0.0, 1.0)
    val c1 = 1.70158
    val c2 = c1 * 1.525
    return if (x < 0.5) {
        ((2.0 * x).pow(2.0) * ((c2 + 1.0) * 2.0 * x - c2)) / 2.0
    } else {
        ((2.0 * x - 2.0).pow(2.0) * ((c2 + 1.0) * (x * 2.0 - 2.0) + c2) + 2.0) /
            2.0
    }
}

private fun easeOutExpo(value: Double): Double {
    val x = value.coerceIn(0.0, 1.0)
    return if (x == 1.0) 1.0 else 1.0 - 2.0.pow(-10.0 * x)
}

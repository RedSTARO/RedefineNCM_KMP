/*
 * Behavioral regression tests for the Kotlin translation of
 * @applemusic-like-lyrics/core 0.5.2 layout.ts, timeline.ts, group.ts, and base/index.ts.
 * Test adaptation dated 2026-07-26.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AmllPlayerEngineTest {

    @Test
    fun timelineBoundarySearchUsesExclusiveStartAndInclusiveEnd() {
        val boundaries = doubleArrayOf(1_000.0, 2_000.0, 3_000.0)

        assertFalse(
            crossesAmllTimelineBoundary(
                boundariesMs = boundaries,
                fromExclusiveMs = 1_000.0,
                toInclusiveMs = 1_999.0,
            ),
        )
        assertTrue(
            crossesAmllTimelineBoundary(
                boundariesMs = boundaries,
                fromExclusiveMs = 1_000.0,
                toInclusiveMs = 2_000.0,
            ),
        )
        assertFalse(
            crossesAmllTimelineBoundary(
                boundariesMs = boundaries,
                fromExclusiveMs = 3_000.0,
                toInclusiveMs = 3_500.0,
            ),
        )
        assertTrue(
            crossesAmllTimelineBoundary(
                boundariesMs = boundaries,
                fromExclusiveMs = 2_000.0,
                toInclusiveMs = 1_999.0,
            ),
        )
    }

    @Test
    fun timelineUpdateSkipsGroupScanUntilTheNextBoundary() {
        val engine = AmllPlayerEngine(
            lines = listOf(line(id = "first", startMs = 1_000.0, endMs = 2_000.0)),
            initialTimeMs = 1_100.0,
        )

        val firstStableUpdate = engine.setTime(timeMs = 1_500.0)
        assertSame(firstStableUpdate, engine.setTime(timeMs = 1_750.0))
        val boundaryUpdate = engine.setTime(timeMs = 2_000.0)
        assertTrue(boundaryUpdate.shouldLayout)
        assertEquals(emptySet(), engine.timelineState.hotGroups)
    }

    @Test
    fun optimizedTimelineMatchesAFullScanAcrossPlaybackTransitions() {
        val engine = AmllPlayerEngine(
            lines = listOf(
                line(id = "a", startMs = 1_000.4, endMs = 2_500.0),
                line(id = "b", startMs = 1_500.0, endMs = 3_000.0),
                line(id = "zero", startMs = 2_000.0, endMs = 2_000.0),
                line(id = "d", startMs = 4_000.0, endMs = 5_000.0),
            ),
            initialTimeMs = 0.0,
        )
        val reference = engine.timelineState.copy(
            hotGroups = LinkedHashSet(engine.timelineState.hotGroups),
            bufferedGroups = LinkedHashSet(engine.timelineState.bufferedGroups),
        )
        val timelineBoundaries = engine.groups
            .flatMap { group -> listOf(group.exactStartTimeMs, group.exactEndTimeMs) }
            .filter(Double::isFinite)
            .distinct()
            .sorted()
            .toDoubleArray()
        val steps = listOf(
            Triple(0.0, false, false),
            Triple(999.4, false, false),
            Triple(1_000.4, false, false),
            Triple(1_000.6, false, false),
            Triple(1_499.6, false, false),
            Triple(2_000.0, false, false),
            Triple(3_500.0, false, false),
            Triple(1_600.0, false, false),
            Triple(2_750.0, true, false),
            Triple(5_500.0, false, true),
            Triple(5_500.0, false, false),
        )

        for ((timeMs, isSeek, hasBottomContent) in steps) {
            val roundedTimeMs = floor(timeMs + 0.5)
            val previousTimeMs = reference.currentTimeMs
            val crossedTimelineBoundary = crossesAmllTimelineBoundary(
                boundariesMs = timelineBoundaries,
                fromExclusiveMs = previousTimeMs,
                toInclusiveMs = roundedTimeMs,
            )
            reference.isSeeking = isSeek
            reference.currentTimeMs = roundedTimeMs
            val stateResult = computeAmllPlayerTimeState(
                timeMs = roundedTimeMs,
                currentGroups = engine.groups,
                timelineState = reference,
            )
            val commitResult = commitAmllPlayerTimeState(
                timelineState = reference,
                timeMs = roundedTimeMs,
                currentGroups = engine.groups,
                hasBottomContent = hasBottomContent,
                stateResult = stateResult,
            )
            val expected = AmllTimeUpdateResult(
                applied = true,
                shouldLayout = commitResult.shouldLayout,
                shouldResetScroll = commitResult.shouldResetScroll,
                groupsToEnable = commitResult.groupsToEnable,
                groupsToDisable = commitResult.groupsToDisable,
                shouldRefreshInactiveWords =
                    isSeek ||
                        roundedTimeMs < previousTimeMs ||
                        crossedTimelineBoundary,
            )

            assertEquals(
                expected = expected,
                actual = engine.setTime(
                    timeMs = timeMs,
                    isSeek = isSeek,
                    hasBottomContent = hasBottomContent,
                ),
                message = "time=$timeMs seek=$isSeek bottom=$hasBottomContent",
            )
            assertEquals(reference, engine.timelineState)
        }
    }

    @Test
    fun stableEngineFrameIsReusedUntilEngineStateChanges() {
        val engine = AmllPlayerEngine(
            lines = listOf(line(id = "first", startMs = 0.0, endMs = 1_000.0)),
        )

        val firstFrame = engine.currentFrame()
        assertSame(firstFrame, engine.currentFrame())
        assertSame(firstFrame, engine.update(deltaMs = 16.0))

        engine.setLines(
            lines = listOf(line(id = "second", startMs = 0.0, endMs = 2_000.0)),
        )
        assertNotSame(firstFrame, engine.currentFrame())
    }

    @Test
    fun hotGroupsUseStartInclusiveAndEndExclusiveIntervals() {
        val groups = listOf(
            group(id = "a", startMs = 1_000.0, endMs = 2_000.0),
            group(id = "b", startMs = 1_500.0, endMs = 2_500.0),
        )
        val state = AmllTimelineState()

        val before = computeAmllPlayerTimeState(999.0, groups, state)
        assertEquals(emptySet(), before.nextHotGroups)

        val first = computeAmllPlayerTimeState(1_000.0, groups, state)
        assertEquals(setOf(0), first.nextHotGroups)
        assertEquals(setOf(0), first.addedIds)

        state.hotGroups = linkedSetOf(0)
        val overlap = computeAmllPlayerTimeState(1_500.0, groups, state)
        assertEquals(setOf(0, 1), overlap.nextHotGroups)
        assertEquals(setOf(1), overlap.addedIds)

        state.hotGroups = linkedSetOf(0, 1)
        val firstEnd = computeAmllPlayerTimeState(2_000.0, groups, state)
        assertEquals(setOf(1), firstEnd.nextHotGroups)
        assertEquals(setOf(0), firstEnd.removedHotIds)
    }

    @Test
    fun seekingReplacesBufferAndPicksTheFirstHotGroup() {
        val groups = listOf(
            group(id = "a", startMs = 1_000.0, endMs = 3_000.0),
            group(id = "b", startMs = 2_000.0, endMs = 4_000.0),
        )
        val state = AmllTimelineState(
            hotGroups = linkedSetOf(0),
            bufferedGroups = linkedSetOf(0),
            scrollToIndex = 0,
            isSeeking = true,
            initialLayoutFinished = true,
        )
        val computed = computeAmllPlayerTimeState(2_500.0, groups, state)
        val committed = commitAmllPlayerTimeState(
            timelineState = state,
            timeMs = 2_500.0,
            currentGroups = groups,
            hasBottomContent = false,
            stateResult = computed,
        )

        assertEquals(setOf(0, 1), state.hotGroups)
        assertEquals(setOf(0, 1), state.bufferedGroups)
        assertEquals(0, state.scrollToIndex)
        assertEquals(listOf(0, 1), committed.groupsToEnable)
        assertTrue(committed.shouldResetScroll)
        assertTrue(committed.shouldLayout)
    }

    @Test
    fun normalPlaybackRetainsOverlappingBuffersThenClearsTheFinishedSet() {
        val groups = listOf(
            group(id = "a", startMs = 0.0, endMs = 3_000.0),
            group(id = "b", startMs = 2_000.0, endMs = 4_000.0),
        )
        val state = AmllTimelineState(
            hotGroups = linkedSetOf(0),
            bufferedGroups = linkedSetOf(0),
            scrollToIndex = 0,
            initialLayoutFinished = true,
        )

        val added = computeAmllPlayerTimeState(2_000.0, groups, state)
        val addedCommit = commitAmllPlayerTimeState(
            timelineState = state,
            timeMs = 2_000.0,
            currentGroups = groups,
            hasBottomContent = false,
            stateResult = added,
        )
        assertEquals(setOf(0, 1), state.bufferedGroups)
        assertEquals(0, state.scrollToIndex)
        assertEquals(listOf(1), addedCommit.groupsToEnable)

        val finished = computeAmllPlayerTimeState(4_000.0, groups, state)
        val finishedCommit = commitAmllPlayerTimeState(
            timelineState = state,
            timeMs = 4_000.0,
            currentGroups = groups,
            hasBottomContent = false,
            stateResult = finished,
        )
        assertEquals(emptySet(), state.bufferedGroups)
        assertEquals(listOf(0, 1), finishedCommit.groupsToDisable)
        assertEquals(1, state.scrollToIndex)
    }

    @Test
    fun seekWithoutHotGroupChoosesNextLineOrBottom() {
        val groups = listOf(
            group(id = "a", startMs = 1_000.0, endMs = 2_000.0),
            group(id = "b", startMs = 3_000.0, endMs = 4_000.0),
        )

        assertEquals(
            1,
            pickAmllScrollToIndexForSeek(
                timeMs = 2_500.0,
                currentGroups = groups,
                bufferedGroups = emptySet(),
            ),
        )
        assertEquals(
            2,
            pickAmllScrollToIndexForSeek(
                timeMs = 5_000.0,
                currentGroups = groups,
                bufferedGroups = emptySet(),
            ),
        )
        assertEquals(
            0,
            pickAmllScrollToIndexForSeek(
                timeMs = 5_000.0,
                currentGroups = groups,
                bufferedGroups = setOf(1, 0),
            ),
        )
    }

    @Test
    fun endOfTimelineTargetsLastLineOrBottomContent() {
        val groups = listOf(
            group(id = "a", startMs = 0.0, endMs = 1_000.0),
            group(id = "b", startMs = 1_000.0, endMs = 2_000.0),
        )
        fun committedScrollIndex(hasBottomContent: Boolean): Int {
            val state = AmllTimelineState(
                scrollToIndex = 0,
                initialLayoutFinished = true,
            )
            val computed = computeAmllPlayerTimeState(2_000.0, groups, state)
            commitAmllPlayerTimeState(
                timelineState = state,
                timeMs = 2_000.0,
                currentGroups = groups,
                hasBottomContent = hasBottomContent,
                stateResult = computed,
            )
            return state.scrollToIndex
        }

        assertEquals(1, committedScrollIndex(hasBottomContent = false))
        assertEquals(2, committedScrollIndex(hasBottomContent = true))
    }

    @Test
    fun interludeUsesTwentyMillisecondBiasAndTwoHundredFiftyMillisecondLeadIn() {
        val groups = listOf(
            group(id = "a", startMs = 0.0, endMs = 1_000.0),
            group(id = "b", startMs = 6_000.0, endMs = 7_000.0, isDuet = true),
        )

        val interlude = computeAmllCurrentInterlude(
            currentTimeMs = 2_000.0,
            scrollToIndex = 1,
            currentGroups = groups,
        )
        checkNotNull(interlude)
        assertClose(2_020.0, interlude.startTimeMs)
        assertClose(5_750.0, interlude.endTimeMs)
        assertEquals(0, interlude.anchorLineIndex)
        assertTrue(interlude.isNextDuet)

        assertNull(
            computeAmllCurrentInterlude(
                currentTimeMs = 5_730.0,
                scrollToIndex = 1,
                currentGroups = groups,
            ),
        )
    }

    @Test
    fun fourSecondGapIsIncludedButAnythingShorterIsRejected() {
        val exact = listOf(
            group(id = "a", startMs = 0.0, endMs = 1_000.0),
            group(id = "b", startMs = 5_250.0, endMs = 6_000.0),
        )
        val short = listOf(
            group(id = "a", startMs = 0.0, endMs = 1_000.0),
            group(id = "b", startMs = 5_249.0, endMs = 6_000.0),
        )

        assertTrue(
            computeAmllCurrentInterlude(
                currentTimeMs = 2_000.0,
                scrollToIndex = 1,
                currentGroups = exact,
            ) != null,
        )
        assertNull(
            computeAmllCurrentInterlude(
                currentTimeMs = 2_000.0,
                scrollToIndex = 1,
                currentGroups = short,
            ),
        )
    }

    @Test
    fun ySpringParametersUseFixedAndIntervalDerivedBranches() {
        val groups = listOf(
            group(id = "a", startMs = 0.0, endMs = 100.0),
            group(id = "b", startMs = 450.0, endMs = 800.0),
        )

        val seeking = computeAmllLinePosYSpringParameters(
            enabled = true,
            currentGroups = groups,
            scrollToIndex = 1,
            isSeeking = true,
            isInterludeActive = false,
        )
        assertEquals(90.0, seeking.parameters?.stiffness)
        assertEquals(15.0, seeking.parameters?.damping)

        val dynamic = computeAmllLinePosYSpringParameters(
            enabled = true,
            currentGroups = groups,
            scrollToIndex = 1,
            isSeeking = false,
            isInterludeActive = false,
        )
        assertClose(213.5275281648062, checkNotNull(dynamic.parameters?.stiffness))
        assertClose(32.14767855254345, checkNotNull(dynamic.parameters?.damping))

        assertFalse(
            computeAmllLinePosYSpringParameters(
                enabled = false,
                currentGroups = groups,
                scrollToIndex = 1,
                isSeeking = false,
                isInterludeActive = false,
            ).shouldUpdate,
        )
    }

    @Test
    fun presentationAndBlurFollowTheExactDistanceRules() {
        assertClose(
            5.0,
            computeAmllLineBlur(
                enableBlur = true,
                isUserScrolling = false,
                isActive = false,
                itemIndex = 2,
                scrollToIndex = 5,
                latestIndex = 6,
                isCompact = false,
            ),
        )
        assertClose(
            4.0,
            computeAmllLineBlur(
                enableBlur = true,
                isUserScrolling = false,
                isActive = false,
                itemIndex = 2,
                scrollToIndex = 5,
                latestIndex = 6,
                isCompact = true,
            ),
        )
        assertClose(
            4.0,
            computeAmllLineBlur(
                enableBlur = true,
                isUserScrolling = false,
                isActive = false,
                itemIndex = 9,
                scrollToIndex = 5,
                latestIndex = 6,
                isCompact = false,
            ),
        )

        val hiddenPassed = computeAmllGroupPresentation(
            groupIndex = 1,
            scrollToIndex = 3,
            latestIndex = 4,
            hasBuffered = false,
            hidePassedLines = true,
            isPlaying = true,
            isNonDynamic = false,
            enableBlur = true,
            isUserScrolling = false,
            isCompact = false,
        )
        assertClose(1e-4, hiddenPassed.targetOpacity)

        val buffered = computeAmllGroupPresentation(
            groupIndex = 3,
            scrollToIndex = 3,
            latestIndex = 3,
            hasBuffered = true,
            hidePassedLines = false,
            isPlaying = true,
            isNonDynamic = true,
            enableBlur = true,
            isUserScrolling = false,
            isCompact = false,
        )
        assertTrue(buffered.isActive)
        assertClose(0.85, buffered.targetOpacity)
        assertClose(0.0, buffered.blurLevel)
    }

    @Test
    fun layoutTargetsMatchCore052OrderingOffsetsAndDelays() {
        val groups = listOf(
            group(id = "a", startMs = 0.0, endMs = 1_000.0),
            group(id = "b", startMs = 1_000.0, endMs = 2_000.0),
            group(id = "c", startMs = 2_000.0, endMs = 3_000.0),
        )
        val timeline = AmllTimelineState(
            currentTimeMs = 1_500.0,
            lastCurrentTimeMs = 1_500.0,
            hotGroups = linkedSetOf(1),
            bufferedGroups = linkedSetOf(1),
            scrollToIndex = 1,
            isPlaying = true,
            initialLayoutFinished = true,
        )
        val result = calculateAmllLayoutTargets(
            currentGroups = groups,
            timelineState = timeline,
            layoutState = AmllPlayerLayoutState(),
            input = AmllLayoutInput(
                viewportWidthPx = 1_200.0,
                viewportHeightPx = 1_000.0,
                groupHeightsPx = listOf(100.0, 80.0, 120.0),
                interludeDotsWidthPx = 30.0,
                interludeDotsHeightPx = 20.0,
                scrollOffsetPx = 10.0,
                fontSizePx = 40.0,
                isNonDynamic = false,
                isCompact = false,
                isPlaying = true,
            ),
        )

        assertNull(result.interlude)
        assertClose(-100.0, result.scrollMinOffsetPx)
        assertClose(10.0, result.scrollMaxOffsetPx)
        assertEquals(3, result.groupTargets.size)

        assertGroupTarget(
            target = result.groupTargets[0],
            topPx = 200.0,
            delaySeconds = 0.0,
            active = false,
            opacity = 1.0,
            blur = 3.0,
            mainScale = 97.0,
            backgroundScale = 75.0,
            backgroundSlide = -80.0,
        )
        assertGroupTarget(
            target = result.groupTargets[1],
            topPx = 300.0,
            delaySeconds = 0.05,
            active = true,
            opacity = 0.85,
            blur = 0.0,
            mainScale = 100.0,
            backgroundScale = 100.0,
            backgroundSlide = 0.0,
        )
        assertGroupTarget(
            target = result.groupTargets[2],
            topPx = 380.0,
            delaySeconds = 0.1,
            active = false,
            opacity = 1.0,
            blur = 2.0,
            mainScale = 97.0,
            backgroundScale = 75.0,
            backgroundSlide = -80.0,
        )
        assertClose(500.0, result.bottomLineTarget.yPx)
        assertClose(0.14761904761904762, result.bottomLineTarget.delaySeconds)
        assertClose(3.0, result.bottomLineTarget.blurLevel)
        assertClose(170.0, checkNotNull(result.ySpringParameterUpdate?.stiffness))
        assertClose(
            sqrt(170.0) * 2.2,
            checkNotNull(result.ySpringParameterUpdate?.damping),
        )
    }

    @Test
    fun interludeDotsAreInsertedBeforeNextLineAndRightAlignedForDuet() {
        val groups = listOf(
            group(id = "a", startMs = 0.0, endMs = 1_000.0),
            group(id = "b", startMs = 6_000.0, endMs = 7_000.0, isDuet = true),
        )
        val result = calculateAmllLayoutTargets(
            currentGroups = groups,
            timelineState = AmllTimelineState(
                currentTimeMs = 2_000.0,
                scrollToIndex = 1,
                bufferedGroups = linkedSetOf(1),
                isPlaying = true,
                initialLayoutFinished = true,
            ),
            layoutState = AmllPlayerLayoutState(),
            input = AmllLayoutInput(
                viewportWidthPx = 1_000.0,
                viewportHeightPx = 1_000.0,
                groupHeightsPx = listOf(100.0, 80.0),
                interludeDotsWidthPx = 30.0,
                interludeDotsHeightPx = 20.0,
                fontSizePx = 40.0,
                isCompact = true,
                isPlaying = true,
            ),
        )

        val dots = checkNotNull(result.interludeDotsTarget)
        assertClose(970.0, dots.xPx)
        assertClose(274.0, dots.yPx)
        assertClose(2_020.0, dots.startTimeMs)
        assertClose(5_750.0, dots.endTimeMs)
        assertClose(158.0, result.groupTargets[0].topPx)
        assertClose(310.0, result.groupTargets[1].topPx)
    }

    @Test
    fun groupTargetPreservesBackgroundOrderingScaleAndPauseRules() {
        val main = line(id = "main", startMs = 1_000.0, endMs = 2_000.0)
        val background = line(id = "bg", startMs = 900.0, endMs = 2_000.0, isBackground = true)
        val group = AmllLyricGroup(
            id = "group",
            mainLine = main,
            backgroundLine = background,
            isBackgroundFirst = true,
        )
        val inactive = AmllGroupPresentation(
            isActive = false,
            targetOpacity = 1.0,
            blurLevel = 2.0,
        )

        val playing = calculateAmllGroupTarget(
            index = 0,
            topPx = 10.0,
            delaySeconds = 0.05,
            group = group,
            presentation = inactive,
            isPlaying = true,
            enableScale = true,
            alwaysPostpositionBackground = false,
        )
        assertClose(97.0, playing.mainScalePercent)
        assertClose(75.0, playing.backgroundScalePercent)
        assertClose(80.0, playing.backgroundSlideYPercent)
        assertEquals(AmllLineRenderMode.SOLID, playing.renderMode)

        val forcedPostposition = calculateAmllGroupTarget(
            index = 0,
            topPx = 10.0,
            delaySeconds = 0.05,
            group = group,
            presentation = inactive,
            isPlaying = true,
            enableScale = true,
            alwaysPostpositionBackground = true,
        )
        assertClose(-80.0, forcedPostposition.backgroundSlideYPercent)

        val paused = calculateAmllGroupTarget(
            index = 0,
            topPx = 10.0,
            delaySeconds = 0.05,
            group = group,
            presentation = inactive,
            isPlaying = false,
            enableScale = true,
            alwaysPostpositionBackground = false,
        )
        assertClose(100.0, paused.mainScalePercent)
        assertClose(100.0, paused.backgroundScalePercent)
        assertClose(0.0, paused.backgroundSlideYPercent)
    }

    @Test
    fun statefulEngineExposesTargetsAndAdvancesMillisecondsAsSeconds() {
        val lines = listOf(
            line(id = "a", startMs = 0.0, endMs = 1_000.0),
            line(id = "b", startMs = 1_000.0, endMs = 2_000.0),
        )
        val engine = AmllPlayerEngine(lines)
        val input = AmllLayoutInput(
            viewportWidthPx = 1_200.0,
            viewportHeightPx = 800.0,
            groupHeightsPx = listOf(100.0, 100.0),
            interludeDotsWidthPx = 30.0,
            interludeDotsHeightPx = 20.0,
            isCompact = false,
            force = true,
        )
        val initial = engine.layout(input)
        assertClose(
            initial.groupTargets[0].topPx,
            engine.currentFrame().groups[0].topPx,
        )
        assertFalse(engine.currentFrame().groups[0].animateMaskAlpha)

        val timeResult = engine.setTime(timeMs = 1_000.0)
        assertTrue(timeResult.shouldLayout)
        val movedTargets = engine.layout(input.copy(force = false))
        assertTrue(engine.currentFrame().groups[0].animateMaskAlpha)
        val beforeUpdate = engine.currentFrame().groups[0].topPx
        val target = movedTargets.groupTargets[0].topPx
        assertTrue(beforeUpdate != target)

        val afterHundredMilliseconds = engine.update(100.0).groups[0].topPx
        assertTrue(afterHundredMilliseconds < beforeUpdate)
        assertTrue(afterHundredMilliseconds > target)

        repeat(200) {
            engine.update(50.0)
        }
        assertClose(target, engine.currentFrame().groups[0].topPx, tolerance = 0.01)
        assertEquals(
            movedTargets.groupTargets.map { it.isActive },
            engine.linePresentations.map { it.isActive },
        )
    }

    @Test
    fun initialNonForcedLayoutStartsAtTwoViewportsAndUsesUnknownHeightFallback() {
        val engine = AmllPlayerEngine(
            lines = listOf(
                line(id = "a", startMs = 0.0, endMs = 1_000.0),
                line(id = "b", startMs = 1_000.0, endMs = 2_000.0),
            ),
        )
        val result = engine.layout(
            AmllLayoutInput(
                viewportWidthPx = 1_200.0,
                viewportHeightPx = 800.0,
                groupHeightsPx = listOf(null, null),
                interludeDotsWidthPx = 30.0,
                interludeDotsHeightPx = 20.0,
                isCompact = false,
                sync = true,
                force = false,
            ),
        )

        // `LyricLineGroup` constructor uses window.innerHeight * 2 before calcLayout(true).
        assertClose(1_600.0, engine.currentFrame().groups[0].topPx)
        // Unknown groups use viewportHeight / 5, not Compose's eager measurement.
        assertClose(160.0, result.groupTargets[1].topPx - result.groupTargets[0].topPx)

        val advanced = engine.update(16.0).groups[0].topPx
        assertTrue(advanced < 1_600.0)
        assertTrue(advanced > result.groupTargets[0].topPx)
    }

    @Test
    fun hiddenPageFreezesLyricGroupsButPageShowCanResumeTheirSprings() {
        val engine = AmllPlayerEngine(
            lines = listOf(line(id = "a", startMs = 0.0, endMs = 1_000.0)),
        )
        engine.layout(
            AmllLayoutInput(
                viewportWidthPx = 1_200.0,
                viewportHeightPx = 800.0,
                groupHeightsPx = listOf(null),
                interludeDotsWidthPx = 30.0,
                interludeDotsHeightPx = 20.0,
                sync = true,
                force = false,
            ),
        )

        val beforeHiddenFrame = engine.currentFrame().groups.single().topPx
        val hiddenFrame = engine.update(
            deltaMs = 100.0,
            isPageVisible = false,
        )
        assertClose(beforeHiddenFrame, hiddenFrame.groups.single().topPx)

        val visibleFrame = engine.update(
            deltaMs = 100.0,
            isPageVisible = true,
        )
        assertTrue(visibleFrame.groups.single().topPx < beforeHiddenFrame)
    }

    @Test
    fun alwaysPostpositionResolvesBackgroundInitialDirectionAtGroupCreation() {
        val main = line(id = "main", startMs = 1_000.0, endMs = 2_000.0)
        val background = line(
            id = "background",
            startMs = 900.0,
            endMs = 2_000.0,
            isBackground = true,
        )
        val group = AmllLyricGroup(
            id = "group",
            mainLine = main,
            backgroundLine = background,
            isBackgroundFirst = true,
        )
        val engine = AmllPlayerEngine(
            initialAlwaysPostpositionBackground = true,
        )
        engine.setGroups(
            groups = listOf(group),
            initialTimeMs = 0.0,
            isNonDynamic = true,
        )

        engine.layout(
            AmllLayoutInput(
                viewportWidthPx = 1_200.0,
                viewportHeightPx = 800.0,
                groupHeightsPx = listOf(100.0),
                interludeDotsWidthPx = 30.0,
                interludeDotsHeightPx = 20.0,
                sync = true,
                force = false,
                alwaysPostpositionBackground = true,
            ),
        )

        // addBgLine() leaves bgSlideY at its base -80 when always-postposition is already enabled.
        assertClose(-80.0, engine.currentFrame().groups.single().backgroundSlideYPercent)
    }

    @Test
    fun cssFixedSerializationDoesNotUseKotlinTiesToEvenRounding() {
        assertClose(1.3, roundToAmllPrecision(value = 1.25, scale = 10.0))
        assertClose(-1.3, roundToAmllPrecision(value = -1.25, scale = 10.0))
    }

    @Test
    fun javascriptScrollClampAcceptsInvertedShortDocumentBoundary() {
        assertClose(
            expected = -120.0,
            actual = clampAmllScrollOffsetLikeJs(
                value = 0.0,
                minimum = 0.0,
                maximum = -120.0,
            ),
        )
        assertClose(
            expected = 20.0,
            actual = clampAmllScrollOffsetLikeJs(
                value = 50.0,
                minimum = -20.0,
                maximum = 20.0,
            ),
        )
    }

    @Test
    fun backgroundHiddenStateUsesSerializedSlideAndResolvedOrdering() {
        assertTrue(
            isAmllBackgroundWrapperHidden(
                slideYPercent = 79.96,
                isActive = false,
                shouldBackgroundBeFirst = true,
            ),
        )
        assertTrue(
            isAmllBackgroundWrapperHidden(
                slideYPercent = -79.96,
                isActive = false,
                shouldBackgroundBeFirst = false,
            ),
        )
        assertFalse(
            isAmllBackgroundWrapperHidden(
                slideYPercent = 80.0,
                isActive = true,
                shouldBackgroundBeFirst = true,
            ),
        )
    }

    @Test
    fun eachStatefulInterludeLayoutCarriesANewSetInterludeRevision() {
        val engine = AmllPlayerEngine(
            lines = listOf(
                line(id = "a", startMs = 5_000.0, endMs = 6_000.0),
            ),
        )
        val input = AmllLayoutInput(
            viewportWidthPx = 1_200.0,
            viewportHeightPx = 800.0,
            groupHeightsPx = listOf(null),
            interludeDotsWidthPx = 30.0,
            interludeDotsHeightPx = 20.0,
            isCompact = false,
            sync = true,
            force = false,
        )

        val first = engine.layout(input).interludeDotsTarget
        val second = engine.layout(input).interludeDotsTarget
        assertTrue(first != null)
        assertTrue(second != null)
        assertTrue(checkNotNull(second).layoutRevision > checkNotNull(first).layoutRevision)
    }

    @Test
    fun interludeGlobalOpacityIsAppliedToEachDotExactlyOnce() {
        val visual = computeAmllInterludeVisual(
            target = AmllInterludeDotsTarget(
                xPx = 0.0,
                yPx = 0.0,
                startTimeMs = 0.0,
                endTimeMs = 5_000.0,
            ),
            nowMs = 750.0,
        )
        val dotsDuration = 5_000.0 - 750.0
        val dotOpacity = maxOf(0.25, ((750.0 * 3.0) / dotsDuration) * 0.75)
        val globalOpacity = 0.5

        assertClose(globalOpacity * dotOpacity, visual.dotAlphas[0])
    }

    @Test
    fun wheelDeltaModeKeepsCorePixelLineAndPageBranchesDistinct() {
        assertEquals(
            AmllWheelTranslation(deltaPx = 2.5, sync = true),
            translateAmllWheelDelta(
                deltaY = 2.5,
                mode = AmllWheelDeltaMode.PIXEL,
            ),
        )
        assertEquals(
            AmllWheelTranslation(deltaPx = 125.0, sync = false),
            translateAmllWheelDelta(
                deltaY = 2.5,
                mode = AmllWheelDeltaMode.LINE,
            ),
        )
        assertEquals(
            AmllWheelTranslation(deltaPx = 125.0, sync = false),
            translateAmllWheelDelta(
                deltaY = 2.5,
                mode = AmllWheelDeltaMode.PAGE,
            ),
        )
    }

    @Test
    fun touchClickUsesStrictPerAxisTenCssPixelBoundary() {
        val start = Offset.Zero

        assertTrue(
            isAmllTouchClickCandidate(
                start = start,
                end = Offset(9.9f, 9.9f),
                thresholdPx = 10.0,
            ),
        )
        assertFalse(
            isAmllTouchClickCandidate(
                start = start,
                end = Offset(10f, 0f),
                thresholdPx = 10.0,
            ),
        )
        assertFalse(
            isAmllTouchClickCandidate(
                start = start,
                end = Offset(0f, -10f),
                thresholdPx = 10.0,
            ),
        )
    }

    @Test
    fun pointerDragCannotBecomeATapAgainByReturningToItsStart() {
        val start = Offset.Zero
        var exceeded = false

        exceeded = hasAmllPointerExceededTapThreshold(
            alreadyExceeded = exceeded,
            start = start,
            current = Offset(20f, 0f),
            thresholdPx = 10.0,
        )
        exceeded = hasAmllPointerExceededTapThreshold(
            alreadyExceeded = exceeded,
            start = start,
            current = Offset.Zero,
            thresholdPx = 10.0,
        )

        assertTrue(exceeded)
    }

    @Test
    fun touchMoveUsesInitialTouchPositionAndJavascriptClamp() {
        assertClose(
            expected = -5.0,
            actual = computeAmllTouchScrollOffset(
                startScrollOffsetPx = 20.0,
                startTouchYPx = 100.0,
                currentTouchYPx = 130.0,
                minimumPx = -5.0,
                maximumPx = 100.0,
            ),
        )
        assertClose(
            expected = 40.0,
            actual = computeAmllTouchScrollOffset(
                startScrollOffsetPx = 20.0,
                startTouchYPx = 100.0,
                currentTouchYPx = 80.0,
                minimumPx = -5.0,
                maximumPx = 100.0,
            ),
        )
    }

    @Test
    fun inertiaMatchesCoreFrameSkipStopFrictionAndBoundaryRules() {
        val moved = advanceAmllInertiaFrame(
            scrollOffsetPx = 0.0,
            speedPxPerMs = 1.0,
            deltaMs = 16.0,
            minimumPx = -100.0,
            maximumPx = 100.0,
        )
        assertClose(-16.0, moved.scrollOffsetPx)
        assertClose(0.95, moved.speedPxPerMs)
        assertTrue(moved.shouldLayout)
        assertFalse(moved.finished)

        val skipped = advanceAmllInertiaFrame(
            scrollOffsetPx = 12.0,
            speedPxPerMs = 1.0,
            deltaMs = 101.0,
            minimumPx = -100.0,
            maximumPx = 100.0,
        )
        assertEquals(
            AmllInertiaFrameResult(
                scrollOffsetPx = 12.0,
                speedPxPerMs = 1.0,
                shouldLayout = false,
                finished = false,
            ),
            skipped,
        )

        val stopped = advanceAmllInertiaFrame(
            scrollOffsetPx = 12.0,
            speedPxPerMs = 0.05,
            deltaMs = 16.0,
            minimumPx = -100.0,
            maximumPx = 100.0,
        )
        assertTrue(stopped.finished)

        val clamped = advanceAmllInertiaFrame(
            scrollOffsetPx = 0.0,
            speedPxPerMs = -10.0,
            deltaMs = 16.0,
            minimumPx = -100.0,
            maximumPx = 100.0,
        )
        assertClose(100.0, clamped.scrollOffsetPx)
    }

    @Test
    fun touchClickHitTestUsesViewportBoundsAndTopmostRenderedGroup() {
        val tops = listOf(10.04, 15.0)
        val heights = listOf(20, 20)

        assertEquals(
            1,
            pickAmllTouchClickGroupIndex(
                position = Offset(20f, 20f),
                viewportWidthPx = 200,
                viewportHeightPx = 100,
                groupTopsPx = tops,
                groupHeightsPx = heights,
            ),
        )
        assertNull(
            pickAmllTouchClickGroupIndex(
                position = Offset(-1f, 20f),
                viewportWidthPx = 200,
                viewportHeightPx = 100,
                groupTopsPx = tops,
                groupHeightsPx = heights,
            ),
        )
    }

    private fun assertGroupTarget(
        target: AmllGroupTarget,
        topPx: Double,
        delaySeconds: Double,
        active: Boolean,
        opacity: Double,
        blur: Double,
        mainScale: Double,
        backgroundScale: Double,
        backgroundSlide: Double,
    ) {
        assertClose(topPx, target.topPx)
        assertClose(delaySeconds, target.delaySeconds)
        assertEquals(active, target.isActive)
        assertClose(opacity, target.opacity)
        assertClose(blur, target.blurLevel)
        assertClose(mainScale, target.mainScalePercent)
        assertClose(backgroundScale, target.backgroundScalePercent)
        assertClose(backgroundSlide, target.backgroundSlideYPercent)
    }

    private fun group(
        id: String,
        startMs: Double,
        endMs: Double,
        isDuet: Boolean = false,
    ): AmllLyricGroup {
        val line = line(
            id = id,
            startMs = startMs,
            endMs = endMs,
            isDuet = isDuet,
        )
        return AmllLyricGroup(
            id = "group:$id",
            mainLine = line,
        )
    }

    private fun line(
        id: String,
        startMs: Double,
        endMs: Double,
        isBackground: Boolean = false,
        isDuet: Boolean = false,
    ): AmllLyricLine = AmllLyricLine(
        id = id,
        startTimeMs = startMs.toLong(),
        endTimeMs = endMs.toLong(),
        mainText = id,
        isBackground = isBackground,
        isDuet = isDuet,
        exactStartTimeMs = startMs,
        exactEndTimeMs = endMs,
    )

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-10,
    ) {
        assertEquals(
            expected = expected,
            actual = actual,
            absoluteTolerance = tolerance,
        )
    }
}

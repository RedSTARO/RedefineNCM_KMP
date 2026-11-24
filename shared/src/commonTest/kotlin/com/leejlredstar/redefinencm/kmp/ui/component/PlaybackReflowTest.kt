package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.adaptive.WindowFold
import com.leejlredstar.redefinencm.kmp.ui.amll.AmllReflowLayout
import com.leejlredstar.redefinencm.kmp.ui.amll.nativeAmllPanes
import com.leejlredstar.redefinencm.kmp.ui.amll.nativeAmllReflowLayout
import com.leejlredstar.redefinencm.kmp.ui.screen.NowPlayingReflowLayout
import com.leejlredstar.redefinencm.kmp.ui.screen.nowPlayingPanes
import com.leejlredstar.redefinencm.kmp.ui.screen.nowPlayingReflowLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackReflowTest {
    private val density = Density(1f)
    private val cover = IntRect(0, 0, 443, 920)
    private val inner = IntRect(0, 0, 852, 792)
    private val book = WindowFold(IntRect(420, 0, 432, 860), isVertical = true, isOccluding = true)
    private val tabletop = WindowFold(IntRect(0, 422, 852, 432), isVertical = false)

    @Test
    fun coverAndExpandedFlatBothUseOnePaneButReflowInBothDirections() {
        assertEquals(PlaybackPaneArrangement.Full, player(cover).layout.arrangement)
        assertEquals(PlaybackPaneArrangement.Full, player(inner).layout.arrangement)
        assertEquals(ReflowChange.WidthClass, playbackReflowChange(player(cover), player(inner)))
        assertEquals(ReflowChange.WidthClass, playbackReflowChange(player(inner), player(cover)))
        assertEquals(ReflowChange.WidthClass, playbackReflowChange(amll(cover), amll(inner)))
        assertEquals(ReflowChange.WidthClass, playbackReflowChange(amll(inner), amll(cover)))
    }

    @Test
    fun bookAndTabletopReactToOccupiedPanesNotHingeMotionWithinTheSamePlacement() {
        val shiftedBook = book.copy(bounds = IntRect(413, 0, 425, 860))
        assertEquals(PlaybackPaneArrangement.SideBySide, player(inner, book).layout.arrangement)
        assertEquals(PlaybackPaneArrangement.Top, player(inner, tabletop).layout.arrangement)
        assertEquals(ReflowChange.Pane, playbackReflowChange(player(inner), player(inner, book)))
        assertEquals(ReflowChange.WidthClass, playbackReflowChange(player(cover), player(inner, book)))
        assertEquals(ReflowChange.WidthClass, playbackReflowChange(player(inner, book), player(cover)))
        assertEquals(ReflowChange.Pane, playbackReflowChange(amll(inner, book), amll(inner, tabletop)))
        assertEquals(ReflowChange.None, playbackReflowChange(player(inner, book), player(inner, shiftedBook)))
        assertEquals(ReflowChange.None, playbackReflowChange(amll(inner, book), amll(inner, book)))
    }

    @Test
    fun invalidFoldsDoNotTurnOffWideFlatLayoutsOrAnimate() {
        val widePlayer = IntRect(0, 0, 1080, 620)
        val wideLyrics = IntRect(0, 0, 852, 306)
        val edgeCrease = WindowFold(IntRect(1080, 0, 1080, 860), isVertical = true)
        val beyondWindow = WindowFold(IntRect(1090, 0, 1110, 860), isVertical = true, isOccluding = true)
        assertEquals(PlaybackPaneArrangement.SideBySide, player(widePlayer).layout.arrangement)
        assertEquals(PlaybackPaneArrangement.SideBySide, amll(wideLyrics).layout.arrangement)
        for (fold in listOf(edgeCrease, beyondWindow)) {
            assertNull(player(widePlayer, fold).fold)
            assertNull(amll(wideLyrics, fold).fold)
            assertEquals(player(widePlayer).layout, player(widePlayer, fold).layout)
            assertEquals(amll(wideLyrics).layout, amll(wideLyrics, fold).layout)
            assertEquals(ReflowChange.None, playbackReflowChange(player(widePlayer), player(widePlayer, fold)))
            assertEquals(ReflowChange.None, playbackReflowChange(amll(wideLyrics), amll(wideLyrics, fold)))
        }
    }

    @Test
    fun ordinarySplitDraggingIsNotAReflowEvenAcrossLayoutThresholds() {
        val split = IntRect(0, 0, 852, 306)
        val tallSplit = IntRect(0, 0, 852, 524)
        val narrow = IntRect(0, 0, 296, 792)
        assertEquals(ReflowChange.Window, playbackReflowChange(player(inner), player(split, multi = true)))
        assertEquals(ReflowChange.Window, playbackReflowChange(amll(inner), amll(split, multi = true)))
        assertEquals(ReflowChange.None, playbackReflowChange(player(tallSplit, multi = true), player(split, multi = true)))
        assertEquals(ReflowChange.None, playbackReflowChange(amll(split, multi = true), amll(tallSplit, multi = true)))
        assertEquals(ReflowChange.WidthClass, playbackReflowChange(player(split, multi = true), player(narrow, multi = true, rotation = 1)))
    }

    @Test
    fun keyboardAndLyricLoadsAreIndependentFromRealFoldReflow() {
        val window = AndroidReflowWindow(IntSize(inner.width, inner.height), rotation = 0, inMultiWindow = false)
        val short = IntRect(0, 0, 852, 306)
        assertEquals(ReflowChange.None, playbackReflowChange(player(inner), player(short).copy(window = window), keyboardChanging = true))
        assertEquals(ReflowChange.Pane, playbackReflowChange(player(inner), player(inner, book), keyboardChanging = true))
        assertEquals(ReflowChange.None, playbackReflowChange(amll(inner, book, lyricCount = 0), amll(inner, book)))
        assertEquals(ReflowChange.None, playbackReflowChange(amll(inner), amll(inner, lyricCount = 0)))
    }

    @Test
    fun invalidSamplesDoNotOverwriteHistoryButALeaveDoes() {
        val before = player(cover)
        val after = player(inner)
        val history = ReflowHistory<PlaybackReflowTarget<NowPlayingReflowLayout>>()
        assertNull(history.observe(active = true, target = before, keyboardVisible = false).previous)
        history.observe(active = true, target = null, keyboardVisible = true)
        val transition = history.observe(active = true, target = after, keyboardVisible = false)
        assertEquals(before, transition.previous)
        assertTrue(transition.keyboardChanging)
        history.observe(active = false, target = null, keyboardVisible = false)
        assertNull(history.observe(active = true, target = after, keyboardVisible = false).previous)
    }

    @Test
    fun widthClassWaitsForTheNewWindowDrawBeforeAdvancing() = runTest {
        val clock = BroadcastFrameClock()
        val committed = CompletableDeferred<Unit>()
        var requests = 0
        val motion = AdaptiveReflow<PlaybackReflowTarget<NowPlayingReflowLayout>>(
            scope = CoroutineScope(coroutineContext + clock),
            canAnimate = { true },
            canPresent = { true },
            awaitFrameCommit = { requests++; committed.await() },
            classify = ::playbackReflowChange,
        )
        motion.observe(player(cover), active = true, keyboardVisible = false)
        assertEquals(1f, motion.progress)
        assertEquals(0, requests)
        motion.observe(player(inner), active = true, keyboardVisible = false)
        advanceTimeBy(700)
        assertEquals(1, requests)
        assertEquals(0f, motion.progress)
        committed.complete(Unit)
        runCurrent()
        advanceTimeBy(240)
        runCurrent()
        fun frame(ms: Long) {
            clock.sendFrame(ms * 1_000_000)
            runCurrent()
        }
        frame(950)
        frame(966)
        frame(982)
        frame(1094)
        assertTrue(motion.progress > 0f && motion.progress < 1f)
        frame(1330)
        assertEquals(1f, motion.progress)
    }

    @Test
    fun timeoutAndReducedMotionNeverLeaveATargetOffset() = runTest {
        val committed = CompletableDeferred<Unit>()
        var enabled = true
        val motion = AdaptiveReflow<PlaybackReflowTarget<NowPlayingReflowLayout>>(
            scope = CoroutineScope(coroutineContext + BroadcastFrameClock()),
            canAnimate = { enabled },
            canPresent = { true },
            awaitFrameCommit = { committed.await() },
            classify = ::playbackReflowChange,
        )
        motion.observe(player(cover), active = true, keyboardVisible = false)
        motion.observe(player(inner), active = true, keyboardVisible = false)
        assertEquals(0f, motion.progress)
        advanceTimeBy(1_450)
        runCurrent()
        assertEquals(1f, motion.progress)
        enabled = false
        motion.observe(player(cover), active = true, keyboardVisible = false)
        assertEquals(1f, motion.progress)
        committed.complete(Unit)
        runCurrent()
        assertEquals(1f, motion.progress)
    }

    private fun player(
        full: IntRect,
        fold: WindowFold? = null,
        multi: Boolean = false,
        rotation: Int = 0,
    ): PlaybackReflowTarget<NowPlayingReflowLayout> {
        val projected = fold?.let { PlaybackFold(it, IntOffset(0, 24)) }
        val (art, controls) = nowPlayingPanes(full, density, projected, isWidePlaybackViewport(full.width.dp, full.height.dp))
        val expanded = hasExpandedPlaybackWidth(full.width.dp)
        val layout = nowPlayingReflowLayout(full, art, controls, density)
        return PlaybackReflowTarget(full, projected?.placementIn(full), layout, expanded, fold,
            AndroidReflowWindow(IntSize(full.width, full.height), rotation, multi))
    }

    private fun amll(
        full: IntRect,
        fold: WindowFold? = null,
        lyricCount: Int = 20,
        multi: Boolean = false,
    ): PlaybackReflowTarget<AmllReflowLayout> {
        val projected = fold?.let { PlaybackFold(it, IntOffset(0, 24)) }
        val wide = isWidePlaybackViewport(full.width.dp, full.height.dp)
        val (lyrics, controls) = nativeAmllPanes(full, density, projected, wide, lyricCount)
        val expanded = hasExpandedPlaybackWidth(full.width.dp)
        val layout = nativeAmllReflowLayout(full, lyrics, controls, density)
        return PlaybackReflowTarget(full, projected?.placementIn(full), layout, expanded, fold,
            AndroidReflowWindow(IntSize(full.width, full.height), 0, multi))
    }
}

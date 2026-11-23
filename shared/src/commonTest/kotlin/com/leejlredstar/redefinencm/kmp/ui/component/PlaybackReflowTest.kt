package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.adaptive.WindowFold
import com.leejlredstar.redefinencm.kmp.ui.amll.AmllReflowLayout
import com.leejlredstar.redefinencm.kmp.ui.amll.nativeAmllPanes
import com.leejlredstar.redefinencm.kmp.ui.amll.nativeAmllReflowLayout
import com.leejlredstar.redefinencm.kmp.ui.screen.NowPlayingReflowLayout
import com.leejlredstar.redefinencm.kmp.ui.screen.nowPlayingPanes
import com.leejlredstar.redefinencm.kmp.ui.screen.nowPlayingReflowLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackReflowTest {
    private val density = Density(1f)
    private val cover = IntRect(0, 0, 443, 920)
    private val inner = IntRect(0, 0, 852, 792)
    private val book = PlaybackFold(
        WindowFold(IntRect(420, 0, 432, 860), isVertical = true, isOccluding = true), IntOffset(0, 24),
    )
    private val tabletop = PlaybackFold(
        WindowFold(IntRect(0, 422, 852, 432), isVertical = false), IntOffset(0, 24),
    )

    @Test
    fun coverAndExpandedFlatBothUseOnePaneButReflowInBothDirections() {
        assertEquals(PlaybackPaneArrangement.Full, player(cover).layout.arrangement)
        assertEquals(PlaybackPaneArrangement.Full, player(inner).layout.arrangement)
        assertEquals(PlaybackReflowAction.Fade, action(player(cover), player(inner)))
        assertEquals(PlaybackReflowAction.Fade, action(player(inner), player(cover)))
        assertEquals(PlaybackReflowAction.Fade, action(amll(cover), amll(inner)))
        assertEquals(PlaybackReflowAction.Fade, action(amll(inner), amll(cover)))
    }

    @Test
    fun bookAndTabletopReactToOccupiedPanesNotHingeMotionWithinTheSamePlacement() {
        val shiftedBook = PlaybackFold(WindowFold(IntRect(413, 0, 425, 860), true, true), IntOffset(0, 24))
        assertEquals(PlaybackPaneArrangement.SideBySide, player(inner, book).layout.arrangement)
        assertEquals(PlaybackPaneArrangement.Top, player(inner, tabletop).layout.arrangement)
        assertEquals(PlaybackReflowAction.Fade, action(player(inner), player(inner, book)))
        assertEquals(PlaybackReflowAction.Fade, action(amll(inner, book), amll(inner, tabletop)))
        assertEquals(PlaybackReflowAction.Keep, action(player(inner, book), player(inner, shiftedBook)))
        assertEquals(PlaybackReflowAction.Keep, action(amll(inner, book), amll(inner, book)))

        val edgeCrease = PlaybackFold(WindowFold(IntRect(852, 0, 852, 860), true), IntOffset(0, 24))
        assertEquals(PlaybackReflowAction.Keep, action(player(inner), player(inner, edgeCrease)))
        assertEquals(PlaybackReflowAction.Keep, action(amll(inner), amll(inner, edgeCrease)))
    }

    @Test
    fun shortSplitChangesModeButNearbyDragAndLyricLoadsDoNotAnimate() {
        val split = IntRect(0, 0, 852, 306)
        val splitDragged = IntRect(0, 0, 852, 314)
        val narrow = IntRect(0, 0, 296, 792)
        assertEquals(PlaybackReflowAction.Fade, action(player(inner), player(split)))
        assertEquals(PlaybackReflowAction.Fade, action(amll(inner), amll(split)))
        assertEquals(PlaybackReflowAction.Fade, action(player(split), player(narrow)))
        assertEquals(PlaybackReflowAction.Fade, action(amll(split), amll(narrow)))
        assertEquals(PlaybackReflowAction.Keep, action(player(split), player(splitDragged)))
        assertEquals(PlaybackReflowAction.Keep, action(amll(split), amll(splitDragged)))
        assertEquals(PlaybackReflowAction.Keep, action(amll(split, lyricCount = 0), amll(split)))
        assertEquals(PlaybackReflowAction.Keep, action(amll(inner, book, lyricCount = 0), amll(inner, book)))
    }

    @Test
    fun boundsThenHingeThenQuickReversalReuseTheFirstFade() {
        val before = player(cover)
        val resized = player(inner)
        val folded = player(inner, book)
        val resizedAgain = player(IntRect(0, 0, 860, 800), book)
        assertEquals(PlaybackReflowAction.Fade, action(before, resized))
        assertEquals(PlaybackReflowAction.Keep, action(resized, folded, fading = true))
        assertEquals(PlaybackReflowAction.Keep, action(folded, resizedAgain, fading = true))
        assertEquals(PlaybackReflowAction.Keep, action(resizedAgain, before, fading = true))
        assertEquals(PlaybackReflowAction.Fade, action(before, folded))
        assertEquals(PlaybackReflowAction.Fade, action(folded, before, fading = false))
    }

    @Test
    fun firstLayoutReducedMotionZeroScaleAndImeSnapEvenDuringAFade() {
        val before = player(cover)
        val after = player(inner, book)
        assertEquals(PlaybackReflowAction.Snap, action(null, after))
        assertEquals(PlaybackReflowAction.Snap, action(before, null, fading = true))
        assertEquals(PlaybackReflowAction.Snap, action(before, after, fading = true, reducedMotion = true))
        assertEquals(PlaybackReflowAction.Snap, action(before, after, fading = true, motionScale = 0f))
        assertEquals(PlaybackReflowAction.Snap, action(before, after, fading = true, keyboard = true))
    }

    private fun player(full: IntRect, fold: PlaybackFold? = null): PlaybackReflowTarget<NowPlayingReflowLayout> {
        val (art, controls) = nowPlayingPanes(full, density, fold, isWidePlaybackViewport(full.width.dp, full.height.dp))
        val layout = nowPlayingReflowLayout(full, art, controls, density, hasExpandedPlaybackWidth(full.width.dp))
        return PlaybackReflowTarget(full, fold.takeUnless { art == null && controls == full }, layout)
    }

    private fun amll(
        full: IntRect,
        fold: PlaybackFold? = null,
        lyricCount: Int = 20,
    ): PlaybackReflowTarget<AmllReflowLayout> {
        val wide = isWidePlaybackViewport(full.width.dp, full.height.dp)
        val (lyrics, controls) = nativeAmllPanes(full, density, fold, wide, lyricCount)
        val layout = nativeAmllReflowLayout(full, lyrics, controls, density, hasExpandedPlaybackWidth(full.width.dp))
        return PlaybackReflowTarget(full, fold.takeUnless { lyrics == full && controls == full }, layout)
    }

    private fun <Layout : Any> action(
        previous: PlaybackReflowTarget<Layout>?,
        current: PlaybackReflowTarget<Layout>?,
        reducedMotion: Boolean = false,
        motionScale: Float = 1f,
        keyboard: Boolean = false,
        fading: Boolean = false,
    ) = playbackReflowAction(previous, current, reducedMotion, motionScale, keyboard, fading)
}

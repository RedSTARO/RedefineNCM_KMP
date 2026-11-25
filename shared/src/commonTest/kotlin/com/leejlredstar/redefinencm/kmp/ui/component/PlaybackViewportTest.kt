package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.adaptive.WindowFold
import com.leejlredstar.redefinencm.kmp.ui.amll.nativeAmllPanes
import com.leejlredstar.redefinencm.kmp.ui.screen.nowPlayingPanes
import com.leejlredstar.redefinencm.kmp.ui.screen.nowPlayingTransportMetrics
import com.leejlredstar.redefinencm.kmp.ui.screen.stackedPlayerArtworkSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlaybackViewportTest {
    private val density = Density(1f)

    @Test
    fun flatPlayerNeedsBothRealLandscapeAndRoomForTwoPanes() {
        val cover = IntRect(0, 0, 443, 920)
        val inner = IntRect(0, 0, 852, 792)
        val nearSquare = IntRect(0, 0, 883, 780)
        assertFalse(isWidePlaybackViewport(852.dp, 883.dp))
        assertFalse(isWidePlaybackViewport(883.dp, 852.dp))
        assertEquals(null to cover, nowPlayingPanes(cover, density, null, wideWindow = false))
        assertEquals(null to inner, nowPlayingPanes(inner, density, null, wideWindow = false))
        assertEquals(null to nearSquare, nowPlayingPanes(nearSquare, density, null, wideWindow = false))
        assertEquals(null to IntRect(0, 0, 500, 250),
            nowPlayingPanes(IntRect(0, 0, 500, 250), density, null, wideWindow = true))
        assertNotEquals(null, nowPlayingPanes(IntRect(0, 0, 1080, 620), density, null, wideWindow = true).first)
        assertNotEquals(null, nowPlayingPanes(IntRect(0, 0, 852, 262), density, null, wideWindow = true).first)
    }

    @Test
    fun bookSeparatesContentButShallowTabletopKeepsOneSafePane() {
        val viewport = IntRect(0, 0, 852, 792)
        val book = PlaybackFold(WindowFold(IntRect(420, 0, 432, 860), isVertical = true), IntOffset(0, 24))
        val left = IntRect(0, 0, 420, 792)
        val right = IntRect(432, 0, 852, 792)
        assertEquals(left to right, nowPlayingPanes(viewport, density, book, wideWindow = false))
        assertEquals(left to right, nativeAmllPanes(viewport, density, book, wideWindow = false, lyricCount = 42))
        assertEquals(left to left, nativeAmllPanes(viewport, density, book, wideWindow = false, lyricCount = 1))
        val tabletop = PlaybackFold(WindowFold(IntRect(0, 422, 852, 432), isVertical = false), IntOffset(0, 24))
        val upper = IntRect(0, 0, 852, 398)
        assertEquals(null to upper, nowPlayingPanes(viewport, density, tabletop, wideWindow = false))
        assertEquals(upper to upper, nativeAmllPanes(viewport, density, tabletop, wideWindow = false, lyricCount = 42))
    }

    @Test
    fun clippedOpaqueHingeStillBlocksItsPixelsWithoutForcingAPanePair() {
        val viewport = IntRect(0, 0, 600, 700)
        val hinge = PlaybackFold(
            WindowFold(IntRect(0, 370, 290, 410), isVertical = false, isOccluding = true),
            IntOffset.Zero,
        )
        val upper = IntRect(0, 0, 600, 370)
        assertEquals(null, hinge.spanningPanes(viewport))
        assertEquals(null to upper, nowPlayingPanes(viewport, density, hinge, wideWindow = false))
        assertEquals(upper to upper, nativeAmllPanes(viewport, density, hinge, wideWindow = false, lyricCount = 0))
    }

    @Test
    fun shortFlatLyricsOnlySplitWithUsefulContentAndUsableHeight() {
        val wide = IntRect(0, 0, 852, 262)
        val narrow = IntRect(0, 0, 296, 800)
        val tooShort = IntRect(0, 0, 852, 210)
        assertEquals(wide to wide, nativeAmllPanes(wide, density, null, wideWindow = true, lyricCount = 0))
        assertEquals(narrow to narrow, nativeAmllPanes(narrow, density, null, wideWindow = false, lyricCount = 42))
        assertEquals(tooShort to tooShort,
            nativeAmllPanes(tooShort, density, null, wideWindow = true, lyricCount = 42))
        val (lyrics, controls) = nativeAmllPanes(wide, density, null, wideWindow = true, lyricCount = 42)
        assertEquals(wide.width, lyrics.width + controls.width)
        assertEquals(lyrics.right, controls.left)
        assertTrue(controls.width >= 338)
    }

    @Test
    fun stackedArtworkLeavesRoomForTheMeasuredControls() {
        assertEquals(386.dp, stackedPlayerArtworkSize(796.dp, 410.dp, 560.dp))
        assertEquals(340.dp, stackedPlayerArtworkSize(796.dp, 300.dp, 340.dp))
        assertEquals(64.dp, stackedPlayerArtworkSize(460.dp, 440.dp, 340.dp))
    }

    @Test
    fun skipButtonsFitTogetherEvenAtFractionalDensity() {
        val foldDensity = Density(2.4375f)
        val bookInnerWidth = 960 // A ~426 dp pane minus its two 16 dp page margins.
        val book = nowPlayingTransportMetrics(bookInnerWidth, foldDensity, compact = false, tight = false)
        assertTrue(book.fits(bookInnerWidth, foldDensity))
        assertEquals(96.dp, book.play.height)
        assertEquals(136.dp, nowPlayingTransportMetrics(1200, foldDensity, compact = false, tight = false).play.height)
        val narrow = nowPlayingTransportMetrics(220, density, compact = false, tight = false)
        assertTrue(narrow.fits(220, density))
        assertEquals(48.dp, narrow.skip.width)
        assertEquals(48.dp, narrow.skip.height)
        assertFalse(narrow.fits(160, density))
    }
}

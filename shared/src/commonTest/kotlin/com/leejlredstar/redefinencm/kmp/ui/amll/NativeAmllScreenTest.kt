package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.calculateAmllLyricVisualParameters
import com.leejlredstar.amll.compose.nextAmllArtworkUriAfterFailure
import com.leejlredstar.redefinencm.kmp.ui.adaptive.WindowFold
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackFold
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeAmllScreenTest {
    @Test
    fun lyricsReclaimOnlyTheUnobstructedPartOfTheStatusBar() {
        assertEquals(77, amllStatusBarOverlap(safeTop = 77, statusBarTop = 77, obstructedTop = 0))
        assertEquals(27, amllStatusBarOverlap(safeTop = 77, statusBarTop = 77, obstructedTop = 50))
        assertEquals(0, amllStatusBarOverlap(safeTop = 77, statusBarTop = 77, obstructedTop = 77))
        assertEquals(0, amllStatusBarOverlap(safeTop = 96, statusBarTop = 77, obstructedTop = 96))
        assertEquals(0, amllStatusBarOverlap(safeTop = 40, statusBarTop = 0, obstructedTop = 0))
    }

    @Test
    fun onlyATopLyricPaneExtendsWhileItsSidesAndBottomStaySafe() {
        val upper = IntRect(82, 0, 1_100, 510)
        val lower = upper.translate(IntOffset(0, 530))
        assertEquals(upper.copy(top = -77), amllLyricViewport(upper, 77, fold = null))
        assertEquals(lower, amllLyricViewport(lower, 77, fold = null))
        assertEquals(upper, amllLyricViewport(upper, 0, fold = null))
    }

    @Test
    fun lyricExtensionDoesNotCrossAnOpaqueHingeAboveTheSafePane() {
        val pane = IntRect(0, 0, 400, 795)
        val origin = IntOffset(0, 77)
        val hinge = WindowFold(IntRect(0, 18, 800, 27), isVertical = false, isOccluding = true)
        assertEquals(pane.copy(top = -50), amllLyricViewport(pane, 77, PlaybackFold(hinge, origin)))
        val aside = hinge.copy(bounds = IntRect(410, 0, 430, 920), isVertical = true)
        assertEquals(pane.copy(top = -77), amllLyricViewport(pane, 77, PlaybackFold(aside, origin)))
    }

    @Test
    fun unfoldedAndroidPortraitCapsLyricType() {
        val parameters = calculateNativeAmllVisualParameters(
            viewportWidth = 608.dp,
            viewportHeight = 830.dp,
            windowHeight = 860.dp,
            androidPresentation = true,
            reducedMotion = false,
        )
        assertEquals(36f, parameters.baseFontSizeSp)
    }

    @Test
    fun bottomInsetsDoNotTurnAPortraitWindowIntoLandscape() {
        val parameters = calculateNativeAmllVisualParameters(
            viewportWidth = 620.dp,
            viewportHeight = 600.dp,
            windowHeight = 625.dp,
            androidPresentation = true,
            reducedMotion = false,
        )
        assertEquals(36f, parameters.baseFontSizeSp)
    }

    @Test
    fun landscapeNarrowPhonesAndNonAndroidWindowsKeepAmllDefaults() {
        assertEquals(
            calculateAmllLyricVisualParameters(860f, 578f, reducedMotion = true),
            calculateNativeAmllVisualParameters(860.dp, 578.dp, 608.dp, androidPresentation = true, reducedMotion = true),
        )
        // At this landscape width the original font exceeds 36 dp, so an unconditional cap fails.
        assertEquals(
            calculateAmllLyricVisualParameters(700f, 365f, reducedMotion = false),
            calculateNativeAmllVisualParameters(700.dp, 365.dp, 390.dp, androidPresentation = true, reducedMotion = false),
        )
        assertEquals(
            calculateAmllLyricVisualParameters(390f, 818f, reducedMotion = false),
            calculateNativeAmllVisualParameters(390.dp, 818.dp, 842.dp, androidPresentation = true, reducedMotion = false),
        )
        assertEquals(
            calculateAmllLyricVisualParameters(608f, 860f, reducedMotion = false),
            calculateNativeAmllVisualParameters(608.dp, 860.dp, 860.dp, androidPresentation = false, reducedMotion = false),
        )
    }

    // Dynamic-cover details behavior is covered separately in SongWikiDetailsTest.
    @Test
    fun localArtworkFailureFallsBackOnceToTheRemoteArtwork() {
        assertEquals(
            "https://music.example/cover.jpg",
            nextAmllArtworkUriAfterFailure(
                failedUri = "file:///downloads/42.cover.heic",
                primaryUri = "file:///downloads/42.cover.heic",
                fallbackUri = "https://music.example/cover.jpg",
            ),
        )
        assertEquals(
            null,
            nextAmllArtworkUriAfterFailure(
                failedUri = "https://music.example/cover.jpg",
                primaryUri = "file:///downloads/42.cover.heic",
                fallbackUri = "https://music.example/cover.jpg",
            ),
        )
    }
}

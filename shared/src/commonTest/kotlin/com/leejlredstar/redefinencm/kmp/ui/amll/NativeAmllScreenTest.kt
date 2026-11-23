package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.calculateAmllLyricVisualParameters
import com.leejlredstar.amll.compose.nextAmllArtworkUriAfterFailure
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeAmllScreenTest {
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

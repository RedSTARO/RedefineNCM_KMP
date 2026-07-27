package com.leejlredstar.redefinencm.kmp.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeDynamicCoverLayerTest {
    @Test
    fun fullScreenBackgroundUsesExactDesktopAndAndroidCssParameters() {
        val desktop = nativeDynamicCoverVisualSpec(
            showBadge = false,
            androidPresentation = false,
        )
        val android = nativeDynamicCoverVisualSpec(
            showBadge = false,
            androidPresentation = true,
        )

        assertEquals(400, desktop.fadeDurationMillis)
        assertEquals(1.30f, desktop.saturation)
        assertEquals(400, android.fadeDurationMillis)
        assertEquals(1.15f, android.saturation)
        assertCssEase(android)
    }

    @Test
    fun wikiCoverUsesItsOwnUnfilteredTwoHundredTwentyMillisecondFade() {
        val wiki = nativeDynamicCoverVisualSpec(
            showBadge = true,
            androidPresentation = true,
        )

        assertEquals(220, wiki.fadeDurationMillis)
        assertEquals(1f, wiki.saturation)
        assertCssEase(wiki)
    }

    @Test
    fun pausedBackgroundKeepsLastFrameButPausedWikiCoverDoesNot() {
        assertTrue(
            nativeDynamicCoverIsVisible(
                hasPresentedFrame = true,
                play = false,
                showBadge = false,
            ),
        )
        assertFalse(
            nativeDynamicCoverIsVisible(
                hasPresentedFrame = true,
                play = false,
                showBadge = true,
            ),
        )
        assertFalse(
            nativeDynamicCoverIsVisible(
                hasPresentedFrame = false,
                play = true,
                showBadge = false,
            ),
        )
    }

    @Test
    fun decoderPlaybackRequiresBothSharedRequestAndActiveHostLifecycle() {
        assertTrue(
            nativeDynamicCoverShouldPlay(
                requestedPlay = true,
                lifecycleActive = true,
            ),
        )
        assertFalse(
            nativeDynamicCoverShouldPlay(
                requestedPlay = true,
                lifecycleActive = false,
            ),
        )
        assertFalse(
            nativeDynamicCoverShouldPlay(
                requestedPlay = false,
                lifecycleActive = true,
            ),
        )
        assertFalse(
            nativeDynamicCoverShouldPlay(
                requestedPlay = false,
                lifecycleActive = false,
            ),
        )
    }

    private fun assertCssEase(spec: NativeDynamicCoverVisualSpec) {
        assertEquals(0.25f, spec.easingX1)
        assertEquals(0.10f, spec.easingY1)
        assertEquals(0.25f, spec.easingX2)
        assertEquals(1.00f, spec.easingY2)
    }
}

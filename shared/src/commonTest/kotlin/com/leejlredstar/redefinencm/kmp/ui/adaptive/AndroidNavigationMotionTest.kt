package com.leejlredstar.redefinencm.kmp.ui.adaptive

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackReflowAction.Fade
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackReflowAction.Keep
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackReflowAction.Snap
import com.leejlredstar.redefinencm.kmp.ui.component.reflowFadeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidNavigationMotionTest {
    private val cover = androidNavigationLayout(443.dp, 994.dp)
    private val inner = androidNavigationLayout(852.dp, 883.dp)
    private val split = androidNavigationLayout(852.dp, 306.dp)

    @Test
    fun chromeChangesStyleInPlaceButDoesNotFadeForSameStyleResizes() {
        assertEquals(Fade, action(cover, inner))
        assertEquals(Fade, action(inner, cover))
        assertEquals(Fade, action(inner, split))
        assertEquals(Fade, action(split, cover))
        assertEquals(Keep, action(inner, androidNavigationLayout(883.dp, 852.dp)))
        assertEquals(Keep, action(split, androidNavigationLayout(296.dp, 852.dp)))
    }

    @Test
    fun onlyAnEffectiveChangeToTheUsablePaneCanTriggerFoldingMotion() {
        val size = IntSize(852, 883)
        val hinge = WindowFold(IntRect(0, 416, 852, 452), isVertical = false, isOccluding = true)
        fun layoutFor(fold: WindowFold): AndroidNavigationLayout {
            val pane = fold.browsePaneIn(IntOffset.Zero, size)
            return androidNavigationLayout(pane.width.dp, pane.height.dp)
        }

        val compactPane = layoutFor(hinge)
        assertEquals(AndroidNavigationLayout.Compact, compactPane)
        assertEquals(Fade, action(inner, compactPane))
        assertEquals(Fade, action(compactPane, inner))
        assertEquals(Keep, action(inner, layoutFor(hinge.copy(isOccluding = false))))
        assertEquals(Keep, action(inner, layoutFor(hinge.copy(bounds = IntRect(0, 900, 852, 930)))))
        assertEquals(Keep, action(inner, layoutFor(hinge.copy(bounds = IntRect(0, 410, 852, 410)))))
        assertEquals(Keep, action(compactPane, layoutFor(hinge.copy(bounds = IntRect(0, 420, 852, 454)))))
    }

    @Test
    fun firstLayoutImeReducedMotionAndScaleZeroSnapAndQuickReversalsKeepTheRunningFade() {
        assertEquals(Snap, action(null, inner))
        assertEquals(Snap, action(inner, null, fading = true))
        assertEquals(Snap, action(inner, split, keyboardVisible = true, fading = true))
        assertEquals(Snap, action(inner, split, reducedMotion = true, fading = true))
        assertEquals(Snap, action(inner, split, motionScale = 0f, fading = true))
        assertEquals(Fade, action(inner, split))
        assertEquals(Keep, action(split, inner, fading = true))
        assertEquals(Keep, action(inner, cover, fading = true))
    }

    @Test
    fun fabSlotChangesOnlyWithGeometrySoRouteAndMediaCanUseTheirExistingVisibilityAnimation() {
        assertEquals(cover, androidMiniPlayerFabSlot(cover, keyboardVisible = false, fits = true))
        assertEquals(inner, androidMiniPlayerFabSlot(inner, keyboardVisible = false, fits = true))
        assertNull(androidMiniPlayerFabSlot(split, keyboardVisible = false, fits = true))
        assertNull(androidMiniPlayerFabSlot(cover, keyboardVisible = true, fits = true))
        assertNull(androidMiniPlayerFabSlot(inner, keyboardVisible = true, fits = true))
        assertNull(androidMiniPlayerFabSlot(cover, keyboardVisible = false, fits = false))
    }

    private fun action(
        previous: AndroidNavigationLayout?,
        current: AndroidNavigationLayout?,
        reducedMotion: Boolean = false,
        motionScale: Float = 1f,
        keyboardVisible: Boolean = false,
        fading: Boolean = false,
    ) = reflowFadeAction(previous, current, reducedMotion, motionScale, keyboardVisible, fading)
}

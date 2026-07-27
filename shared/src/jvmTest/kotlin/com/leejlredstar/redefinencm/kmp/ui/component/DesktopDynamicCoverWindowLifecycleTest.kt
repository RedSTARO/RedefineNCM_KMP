package com.leejlredstar.redefinencm.kmp.ui.component

import java.awt.Frame
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDynamicCoverWindowLifecycleTest {
    @Test
    fun decoderRunsOnlyWhileWindowIsVisibleAndNotIconified() {
        assertTrue(
            desktopDynamicCoverLifecycleActive(
                isVisible = true,
                extendedState = Frame.NORMAL,
            ),
        )
        assertTrue(
            desktopDynamicCoverLifecycleActive(
                isVisible = true,
                extendedState = Frame.MAXIMIZED_BOTH,
            ),
        )
        assertFalse(
            desktopDynamicCoverLifecycleActive(
                isVisible = false,
                extendedState = Frame.NORMAL,
            ),
        )
        assertFalse(
            desktopDynamicCoverLifecycleActive(
                isVisible = true,
                extendedState = Frame.ICONIFIED,
            ),
        )
        assertFalse(
            desktopDynamicCoverLifecycleActive(
                isVisible = true,
                extendedState = Frame.MAXIMIZED_BOTH or Frame.ICONIFIED,
            ),
        )
    }
}

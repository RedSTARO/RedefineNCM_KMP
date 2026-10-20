package com.leejlredstar.redefinencm.kmp.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The bars follow the page shown last, and the theme once no page declares a background. */
class SystemBarBackgroundsTest {
    @Test
    fun theNewestDeclaredPageWinsUntilItLeaves() {
        val backgrounds = SystemBarBackgrounds()
        val nowPlaying = Any()
        val lyrics = Any()
        assertNull(backgrounds.darkest)

        backgrounds.declare(nowPlaying, dark = false)
        backgrounds.declare(lyrics, dark = true)
        assertEquals(true, backgrounds.darkest)

        // The page beneath changes its colour without taking over.
        backgrounds.declare(nowPlaying, dark = true)
        backgrounds.declare(nowPlaying, dark = false)
        assertEquals(true, backgrounds.darkest)

        backgrounds.withdraw(lyrics)
        assertEquals(false, backgrounds.darkest)
        backgrounds.withdraw(nowPlaying)
        assertNull(backgrounds.darkest)
    }
}

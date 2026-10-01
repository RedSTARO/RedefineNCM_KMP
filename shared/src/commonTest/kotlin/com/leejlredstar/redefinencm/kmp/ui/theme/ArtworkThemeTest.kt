package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtworkThemeTest {
    private val orange = Color(0xFFFB9D63)
    private val blue = Color(0xFF3355FF)

    @AfterTest
    fun leaveTheAppGrey() {
        ArtworkTheme.setSeed(null)
    }

    @Test
    fun theSchemeTakesTheColourOfTheCoverThatIsPlaying() {
        ArtworkTheme.follow(extracted = orange, hasMedia = true)

        assertEquals(orange, ArtworkTheme.seed.value)
    }

    @Test
    fun aTrackWhoseCoverIsStillLoadingKeepsTheColourBeforeIt() {
        ArtworkTheme.follow(extracted = orange, hasMedia = true)

        ArtworkTheme.follow(extracted = null, hasMedia = true)
        assertEquals(orange, ArtworkTheme.seed.value)

        ArtworkTheme.follow(extracted = blue, hasMedia = true)
        assertEquals(blue, ArtworkTheme.seed.value)
    }

    @Test
    fun anEmptyPlayerReturnsTheAppToGrey() {
        ArtworkTheme.follow(extracted = orange, hasMedia = true)

        ArtworkTheme.follow(extracted = null, hasMedia = false)

        assertNull(ArtworkTheme.seed.value)
    }
}

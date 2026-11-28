package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeGalleryTest {
    @Test
    fun preferredWidthsLeaveRoomForPeeksAndStayBoundedOnWidePanes() {
        for (style in HomeGalleryStyle.entries) {
            assertEquals(70.dp, homeGalleryPreferredWidth(style, 70.dp))
            val compact = 300.dp
            val target = homeGalleryPreferredWidth(style, compact)
            assertTrue(compact - target >= 56.dp + 8.dp)
        }
        assertEquals(156.dp, homeGalleryPreferredWidth(HomeGalleryStyle.Track, 900.dp))
        assertEquals(224.dp, homeGalleryPreferredWidth(HomeGalleryStyle.Playlist, 900.dp))
    }

    @Test
    fun fullPlaylistCardsStayWiderThanTallAcrossPanes() {
        for (pane in listOf(70.dp, 260.dp, 360.dp, 900.dp)) {
            val width = homeGalleryPreferredWidth(HomeGalleryStyle.Playlist, pane)
            assertTrue(HomeGalleryStyle.Playlist.baseHeight(width) < width)
        }
    }

    @Test
    fun captionsFitMediumCardsAndRespectTheCapsuleEnds() {
        val medium = Rect(30f, 0f, 154f, 120f)
        // A centered 48px radius and a 16px baseline inset shave off 12.2px per side.
        assertEquals(83, homeGalleryCaptionWidth(medium, 48f, 56f, 104f, 8f))
        // An off-center mask gets the same text measure; its position is used at draw time.
        assertEquals(83, homeGalleryCaptionWidth(medium.translate(20f, 5f), 48f, 61f, 109f, 8f))
        val peek = Rect(10f, 0f, 58f, 120f)
        assertTrue(homeGalleryCaptionWidth(peek, 24f, 56f, 104f, 8f) < 44)
        assertEquals(0, homeGalleryCaptionWidth(medium, 48f, 3f, 121f, 8f))
    }
}

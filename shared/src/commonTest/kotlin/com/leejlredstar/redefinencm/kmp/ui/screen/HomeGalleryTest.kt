package com.leejlredstar.redefinencm.kmp.ui.screen

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
    fun captionClearsItsMaskBeforeTextCanBeCut() {
        assertEquals(0f, homeGalleryCaptionAlpha(0f, 0f))
        // The caption occupies the center 76%, and has already vanished at this width.
        assertEquals(0f, homeGalleryCaptionAlpha(152f, 200f))
        assertTrue(homeGalleryCaptionAlpha(180f, 200f) in 0.49f..0.51f)
        assertEquals(1f, homeGalleryCaptionAlpha(200f, 200f))
    }
}

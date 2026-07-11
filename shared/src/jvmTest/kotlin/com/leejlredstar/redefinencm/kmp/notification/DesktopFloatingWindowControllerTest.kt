package com.leejlredstar.redefinencm.kmp.notification

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopFloatingWindowControllerTest {
    @BeforeTest
    fun setUp() {
        LyricNotificationController.setOptionalSurfaceEnabled(false)
        LyricNotificationController.reset()
    }

    @AfterTest
    fun tearDown() {
        LyricNotificationController.setOptionalSurfaceEnabled(false)
        LyricNotificationController.reset()
    }

    @Test
    fun optionalDesktopLyricsGateUpdatesImmediately() {
        publish(title = "First", lyric = "disabled payload")
        assertNull(LyricNotificationController.floatingLyricData.value)
        assertFalse(LyricNotificationController.isWindowVisible.value)

        LyricNotificationController.setOptionalSurfaceEnabled(true)
        assertEquals(
            "disabled payload",
            LyricNotificationController.floatingLyricData.value?.currentLyric,
        )
        assertTrue(LyricNotificationController.isWindowVisible.value)

        LyricNotificationController.setOptionalSurfaceEnabled(false)
        assertNull(LyricNotificationController.floatingLyricData.value)
        assertFalse(LyricNotificationController.isWindowVisible.value)

        publish(title = "Second", lyric = "new disabled payload")
        LyricNotificationController.show()
        LyricNotificationController.toggle()
        assertNull(LyricNotificationController.floatingLyricData.value)
        assertFalse(LyricNotificationController.isWindowVisible.value)

        LyricNotificationController.setOptionalSurfaceEnabled(true)
        assertEquals(
            "new disabled payload",
            LyricNotificationController.floatingLyricData.value?.currentLyric,
        )
        assertTrue(LyricNotificationController.isWindowVisible.value)
    }

    @Test
    fun dismissedTrackStaysHiddenUntilTrackChanges() {
        LyricNotificationController.setOptionalSurfaceEnabled(true)
        publish(title = "First", lyric = "line one")
        LyricNotificationController.hide()

        publish(title = "First", lyric = "line two")
        assertFalse(LyricNotificationController.isWindowVisible.value)

        publish(title = "Second", lyric = "next track")
        assertTrue(LyricNotificationController.isWindowVisible.value)
    }

    private fun publish(title: String, lyric: String) {
        LyricNotificationController.updateLyric(
            title = title,
            artist = "Artist",
            currentLyric = lyric,
            nextLyric = "Next",
            artworkUri = "https://example.test/$title.jpg",
            isPlaying = true,
            positionMs = 1_000L,
            durationMs = 120_000L,
        )
    }
}

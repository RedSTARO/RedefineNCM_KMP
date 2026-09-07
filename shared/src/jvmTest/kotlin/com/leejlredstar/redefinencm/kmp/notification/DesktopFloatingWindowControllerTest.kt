package com.leejlredstar.redefinencm.kmp.notification

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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
    fun windowLayoutIsItsOwnStateAndSurvivesTheSurfaceBeingDisabled() {
        assertFalse(LyricNotificationController.isWindowLocked.value)
        assertEquals(LyricSurfaceAlignment.CENTER, LyricNotificationController.windowAlignment.value)

        LyricNotificationController.setOptionalSurfaceLocked(true)
        LyricNotificationController.setOptionalSurfaceAlignment(LyricSurfaceAlignment.END)
        LyricNotificationController.setOptionalSurfaceEnabled(false)
        LyricNotificationController.reset()

        assertTrue(LyricNotificationController.isWindowLocked.value)
        assertEquals(LyricSurfaceAlignment.END, LyricNotificationController.windowAlignment.value)

        LyricNotificationController.setOptionalSurfaceLocked(false)
        LyricNotificationController.setOptionalSurfaceAlignment(LyricSurfaceAlignment.DEFAULT)
    }

    @Test
    fun clickThroughStyleAddsAndRemovesOnlyTheTransparentBit() {
        val layeredOnly = 0x00080000
        val locked = clickThroughExtendedStyle(0x00000100, enabled = true)
        assertEquals(0x00000100 or 0x00000020 or layeredOnly, locked)
        assertEquals(0x00000100 or layeredOnly, clickThroughExtendedStyle(locked, enabled = false))
        assertEquals(locked, clickThroughExtendedStyle(locked, enabled = true))
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

    @Test
    fun positionUpdatesDoNotRepublishWindowContent() {
        LyricNotificationController.setOptionalSurfaceEnabled(true)
        publish(title = "First", lyric = "line one")
        val content = LyricNotificationController.floatingLyricData.value

        LyricNotificationController.updateLyric(
            title = "First",
            artist = "Artist",
            currentLyric = "line one",
            nextLyric = "Next",
            artworkUri = "https://example.test/First.jpg",
            isPlaying = true,
            positionMs = 42_000L,
            durationMs = 120_000L,
        )

        assertSame(content, LyricNotificationController.floatingLyricData.value)
        assertEquals(42_000L, LyricNotificationController.playbackProgress.value.positionMs)
        assertEquals(0.35f, LyricNotificationController.playbackProgress.value.fraction)
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

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
        DesktopLyricWindow.setEnabled(false)
        DesktopLyricWindow.reset()
    }

    @AfterTest
    fun tearDown() {
        DesktopLyricWindow.setEnabled(false)
        DesktopLyricWindow.reset()
    }

    @Test
    fun optionalDesktopLyricsGateUpdatesImmediately() {
        publish(title = "First", lyric = "disabled payload")
        assertNull(DesktopLyricWindow.floatingLyricData.value)
        assertFalse(DesktopLyricWindow.isWindowVisible.value)

        DesktopLyricWindow.setEnabled(true)
        assertEquals(
            "disabled payload",
            DesktopLyricWindow.floatingLyricData.value?.currentLyric,
        )
        assertTrue(DesktopLyricWindow.isWindowVisible.value)

        DesktopLyricWindow.setEnabled(false)
        assertNull(DesktopLyricWindow.floatingLyricData.value)
        assertFalse(DesktopLyricWindow.isWindowVisible.value)

        publish(title = "Second", lyric = "new disabled payload")
        DesktopLyricWindow.show()
        DesktopLyricWindow.toggle()
        assertNull(DesktopLyricWindow.floatingLyricData.value)
        assertFalse(DesktopLyricWindow.isWindowVisible.value)

        DesktopLyricWindow.setEnabled(true)
        assertEquals(
            "new disabled payload",
            DesktopLyricWindow.floatingLyricData.value?.currentLyric,
        )
        assertTrue(DesktopLyricWindow.isWindowVisible.value)
    }

    @Test
    fun windowLayoutIsItsOwnStateAndSurvivesTheSurfaceBeingDisabled() {
        assertFalse(DesktopLyricWindow.isWindowLocked.value)
        assertEquals(LyricSurfaceAlignment.CENTER, DesktopLyricWindow.windowAlignment.value)

        DesktopLyricWindow.setLocked(true)
        DesktopLyricWindow.setAlignment(LyricSurfaceAlignment.END)
        DesktopLyricWindow.setEnabled(false)
        DesktopLyricWindow.reset()

        assertTrue(DesktopLyricWindow.isWindowLocked.value)
        assertEquals(LyricSurfaceAlignment.END, DesktopLyricWindow.windowAlignment.value)

        DesktopLyricWindow.setLocked(false)
        DesktopLyricWindow.setAlignment(LyricSurfaceAlignment.DEFAULT)
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
        DesktopLyricWindow.setEnabled(true)
        publish(title = "First", lyric = "line one")
        DesktopLyricWindow.hide()

        publish(title = "First", lyric = "line two")
        assertFalse(DesktopLyricWindow.isWindowVisible.value)

        publish(title = "Second", lyric = "next track")
        assertTrue(DesktopLyricWindow.isWindowVisible.value)
    }

    @Test
    fun positionUpdatesDoNotRepublishWindowContent() {
        DesktopLyricWindow.setEnabled(true)
        publish(title = "First", lyric = "line one")
        val content = DesktopLyricWindow.floatingLyricData.value

        DesktopLyricWindow.updateLyric(
            title = "First",
            artist = "Artist",
            currentLyric = "line one",
            nextLyric = "Next",
            artworkUri = "https://example.test/First.jpg",
            isPlaying = true,
            positionMs = 42_000L,
            durationMs = 120_000L,
        )

        assertSame(content, DesktopLyricWindow.floatingLyricData.value)
        assertEquals(42_000L, DesktopLyricWindow.playbackProgress.value.positionMs)
        assertEquals(0.35f, DesktopLyricWindow.playbackProgress.value.fraction)
    }

    private fun publish(title: String, lyric: String) {
        DesktopLyricWindow.updateLyric(
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

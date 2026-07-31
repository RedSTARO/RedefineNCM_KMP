package com.leejlredstar.redefinencm.kmp

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopRenderingConfigurationTest {

    @Test
    fun defaultsDesktopRenderingToUncappedFramePacing() {
        withRestoredSystemProperties {
            System.clearProperty("skiko.vsync.enabled")
            System.clearProperty("skiko.vsync.framelimit.fallback.enabled")

            configureUncappedDesktopRendering()

            assertEquals("false", System.getProperty("skiko.vsync.enabled"))
            assertEquals(
                "false",
                System.getProperty("skiko.vsync.framelimit.fallback.enabled"),
            )
        }
    }

    @Test
    fun explicitJvmFramePacingOverrideIsPreserved() {
        withRestoredSystemProperties {
            System.setProperty("skiko.vsync.enabled", "true")
            System.setProperty("skiko.vsync.framelimit.fallback.enabled", "true")

            configureUncappedDesktopRendering()

            assertEquals("true", System.getProperty("skiko.vsync.enabled"))
            assertEquals(
                "true",
                System.getProperty("skiko.vsync.framelimit.fallback.enabled"),
            )
        }
    }

    private inline fun withRestoredSystemProperties(block: () -> Unit) {
        val previousVsync = System.getProperty("skiko.vsync.enabled")
        val previousFallback = System.getProperty("skiko.vsync.framelimit.fallback.enabled")
        try {
            block()
        } finally {
            restoreProperty("skiko.vsync.enabled", previousVsync)
            restoreProperty("skiko.vsync.framelimit.fallback.enabled", previousFallback)
        }
    }

    private fun restoreProperty(key: String, value: String?) {
        if (value == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, value)
        }
    }
}

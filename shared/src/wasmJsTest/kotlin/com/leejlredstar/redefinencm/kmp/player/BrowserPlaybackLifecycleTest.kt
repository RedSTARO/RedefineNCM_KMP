package com.leejlredstar.redefinencm.kmp.player

import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserPlaybackLifecycleTest {
    @Test
    fun pageHideTriggersPauseAndDisposeRemovesLifecycleListeners() {
        var pauseCalls = 0
        val lifecycle = BrowserPlaybackLifecycle { pauseCalls += 1 }

        window.dispatchEvent(Event("pagehide"))
        assertEquals(1, pauseCalls)

        lifecycle.dispose()
        window.dispatchEvent(Event("pagehide"))
        assertEquals(1, pauseCalls)
    }
}

package com.leejlredstar.redefinencm.kmp.player

import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * Owns the browser lifecycle listeners that must synchronously stop pending or active playback
 * when the page goes away.
 *
 * A hidden tab is not a page going away. This used to stop on `visibilitychange` to hidden as
 * well, so switching tabs or minimising the browser paused the music. Desktop browsers keep a
 * hidden tab's audio running; mobile Safari may still suspend the page on lock regardless.
 */
internal class BrowserPlaybackLifecycle(onPageLeave: () -> Unit) {
    private var disposed = false
    private val pageLeaveListener: (Event) -> Unit = { onPageLeave() }

    init {
        window.addEventListener("pagehide", pageLeaveListener)
        // Do not preventDefault or set returnValue: pausing must not create a leave-page prompt.
        window.addEventListener("beforeunload", pageLeaveListener)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        window.removeEventListener("pagehide", pageLeaveListener)
        window.removeEventListener("beforeunload", pageLeaveListener)
    }
}

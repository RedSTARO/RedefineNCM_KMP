@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.player

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Document
import org.w3c.dom.events.Event
import kotlin.JsFun

/** Owns browser lifecycle listeners that must synchronously stop pending or active playback. */
internal class BrowserPlaybackLifecycle(onPageLeave: () -> Unit) {
    private var disposed = false
    private val visibilityChangeListener: (Event) -> Unit = {
        if (documentVisibilityState(document) == "hidden") onPageLeave()
    }
    private val pageLeaveListener: (Event) -> Unit = { onPageLeave() }

    init {
        document.addEventListener("visibilitychange", visibilityChangeListener)
        window.addEventListener("pagehide", pageLeaveListener)
        // Do not preventDefault or set returnValue: pausing must not create a leave-page prompt.
        window.addEventListener("beforeunload", pageLeaveListener)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        document.removeEventListener("visibilitychange", visibilityChangeListener)
        window.removeEventListener("pagehide", pageLeaveListener)
        window.removeEventListener("beforeunload", pageLeaveListener)
    }
}

/** `Document.visibilityState` is not exposed by kotlinx-browser's Wasm declaration. */
@JsFun("(document) => document.visibilityState")
private external fun documentVisibilityState(document: Document): String

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

/*
 * Native Compose translation support for @applemusic-like-lyrics/core 0.5.2
 * packages/core/src/lyric-player/base/{index,scroll}.ts.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.JsFun

@Composable
internal actual fun Modifier.amllPlatformEvents(
    onWheel: (deltaY: Double, mode: AmllWheelDeltaMode) -> Boolean,
    onPageVisibilityChanged: (visible: Boolean, forceResync: Boolean) -> Unit,
): Modifier {
    val token = remember { newAmllPlatformEventToken() }
    val latestOnWheel by rememberUpdatedState(onWheel)
    val latestOnPageVisibilityChanged by rememberUpdatedState(onPageVisibilityChanged)
    var viewportBoundsInWindow by remember { mutableStateOf<Rect?>(null) }

    DisposableEffect(token) {
        installAmllPlatformEvents(
            token = token,
            onWheel = wheel@{ deltaY, deltaMode, canvasX, canvasY ->
                val bounds = viewportBoundsInWindow ?: return@wheel false
                if (!bounds.contains(Offset(canvasX.toFloat(), canvasY.toFloat()))) {
                    return@wheel false
                }
                val mode = when (deltaMode) {
                    0 -> AmllWheelDeltaMode.PIXEL
                    1 -> AmllWheelDeltaMode.LINE
                    2 -> AmllWheelDeltaMode.PAGE
                    // DOM specifies only 0, 1, and 2. AMLL's source sends every unknown value
                    // through the same non-pixel branch as LINE/PAGE.
                    else -> AmllWheelDeltaMode.LINE
                }
                latestOnWheel(deltaY, mode)
            },
            onPageVisibilityChanged = { visible, forceResync ->
                latestOnPageVisibilityChanged(visible, forceResync)
            },
        )
        onDispose {
            uninstallAmllPlatformEvents(token)
        }
    }

    return onGloballyPositioned { coordinates ->
        viewportBoundsInWindow = coordinates.boundsInWindow()
    }
}

@JsFun(
    "() => globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)",
)
private external fun newAmllPlatformEventToken(): String

@JsFun(
    """(token, onWheel, onPageVisibilityChanged) => {
        const subscriptions = globalThis.__redefineNcmAmllPlatformEvents ??= new Map();
        const previous = subscriptions.get(token);
        if (previous) {
            window.removeEventListener("wheel", previous.wheel, true);
            window.removeEventListener("pagehide", previous.pageHide);
            window.removeEventListener("pageshow", previous.pageShow);
            document.removeEventListener("visibilitychange", previous.visibilityChange);
            subscriptions.delete(token);
        }

        const findComposeCanvas = () => {
            const host = document.getElementById("redefineNcmApp");
            const positioningContainer = host?.firstElementChild;
            const shadowHost = positioningContainer?.firstElementChild;
            return shadowHost?.shadowRoot?.querySelector("canvas") ??
                host?.querySelector("canvas") ??
                null;
        };
        const wheel = event => {
            const canvas = findComposeCanvas();
            const rect = canvas?.getBoundingClientRect();
            if (!canvas || !rect || rect.width <= 0 || rect.height <= 0) return;

            // Compose layout coordinates are in the CanvasKit backing-store coordinate space.
            // Map CSS client coordinates through the current canvas scale before comparing them
            // with LayoutCoordinates.boundsInWindow().
            const canvasX = (event.clientX - rect.left) * canvas.width / rect.width;
            const canvasY = (event.clientY - rect.top) * canvas.height / rect.height;
            if (!onWheel(event.deltaY, event.deltaMode, canvasX, canvasY)) return;

            event.preventDefault();
            // The common Compose pointer API discards deltaMode. Stop this accepted native event
            // before CanvasKit can emit a second, lossy PointerEventType.Scroll for it.
            event.stopImmediatePropagation();
        };
        const pageHide = () => onPageVisibilityChanged(false, false);
        const pageShow = () => onPageVisibilityChanged(true, true);
        const visibilityChange = () => {
            const visible = document.visibilityState !== "hidden";
            onPageVisibilityChanged(visible, visible);
        };

        window.addEventListener("wheel", wheel, { capture: true, passive: false });
        window.addEventListener("pagehide", pageHide);
        window.addEventListener("pageshow", pageShow);
        document.addEventListener("visibilitychange", visibilityChange);
        subscriptions.set(token, {
            wheel,
            pageHide,
            pageShow,
            visibilityChange,
        });
        onPageVisibilityChanged(document.visibilityState !== "hidden", false);
    }""",
)
private external fun installAmllPlatformEvents(
    token: String,
    onWheel: (
        deltaY: Double,
        deltaMode: Int,
        canvasX: Double,
        canvasY: Double,
    ) -> Boolean,
    onPageVisibilityChanged: (Boolean, Boolean) -> Unit,
)

@JsFun(
    """(token) => {
        const subscriptions = globalThis.__redefineNcmAmllPlatformEvents;
        const subscription = subscriptions?.get(token);
        if (!subscription) return;
        window.removeEventListener("wheel", subscription.wheel, true);
        window.removeEventListener("pagehide", subscription.pageHide);
        window.removeEventListener("pageshow", subscription.pageShow);
        document.removeEventListener("visibilitychange", subscription.visibilityChange);
        subscriptions.delete(token);
    }""",
)
private external fun uninstallAmllPlatformEvents(token: String)

/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * Native Compose translation/adaptation of Apple Music-like Lyrics and the former
 * RedefineNCM AMLL host.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlin.js.ExperimentalWasmJsInterop::class,
)

package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLSpanElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import kotlin.JsFun

@Composable
internal actual fun NativeDynamicCoverLayer(
    url: String,
    modifier: Modifier,
    play: Boolean,
    showBadge: Boolean,
    reducedMotion: Boolean,
    onVisibilityChanged: (Boolean) -> Unit,
) {
    val videoUrl = url.trim()
    if (videoUrl.isEmpty()) return

    val visualSpec = remember(showBadge) {
        nativeDynamicCoverVisualSpec(
            showBadge = showBadge,
            androidPresentation = false,
        )
    }
    val node = remember(videoUrl, showBadge, visualSpec) {
        WebDynamicCoverNode(
            url = videoUrl,
            showBadge = showBadge,
            visualSpec = visualSpec,
        )
    }

    LaunchedEffect(node, play, reducedMotion) {
        node.setReducedMotion(reducedMotion)
        node.setPlaying(play)
    }
    val visible by node.visible.collectAsState()
    val latestOnVisibilityChanged by rememberUpdatedState(onVisibilityChanged)
    LaunchedEffect(visible) {
        latestOnVisibilityChanged(visible)
    }
    DisposableEffect(Unit) {
        onDispose { latestOnVisibilityChanged(false) }
    }

    if (showBadge) {
        // HTML interop is suitable for the bounded wiki artwork slot. The node owns
        // its badge too, so the browser video and the source CSS tokens remain in
        // one stacking context.
        key(node) {
            HtmlElementView(
                factory = { node.root },
                update = {
                    node.setReducedMotion(reducedMotion)
                    node.setPlaying(play)
                },
                onRelease = { node.release() },
                modifier = modifier,
            )
        }
    } else {
        val underlayReady by node.canvasUnderlayReady.collectAsState()
        /*
         * Compose for Web places HtmlElementView above the whole Canvas, which would
         * cover lyrics during a full-screen background transition. Mount this one video
         * immediately below the Compose canvas, then progressively erase only the
         * already-drawn static cover at this exact sibling position. Later common
         * brightness/scrim siblings still draw above both sources.
         */
        DisposableEffect(node) {
            node.mountAsCanvasUnderlay()
            onDispose {
                node.release()
            }
        }
        key(node) {
            val underlayBlend by animateFloatAsState(
                targetValue = if (underlayReady) 1f else 0f,
                animationSpec = if (reducedMotion) {
                    snap()
                } else {
                    tween(
                        durationMillis = visualSpec.fadeDurationMillis,
                        easing = CubicBezierEasing(
                            visualSpec.easingX1,
                            visualSpec.easingY1,
                            visualSpec.easingX2,
                            visualSpec.easingY2,
                        ),
                    )
                },
                label = "wasm-dynamic-cover-underlay",
            )
            Box(
                modifier = Modifier
                    .drawWithContent {
                        drawContent()
                        if (underlayBlend > 0f) {
                            drawRect(
                                color = androidx.compose.ui.graphics.Color.Black.copy(
                                    alpha = underlayBlend,
                                ),
                                blendMode = BlendMode.DstOut,
                            )
                        }
                    }
                    .then(modifier),
            )
        }
    }
}

private class WebDynamicCoverNode(
    url: String,
    private val showBadge: Boolean,
    visualSpec: NativeDynamicCoverVisualSpec,
) {
    private val opacityTransition =
        "opacity ${visualSpec.fadeDurationMillis}ms " +
            "cubic-bezier(${visualSpec.easingX1}, ${visualSpec.easingY1}, " +
            "${visualSpec.easingX2}, ${visualSpec.easingY2})"
    val root: HTMLDivElement = (document.createElement("div") as HTMLDivElement).apply {
        style.position = "relative"
        style.width = "100%"
        style.height = "100%"
        style.backgroundColor = "transparent"
        style.setProperty("overflow", "hidden")
        // The shared wiki cover uses 96/108/144 px with 24/26/32 px radii.
        // All three points are exactly radius = 8px + width / 6.
        style.borderRadius = "clamp(24px, calc(8px + 16.6666667%), 32px)"
        style.setProperty("pointer-events", "none")
        setAttribute("aria-hidden", "true")
    }
    private val video: HTMLVideoElement =
        (document.createElement("video") as HTMLVideoElement).apply {
            muted = true
            loop = true
            autoplay = !showBadge
            preload = if (showBadge) "metadata" else "auto"
            setAttribute("muted", "")
            if (!showBadge) setAttribute("autoplay", "")
            setAttribute("loop", "")
            setAttribute("playsinline", "")
            setAttribute("aria-hidden", "true")
            style.position = "absolute"
            style.left = "0"
            style.top = "0"
            style.width = "100%"
            style.height = "100%"
            style.objectFit = "cover"
            style.opacity = "0"
            style.backgroundColor = "transparent"
            style.setProperty("pointer-events", "none")
            style.transition = if (showBadge) opacityTransition else "none"
            if (showBadge) {
                style.zIndex = "2"
                style.borderRadius = "inherit"
            } else {
                // Common Compose draws brightness and scrim after the clear node.
                // Blur, saturation and the 120% * 110% overscan must therefore be
                // applied on the real DOM pixels here rather than on an empty layer.
                style.transformOrigin = "50% 50%"
                style.transform = "scale(1.32)"
                style.filter = "blur(48px) saturate(${visualSpec.saturation})"
            }
        }
    private val badge: HTMLSpanElement? =
        if (showBadge) {
            (document.createElement("span") as HTMLSpanElement).apply {
                textContent = "动态封面"
                hidden = true
                style.position = "absolute"
                style.right = "8px"
                style.bottom = "8px"
                style.zIndex = "3"
                style.padding = "6px 10px"
                style.borderRadius = "999px"
                style.color = "#9cf2dc"
                style.backgroundColor = "rgba(0, 81, 68, 0.92)"
                style.fontSize = "11px"
                style.fontWeight = "700"
                style.letterSpacing = ".02em"
                style.setProperty("pointer-events", "none")
                style.setProperty("backdrop-filter", "blur(12px)")
                style.setProperty("-webkit-backdrop-filter", "blur(12px)")
            }
        } else {
            null
        }

    private var requestedPlay = false
    private var lifecycleActive = true
    private var firstFrameReady = false
    private var framePresented = false
    private var released = false
    private var mountedAsUnderlay = false
    private val lifecycleToken = newWebDynamicCoverLifecycleToken()
    private val _canvasUnderlayReady = MutableStateFlow(false)
    val canvasUnderlayReady = _canvasUnderlayReady.asStateFlow()
    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()

    private val loadedDataListener: (Event) -> Unit = {
        if (!released) {
            firstFrameReady = true
            if (decoderShouldPlay) requestPlayback()
        }
    }
    private val errorListener: (Event) -> Unit = {
        if (!released) {
            firstFrameReady = false
            hideVideo()
            video.pause()
            video.removeAttribute("src")
            video.load()
        }
    }
    private val playingListener: (Event) -> Unit = {
        if (!released) {
            if (!decoderShouldPlay) {
                // The background element carries the source `autoplay` attribute.
                // Explicitly suppress that browser-driven start when either shared
                // AMLL state or the browser page lifecycle has paused it.
                video.pause()
            } else if (firstFrameReady) {
                revealVideo()
            }
        }
    }

    init {
        root.appendChild(video)
        badge?.let { root.appendChild(it) }
        video.addEventListener("loadeddata", loadedDataListener)
        video.addEventListener("error", errorListener)
        video.addEventListener("playing", playingListener)
        installWebDynamicCoverLifecycle(lifecycleToken) { active ->
            setLifecycleActive(active)
        }
        video.src = url
        video.load()
    }

    fun mountAsCanvasUnderlay() {
        if (released || mountedAsUnderlay) return
        mountedAsUnderlay = mountDynamicVideoBelowComposeCanvas(video)
        updateCanvasUnderlayReady()
        if (mountedAsUnderlay && decoderShouldPlay && firstFrameReady) {
            requestPlayback()
        } else if (!mountedAsUnderlay) {
            hideVideo()
            video.pause()
        }
    }

    fun setPlaying(value: Boolean) {
        if (released) return
        requestedPlay = value
        applyPlaybackState()
    }

    private fun setLifecycleActive(value: Boolean) {
        if (released || lifecycleActive == value) return
        lifecycleActive = value
        applyPlaybackState()
    }

    private val decoderShouldPlay: Boolean
        get() = nativeDynamicCoverShouldPlay(
            requestedPlay = requestedPlay,
            lifecycleActive = lifecycleActive,
        )

    private fun applyPlaybackState() {
        if (released) return
        if (decoderShouldPlay) {
            if (firstFrameReady && video.paused) requestPlayback()
        } else {
            video.pause()
            // AMLL's background suppression pauses #dynamic-bg without removing
            // `.visible`; the bounded wiki video hides while inactive.
            if (showBadge) hideVideo()
        }
    }

    fun setReducedMotion(value: Boolean) {
        if (!released) {
            video.style.transition =
                if (!showBadge || value) "none" else opacityTransition
        }
    }

    private fun requestPlayback() {
        if (released || !decoderShouldPlay || !firstFrameReady) return
        if (!showBadge && !mountedAsUnderlay) return
        video.play().then(
            onFulfilled = {
                if (!released && decoderShouldPlay && !video.paused) revealVideo()
                null
            },
            onRejected = {
                if (!released) hideVideo()
                null
            },
        )
    }

    private fun revealVideo() {
        video.style.opacity = "1"
        framePresented = true
        _visible.value = true
        updateCanvasUnderlayReady()
        badge?.hidden = false
    }

    private fun hideVideo() {
        video.style.opacity = "0"
        framePresented = false
        _visible.value = false
        updateCanvasUnderlayReady()
        badge?.hidden = true
    }

    private fun updateCanvasUnderlayReady() {
        _canvasUnderlayReady.value =
            !showBadge && !released && mountedAsUnderlay && framePresented
    }

    fun release() {
        if (released) return
        released = true
        requestedPlay = false
        lifecycleActive = false
        uninstallWebDynamicCoverLifecycle(lifecycleToken)
        video.pause()
        hideVideo()
        video.removeEventListener("loadeddata", loadedDataListener)
        video.removeEventListener("error", errorListener)
        video.removeEventListener("playing", playingListener)
        video.removeAttribute("src")
        video.load()
        if (mountedAsUnderlay) {
            unmountDynamicVideo(video)
            mountedAsUnderlay = false
        }
        updateCanvasUnderlayReady()
    }
}

@JsFun(
    """(video) => {
        const host = document.getElementById("redefineNcmApp");
        const positioningContainer = host?.firstElementChild;
        const shadowHost = positioningContainer?.firstElementChild;
        const canvas = shadowHost?.shadowRoot?.querySelector("canvas") ??
            host?.querySelector("canvas") ??
            null;
        const appContainer = canvas?.parentElement;
        if (!host || !canvas || !appContainer) {
            console.error(
                "[RedefineNCM] Dynamic-cover underlay mount failed: " +
                "Compose canvas was not found below #redefineNcmApp."
            );
            return false;
        }

        video.style.position = "absolute";
        video.style.inset = "0";
        video.style.zIndex = "0";
        appContainer.insertBefore(video, canvas);

        // Keep CanvasKit and every Compose layer above the underlay. Clearing the
        // NativeDynamicCoverLayer node exposes this video through the alpha canvas.
        canvas.style.position = "relative";
        canvas.style.zIndex = "1";
        return true;
    }""",
)
private external fun mountDynamicVideoBelowComposeCanvas(
    video: HTMLVideoElement,
): Boolean

@JsFun(
    """(video) => {
        if (video?.parentNode) video.parentNode.removeChild(video);
    }""",
)
private external fun unmountDynamicVideo(
    video: HTMLVideoElement,
)

@JsFun(
    "() => globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)",
)
private external fun newWebDynamicCoverLifecycleToken(): String

@JsFun(
    """(token, onLifecycleActive) => {
        const subscriptions = globalThis.__redefineNcmDynamicCoverLifecycle ??= new Map();
        const previous = subscriptions.get(token);
        if (previous) {
            window.removeEventListener("pagehide", previous.pageHide);
            window.removeEventListener("pageshow", previous.pageShow);
            document.removeEventListener("visibilitychange", previous.visibilityChange);
            subscriptions.delete(token);
        }

        const pageHide = () => onLifecycleActive(false);
        const pageShow = () => {
            onLifecycleActive(document.visibilityState !== "hidden");
        };
        const visibilityChange = () => {
            onLifecycleActive(document.visibilityState !== "hidden");
        };

        window.addEventListener("pagehide", pageHide);
        window.addEventListener("pageshow", pageShow);
        document.addEventListener("visibilitychange", visibilityChange);
        subscriptions.set(token, {
            pageHide,
            pageShow,
            visibilityChange,
        });
        onLifecycleActive(document.visibilityState !== "hidden");
    }""",
)
private external fun installWebDynamicCoverLifecycle(
    token: String,
    onLifecycleActive: (Boolean) -> Unit,
)

@JsFun(
    """(token) => {
        const subscriptions = globalThis.__redefineNcmDynamicCoverLifecycle;
        const subscription = subscriptions?.get(token);
        if (!subscription) return;
        window.removeEventListener("pagehide", subscription.pageHide);
        window.removeEventListener("pageshow", subscription.pageShow);
        document.removeEventListener("visibilitychange", subscription.visibilityChange);
        subscriptions.delete(token);
    }""",
)
private external fun uninstallWebDynamicCoverLifecycle(token: String)

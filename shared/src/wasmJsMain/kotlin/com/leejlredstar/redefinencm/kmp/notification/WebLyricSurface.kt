@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.notification

/**
 * The browser's lyric surface: a DOM pill fixed in the page, the tab title, and a notification
 * once the user has granted that permission.
 *
 * It is part of playback rather than something to switch on. The pill stays off the Now Playing
 * and lyric pages, which show the same thing full size; at phone width it sits at the bottom,
 * above the navigation and the mini player, instead of over the pages' top bars. Screen readers do
 * not read it: the page and the tab title already carry the track, and announcing every line
 * talked over everything else.
 */
object WebLyricSurface : PageOverlayLyricSurface {
    override fun setPlayerPageShown(shown: Boolean) {
        setBrowserLyricSurfaceHidden(shown)
    }

    override fun updateLyric(
        title: String?,
        artist: String?,
        currentLyric: String?,
        nextLyric: String?,
        artworkUri: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        // The payload is trimmed and its position clamped, as on every other target, so a
        // negative position never reaches the DOM. Like the desktop window, the browser surface
        // has separate lines, so a blank lyric stays blank rather than repeating the title.
        val payload = lyricPayloadOf(
            title = title,
            artist = artist,
            currentLyric = currentLyric,
            nextLyric = nextLyric,
            artworkUri = artworkUri,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
        )
        updateBrowserLyricSurface(
            title = payload.title,
            artist = payload.artist,
            currentLyric = payload.currentLyric,
            nextLyric = payload.nextLyric,
            artworkUri = payload.artworkUri,
            isPlaying = payload.isPlaying,
            positionMs = payload.positionMs.toDouble(),
            durationMs = payload.durationMs.toDouble(),
        )
    }

    override fun clearFocus() {
        clearBrowserLyricSurface()
    }

    override fun reset() {
        clearBrowserLyricSurface()
    }
}

actual val lyricSurface: LyricSurface = WebLyricSurface

private fun updateBrowserLyricSurface(
    title: String,
    artist: String,
    currentLyric: String,
    nextLyric: String,
    artworkUri: String,
    isPlaying: Boolean,
    positionMs: Double,
    durationMs: Double,
): Unit = js(
    """{
        const surfaceId = "redefinencm-web-lyric";
        let surface = document.getElementById(surfaceId);
        if (!surface) {
            surface = document.createElement("section");
            surface.id = surfaceId;
            surface.setAttribute("aria-hidden", "true");
            const artwork = document.createElement("img");
            artwork.dataset.role = "artwork";
            artwork.alt = "";
            const text = document.createElement("div");
            text.dataset.role = "text";
            const current = document.createElement("div");
            current.dataset.role = "current";
            const next = document.createElement("div");
            next.dataset.role = "next";
            const progress = document.createElement("div");
            progress.dataset.role = "progress";
            text.append(current, next);
            surface.append(artwork, text, progress);
            document.body.appendChild(surface);
        }

        surface.hidden = Boolean(globalThis.__redefineNcmLyricSurfaceHidden);
        const trimmedCurrentLyric = currentLyric.trim();
        const currentText = trimmedCurrentLyric || title.trim() || "RedefineNCM";
        const detailText = nextLyric.trim() || [title, artist].filter(Boolean).join(" · ");
        const currentNode = surface.querySelector('[data-role="current"]');
        const nextNode = surface.querySelector('[data-role="next"]');
        const visibleCurrentText = (isPlaying ? "▶" : "Ⅱ") + " " + currentText;
        if (currentNode.textContent !== visibleCurrentText) currentNode.textContent = visibleCurrentText;
        if (nextNode.textContent !== detailText) nextNode.textContent = detailText;
        const artwork = surface.querySelector('[data-role="artwork"]');
        if (artworkUri) {
            artwork.src = artworkUri;
            artwork.hidden = false;
        } else {
            artwork.removeAttribute("src");
            artwork.hidden = true;
        }
        const ratio = durationMs > 0 ? Math.max(0, Math.min(1, positionMs / durationMs)) : 0;
        surface.querySelector('[data-role="progress"]').style.transform = "scaleX(" + ratio + ")";
        document.title = trimmedCurrentLyric
            ? trimmedCurrentLyric + " · " + (title || "RedefineNCM")
            : (title || "RedefineNCM");

        const notificationSignature = [currentText, detailText, artworkUri].join("\u0000");
        if (typeof Notification !== "undefined" &&
            Notification.permission === "granted" &&
            globalThis.__redefineNcmLyricNotificationSignature !== notificationSignature) {
            try {
                globalThis.__redefineNcmLyricNotification?.close();
                globalThis.__redefineNcmLyricNotification = new Notification(currentText, {
                    body: detailText,
                    icon: artworkUri || undefined,
                    tag: "redefinencm-current-lyric",
                    renotify: false,
                    silent: true,
                });
            } catch (_) {
                // Mobile browsers may require ServiceWorkerRegistration.showNotification().
                // The in-page DOM lyric surface remains the authoritative Web fallback.
                globalThis.__redefineNcmLyricNotification = null;
            }
            globalThis.__redefineNcmLyricNotificationSignature = notificationSignature;
        }
    }""",
)

private fun setBrowserLyricSurfaceHidden(hidden: Boolean): Unit = js(
    """{
        globalThis.__redefineNcmLyricSurfaceHidden = hidden;
        const surface = document.getElementById("redefinencm-web-lyric");
        if (surface) surface.hidden = hidden;
    }""",
)

private fun clearBrowserLyricSurface(): Unit = js(
    """{
        document.getElementById("redefinencm-web-lyric")?.remove();
        globalThis.__redefineNcmLyricNotification?.close();
        globalThis.__redefineNcmLyricNotification = null;
        globalThis.__redefineNcmLyricNotificationSignature = null;
        document.title = "RedefineNCM";
    }""",
)

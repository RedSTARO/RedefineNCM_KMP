@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.notification

/** Browser lyric surface: a live DOM pill, tab title, and an optional granted notification. */
actual object LyricNotificationController {
    actual fun updateLyric(
        title: String?,
        artist: String?,
        currentLyric: String?,
        nextLyric: String?,
        artworkUri: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        updateBrowserLyricSurface(
            title = title.orEmpty(),
            artist = artist.orEmpty(),
            currentLyric = currentLyric.orEmpty(),
            nextLyric = nextLyric.orEmpty(),
            artworkUri = artworkUri.orEmpty(),
            isPlaying = isPlaying,
            positionMs = positionMs.toDouble(),
            durationMs = durationMs.toDouble(),
        )
    }

    actual fun clearFocus() {
        clearBrowserLyricSurface()
    }

    actual fun reset() {
        clearBrowserLyricSurface()
    }
}

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
            surface.setAttribute("role", "status");
            surface.setAttribute("aria-live", "polite");
            Object.assign(surface.style, {
                position: "fixed",
                top: "12px",
                left: "50%",
                transform: "translateX(-50%)",
                zIndex: "2147483647",
                display: "grid",
                gridTemplateColumns: "auto minmax(0, 1fr)",
                gap: "10px",
                alignItems: "center",
                maxWidth: "min(720px, calc(100vw - 24px))",
                padding: "9px 14px",
                borderRadius: "22px",
                color: "white",
                background: "rgba(20, 20, 24, .84)",
                boxShadow: "0 8px 28px rgba(0, 0, 0, .26)",
                backdropFilter: "blur(16px)",
                pointerEvents: "none",
                fontFamily: "system-ui, sans-serif",
            });
            const artwork = document.createElement("img");
            artwork.dataset.role = "artwork";
            Object.assign(artwork.style, {
                width: "42px",
                height: "42px",
                borderRadius: "12px",
                objectFit: "cover",
                display: "none",
            });
            const text = document.createElement("div");
            text.style.minWidth = "0";
            const current = document.createElement("div");
            current.dataset.role = "current";
            Object.assign(current.style, {
                overflow: "hidden",
                whiteSpace: "nowrap",
                textOverflow: "ellipsis",
                fontSize: "15px",
                fontWeight: "700",
            });
            const next = document.createElement("div");
            next.dataset.role = "next";
            Object.assign(next.style, {
                overflow: "hidden",
                whiteSpace: "nowrap",
                textOverflow: "ellipsis",
                marginTop: "2px",
                opacity: ".68",
                fontSize: "12px",
            });
            const progress = document.createElement("div");
            progress.dataset.role = "progress";
            Object.assign(progress.style, {
                position: "absolute",
                left: "16px",
                right: "16px",
                bottom: "3px",
                height: "2px",
                borderRadius: "2px",
                transformOrigin: "left center",
                background: "rgba(255,255,255,.72)",
            });
            text.append(current, next);
            surface.append(artwork, text, progress);
            document.body.appendChild(surface);
        }

        const currentText = currentLyric.trim() || title.trim() || "RedefineNCM";
        const detailText = nextLyric.trim() || [title, artist].filter(Boolean).join(" · ");
        const currentNode = surface.querySelector('[data-role="current"]');
        const nextNode = surface.querySelector('[data-role="next"]');
        const visibleCurrentText = (isPlaying ? "▶" : "Ⅱ") + " " + currentText;
        if (currentNode.textContent !== visibleCurrentText) currentNode.textContent = visibleCurrentText;
        if (nextNode.textContent !== detailText) nextNode.textContent = detailText;
        const artwork = surface.querySelector('[data-role="artwork"]');
        if (artworkUri) {
            artwork.src = artworkUri;
            artwork.style.display = "block";
        } else {
            artwork.removeAttribute("src");
            artwork.style.display = "none";
        }
        const ratio = durationMs > 0 ? Math.max(0, Math.min(1, positionMs / durationMs)) : 0;
        surface.querySelector('[data-role="progress"]').style.transform = "scaleX(" + ratio + ")";
        document.title = currentLyric.trim()
            ? currentLyric.trim() + " — " + (title || "RedefineNCM")
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

private fun clearBrowserLyricSurface(): Unit = js(
    """{
        document.getElementById("redefinencm-web-lyric")?.remove();
        globalThis.__redefineNcmLyricNotification?.close();
        globalThis.__redefineNcmLyricNotification = null;
        globalThis.__redefineNcmLyricNotificationSignature = null;
        document.title = "RedefineNCM";
    }""",
)

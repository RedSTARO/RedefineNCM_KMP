@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.JsFun

@Composable
actual fun rememberReducedMotionEnabled(): Boolean {
    val listenerToken = remember { newReducedMotionListenerToken() }
    var reducedMotionEnabled by remember {
        mutableStateOf(webPrefersReducedMotion())
    }

    DisposableEffect(listenerToken) {
        installReducedMotionListener(listenerToken) { matches ->
            reducedMotionEnabled = matches
        }
        onDispose {
            uninstallReducedMotionListener(listenerToken)
        }
    }

    return reducedMotionEnabled
}

@JsFun(
    "() => globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)",
)
private external fun newReducedMotionListenerToken(): String

@JsFun(
    """() => typeof globalThis.matchMedia === "function" &&
        globalThis.matchMedia("(prefers-reduced-motion: reduce)").matches""",
)
private external fun webPrefersReducedMotion(): Boolean

@JsFun(
    """(token, callback) => {
        const subscriptions = globalThis.__redefineNcmReducedMotionSubscriptions ??= new Map();
        const previous = subscriptions.get(token);
        if (previous) {
            if (previous.modern) {
                previous.preference.removeEventListener("change", previous.listener);
            } else {
                previous.preference.removeListener(previous.listener);
            }
            subscriptions.delete(token);
        }

        if (typeof globalThis.matchMedia !== "function") {
            callback(false);
            return;
        }

        const preference = globalThis.matchMedia("(prefers-reduced-motion: reduce)");
        const listener = event => callback(event.matches);
        const modern = typeof preference.addEventListener === "function";
        if (modern) {
            preference.addEventListener("change", listener);
        } else {
            preference.addListener(listener);
        }
        subscriptions.set(token, { preference, listener, modern });
        callback(preference.matches);
    }""",
)
private external fun installReducedMotionListener(
    token: String,
    callback: (Boolean) -> Unit,
)

@JsFun(
    """(token) => {
        const subscriptions = globalThis.__redefineNcmReducedMotionSubscriptions;
        const subscription = subscriptions?.get(token);
        if (!subscription) return;
        if (subscription.modern) {
            subscription.preference.removeEventListener("change", subscription.listener);
        } else {
            subscription.preference.removeListener(subscription.listener);
        }
        subscriptions.delete(token);
    }""",
)
private external fun uninstallReducedMotionListener(token: String)

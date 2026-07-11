@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val currentOnBack = rememberUpdatedState(onBack)
    val token = remember { newWebBackHandlerToken() }
    DisposableEffect(enabled, token) {
        if (enabled) {
            installWebBackHandler(token) { currentOnBack.value() }
        }
        onDispose {
            if (enabled) uninstallWebBackHandler(token)
        }
    }
}

private fun newWebBackHandlerToken(): String = js(
    "crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2)",
)

private fun installWebBackHandler(token: String, callback: () -> Unit): Unit = js(
    """{
        const state = globalThis.__redefineNcmBackState ??= {
            handlers: new Map(),
            installed: false,
            suppressOnce: false,
        };
        state.handlers.delete(token);
        state.handlers.set(token, callback);
        if (!state.installed) {
            window.addEventListener("popstate", () => {
                if (state.suppressOnce) {
                    state.suppressOnce = false;
                    return;
                }
                const handlers = Array.from(state.handlers.entries());
                const current = handlers.length === 0 ? null : handlers[handlers.length - 1];
                if (current === null) return;
                const [currentToken, currentCallback] = current;
                currentCallback();
                setTimeout(() => {
                    if (state.handlers.has(currentToken) &&
                        history.state?.__redefineNcmBackToken !== currentToken) {
                        history.pushState({ __redefineNcmBackToken: currentToken }, "", location.href);
                    }
                }, 0);
            });
            state.installed = true;
        }
        if (history.state?.__redefineNcmBackToken !== token) {
            history.pushState({ __redefineNcmBackToken: token }, "", location.href);
        }
    }""",
)

private fun uninstallWebBackHandler(token: String): Unit = js(
    """{
        const state = globalThis.__redefineNcmBackState;
        if (!state) return;
        state.handlers.delete(token);
        if (history.state?.__redefineNcmBackToken === token) {
            state.suppressOnce = true;
            history.back();
        }
    }""",
)

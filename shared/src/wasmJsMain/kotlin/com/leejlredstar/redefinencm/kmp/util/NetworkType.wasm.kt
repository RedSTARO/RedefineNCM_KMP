@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.util

/**
 * Network Information is the only browser API that can name a transport. It is optional and most
 * engines leave `type` empty, so an unknown transport falls back to Wi-Fi, matching desktop.
 */
@JsFun(
    """() => {
        const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
        return connection && typeof connection.type === "string" ? connection.type : "";
    }""",
)
private external fun browserConnectionType(): String

actual fun currentNetworkType(): NetworkType =
    if (browserConnectionType().equals("cellular", ignoreCase = true)) {
        NetworkType.CELLULAR
    } else {
        NetworkType.WIFI
    }

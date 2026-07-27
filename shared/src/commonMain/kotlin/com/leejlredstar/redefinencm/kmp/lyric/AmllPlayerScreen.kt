package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable
import com.leejlredstar.redefinencm.kmp.ui.amll.NativeAmllScreen

enum class AmllRendererMode {
    LegacyWebView,
    NativeCompose,
}

/**
 * Whether the current target can use the legacy AMLL WebView route.
 */
expect val supportsLegacyAmllWebView: Boolean

/**
 * Pure utility for selecting the route from a persisted preference plus platform support.
 */
fun resolveAmllRendererMode(
    useNativeRenderer: Boolean,
    legacyWebViewSupported: Boolean,
): AmllRendererMode = if (useNativeRenderer || !legacyWebViewSupported) {
    AmllRendererMode.NativeCompose
} else {
    AmllRendererMode.LegacyWebView
}

/**
 * Current sole route for the full-player surface.
 */
@Composable
fun AmllPlayerScreen(
    useNativeRenderer: Boolean,
    onBack: () -> Unit,
) {
    val mode = resolveAmllRendererMode(
        useNativeRenderer = useNativeRenderer,
        legacyWebViewSupported = supportsLegacyAmllWebView,
    )

    when (mode) {
        AmllRendererMode.NativeCompose -> NativeAmllScreen(onBack = onBack)
        AmllRendererMode.LegacyWebView -> WebViewLyricScreen(onBack = onBack)
    }
}

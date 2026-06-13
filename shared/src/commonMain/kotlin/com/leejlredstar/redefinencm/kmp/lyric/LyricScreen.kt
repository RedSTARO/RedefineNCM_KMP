package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable

/**
 * Platform-specific WebView-based lyric screen.
 *
 * - **Android**: loads the AMLL (Apple Music-Like Lyrics) engine in a WebView
 * - **Other platforms**: falls back to the Compose [FullLyricScreen]
 *
 * Opens when the user chooses the "WebView" lyric experience from NowPlayingScreen.
 */
expect @Composable fun WebViewLyricScreen(onBack: () -> Unit = {})

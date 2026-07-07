package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable

/**
 * Platform-specific WebView-based lyric screen.
 *
 * - **Android**: loads the AMLL (Apple Music-Like Lyrics) engine in the system WebView
 * - **Desktop/JVM**: loads the same AMLL assets in the system WebView2 host
 * - **iOS**: falls back to the Compose [FullLyricScreen]
 *
 * Opens when the user chooses the "WebView" lyric experience from NowPlayingScreen.
 */
@Composable
expect fun WebViewLyricScreen(onBack: () -> Unit = {})

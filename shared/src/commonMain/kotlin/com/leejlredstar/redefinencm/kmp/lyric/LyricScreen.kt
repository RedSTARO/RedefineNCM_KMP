package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable

/**
 * Platform-specific WebView-based lyric screen.
 *
 * - **Android**: loads the AMLL (Apple Music-Like Lyrics) engine in the system WebView
 * - **Supported Windows Desktop**: loads the same AMLL assets in the system WebView2 host
 * - **Other JVM platforms, iOS, and Web**: fall back to the Compose [FullLyricScreen]
 *
 * This is the sole full-screen player route. Mini-player and OS now-playing entry points
 * open it directly; the former KMP NowPlaying screen no longer participates in navigation.
 */
@Composable
expect fun WebViewLyricScreen(onBack: () -> Unit = {})

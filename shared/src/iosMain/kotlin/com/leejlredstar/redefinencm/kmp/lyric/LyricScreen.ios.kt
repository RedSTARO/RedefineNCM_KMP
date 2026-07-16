package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable
import com.leejlredstar.redefinencm.kmp.ui.screen.FullLyricScreen

actual val supportsDynamicNowPlayingCover: Boolean = false

/**
 * iOS has no Android-style [android.webkit.WebView]; fall back to the
 * pure-Compose [FullLyricScreen] karaoke renderer.
 */
@Composable
actual fun WebViewLyricScreen(onBack: () -> Unit) {
    FullLyricScreen(onBack = onBack)
}

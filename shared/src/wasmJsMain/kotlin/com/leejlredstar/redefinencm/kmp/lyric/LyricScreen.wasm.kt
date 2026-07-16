package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable
import com.leejlredstar.redefinencm.kmp.ui.screen.FullLyricScreen

actual val supportsDynamicNowPlayingCover: Boolean = false

/** Browser fallback matching iOS: keep the complete Compose lyric experience. */
@Composable
actual fun WebViewLyricScreen(onBack: () -> Unit) {
    FullLyricScreen(onBack = onBack)
}

package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable
import com.leejlredstar.redefinencm.kmp.ui.amll.NativeAmllScreen

/**
 * Web/Wasm never advertises Legacy WebView support. Keep the expect/actual contract complete and
 * fail safe to the Native renderer if this platform host is called directly.
 */
@Composable
actual fun WebViewLyricScreen(onBack: () -> Unit) {
    NativeAmllScreen(onBack = onBack)
}

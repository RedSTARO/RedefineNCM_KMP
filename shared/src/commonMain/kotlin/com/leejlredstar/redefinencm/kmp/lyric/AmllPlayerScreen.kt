package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable
import com.leejlredstar.redefinencm.kmp.ui.amll.NativeAmllScreen

/**
 * The only full-player surface, on every target.
 *
 * It always renders `NativeAmllScreen`, native Compose from the same AMLL sources. There is no
 * AMLL WebView renderer to select (Android System WebView, Windows x64 WebView2 and iOS
 * WKWebView, all driving one bundled `player.html`) and no persisted preference to honour.
 */
@Composable
fun AmllPlayerScreen(onBack: () -> Unit) {
    NativeAmllScreen(onBack = onBack)
}

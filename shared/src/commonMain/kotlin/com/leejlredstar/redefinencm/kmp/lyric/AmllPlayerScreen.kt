package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable
import com.leejlredstar.redefinencm.kmp.ui.amll.NativeAmllScreen

/**
 * The only full-player surface, on every target.
 *
 * The Legacy AMLL WebView renderer this used to select between — Android System WebView,
 * Windows x64 WebView2 and iOS WKWebView, all driving one bundled `player.html` — is gone.
 * `NativeAmllScreen` is native Compose from the same AMLL sources, so there is nothing left
 * to choose and no persisted preference to honour.
 */
@Composable
fun AmllPlayerScreen(onBack: () -> Unit) {
    NativeAmllScreen(onBack = onBack)
}

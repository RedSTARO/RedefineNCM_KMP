package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable

/**
 * Shared Lyric screen API for desktop legacy AMLL and platform-specific implementations.
 */
/**
 * Platform host for the AMLL full-screen lyric renderer.
 */
@Composable
expect fun WebViewLyricScreen(onBack: () -> Unit)

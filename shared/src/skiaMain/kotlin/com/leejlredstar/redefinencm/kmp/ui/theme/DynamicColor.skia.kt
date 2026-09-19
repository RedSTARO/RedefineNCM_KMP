package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

// Desktop, iOS and the browser have no wallpaper palette to read.
actual val dynamicColorSupported: Boolean = false

@Composable
internal actual fun rememberDynamicColorScheme(dark: Boolean): ColorScheme? = null

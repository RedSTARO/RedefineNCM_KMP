package com.leejlredstar.redefinencm.kmp.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun SystemBarIcons(darkBackground: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    // AndroidX enableEdgeToEdge reapplies the system theme on every configuration change.
    // Reassert the current page's contrast even when a fold/resize did not change its palette.
    LaunchedEffect(view, LocalConfiguration.current, darkBackground) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkBackground
            isAppearanceLightNavigationBars = !darkBackground
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

package com.leejlredstar.redefinencm.kmp.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.toArgb

/** Primitive boundary: the Android shell does not depend directly on Material3. */
fun androidLaunchBackgroundColor(dark: Boolean): Int =
    (if (dark) DarkColors else LightColors).surface.toArgb()

internal fun ThemeMode.applicationNightMode(): Int = when (this) {
    ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
    ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
    // setApplicationNightMode maps AUTO to UI_MODE_NIGHT_UNDEFINED in UiModeManagerService,
    // removing the package override so system configuration determines its night qualifier.
    ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
}

/** Persist the package override so Android 12+ can also theme the next system splash screen. */
fun syncAndroidApplicationTheme(context: Context, mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode.applicationNightMode())
    }.onFailure { System.err.println("Could not update application night mode: ${it::class.simpleName}") }
}

package com.leejlredstar.redefinencm.kmp.ui.theme

import android.app.UiModeManager
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidApplicationThemeTest {
    @Test
    fun appChoicesMapToPackageOverridesAndSystemClearsTheOverride() {
        assertEquals(UiModeManager.MODE_NIGHT_NO, ThemeMode.Light.applicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_YES, ThemeMode.Dark.applicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, ThemeMode.System.applicationNightMode())
    }
}

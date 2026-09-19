package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light, dark, or whatever the system is set to. */
enum class ThemeMode(val wireValue: String, val displayName: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色"),
    ;

    companion object {
        fun fromWireValue(value: String): ThemeMode = entries.firstOrNull { it.wireValue == value } ?: System
    }
}

/**
 * The theme the user picked, read by every [RedefineNCMTheme].
 *
 * The app used to follow the system's light or dark setting with no way to choose, and never
 * offered Android 12's wallpaper colours. A shared holder rather than a parameter because the
 * theme is applied in several roots — the app, the desktop window chrome and its lyric window,
 * the Android permission dialog — and all of them have to agree.
 */
object ThemePreferences {
    private val _mode = MutableStateFlow(ThemeMode.System)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false)
    /** Android 12+ wallpaper colours in place of the app's own scheme. */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    /** Reads the stored choice; call once the settings are available, before the first frame. */
    fun load(settings: PlatformSettings) {
        _mode.value = ThemeMode.fromWireValue(settings.getString(SettingKeys.THEME_MODE, ""))
        _dynamicColor.value = settings.getBoolean(SettingKeys.DYNAMIC_COLOR, false)
    }

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
    }
}

/** Whether this platform can derive a scheme from the wallpaper (Android 12 and later). */
expect val dynamicColorSupported: Boolean

/** The wallpaper-derived scheme, or null where [dynamicColorSupported] is false. */
@Composable
internal expect fun rememberDynamicColorScheme(dark: Boolean): ColorScheme?

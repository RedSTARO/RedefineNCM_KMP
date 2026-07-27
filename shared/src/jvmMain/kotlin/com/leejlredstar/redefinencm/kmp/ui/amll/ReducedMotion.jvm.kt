package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

@Composable
actual fun rememberReducedMotionEnabled(): Boolean =
    remember { readReducedMotionEnabled() }

private fun readReducedMotionEnabled(): Boolean {
    val explicitOverride = System.getProperty(REDUCED_MOTION_PROPERTY)
        ?.trim()
        ?.lowercase()
        ?.let { value ->
            when (value) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
        }
    if (explicitOverride != null) return explicitOverride
    if (!runningOnWindows()) return false

    return runCatching {
        val clientAreaAnimationEnabled = IntByReference()
        val preferenceRead = ReducedMotionUser32.INSTANCE.SystemParametersInfoW(
            uiAction = SPI_GETCLIENTAREAANIMATION,
            uiParam = 0,
            pvParam = clientAreaAnimationEnabled,
            fWinIni = 0,
        )
        preferenceRead && clientAreaAnimationEnabled.value == 0
    }.getOrDefault(false)
}

private fun runningOnWindows(): Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

private const val REDUCED_MOTION_PROPERTY = "redefinencm.reduceMotion"
private const val SPI_GETCLIENTAREAANIMATION = 0x1042

private interface ReducedMotionUser32 : StdCallLibrary {
    @Suppress("FunctionName")
    fun SystemParametersInfoW(
        uiAction: Int,
        uiParam: Int,
        pvParam: IntByReference,
        fWinIni: Int,
    ): Boolean

    companion object {
        val INSTANCE: ReducedMotionUser32 =
            Native.load("user32", ReducedMotionUser32::class.java)
    }
}

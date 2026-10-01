package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Android window shows `launch_background` until Compose draws its first frame. It has to be
 * the surface that frame will draw, or every cold start flashes from one colour to another.
 */
class AndroidLaunchBackgroundTest {
    private val resources: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }
        .resolve("androidApp/src/main/res")

    private fun launchBackground(qualifier: String): String =
        Regex("""<color name="launch_background">#([0-9A-Fa-f]{6})</color>""")
            .find(resources.resolve("$qualifier/colors.xml").readText())!!
            .groupValues[1]
            .uppercase()

    private fun surfaceOf(dark: Boolean): String =
        "%06X".format(neutralColorScheme(dark).surface.toArgb() and 0xFFFFFF)

    @Test
    fun theLightWindowStartsOnTheLightSurface() {
        assertEquals(surfaceOf(dark = false), launchBackground("values"))
    }

    @Test
    fun theDarkWindowStartsOnTheDarkSurface() {
        assertEquals(surfaceOf(dark = true), launchBackground("values-night"))
    }
}

package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class OklchTest {

    private fun assertRoundTrips(color: Color) {
        val result = color.toOklch().toColor()
        listOf(
            color.red to result.red,
            color.green to result.green,
            color.blue to result.blue,
        ).forEach { (expected, actual) ->
            assertTrue(
                abs(expected - actual) < 0.01f,
                "$color did not round-trip: got $result",
            )
        }
    }

    @Test
    fun roundTripsAcrossTheGamut() {
        listOf(
            Color(0xFFFFE94A), // the saturated yellow that motivated this
            Color(0xFF006B5B),
            Color(0xFF3355FF),
            Color.White,
            Color.Black,
            Color(0xFF808080),
        ).forEach(::assertRoundTrips)
    }

    @Test
    fun toneChangesLightnessButKeepsHue() {
        val source = Color(0xFFFFE94A)
        val darker = source.withTone(0.35f)
        assertTrue(darker.toOklch().lightness < source.toOklch().lightness)
        assertTrue(
            abs(darker.toOklch().hue - source.toOklch().hue) < 0.08f,
            "hue drifted: ${darker.toOklch().hue} vs ${source.toOklch().hue}",
        )
    }

    @Test
    fun clampChromaCapsSaturationWithoutMovingHue() {
        val source = Color(0xFFFFE94A)
        val muted = source.clampChroma(max = 0.04f)
        assertTrue(muted.toOklch().chroma <= 0.045f, "chroma not capped: ${muted.toOklch().chroma}")
        assertTrue(abs(muted.toOklch().hue - source.toOklch().hue) < 0.08f)
    }

    @Test
    fun greyHasEssentiallyNoChroma() {
        assertTrue(Color(0xFF808080).toOklch().chroma < 0.01f)
    }
}

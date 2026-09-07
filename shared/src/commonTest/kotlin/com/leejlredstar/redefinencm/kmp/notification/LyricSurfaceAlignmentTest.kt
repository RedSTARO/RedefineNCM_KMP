package com.leejlredstar.redefinencm.kmp.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricSurfaceAlignmentTest {
    @Test
    fun wireValuesRoundTripAndUnknownOnesFallBackToCentre() {
        LyricSurfaceAlignment.entries.forEach { alignment ->
            assertEquals(alignment, LyricSurfaceAlignment.fromWireValueOrNull(alignment.wireValue))
            assertEquals(alignment, LyricSurfaceAlignment.fromWireValueOrDefault(alignment.wireValue))
        }
        assertEquals(LyricSurfaceAlignment.END, LyricSurfaceAlignment.fromWireValueOrNull(" End "))
        assertNull(LyricSurfaceAlignment.fromWireValueOrNull("left"))
        assertEquals(LyricSurfaceAlignment.CENTER, LyricSurfaceAlignment.fromWireValueOrDefault(""))
        assertEquals(LyricSurfaceAlignment.CENTER, LyricSurfaceAlignment.fromWireValueOrDefault("justify"))
    }
}

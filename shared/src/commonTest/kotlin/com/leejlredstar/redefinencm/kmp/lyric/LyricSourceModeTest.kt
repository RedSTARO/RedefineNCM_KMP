package com.leejlredstar.redefinencm.kmp.lyric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricSourceModeTest {
    @Test
    fun policiesHaveStableSourceOrder() {
        assertEquals(
            listOf(LyricSource.AMLL_TTML, LyricSource.NCM_BACKEND),
            LyricSourceMode.TTML_PREFERRED.sourceOrder,
        )
        assertEquals(
            listOf(LyricSource.NCM_BACKEND, LyricSource.AMLL_TTML),
            LyricSourceMode.BACKEND_PREFERRED.sourceOrder,
        )
        assertEquals(listOf(LyricSource.AMLL_TTML), LyricSourceMode.TTML_ONLY.sourceOrder)
        assertEquals(listOf(LyricSource.NCM_BACKEND), LyricSourceMode.BACKEND_ONLY.sourceOrder)
    }

    @Test
    fun genericDecoderUsesTtmlPreferredDefault() {
        assertNull(LyricSourceMode.fromWireValueOrNull("future-mode"))
        assertEquals(
            LyricSourceMode.TTML_PREFERRED,
            LyricSourceMode.fromWireValue("future-mode"),
        )
    }

    @Test
    fun unknownStoredValueFailsClosedToBackendOnly() {
        assertEquals(
            LyricSourceMode.BACKEND_ONLY,
            LyricSourceMode.fromStoredWireValue("future-mode"),
        )
        assertEquals(
            LyricSourceMode.TTML_PREFERRED,
            LyricSourceMode.fromStoredWireValue(LyricSourceMode.DEFAULT.wireValue),
        )
    }
}

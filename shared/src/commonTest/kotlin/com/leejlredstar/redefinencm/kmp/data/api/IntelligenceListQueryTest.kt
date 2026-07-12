package com.leejlredstar.redefinencm.kmp.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IntelligenceListQueryTest {
    @Test
    fun mapsRequiredParametersAndOmitsAbsentStartSong() {
        assertEquals(
            listOf(
                "id" to "33894312",
                "pid" to "24381616",
            ),
            intelligenceListQueryParameters(
                id = 33_894_312,
                pid = 24_381_616,
                sid = null,
            ),
        )
    }

    @Test
    fun mapsOptionalStartSong() {
        assertEquals(
            listOf(
                "id" to "33894312",
                "pid" to "24381616",
                "sid" to "36871368",
            ),
            intelligenceListQueryParameters(
                id = 33_894_312,
                pid = 24_381_616,
                sid = 36_871_368,
            ),
        )
    }

    @Test
    fun rejectsNonPositiveIdentifiers() {
        assertFailsWith<IllegalArgumentException> {
            intelligenceListQueryParameters(id = 0, pid = 1, sid = null)
        }
        assertFailsWith<IllegalArgumentException> {
            intelligenceListQueryParameters(id = 1, pid = -1, sid = null)
        }
        assertFailsWith<IllegalArgumentException> {
            intelligenceListQueryParameters(id = 1, pid = 2, sid = 0)
        }
    }
}

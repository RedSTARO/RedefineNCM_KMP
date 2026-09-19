package com.leejlredstar.redefinencm.kmp.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseDateTest {
    @Test
    fun readsTheDateAtChinaTime() {
        // 2020-08-31T16:00:00Z is midnight on 1 September in China, which is the release date.
        assertEquals("2020-09-01", formatReleaseDate(1_598_889_600_000L))
    }

    @Test
    fun handlesLeapDaysAndYearEnds() {
        assertEquals("2024-02-29", formatReleaseDate(1_709_136_000_000L))
        assertEquals("1999-12-31", formatReleaseDate(946_569_600_000L))
        assertEquals("1970-01-01", formatReleaseDate(1L))
    }

    @Test
    fun anUnknownTimeHasNoDate() {
        assertEquals("", formatReleaseDate(0L))
    }
}

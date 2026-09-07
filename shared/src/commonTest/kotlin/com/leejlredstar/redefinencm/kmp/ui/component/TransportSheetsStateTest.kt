package com.leejlredstar.redefinencm.kmp.ui.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransportSheetsStateTest {

    @Test
    fun startsWithBothSheetsClosed() {
        val state = TransportSheetsState()
        assertFalse(state.showQueue)
        assertFalse(state.showComments)
        assertFalse(state.anyOpen)
    }

    @Test
    fun openingOneSheetClosesTheOther() {
        val state = TransportSheetsState()
        state.openQueue()
        assertTrue(state.showQueue)
        assertFalse(state.showComments)

        state.openComments()
        assertFalse(state.showQueue)
        assertTrue(state.showComments)
    }

    @Test
    fun anyOpenTracksEitherSheet() {
        val state = TransportSheetsState()
        state.openQueue()
        assertTrue(state.anyOpen)
        state.openComments()
        assertTrue(state.anyOpen)
        state.dismiss()
        assertFalse(state.anyOpen)
    }

    @Test
    fun dismissClosesWhicheverSheetIsUp() {
        val state = TransportSheetsState()
        state.openComments()
        state.dismiss()
        assertFalse(state.showQueue)
        assertFalse(state.showComments)
    }
}

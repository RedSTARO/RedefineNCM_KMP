package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EditorContinuityTest {
    @Test
    fun onlyCurrentTextAndSelectionAreSaved() {
        val state = TextFieldState("previous draft")
        state.setTextAndPlaceCursorAtEnd("current draft")
        state.edit { selection = TextRange(9, 1) }

        val saved = assertNotNull(with(EditorTextSaver) { SaverScope { true }.save(state) })
        assertEquals(listOf("current draft", 9, 1), saved)
        val restored = assertNotNull(EditorTextSaver.restore(saved))
        assertEquals("current draft", restored.text.toString())
        assertEquals(TextRange(9, 1), restored.selection)
    }

    @Test
    fun oversizedPastesDoNotExpandTheBundleOrSplitASurrogatePair() {
        val prefix = "a".repeat(MaxSavedEditorLength - 1)
        val state = TextFieldState(prefix + "\uD83C\uDFB5")

        val saved = assertNotNull(with(EditorTextSaver) { SaverScope { true }.save(state) })
        val restored = assertNotNull(EditorTextSaver.restore(saved))
        assertEquals(prefix, restored.text.toString())
        assertEquals(TextRange(prefix.length), restored.selection)
    }
}

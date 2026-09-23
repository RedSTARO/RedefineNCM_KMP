package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.util.DEFAULT_SETTINGS_NODE
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import kotlinx.coroutines.test.runTest
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

/** A slot's compare-and-write only lands while the stored value is the one it was given. */
class CredentialSlotTest {
    @Test
    fun replaceWritesOnlyWhileTheStoredValueIsTheExpectedOne() = runTest {
        val nodeName = "$DEFAULT_SETTINGS_NODE.test.credential-slot"
        Preferences.userRoot().node(nodeName).removeNode()
        try {
            val slot = QQCredentialSlot(PlatformSettings(nodeName = nodeName))
            slot.write("musicid=1; musickey=a").getOrThrow()

            assertEquals(false, slot.replace(expected = "musicid=1; musickey=b", credential = "x").getOrThrow())
            assertEquals("musicid=1; musickey=a", slot.read())

            assertEquals(true, slot.replace(expected = "musicid=1; musickey=a", credential = "x").getOrThrow())
            assertEquals("x", slot.read())
        } finally {
            Preferences.userRoot().node(nodeName).removeNode()
        }
    }
}

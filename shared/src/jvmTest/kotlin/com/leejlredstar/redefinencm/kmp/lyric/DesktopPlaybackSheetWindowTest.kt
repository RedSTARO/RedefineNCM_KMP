package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The transport console moved into `player.html`, so the only Compose window left over the
 * WebView hosts the queue and comment sheets. These cover the sizing that window still owns and
 * the parsing of the single bridge call the in-page console drives.
 */
class DesktopPlaybackSheetWindowTest {
    @Test
    fun usesTheSheetBoundsWhenTheOwnerIsLargeEnough() {
        assertEquals(
            DpSize(840.dp, 680.dp),
            desktopPlaybackSheetWindowSize(
                availableWidth = 1_280.dp,
                availableHeight = 820.dp,
            ),
        )
    }

    @Test
    fun clampsToTheAvailableOwnerContent() {
        assertEquals(
            DpSize(268.dp, 64.dp),
            desktopPlaybackSheetWindowSize(
                availableWidth = 300.dp,
                availableHeight = 80.dp,
            ),
        )
        assertEquals(
            DpSize.Zero,
            desktopPlaybackSheetWindowSize(
                availableWidth = 24.dp,
                availableHeight = 8.dp,
            ),
        )
    }

    @Test
    fun readsTheActionAndOptionalValueFromABoundCall() {
        assertEquals("playPause" to null, parseAmllTransportRequest("""["playPause"]"""))
        assertEquals("next" to null, parseAmllTransportRequest("""["next"]"""))
        assertEquals("seek" to 48_100L, parseAmllTransportRequest("""["seek",48100]"""))
        assertEquals(
            "seek" to 48_100L,
            parseAmllTransportRequest("""{"action":"seek","value":48100}"""),
        )
    }

    @Test
    fun yieldsAnEmptyActionForUnusableRequests() {
        assertEquals("" to null, parseAmllTransportRequest(null))
        assertEquals("" to null, parseAmllTransportRequest("   "))
        assertEquals("" to null, parseAmllTransportRequest("not json"))
        assertEquals("" to null, parseAmllTransportRequest("[]"))
    }

    @Test
    fun keepsANonNumericSeekValueOutOfTheHostCall() {
        // The host only seeks when the parsed value is non-null, so a malformed payload must not
        // arrive as 0 and jump the track back to its start.
        assertNull(parseAmllTransportRequest("""["seek","abc"]""").second)
    }
}

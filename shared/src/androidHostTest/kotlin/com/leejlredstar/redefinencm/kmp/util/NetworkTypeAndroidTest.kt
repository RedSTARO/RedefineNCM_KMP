package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** Wi-Fi is Wi-Fi under a VPN; a network that hides its transport falls back to metered. */
class NetworkTypeAndroidTest {
    @Test
    fun theTransportDecidesAndMeteredOnlyFillsIn() {
        assertEquals(NetworkType.CELLULAR, networkTypeFor(cellular = true, wifiOrEthernet = false, metered = true))
        assertEquals(NetworkType.WIFI, networkTypeFor(cellular = false, wifiOrEthernet = true, metered = false))
        // A VPN app that leaves itself metered, over home Wi-Fi.
        assertEquals(NetworkType.WIFI, networkTypeFor(cellular = false, wifiOrEthernet = true, metered = true))
        // A VPN that names no transport beneath it.
        assertEquals(NetworkType.CELLULAR, networkTypeFor(cellular = false, wifiOrEthernet = false, metered = true))
        assertEquals(NetworkType.WIFI, networkTypeFor(cellular = false, wifiOrEthernet = false, metered = false))
    }
}

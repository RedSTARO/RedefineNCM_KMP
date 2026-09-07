package com.leejlredstar.redefinencm.kmp

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopOsTest {

    @Test
    fun classifiesTheHostNamesJvmsActuallyReport() {
        assertEquals(DesktopOs.Windows, DesktopOs.of("Windows 11"))
        assertEquals(DesktopOs.Windows, DesktopOs.of("Windows Server 2022"))
        assertEquals(DesktopOs.Linux, DesktopOs.of("Linux"))
        assertEquals(DesktopOs.MacOs, DesktopOs.of("Mac OS X"))
        // Apple JVMs have reported both spellings.
        assertEquals(DesktopOs.MacOs, DesktopOs.of("Darwin"))
    }

    @Test
    fun matchesRegardlessOfCase() {
        assertEquals(DesktopOs.Windows, DesktopOs.of("windows 10"))
        assertEquals(DesktopOs.Linux, DesktopOs.of("LINUX"))
    }

    @Test
    fun anUnknownHostGetsNoNativeIntegration() {
        assertEquals(DesktopOs.Other, DesktopOs.of("FreeBSD"))
        assertEquals(DesktopOs.Other, DesktopOs.of("SunOS"))
        assertEquals(DesktopOs.Other, DesktopOs.of(""))
    }

    @Test
    fun classifiesTheArchitecturesWeShipNativesFor() {
        assertEquals(DesktopArch.X64, DesktopArch.of("amd64"))
        assertEquals(DesktopArch.X64, DesktopArch.of("x86_64"))
        assertEquals(DesktopArch.Arm64, DesktopArch.of("aarch64"))
        assertEquals(DesktopArch.Arm64, DesktopArch.of("ARM64"))
        assertEquals(DesktopArch.Other, DesktopArch.of("x86"))
    }
}

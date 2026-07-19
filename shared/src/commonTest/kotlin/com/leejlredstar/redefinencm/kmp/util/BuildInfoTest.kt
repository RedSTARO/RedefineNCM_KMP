package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildInfoTest {
    @Test
    fun canonicalProductVersionUsesAndroidTagHashFormat() {
        assertEquals("${BuildInfo.BASE_TAG}.${BuildInfo.COMMIT_HASH}", BuildInfo.VERSION_NAME)
        assertTrue(Regex("^v\\d+\\.\\d+\\.\\d+\\.[0-9a-f]{8}$").matches(BuildInfo.VERSION_NAME))
    }

    @Test
    fun nativePackageVersionIsNumericAndTracksTheSameBuild() {
        val semanticComponents = BuildInfo.BASE_VERSION.split('.').map(String::toLong)
        val packageComponents = BuildInfo.NATIVE_PACKAGE_VERSION.split('.').map(String::toLong)

        assertEquals(3, semanticComponents.size)
        assertEquals(3, packageComponents.size)
        assertEquals(semanticComponents[0] + 1, packageComponents[0])
        assertEquals(semanticComponents[1], packageComponents[1])
        assertEquals(BuildInfo.VERSION_CODE.toLong() + semanticComponents[2], packageComponents[2])
        assertTrue(packageComponents[0] in 1..255)
        assertTrue(packageComponents[1] in 0..255)
        assertTrue(packageComponents[2] in 1..65_535)
    }
}

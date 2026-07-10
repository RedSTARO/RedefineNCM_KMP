package com.leejlredstar.redefinencm.kmp

import com.leejlredstar.redefinencm.kmp.util.requiresLegacyDownloadWritePermission
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedLogicAndroidHostTest {

    @Test
    fun legacyPublicDownloadPermissionStopsAtAndroidTen() {
        assertTrue(requiresLegacyDownloadWritePermission(sdkInt = 24, permissionGranted = false))
        assertTrue(requiresLegacyDownloadWritePermission(sdkInt = 28, permissionGranted = false))
        assertFalse(requiresLegacyDownloadWritePermission(sdkInt = 28, permissionGranted = true))
        assertFalse(requiresLegacyDownloadWritePermission(sdkInt = 29, permissionGranted = false))
    }
}

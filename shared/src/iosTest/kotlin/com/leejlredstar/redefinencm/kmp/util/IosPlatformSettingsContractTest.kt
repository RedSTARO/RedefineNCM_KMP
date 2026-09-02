package com.leejlredstar.redefinencm.kmp.util

import platform.Foundation.NSUserDefaults

/**
 * Runs the shared contract against a throwaway NSUserDefaults suite.
 *
 * iOS is the target whose storage differs most — booleans are stored as native values while
 * longs round-trip through a string — so it is the one this contract most needs to cover.
 */
class IosPlatformSettingsContractTest : PlatformSettingsContract() {

    override fun newSettings(): PlatformSettings {
        val suiteName = "com.leejlredstar.redefinencm.kmp.test.${nextSuite++}"
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suiteName)
        return PlatformSettings(suiteName = suiteName)
    }

    private companion object {
        var nextSuite = 0L
    }
}

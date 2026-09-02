package com.leejlredstar.redefinencm.kmp.util

/**
 * Runs the shared contract against a throwaway corner of localStorage.
 *
 * The browser store is a single flat namespace shared with the running app, so the production
 * key prefix is a defaulted constructor parameter and the test uses a unique one.
 */
class WebPlatformSettingsContractTest : PlatformSettingsContract() {

    override fun newSettings(): PlatformSettings =
        PlatformSettings(keyPrefix = "test.${nextPrefix++}.")

    private companion object {
        var nextPrefix = 0L
    }
}

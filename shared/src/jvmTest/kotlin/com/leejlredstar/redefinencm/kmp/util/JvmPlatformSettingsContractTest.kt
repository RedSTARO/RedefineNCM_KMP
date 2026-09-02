package com.leejlredstar.redefinencm.kmp.util

import java.util.prefs.Preferences
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Runs the shared contract against a throwaway `java.util.prefs` node.
 *
 * A fixed node would mean the test suite writes into the developer's real user preferences,
 * which is why the production node name is a defaulted constructor parameter.
 */
@OptIn(ExperimentalAtomicApi::class)
class JvmPlatformSettingsContractTest : PlatformSettingsContract() {

    override fun newSettings(): PlatformSettings {
        val nodeName = "$DEFAULT_SETTINGS_NODE.test.${nextNode.fetchAndAdd(1L)}"
        Preferences.userRoot().node(nodeName).removeNode()
        return PlatformSettings(nodeName = nodeName)
    }

    private companion object {
        val nextNode = AtomicLong(0L)
    }
}

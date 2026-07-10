package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerTest {
    @Test
    fun onlyStrictlyNewerReleaseIsReported() {
        assertTrue(isNewerReleaseVersion("v1.3.0", "v1.2.9"))
        assertTrue(isNewerReleaseVersion("v2.0.0", "v1.99.99"))
        assertFalse(isNewerReleaseVersion("v1.2.3", "v1.2.3"))
        assertFalse(isNewerReleaseVersion("v1.2.2", "v1.2.3"))
    }

    @Test
    fun semanticPrereleaseOrderingIsRespected() {
        assertTrue(isNewerReleaseVersion("v1.0.0", "v1.0.0-rc.1"))
        assertTrue(isNewerReleaseVersion("v1.0.0-rc.2", "v1.0.0-rc.1"))
        assertFalse(isNewerReleaseVersion("v1.0.0-beta", "v1.0.0"))
    }

    @Test
    fun malformedTagsDoNotProduceUpdatePrompts() {
        assertFalse(isNewerReleaseVersion("latest", "v1.0.0"))
        assertFalse(isNewerReleaseVersion("v1.0", "v0.9.0"))
    }
}

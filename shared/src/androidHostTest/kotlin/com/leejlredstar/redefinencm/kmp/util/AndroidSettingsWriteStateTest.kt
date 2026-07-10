package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidSettingsWriteStateTest {

    @Test
    fun failedOlderDuplicateWriteCannotRollBackNewerWrite() {
        val durable = "old"
        val first = beginAndroidSettingsWrite(
            current = AndroidSettingsCacheState(latestRevision = 0L, cachedValue = durable),
            revision = 1L,
            value = "A",
        )
        val second = beginAndroidSettingsWrite(first, revision = 2L, value = "A")

        val afterFirstFailure = reduceAndroidSettingsWriteResult(
            current = second,
            commandRevision = 1L,
            commandValue = "A",
            durableValue = durable,
            succeeded = false,
        )

        assertEquals(2L, afterFirstFailure.latestRevision)
        assertEquals("A", afterFirstFailure.cachedValue)

        val afterSecondSuccess = reduceAndroidSettingsWriteResult(
            current = afterFirstFailure,
            commandRevision = 2L,
            commandValue = "A",
            durableValue = "A",
            succeeded = true,
        )

        assertEquals(2L, afterSecondSuccess.latestRevision)
        assertEquals("A", afterSecondSuccess.cachedValue)
    }

    @Test
    fun latestFailureRestoresLastDurableValue() {
        val pending = beginAndroidSettingsWrite(
            current = AndroidSettingsCacheState(latestRevision = 4L, cachedValue = "old"),
            revision = 5L,
            value = "new",
        )

        val failed = reduceAndroidSettingsWriteResult(
            current = pending,
            commandRevision = 5L,
            commandValue = "new",
            durableValue = "old",
            succeeded = false,
        )

        assertEquals("old", failed.cachedValue)
    }

    @Test
    fun latestSuccessRepairsUnexpectedCacheDrift() {
        val drifted = AndroidSettingsCacheState(latestRevision = 9L, cachedValue = "old")

        val succeeded = reduceAndroidSettingsWriteResult(
            current = drifted,
            commandRevision = 9L,
            commandValue = "new",
            durableValue = "new",
            succeeded = true,
        )

        assertEquals("new", succeeded.cachedValue)
    }
}

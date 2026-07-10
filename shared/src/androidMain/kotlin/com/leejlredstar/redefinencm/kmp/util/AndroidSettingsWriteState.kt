package com.leejlredstar.redefinencm.kmp.util

/**
 * Per-key optimistic-cache state used by [PlatformSettings].
 *
 * The revision identifies the newest write requested by the caller. A completed older write must
 * never change the cache, even when it carries the same value as the newest write.
 */
internal data class AndroidSettingsCacheState(
    val latestRevision: Long,
    val cachedValue: Any?,
)

internal fun beginAndroidSettingsWrite(
    current: AndroidSettingsCacheState?,
    revision: Long,
    value: Any,
): AndroidSettingsCacheState {
    require(current == null || revision > current.latestRevision) {
        "Settings write revisions must increase"
    }
    return AndroidSettingsCacheState(
        latestRevision = revision,
        cachedValue = value,
    )
}

/**
 * Resolves one persistence result against the newest in-process write for the same key.
 *
 * A stale completion is ignored. The latest successful completion reasserts its value in the
 * cache; the latest failed completion restores the last value known to be durable.
 */
internal fun reduceAndroidSettingsWriteResult(
    current: AndroidSettingsCacheState,
    commandRevision: Long,
    commandValue: Any,
    durableValue: Any?,
    succeeded: Boolean,
): AndroidSettingsCacheState {
    if (commandRevision != current.latestRevision) return current
    return current.copy(
        cachedValue = if (succeeded) commandValue else durableValue,
    )
}

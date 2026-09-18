package com.leejlredstar.redefinencm.kmp.lyric

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LyricSource {
    AMLL_TTML,
    NCM_BACKEND,
}

/**
 * Persisted lyric-source policy.
 *
 * [wireValue] is deliberately independent from the enum name so future source additions do not
 * turn a Kotlin refactor into a settings migration.
 */
enum class LyricSourceMode(
    val wireValue: String,
    val displayName: String,
    val sourceOrder: List<LyricSource>,
) {
    TTML_PREFERRED(
        wireValue = "ttml_preferred",
        displayName = "优先用 AMLL 歌词库（找不到时用网易云）",
        sourceOrder = listOf(LyricSource.AMLL_TTML, LyricSource.NCM_BACKEND),
    ),
    BACKEND_PREFERRED(
        wireValue = "backend_preferred",
        displayName = "优先用网易云歌词（找不到时用 AMLL 歌词库）",
        sourceOrder = listOf(LyricSource.NCM_BACKEND, LyricSource.AMLL_TTML),
    ),
    TTML_ONLY(
        wireValue = "ttml_only",
        displayName = "只用 AMLL 歌词库",
        sourceOrder = listOf(LyricSource.AMLL_TTML),
    ),
    BACKEND_ONLY(
        wireValue = "backend_only",
        displayName = "只用网易云歌词",
        sourceOrder = listOf(LyricSource.NCM_BACKEND),
    );

    override fun toString(): String = displayName

    companion object {
        val DEFAULT = TTML_PREFERRED

        fun fromWireValueOrNull(value: String?): LyricSourceMode? =
            entries.firstOrNull { it.wireValue == value }

        fun fromWireValue(value: String?): LyricSourceMode =
            fromWireValueOrNull(value) ?: DEFAULT

        /**
         * Decodes an already-read persisted value. Callers pass [DEFAULT]'s wire value as the
         * storage getter default, so an unknown non-empty value means corrupt or newer state and
         * must fail closed without contacting a third-party source.
         */
        fun fromStoredWireValue(value: String?): LyricSourceMode =
            fromWireValueOrNull(value) ?: BACKEND_ONLY
    }
}

/**
 * Holds lyric-source selection until the persisted value is known.
 *
 * A restored player can expose its current song before asynchronous settings have loaded. Waiting
 * on this gate prevents the default TTML-first policy from leaking a song ID when the persisted
 * policy is [LyricSourceMode.BACKEND_ONLY]. An explicit update made while loading wins over the
 * older persisted snapshot.
 */
internal class LyricSourceModeGate(
    initialMode: LyricSourceMode = LyricSourceMode.DEFAULT,
) {
    private val ready = CompletableDeferred<Unit>()
    private val mutableMode = MutableStateFlow(initialMode)
    private var explicitUpdateCount = 0L

    val mode: StateFlow<LyricSourceMode> = mutableMode.asStateFlow()

    fun completeInitialLoad(storedMode: LyricSourceMode) {
        if (!ready.isCompleted && explicitUpdateCount == 0L) {
            mutableMode.value = storedMode
        }
        ready.complete(Unit)
    }

    fun failInitialLoad() {
        completeInitialLoad(LyricSourceMode.BACKEND_ONLY)
    }

    fun update(mode: LyricSourceMode): Boolean {
        explicitUpdateCount += 1
        if (mutableMode.value == mode) return false
        mutableMode.value = mode
        return true
    }

    suspend fun awaitMode(): LyricSourceMode {
        ready.await()
        return mutableMode.value
    }

    /**
     * Returns the selected mode only after the persisted privacy choice is known.
     *
     * Callers use this synchronous snapshot to reuse an in-memory lyric without briefly clearing
     * the current surface. Returning null while loading prevents the default TTML-first value from
     * being treated as the user's persisted choice.
     */
    fun currentModeOrNull(): LyricSourceMode? =
        mutableMode.value.takeIf { ready.isCompleted }
}

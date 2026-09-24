package com.leejlredstar.redefinencm.kmp.data.local

import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.getBooleanAsync
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The device-local account: no service behind it and nothing to sign in to. It owns whatever the
 * app keeps only on this device (local playlists and local favourites), so those have a home on
 * the accounts page beside the online accounts.
 *
 * It can be switched off. Off hides its library and its hearts everywhere; what it holds stays
 * stored and still travels with the settings backup, so switching it on again shows it unchanged.
 */
class LocalAccount(private val settings: PlatformSettings) {
    private val writes = Mutex()

    /** Null until the stored switch has been read once. */
    private val enabledState = MutableStateFlow<Boolean?>(null)

    suspend fun name(): String = storedName().ifBlank { DefaultName }

    /** The name the user gave the account, or blank while it keeps the default. */
    suspend fun storedName(): String =
        settings.getStringAsync(SettingKeys.LOCAL_ACCOUNT_NAME, "").trim()

    /** Renames the account; a blank name falls back to [DefaultName]. */
    suspend fun rename(name: String): Result<Unit> {
        settings.awaitLoaded()
        val value = name.trim()
        val previous = settings.getString(SettingKeys.LOCAL_ACCOUNT_NAME, "")
        return withContext(NonCancellable) {
            try {
                settings.setString(SettingKeys.LOCAL_ACCOUNT_NAME, value)
                settings.flush()
                Result.success(Unit)
            } catch (failure: Exception) {
                settings.setString(SettingKeys.LOCAL_ACCOUNT_NAME, previous)
                runCatching { settings.flush() }
                Result.failure(failure)
            }
        }
    }

    /** Whether the account is switched on, as stored. */
    suspend fun isEnabled(): Boolean = settings.getBooleanAsync(
        SettingKeys.LOCAL_ACCOUNT_ENABLED,
        SettingKeys.LOCAL_ACCOUNT_ENABLED_DEFAULT,
    )

    /**
     * The stored switch, then every change to it. Only values read from settings or written
     * through [setEnabled] are emitted, so a collector never acts on a default nobody chose.
     */
    fun enabledUpdates(): Flow<Boolean> = flow {
        if (enabledState.value == null) {
            // A write that landed while this read was in flight is newer; it wins.
            enabledState.compareAndSet(null, isEnabled())
        }
        emitAll(enabledState.filterNotNull())
    }

    /** Switches the account on or off. Nothing it holds is deleted either way. */
    suspend fun setEnabled(enabled: Boolean): Result<Unit> = writes.withLock {
        settings.awaitLoaded()
        val previous = settings.getBoolean(
            SettingKeys.LOCAL_ACCOUNT_ENABLED,
            SettingKeys.LOCAL_ACCOUNT_ENABLED_DEFAULT,
        )
        withContext(NonCancellable) {
            try {
                settings.setBoolean(SettingKeys.LOCAL_ACCOUNT_ENABLED, enabled)
                settings.flush()
                enabledState.value = enabled
                Result.success(Unit)
            } catch (failure: Exception) {
                settings.setBoolean(SettingKeys.LOCAL_ACCOUNT_ENABLED, previous)
                runCatching { settings.flush() }
                Result.failure(failure)
            }
        }
    }

    /**
     * Reads the switch again after something wrote settings without going through this class:
     * importing a backup.
     */
    suspend fun reload() {
        writes.withLock { enabledState.value = isEnabled() }
    }

    companion object {
        val DefaultName: String get() = strings.localAccountDefaultName
    }
}

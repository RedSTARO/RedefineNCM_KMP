package com.leejlredstar.redefinencm.kmp.data.local

import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The device-local account: no service behind it and nothing to sign in to. It owns whatever the
 * app keeps only on this device (local playlists and local favourites), so those have a home on
 * the accounts page beside the online accounts.
 */
class LocalAccount(private val settings: PlatformSettings) {
    suspend fun name(): String =
        settings.getStringAsync(SettingKeys.LOCAL_ACCOUNT_NAME, "").ifBlank { DefaultName }

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

    companion object {
        const val DefaultName = "本地账号"
    }
}

package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Where one provider's signed-in credential lives, and what has to happen when it changes.
 *
 * Login methods produce credentials; slots persist them. The split matters because persistence is
 * provider-specific in ways a login method should not know: NetEase's cookie change has to drop
 * the cached account identity and restart account work, QQ's does not. Every login method for a
 * provider writes through its one slot, so those rules are applied once whatever the method.
 */
interface ProviderCredentialSlot {
    val provider: MusicProviderId

    suspend fun read(): String

    /**
     * Persists [credential] durably and applies the provider's change rules.
     *
     * On failure the previous value is restored before the failure is returned, so a half-written
     * credential never survives to the next request.
     */
    suspend fun write(credential: String): Result<Unit>

    suspend fun clear(): Result<Unit> = write("")
}

/** The registered slots, one per provider. */
class CredentialStore(slots: List<ProviderCredentialSlot>) {
    private val byProvider: Map<MusicProviderId, ProviderCredentialSlot> =
        slots.associateBy { it.provider }

    init {
        require(byProvider.size == slots.size) { "more than one credential slot for a provider" }
    }

    operator fun get(provider: MusicProviderId): ProviderCredentialSlot? = byProvider[provider]

    fun require(provider: MusicProviderId): ProviderCredentialSlot =
        byProvider[provider] ?: error("no credential slot registered for ${provider.key}")
}

/**
 * NetEase's cookie.
 *
 * The identity binding (UID and the cookie fingerprint it was resolved for) is cleared before the
 * credential changes, so a process dying between the individual writes can never start up
 * trusting the old UID for the new cookie. [onChanged] runs after a durable write; the DI module
 * points it at the main view model's account refresh.
 */
class NeteaseCredentialSlot(
    private val settings: PlatformSettings,
    private val onChanged: () -> Unit,
) : ProviderCredentialSlot {
    override val provider: MusicProviderId = MusicProviderId.NETEASE

    override suspend fun read(): String = settings.getStringAsync(SettingKeys.COOKIE, "")

    override suspend fun write(credential: String): Result<Unit> {
        settings.awaitLoaded()
        val previousCookie = settings.getString(SettingKeys.COOKIE, "")
        val previousUid = settings.getLong(SettingKeys.UID, 0L)
        val previousFingerprint = settings.getLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
        return withContext(NonCancellable) {
            try {
                settings.setLong(SettingKeys.UID, 0L)
                settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
                settings.setString(SettingKeys.COOKIE, credential)
                settings.flush()
                onChanged()
                Result.success(Unit)
            } catch (failure: Exception) {
                // Setters update the process cache synchronously. Roll all three back so a failed
                // commit cannot leave requests using an unpersisted credential.
                settings.setLong(SettingKeys.UID, previousUid)
                settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, previousFingerprint)
                settings.setString(SettingKeys.COOKIE, previousCookie)
                runCatching { settings.flush() }
                Result.failure(failure)
            }
        }
    }
}

/**
 * QQ Music's gateway credential, stored as the `Cookie` header value the gateway reads
 * (`musicid=…; musickey=…; …`, see [QQCredential]). Nothing else in the app is keyed by the QQ
 * account yet, so a change has no cache to invalidate.
 */
class QQCredentialSlot(private val settings: PlatformSettings) : ProviderCredentialSlot {
    override val provider: MusicProviderId = MusicProviderId.QQ

    override suspend fun read(): String = settings.getStringAsync(SettingKeys.QQ_COOKIE, "")

    override suspend fun write(credential: String): Result<Unit> {
        settings.awaitLoaded()
        val previous = settings.getString(SettingKeys.QQ_COOKIE, "")
        return withContext(NonCancellable) {
            try {
                settings.setString(SettingKeys.QQ_COOKIE, credential)
                settings.flush()
                Result.success(Unit)
            } catch (failure: Exception) {
                settings.setString(SettingKeys.QQ_COOKIE, previous)
                runCatching { settings.flush() }
                Result.failure(failure)
            }
        }
    }
}

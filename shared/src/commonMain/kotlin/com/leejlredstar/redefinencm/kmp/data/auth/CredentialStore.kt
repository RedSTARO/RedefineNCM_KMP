package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
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

    /**
     * Writes [credential] only while the stored value is still [expected], atomically with respect
     * to every other write through this slot.
     *
     * Background writers use this: a key renewal that raced a sign-out or an account switch must
     * not bring the old account back. `Result.success(false)` means the stored value had moved on
     * and nothing was written.
     */
    suspend fun replace(expected: String, credential: String): Result<Boolean>

    suspend fun clear(): Result<Unit> = write("")

    /**
     * The stored credential now, then after every write through this slot — a sign-in on the
     * login page, a sign-out on the accounts page, a renewal in the background. Pages follow this
     * instead of keeping their own copy.
     */
    fun credentialUpdates(): Flow<String>

    /**
     * Whether [credential] is a signed-in account for this provider. The one definition the
     * accounts page, the settings summary, the login page and the startup check all use.
     */
    fun isSignedIn(credential: String): Boolean = credential.isNotBlank()
}

/**
 * A slot persisted under one settings key.
 *
 * Writes are serialized, so the comparison in [replace] and the write that follows it cannot
 * interleave with a sign-out running on another thread.
 */
abstract class SettingsCredentialSlot(
    protected val settings: PlatformSettings,
    private val key: String,
) : ProviderCredentialSlot {
    private val writes = Mutex()

    /** Null until the stored value has been read once. */
    private val current = MutableStateFlow<String?>(null)

    override suspend fun read(): String = settings.getStringAsync(key, "")

    override suspend fun write(credential: String): Result<Unit> = writes.withLock {
        settings.awaitLoaded()
        persist(credential).onSuccess { current.value = credential }
    }

    override suspend fun replace(expected: String, credential: String): Result<Boolean> =
        writes.withLock {
            settings.awaitLoaded()
            if (settings.getString(key, "") != expected) return@withLock Result.success(false)
            persist(credential).onSuccess { current.value = credential }.map { true }
        }

    override fun credentialUpdates(): Flow<String> = flow {
        if (current.value == null) {
            // A write that landed while this read was in flight is newer; it wins.
            current.compareAndSet(null, read())
        }
        emitAll(current.filterNotNull())
    }

    /** Stores [credential] and applies the provider's change rules; runs under the write lock. */
    protected abstract suspend fun persist(credential: String): Result<Unit>
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
    settings: PlatformSettings,
    private val onChanged: () -> Unit,
) : SettingsCredentialSlot(settings, SettingKeys.COOKIE) {
    override val provider: MusicProviderId = MusicProviderId.NETEASE

    override suspend fun persist(credential: String): Result<Unit> {
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
class QQCredentialSlot(settings: PlatformSettings) :
    SettingsCredentialSlot(settings, SettingKeys.QQ_COOKIE) {
    override val provider: MusicProviderId = MusicProviderId.QQ

    /** Only a credential the gateway can use — `musicid` with `musickey` — is an account. */
    override fun isSignedIn(credential: String): Boolean = QQCredential.parse(credential) != null

    override suspend fun persist(credential: String): Result<Unit> {
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

package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.auth.AccountIdentity
import com.leejlredstar.redefinencm.kmp.data.auth.CredentialTextLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptor
import com.leejlredstar.redefinencm.kmp.data.auth.ServerCheckResult
import com.leejlredstar.redefinencm.kmp.data.local.LocalAccount
import com.leejlredstar.redefinencm.kmp.data.provider.LibraryAggregationMode
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderRegistration
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderRegistrations
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.getBooleanAsync
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Every account the app holds and every provider's configuration, for the accounts page and the
 * settings page's summary of it.
 *
 * Nothing here names a provider: the page is built from [ProviderRegistrations], each provider's
 * signed-in state comes from its credential slot, and its name from its identity source. A
 * credential written anywhere — the login page, a background key renewal — reaches this through
 * the slot, so no page keeps a copy that can go stale.
 */
class AccountsViewModel(
    private val registrations: ProviderRegistrations,
    private val settings: PlatformSettings,
    private val localAccount: LocalAccount,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** One provider's account and configuration, as the accounts page shows it. */
    data class ProviderAccount(
        val registration: ProviderRegistration,
        /** Always true for a provider without a switch. */
        val enabled: Boolean,
        /** The stored backend address; null for a provider without one. */
        val server: String?,
        /** The stored credential, for the pasted-credential field. */
        val credential: String,
        val signedIn: Boolean,
        val identity: AccountIdentity?,
        /** The last check of the backend address, until the address changes. */
        val serverCheck: ServerCheckResult?,
        val checkingServer: Boolean,
    ) {
        val provider: MusicProviderId get() = registration.id
        val descriptor: ProviderLoginDescriptor get() = registration.descriptor

        /** The pasted-credential method, where this platform can sign in at all. */
        val textMethod: CredentialTextLoginMethod?
            get() = registration.loginMethods
                .filterIsInstance<CredentialTextLoginMethod>()
                .firstOrNull()
                ?.takeIf { registration.canSignIn }
    }

    private data class Config(val enabled: Boolean, val server: String?)

    private val configs = MutableStateFlow<Map<MusicProviderId, Config>>(emptyMap())
    private val checks = MutableStateFlow<Map<MusicProviderId, ServerCheckResult>>(emptyMap())
    private val checking = MutableStateFlow<Set<MusicProviderId>>(emptySet())

    private val _localName = MutableStateFlow(LocalAccount.DefaultName)
    val localName: StateFlow<String> = _localName.asStateFlow()

    private val _aggregationMode = MutableStateFlow(LibraryAggregationMode.Default)
    val aggregationMode: StateFlow<LibraryAggregationMode> = _aggregationMode.asStateFlow()

    private val _mergeSameSongs = MutableStateFlow(SettingKeys.MERGE_SAME_SONGS_DEFAULT)
    val mergeSameSongs: StateFlow<Boolean> = _mergeSameSongs.asStateFlow()

    /** The outcome of the last action, for the page's snackbar; consumed once shown. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val accounts: StateFlow<List<ProviderAccount>>

    /** The settings page's line under "账号与平台". */
    val summary: StateFlow<String>

    init {
        val signIns: List<Flow<Pair<String, AccountIdentity?>>> = registrations.all.map { registration ->
            val credential = registration.credentialSlot.credentialUpdates()
                .shareIn(scope, SharingStarted.Eagerly, replay = 1)
            combine(
                credential,
                registration.identity.identities(credential).onStart { emit(null) },
            ) { stored, identity -> stored to identity }
        }
        accounts = combine(
            combine(signIns) { it.toList() },
            configs,
            checks,
            checking,
        ) { credentials, configs, checks, checking ->
            registrations.all.mapIndexed { index, registration ->
                val (credential, identity) = credentials[index]
                val config = configs[registration.id]
                val signedIn = registration.holdsAccount(credential)
                ProviderAccount(
                    registration = registration,
                    enabled = config?.enabled ?: (registration.descriptor.enabledSetting?.default ?: true),
                    server = config?.server ?: registration.descriptor.server?.default,
                    credential = credential,
                    signedIn = signedIn,
                    identity = identity.takeIf { signedIn },
                    serverCheck = checks[registration.id],
                    checkingServer = registration.id in checking,
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
        summary = combine(accounts, localName) { accounts, localName ->
            accountsSummaryLine(
                accounts.map { account ->
                    AccountSummaryEntry(
                        providerName = account.provider.displayName,
                        enabled = account.enabled,
                        signedIn = account.signedIn,
                        accountName = account.identity?.name,
                    )
                },
                localName,
            )
        }.stateIn(scope, SharingStarted.Eagerly, "")
        reload()
    }

    /**
     * Re-reads the switches, addresses, local name and view preference from settings. The accounts
     * page calls it on opening, the settings page after importing a backup.
     */
    fun reload() {
        scope.launch {
            settings.awaitLoaded()
            configs.value = registrations.all.associate { registration ->
                registration.id to Config(
                    enabled = registration.isEnabled(settings),
                    server = registration.descriptor.server?.let { settings.getStringAsync(it.key, it.default) },
                )
            }
            _localName.value = localAccount.name()
            _aggregationMode.value = LibraryAggregationMode.fromWireValueOrDefault(
                settings.getStringAsync(SettingKeys.LIBRARY_AGGREGATION_MODE, ""),
            )
            _mergeSameSongs.value = settings.getBooleanAsync(
                SettingKeys.MERGE_SAME_SONGS,
                SettingKeys.MERGE_SAME_SONGS_DEFAULT,
            )
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun setEnabled(provider: MusicProviderId, enabled: Boolean) {
        val setting = registrations[provider]?.descriptor?.enabledSetting ?: return
        persist(
            write = { settings.setBoolean(setting.key, enabled) },
            onPersisted = { updateConfig(provider) { it.copy(enabled = enabled) } },
        )
    }

    fun saveServer(provider: MusicProviderId, raw: String) {
        val setting = registrations[provider]?.descriptor?.server ?: return
        val address = setting.normalize(raw)
        persist(
            write = { settings.setString(setting.key, address) },
            onPersisted = {
                updateConfig(provider) { it.copy(server = address) }
                checks.update { it - provider }
                _message.value = "已保存；${setting.appliesWhen}"
            },
        )
    }

    /** Checks the address in the field, saved or not, the way the old settings page did. */
    fun checkServer(provider: MusicProviderId, raw: String) {
        val setting = registrations[provider]?.descriptor?.server ?: return
        val check = setting.check ?: return
        val address = setting.normalize(raw)
        checking.update { it + provider }
        checks.update { it - provider }
        scope.launch {
            val result = try {
                check(address)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ServerCheckResult(false, "无法连接到这个地址")
            }
            checks.update { it + (provider to result) }
            checking.update { it - provider }
        }
    }

    fun saveCredential(provider: MusicProviderId, raw: String) {
        val registration = registrations[provider] ?: return
        val method = registration.loginMethods
            .filterIsInstance<CredentialTextLoginMethod>()
            .firstOrNull()
            ?.takeIf { registration.canSignIn }
            ?: return
        method.normalize(raw)
            .onSuccess { normalized ->
                scope.launch {
                    registration.credentialSlot.write(normalized)
                        .onSuccess {
                            _message.value = if (normalized.isEmpty()) {
                                "已清除${provider.displayName}的凭证"
                            } else {
                                "已保存${provider.displayName}的凭证"
                            }
                        }
                        .onFailure { failure -> _message.value = failure.message ?: "凭证保存失败" }
                }
            }
            .onFailure { failure -> _message.value = failure.message ?: "凭证无法识别" }
    }

    fun signOut(provider: MusicProviderId) {
        val slot = registrations[provider]?.credentialSlot ?: return
        scope.launch {
            slot.clear()
                .onSuccess { _message.value = "已退出${provider.displayName}" }
                .onFailure { failure -> _message.value = "退出失败：${failure.message ?: "未知错误"}" }
        }
    }

    fun renameLocal(name: String) {
        scope.launch {
            localAccount.rename(name)
                .onSuccess {
                    _localName.value = localAccount.name()
                    _message.value = "已重命名本地账号"
                }
                .onFailure { failure -> _message.value = "保存失败：${failure.message ?: "未知错误"}" }
        }
    }

    fun setAggregationMode(mode: LibraryAggregationMode) {
        persist(
            write = { settings.setString(SettingKeys.LIBRARY_AGGREGATION_MODE, mode.wireValue) },
            onPersisted = { _aggregationMode.value = mode },
        )
    }

    fun setMergeSameSongs(merge: Boolean) {
        persist(
            write = { settings.setBoolean(SettingKeys.MERGE_SAME_SONGS, merge) },
            onPersisted = { _mergeSameSongs.value = merge },
        )
    }

    private fun updateConfig(provider: MusicProviderId, change: (Config) -> Config) {
        configs.update { current ->
            val registration = registrations[provider] ?: return@update current
            val existing = current[provider] ?: Config(
                enabled = registration.descriptor.enabledSetting?.default ?: true,
                server = registration.descriptor.server?.default,
            )
            current + (provider to change(existing))
        }
    }

    /** Writes, flushes, and on failure re-reads what is actually stored. */
    private fun persist(write: () -> Unit, onPersisted: () -> Unit) {
        scope.launch {
            try {
                settings.awaitLoaded()
                write()
                settings.flush()
                onPersisted()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                reload()
                _message.value = failure.message ?: "设置保存失败"
            }
        }
    }
}

/** One provider's line in the settings summary. */
internal data class AccountSummaryEntry(
    val providerName: String,
    val enabled: Boolean,
    val signedIn: Boolean,
    val accountName: String?,
)

/** "网易云音乐：昵称；QQ音乐：未启用；本地账号" — who is signed in where, in registration order. */
internal fun accountsSummaryLine(entries: List<AccountSummaryEntry>, localName: String): String =
    (
        entries.map { entry ->
            val state = when {
                !entry.enabled -> "未启用"
                entry.signedIn -> entry.accountName?.takeIf(String::isNotBlank) ?: "已登录"
                else -> "未登录"
            }
            "${entry.providerName}：$state"
        } + localName
        ).joinToString("；")

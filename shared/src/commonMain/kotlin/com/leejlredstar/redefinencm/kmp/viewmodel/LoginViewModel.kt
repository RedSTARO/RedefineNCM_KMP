package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.auth.CredentialStore
import com.leejlredstar.redefinencm.kmp.data.auth.LoginHost
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethodRegistry
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptor
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptorRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.uiText
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The login page's host for one provider: what it says (the descriptor), which login methods it
 * lists (the registry), where a credential goes (the slot) and when the page is done.
 *
 * Nothing method-specific lives here. Each method's on-screen state belongs to its presenter,
 * which reaches persistence and navigation only through the [LoginHost] half of this class.
 */
class LoginViewModel(
    override val provider: MusicProviderId,
    registry: LoginMethodRegistry,
    descriptors: ProviderLoginDescriptorRegistry,
    credentials: CredentialStore,
    private val settings: PlatformSettings,
) : LoginHost {
    override val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val slot = credentials.require(provider)

    val descriptor: ProviderLoginDescriptor = descriptors.require(provider)
    val methods: List<LoginMethod> = registry.forProvider(provider)

    private val _storedCredential = MutableStateFlow("")
    override val storedCredential: StateFlow<String> = _storedCredential.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    override val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    /** Set once a method has signed in; the page closes on it. */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private val _server = MutableStateFlow("")
    val server: StateFlow<String> = _server.asStateFlow()

    /** The outcome of the last address save, for the line under the field. */
    private val _serverMessage = MutableStateFlow<ServerNotice?>(null)
    val serverMessage: StateFlow<ServerNotice?> = _serverMessage.asStateFlow()

    init {
        scope.launch {
            settings.awaitLoaded()
            descriptor.server?.let { setting ->
                _server.value = settings.getStringAsync(setting.key, setting.default)
            }
            rememberCredential(slot.read())
        }
    }

    override suspend fun persist(credential: String): Result<Unit> =
        slot.write(credential).onSuccess { rememberCredential(credential) }

    override fun onSignedIn() {
        _finished.value = true
    }

    /**
     * Saves the provider's backend address in the one stored form the accounts page also writes;
     * an empty field restores the default.
     */
    fun saveServer(raw: String) {
        val setting = descriptor.server ?: return
        scope.launch {
            settings.awaitLoaded()
            val value = setting.normalize(raw)
            val previous = settings.getString(setting.key, setting.default)
            try {
                settings.setString(setting.key, value)
                settings.flush()
                _server.value = value
                _serverMessage.value = ServerNotice(
                    UiText { it.serverSavedAppliesWhen(setting.appliesWhen) },
                    isError = false,
                )
            } catch (failure: Exception) {
                runCatching {
                    settings.setString(setting.key, previous)
                    settings.flush()
                }
                _server.value = settings.getString(setting.key, previous)
                _serverMessage.value = ServerNotice(
                    failure.message?.let(::uiText) ?: UiText { it.settingSaveFailed },
                    isError = true,
                )
            }
        }
    }

    private fun rememberCredential(credential: String) {
        _storedCredential.value = credential
        // The slot's own definition, the same one the accounts page and the startup check use.
        _signedIn.value = slot.isSignedIn(credential)
    }

    fun onCleared() {
        scope.cancel()
    }
}

/** The line under the address field after a save, and whether it reports a failure. */
data class ServerNotice(val text: UiText, val isError: Boolean)

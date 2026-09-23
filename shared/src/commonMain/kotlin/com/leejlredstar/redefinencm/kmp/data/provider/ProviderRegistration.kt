package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.data.auth.AccountIdentitySource
import com.leejlredstar.redefinencm.kmp.data.auth.CredentialStore
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethodRegistry
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderCredentialSlot
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptor
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptorRegistry
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.getBooleanAsync

/**
 * Everything the app knows about one provider, registered once in the DI module.
 *
 * The registries the screens use — [MusicProviderRegistry], [CredentialStore],
 * [ProviderLoginDescriptorRegistry], [LoginMethodRegistry] — are derived from the list of these,
 * so a provider cannot be half-registered: a slot without a descriptor, or a login method filed
 * under another provider, fails here at construction instead of on the page that needed it.
 */
class ProviderRegistration(
    val provider: MusicProvider,
    val credentialSlot: ProviderCredentialSlot,
    val descriptor: ProviderLoginDescriptor,
    val loginMethods: List<LoginMethod>,
    val identity: AccountIdentitySource,
) {
    val id: MusicProviderId get() = provider.id

    init {
        require(credentialSlot.provider == id) {
            "the credential slot of ${credentialSlot.provider.key} is registered under ${id.key}"
        }
        require(descriptor.provider == id) {
            "the login descriptor of ${descriptor.provider.key} is registered under ${id.key}"
        }
        require(loginMethods.all { it.provider == id }) {
            "a login method of another provider is registered under ${id.key}"
        }
    }

    /** Whether this platform can sign in to the provider at all. */
    val canSignIn: Boolean get() = descriptor.signInUnavailableReason == null

    /** Whether the provider is switched on; always, for one without a switch. */
    suspend fun isEnabled(settings: PlatformSettings): Boolean =
        descriptor.enabledSetting?.let { settings.getBooleanAsync(it.key, it.default) } ?: true

    /**
     * Whether [credential] is an account this platform can use. A credential stored where the
     * provider cannot be signed in to — a QQ account saved before the Web build stopped offering
     * sign-in — is never sent, so it does not count.
     */
    fun holdsAccount(credential: String): Boolean = canSignIn && credentialSlot.isSignedIn(credential)

    /** Whether the provider is switched on and holds an account this platform can use. */
    suspend fun isSignedIn(settings: PlatformSettings): Boolean =
        isEnabled(settings) && holdsAccount(credentialSlot.read())
}

/** The registered providers, in the order pages list them and search groups their results. */
class ProviderRegistrations(val all: List<ProviderRegistration>) {
    init {
        require(all.map { it.id }.toSet().size == all.size) { "a provider is registered twice" }
    }

    operator fun get(id: MusicProviderId): ProviderRegistration? = all.firstOrNull { it.id == id }

    fun musicProviders(): List<MusicProvider> = all.map { it.provider }

    fun credentialStore(): CredentialStore = CredentialStore(all.map { it.credentialSlot })

    fun descriptorRegistry(): ProviderLoginDescriptorRegistry =
        ProviderLoginDescriptorRegistry(all.map { it.descriptor })

    /** Only providers this platform can sign in to offer their methods. */
    fun loginMethodRegistry(): LoginMethodRegistry =
        LoginMethodRegistry(all.filter { it.canSignIn }.flatMap { it.loginMethods })

    /** Whether any switched-on provider holds a signed-in account. */
    suspend fun anySignedIn(settings: PlatformSettings): Boolean = all.any { it.isSignedIn(settings) }
}

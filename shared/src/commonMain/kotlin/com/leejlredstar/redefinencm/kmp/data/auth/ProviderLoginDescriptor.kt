package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId

/** The self-hosted backend address a provider reads, offered for editing beside its account. */
class ProviderServerSetting(
    val key: String,
    val default: String,
    val label: String,
    /** When a saved address is picked up, for the confirmation line. */
    val appliesWhen: String,
    /**
     * Asks the backend at an address whether it answers, returning the line to show. Null when
     * the provider has no such check.
     */
    val check: (suspend (address: String) -> ServerCheckResult)? = null,
) {
    /**
     * The form an address is stored in: trimmed, with exactly one trailing slash. An empty field
     * means the default — an empty address left every request without a host.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty()) default else "${trimmed.trimEnd('/')}/"
    }
}

/** The outcome of [ProviderServerSetting.check]. */
data class ServerCheckResult(val reachable: Boolean, val message: String)

/** A switch that turns a provider off. */
class ProviderEnabledSetting(
    val key: String,
    val default: Boolean,
    val label: String,
    /** Under the switch: what turning it on needs. */
    val supportingText: String,
)

/**
 * What the login and accounts pages say about one provider, registered per provider so those
 * pages hold no provider branches: a new provider is a `ProviderRegistration` in the DI module.
 */
class ProviderLoginDescriptor(
    val provider: MusicProviderId,
    /** Under the login page's title: what signing in is for, and that it can wait. */
    val introduction: String,
    /** Under the account name: "网易云音乐账号". */
    val accountLabel: String,
    /** Under "未登录": what signing in is for. */
    val signedOutHint: String,
    /** The body of the sign-out confirmation. */
    val logoutWarning: String,
    /** The backend address the provider reads, or null when it has none to edit. */
    val server: ProviderServerSetting?,
    /** The switch that turns the provider off, or null for a provider that is always on. */
    val enabledSetting: ProviderEnabledSetting? = null,
    /**
     * Why this platform cannot sign in to the provider, or null when it can. The page shows it in
     * place of the sign-in button, and the provider's login methods are not offered.
     */
    val signInUnavailableReason: String? = null,
)

/** The registered descriptors, in the order the accounts page lists providers. */
class ProviderLoginDescriptorRegistry(descriptors: List<ProviderLoginDescriptor>) {
    private val byProvider: Map<MusicProviderId, ProviderLoginDescriptor> =
        descriptors.associateBy { it.provider }

    init {
        require(byProvider.size == descriptors.size) { "more than one login descriptor for a provider" }
    }

    val all: List<ProviderLoginDescriptor> = descriptors

    operator fun get(provider: MusicProviderId): ProviderLoginDescriptor? = byProvider[provider]

    fun require(provider: MusicProviderId): ProviderLoginDescriptor =
        byProvider[provider] ?: error("no login descriptor registered for ${provider.key}")
}

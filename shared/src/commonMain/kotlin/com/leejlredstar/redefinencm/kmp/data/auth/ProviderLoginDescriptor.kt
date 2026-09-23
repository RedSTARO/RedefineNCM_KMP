package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId

/** The self-hosted backend address a provider reads, offered for editing beside its login. */
class ProviderServerSetting(
    val key: String,
    val default: String,
    val label: String,
    /** When a saved address is picked up, for the confirmation line. */
    val appliesWhen: String,
)

/**
 * What the login and settings pages say about one provider, registered per provider so those
 * pages hold no provider branches: a new provider is a descriptor, a credential slot and its
 * login methods, all registered in the DI module.
 */
class ProviderLoginDescriptor(
    val provider: MusicProviderId,
    /** Under the login page's title: what signing in is for, and that it can wait. */
    val introduction: String,
    /** Under the account name in settings: "网易云音乐账号". */
    val accountLabel: String,
    /** Under "未登录" in settings: what signing in is for. */
    val signedOutHint: String,
    /** The body of the sign-out confirmation. */
    val logoutWarning: String,
    /** The backend address the provider reads, or null when it has none to edit. */
    val server: ProviderServerSetting?,
    /**
     * The name the accounts page shows for a stored credential, when the credential itself
     * carries one (QQ's carries the account number). Null leaves the page to say "已登录".
     */
    val accountName: (storedCredential: String) -> String? = { null },
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

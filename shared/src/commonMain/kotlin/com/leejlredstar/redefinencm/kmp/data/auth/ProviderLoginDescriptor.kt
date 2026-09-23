package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.text

// The descriptors below are built once, when the DI module starts, but the pages read their words
// every time they draw. So each takes its copy as UiText and hands out the text in the language
// shown at that moment.

/** The self-hosted backend address a provider reads, offered for editing beside its account. */
class ProviderServerSetting(
    val key: String,
    val default: String,
    private val labelText: UiText,
    /** When a saved address is picked up, for the confirmation line. */
    private val appliesWhenText: UiText,
    /**
     * Asks the backend at an address whether it answers, returning the line to show. Null when
     * the provider has no such check.
     */
    val check: (suspend (address: String) -> ServerCheckResult)? = null,
) {
    val label: String get() = labelText.text
    val appliesWhen: String get() = appliesWhenText.text

    /**
     * The form an address is stored in: trimmed, with exactly one trailing slash. An empty field
     * means the default, because an empty address would leave every request without a host.
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
    private val labelText: UiText,
    /** Under the switch: what turning it on needs. */
    private val supportingTextText: UiText,
) {
    val label: String get() = labelText.text
    val supportingText: String get() = supportingTextText.text
}

/**
 * What the login and accounts pages say about one provider, registered per provider so those
 * pages hold no provider branches: a new provider is a `ProviderRegistration` in the DI module.
 */
class ProviderLoginDescriptor(
    val provider: MusicProviderId,
    /** Under the login page's title: what signing in is for, and that it can wait. */
    private val introductionText: UiText,
    /** Under the account name: "网易云音乐账号". */
    private val accountLabelText: UiText,
    /** Under "未登录": what signing in is for. */
    private val signedOutHintText: UiText,
    /** The body of the sign-out confirmation. */
    private val logoutWarningText: UiText,
    /** The backend address the provider reads, or null when it has none to edit. */
    val server: ProviderServerSetting?,
    /** The switch that turns the provider off, or null for a provider that is always on. */
    val enabledSetting: ProviderEnabledSetting? = null,
    /**
     * Why this platform cannot sign in to the provider, or null when it can. The page shows it in
     * place of the sign-in button, and the provider's login methods are not offered.
     */
    private val signInUnavailableReasonText: UiText? = null,
) {
    val introduction: String get() = introductionText.text
    val accountLabel: String get() = accountLabelText.text
    val signedOutHint: String get() = signedOutHintText.text
    val logoutWarning: String get() = logoutWarningText.text
    val signInUnavailableReason: String? get() = signInUnavailableReasonText?.text
}

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

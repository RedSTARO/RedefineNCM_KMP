package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId

/**
 * Every way of signing in the app knows about, in the order the login page lists them.
 *
 * The page asks this for one provider's methods and renders exactly those, so a provider with two
 * QR methods gets a chooser and a provider with none gets no QR section. The registration list
 * lives in the DI module; nothing else enumerates methods.
 */
class LoginMethodRegistry(private val methods: List<LoginMethod>) {
    init {
        val duplicates = methods.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate login method ids: $duplicates" }
    }

    val all: List<LoginMethod> get() = methods

    fun forProvider(provider: MusicProviderId): List<LoginMethod> =
        methods.filter { it.provider == provider }

    fun qrMethods(provider: MusicProviderId): List<QrLoginMethod> =
        forProvider(provider).filterIsInstance<QrLoginMethod>()

    /**
     * The one pasted-credential method for [provider], if any. A provider has one stored
     * credential, so it has at most one way of pasting it.
     */
    fun textMethod(provider: MusicProviderId): CredentialTextLoginMethod? =
        forProvider(provider).filterIsInstance<CredentialTextLoginMethod>().firstOrNull()
}

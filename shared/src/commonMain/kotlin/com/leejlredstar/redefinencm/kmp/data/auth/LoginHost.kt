package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * What the login page offers every login method: a scope that ends when the page does, the
 * provider's persistence, and a way to say the page's job is done.
 *
 * Methods and their presenters never touch settings or navigation themselves. A method that did
 * would have to know which provider's change rules apply on a write, and that is the host's
 * knowledge, held in the provider's [ProviderCredentialSlot].
 */
interface LoginHost {
    val provider: MusicProviderId

    /** Main-dispatched; cancelled when the page is left. Flows launch their work here. */
    val scope: CoroutineScope

    /** The credential stored for [provider] right now, empty when signed out. */
    val storedCredential: StateFlow<String>

    /** Whether [storedCredential] is non-empty. */
    val signedIn: StateFlow<Boolean>

    /**
     * Persists a credential through the provider's slot. An empty string signs out. The failure's
     * message is fit for the screen.
     */
    suspend fun persist(credential: String): Result<Unit>

    /** A method finished signing in; the page closes shortly after. */
    fun onSignedIn()
}

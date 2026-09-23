package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.util.cookieFingerprint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the QQ account the app holds alive.
 *
 * The gateway renews only the accounts installed in its own pool; an account sent per request is
 * the caller's to renew. Two paths do it:
 *
 * - [ensureFresh] asks `/login/check_expired` before the first call that would use a credential,
 *   once per credential rather than once per process — an account signed in or pasted after the
 *   first QQ call is checked too.
 * - [renewAfterRejection] runs when the gateway refuses a signed-in request, so a key that expires
 *   mid-session is renewed then rather than at the next launch. Also once per credential, so a
 *   gateway that refuses everything cannot turn every request into a renewal.
 *
 * A renewed credential is written with [ProviderCredentialSlot.replace] against the value the
 * renewal started from. If the user signed out or switched accounts meanwhile, the answer is
 * dropped instead of written over their choice.
 *
 * Both paths are time-bounded: the first QQ call may be resolving a stream on the player's IO
 * thread, where Android waits under `runBlocking`.
 */
class QQCredentialRenewer(
    /** Resolved lazily: the API calls back into [renewAfterRejection] when a request is refused. */
    private val api: () -> QQMusicApi,
    private val slot: ProviderCredentialSlot,
    private val budgetMillis: Long = DefaultBudgetMillis,
) {
    private val lock = Mutex()

    /** The credential whose expiry has been asked about, by fingerprint. */
    private var checked: Long? = null

    /** The credential whose refusal has already started a renewal, by fingerprint. */
    private var rejected: Long? = null

    /** Renews the stored credential if the gateway says it has expired; asks once per credential. */
    suspend fun ensureFresh() {
        lock.withLock {
            val stored = slot.read()
            val fingerprint = cookieFingerprint(stored)
            if (fingerprint == checked) return
            checked = fingerprint
            if (!renewable(stored)) return
            withTimeoutOrNull(budgetMillis) {
                if (api().credentialExpired() == true) renew(stored)
            }
        }
    }

    /**
     * The gateway refused a request sent with [rejectedCredential]. True when the request is worth
     * sending once more with whatever is stored now: either this call renewed the credential, or
     * the stored one had already changed — another renewal, or another account — since the request
     * left.
     */
    suspend fun renewAfterRejection(rejectedCredential: String): Boolean = lock.withLock {
        val stored = slot.read()
        if (stored != rejectedCredential) return@withLock stored.isNotBlank()
        val fingerprint = cookieFingerprint(stored)
        if (fingerprint == rejected || !renewable(stored)) return@withLock false
        rejected = fingerprint
        withTimeoutOrNull(budgetMillis) { renew(stored) } == true
    }

    private fun renewable(stored: String): Boolean = QQCredential.parse(stored)?.canRefresh == true

    private suspend fun renew(expected: String): Boolean {
        val previous = QQCredential.parse(expected)
        val renewed = api().refreshCredential()
            ?.let(QQCredential::fromGateway)
            ?.takeIf { it.isSignedIn }
            // A renewal answer need not repeat the encrypted UIN; the account has not changed.
            ?.let { it.copy(encryptUin = it.encryptUin.ifBlank { previous?.encryptUin.orEmpty() }) }
            ?.toCookieHeader()
            ?: return false
        val written = slot.replace(expected, renewed).getOrDefault(false)
        // A key issued a moment ago is not worth asking about again.
        if (written) checked = cookieFingerprint(renewed)
        return written
    }

    companion object {
        const val DefaultBudgetMillis = 8_000L
    }
}

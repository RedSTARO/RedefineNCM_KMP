package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/** Who is signed in to a provider, as its backend names them. */
data class AccountIdentity(
    val name: String?,
    val avatarUrl: String? = null,
)

/**
 * Names the account behind a provider's stored credential for the accounts page, so the page holds
 * no provider branches. A source that cannot tell emits null, and the page says "已登录".
 */
fun interface AccountIdentitySource {
    /** Follows [credential] — the stored value as it changes — and names each account. */
    fun identities(credential: Flow<String>): Flow<AccountIdentity?>
}

/**
 * QQ's: the gateway's profile header, found by the encrypted UIN the stored credential carries.
 *
 * The account number shows at once and is replaced by the name when the profile answers. A pasted
 * browser cookie may carry no encrypted UIN; the number is then all there is.
 */
class QQAccountIdentitySource(private val api: QQMusicApi) : AccountIdentitySource {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun identities(credential: Flow<String>): Flow<AccountIdentity?> =
        credential.distinctUntilChanged().transformLatest { stored ->
            val account = QQCredential.parse(stored)
            if (account == null) {
                emit(null)
                return@transformLatest
            }
            val number = AccountIdentity(name = account.musicId.toString())
            emit(number)
            val encryptUin = account.encryptUin.takeIf(String::isNotBlank) ?: return@transformLatest
            val profile = api.userHomepage(encryptUin)?.baseInfo ?: return@transformLatest
            if (profile.name.isNotBlank()) {
                emit(AccountIdentity(profile.name, profile.avatar.takeIf(String::isNotBlank)))
            }
        }
}

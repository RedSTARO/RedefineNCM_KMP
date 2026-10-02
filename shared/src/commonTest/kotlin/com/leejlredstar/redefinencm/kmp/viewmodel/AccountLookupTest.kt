package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.api.dto.UserAccount
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserAccountDetail
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountLookupTest {
    private val cookie = "MUSIC_U=session"

    @Test
    fun anAccountInTheAnswerIsTheSignedInUser() {
        val answer = UserAccount(code = 200, account = UserAccountDetail(id = 42))

        assertEquals(AccountLookup.SignedIn(42), classifyAccountLookup(cookie, answer))
    }

    @Test
    fun withoutACredentialNobodyIsSignedInWhateverTheAnswer() {
        assertEquals(AccountLookup.SignedOut, classifyAccountLookup("", null))
        assertEquals(AccountLookup.SignedOut, classifyAccountLookup("  ", UserAccount(code = 200)))
    }

    @Test
    fun aRequestThatFailedIsNotASignOut() {
        // One dropped request used to show a signed-in user the sign-in prompt, with no retry.
        assertEquals(AccountLookup.Unreachable, classifyAccountLookup(cookie, null))
    }

    @Test
    fun aCredentialTheServiceNoLongerKnowsHasExpired() {
        // What the server this app defaults to answers for a cookie it does not accept.
        assertEquals(AccountLookup.Expired, classifyAccountLookup(cookie, UserAccount(code = 200)))
        assertEquals(AccountLookup.Expired, classifyAccountLookup(cookie, UserAccount(code = 301)))
    }

    @Test
    fun aServiceThatRefusesTheRequestItselfSaysNothingAboutTheAccount() {
        // Rate limited or failing: asking again later is the answer, not signing in again.
        assertEquals(AccountLookup.Unreachable, classifyAccountLookup(cookie, UserAccount(code = -460)))
        assertEquals(AccountLookup.Unreachable, classifyAccountLookup(cookie, UserAccount(code = 502)))
        // An account in an answer that is not a success is not trusted either.
        assertEquals(
            AccountLookup.Unreachable,
            classifyAccountLookup(cookie, UserAccount(code = 502, account = UserAccountDetail(id = 42))),
        )
    }
}

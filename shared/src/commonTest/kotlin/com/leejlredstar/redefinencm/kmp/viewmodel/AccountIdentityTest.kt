package com.leejlredstar.redefinencm.kmp.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccountIdentityTest {
    @Test
    fun cookieFingerprintIsStableAndCredentialSpecific() {
        val cookie = "MUSIC_U=session-a; __csrf=token-a"
        assertEquals(accountCookieFingerprint(cookie), accountCookieFingerprint(cookie))
        assertNotEquals(
            accountCookieFingerprint(cookie),
            accountCookieFingerprint("MUSIC_U=session-b; __csrf=token-a"),
        )
    }

    @Test
    fun emptyCookieHasDeterministicFingerprintButIsNotAnAccountBinding() {
        assertEquals(-3750763034362895579L, accountCookieFingerprint(""))
        assertFalse(isCachedAccountIdentityValid("", 42L, accountCookieFingerprint("")))
    }

    @Test
    fun cachedUidIsAcceptedOnlyForTheCookieItWasBoundTo() {
        val oldCookie = "MUSIC_U=old"
        val newCookie = "MUSIC_U=new"
        val oldFingerprint = accountCookieFingerprint(oldCookie)

        assertTrue(isCachedAccountIdentityValid(oldCookie, 42L, oldFingerprint))
        assertFalse(isCachedAccountIdentityValid(newCookie, 42L, oldFingerprint))
        assertFalse(isCachedAccountIdentityValid(oldCookie, 0L, oldFingerprint))
    }
}

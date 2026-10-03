package com.leejlredstar.redefinencm.kmp.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpClientFactoryTest {
    @Test
    fun masksUrlCredentialsAndSignInKeysWhileKeepingOrdinaryParameters() {
        val redacted = redactSensitiveQueryParameters(
            "GET HTTPS://name:ENCODED%40SECRET@host/login?key=QR_SECRET&captcha=123456&password=PW_SECRET&id=42",
        )
        assertEquals(
            "GET HTTPS://<redacted>@host/login?key=<redacted>&captcha=<redacted>&password=<redacted>&id=42",
            redacted,
        )
    }

    @Test
    fun redactsFingerprintAndQueryCookieWithoutChangingOtherParameters() {
        val logged =
            "REQUEST: http://example.test/audio/match?duration=3&audioFP=abc%2Bdef%3D&cookie=MUSIC_U%3Dsecret&timestamp=1"

        val redacted = redactSensitiveQueryParameters(logged)

        assertEquals(
            "REQUEST: http://example.test/audio/match?duration=3&audioFP=<redacted>&cookie=<redacted>&timestamp=1",
            redacted,
        )
        assertFalse("abc%2Bdef" in redacted)
        assertFalse("secret" in redacted)
    }

    @Test
    fun theSessionIsACredentialInBothDirections() {
        assertTrue(isCredentialHeader("Cookie"))
        assertTrue(isCredentialHeader("Set-Cookie"))
        // HTTP/2 lowercases header names, and engines differ in what they hand back.
        assertTrue(isCredentialHeader("set-cookie"))
        assertTrue(isCredentialHeader("Authorization"))
        assertTrue(isCredentialHeader("Proxy-Authorization"))
        assertFalse(isCredentialHeader("Content-Type"))
    }
}

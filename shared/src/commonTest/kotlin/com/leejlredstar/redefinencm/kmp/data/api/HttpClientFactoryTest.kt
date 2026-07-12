package com.leejlredstar.redefinencm.kmp.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HttpClientFactoryTest {
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
}

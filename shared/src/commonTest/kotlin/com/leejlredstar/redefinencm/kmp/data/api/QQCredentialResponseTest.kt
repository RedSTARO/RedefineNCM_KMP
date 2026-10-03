package com.leejlredstar.redefinencm.kmp.data.api

import com.leejlredstar.redefinencm.kmp.data.auth.QQCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QQCredentialResponseTest {
    @Test
    fun expiryUsesTheGatewaysBooleanEnvelope() {
        assertEquals(true, parseQQCredentialExpired("""{"code":0,"data":null}"""))
        assertEquals(false, parseQQCredentialExpired("""{"code":-1,"msg":"operation failed"}"""))
        assertEquals(true, parseQQCredentialExpired("""{"code":0,"data":true}"""))
        assertEquals(false, parseQQCredentialExpired("""{"code":0,"data":false}"""))
    }

    @Test
    fun malformedOrFailedRepliesAreUnknown() {
        for (body in listOf("{}", "null", "<html>error</html>", """{"data":true}""",
            """{"code":500,"data":true}""", """{"code":0,"data":"false"}""",
            """{"code":0,"data":{}}""")) {
            assertNull(parseQQCredentialExpired(body), body)
        }
    }

    @Test
    fun snakeCaseGatewayIdentityFieldsSurviveCredentialStorage() {
        val credential = ApiJson.decodeFromString<QQGatewayCredential>(
            """{"musicid":42,"musickey":"W_X_key","encrypt_uin":"encrypted-id","login_type":1}""",
        )
        assertEquals("encrypted-id", credential.encryptUin)
        assertEquals(1, credential.loginType)
        val stored = QQCredential.fromGateway(credential).toCookieHeader()
        assertEquals("encrypted-id", QQCredential.parse(stored)?.encryptUin)
    }
}

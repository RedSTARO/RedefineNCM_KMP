package com.leejlredstar.redefinencm.kmp.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three forms a QQ credential arrives in must all end up as the one cookie string the gateway
 * reads, and nothing that lacks the required pair may pass as signed in.
 */
class QQCredentialTest {
    @Test
    fun gatewayJsonIsReadIncludingItsCamelCaseFields() {
        val credential = assertNotNull(
            QQCredential.parse(
                """
                {"openid":"o1","refresh_token":"r1","access_token":"a1","expired_at":99,
                 "musicid":123456,"musickey":"Q_H_L_key","unionid":"u1","str_musicid":"123456",
                 "refresh_key":"k1","musickeyCreateTime":1,"keyExpiresIn":2,"first_login":0,
                 "bindAccountType":0,"needRefreshKeyIn":0,"encryptUin":"e","loginType":1}
                """.trimIndent(),
            ),
        )
        assertEquals(123456L, credential.musicId)
        assertEquals("Q_H_L_key", credential.musicKey)
        assertEquals("r1", credential.refreshToken)
        assertEquals("k1", credential.refreshKey)
        assertEquals(99L, credential.expiredAt)
        assertTrue(credential.canRefresh)

        // `str_musicid` only travels when it says something `musicid` does not. The encrypted UIN
        // rides along under a name the gateway ignores, for the accounts page's profile lookup.
        assertEquals(
            "musicid=123456; musickey=Q_H_L_key; openid=o1; refresh_token=r1; access_token=a1; " +
                "expired_at=99; unionid=u1; refresh_key=k1; encrypt_uin=e",
            credential.toCookieHeader(),
        )
        assertEquals("e", QQCredential.parse(credential.toCookieHeader())?.encryptUin)
    }

    @Test
    fun theCookieFormRoundTrips() {
        val original = QQCredential(
            musicId = 42,
            musicKey = "W_X_key",
            openId = "o",
            refreshToken = "r",
            unionId = "u",
            refreshKey = "k",
        )
        assertEquals(original, QQCredential.parse(original.toCookieHeader()))
    }

    @Test
    fun aBrowserCookieFromYqqIsTranslatedOntoTheGatewaysNames() {
        val credential = assertNotNull(
            QQCredential.parse(
                "pgv_pvid=1; uin=o0123456789; qm_keyst=Q_H_L_abc; psrf_qqrefresh_token=rt; " +
                    "psrf_qqopenid=op; psrf_qqaccess_token=at; psrf_access_token_expiresAt=1700000000; " +
                    "psrf_qqunionid=un; tmeLoginType=2",
            ),
        )
        // The `o` prefix and zero padding QQ writes on `uin` are not part of the account number.
        assertEquals(123456789L, credential.musicId)
        assertEquals("Q_H_L_abc", credential.musicKey)
        assertEquals("rt", credential.refreshToken)
        assertEquals("op", credential.openId)
        assertEquals("at", credential.accessToken)
        assertEquals(1700000000L, credential.expiredAt)
        assertEquals("un", credential.unionId)
        assertTrue(credential.toCookieHeader().startsWith("musicid=123456789; musickey=Q_H_L_abc; "))
    }

    @Test
    fun aWeChatBrowserCookieIsReadToo() {
        val credential = assertNotNull(
            QQCredential.parse("wxuin=987; qm_keyst=W_X_key; wxrefresh_token=wr; wxopenid=wo; wxunionid=wu"),
        )
        assertEquals(987L, credential.musicId)
        assertEquals("wr", credential.refreshToken)
        assertEquals("wo", credential.openId)
        assertEquals("wu", credential.unionId)
    }

    @Test
    fun anythingWithoutTheRequiredPairIsNotAnAccount() {
        assertNull(QQCredential.parse(""))
        assertNull(QQCredential.parse("   "))
        assertNull(QQCredential.parse("musicid=123"))
        assertNull(QQCredential.parse("musickey=abc"))
        assertNull(QQCredential.parse("uin=o0; qm_keyst=abc"))
        assertNull(QQCredential.parse("not a cookie at all"))
        assertNull(QQCredential.parse("""{"musicid":0,"musickey":"abc"}"""))
        assertNull(QQCredential.parse("""{"musicid":"garbage"}"""))
    }

    @Test
    fun aKeyWithoutRenewalFieldsCannotBeRefreshed() {
        val credential = assertNotNull(QQCredential.parse("musicid=1; musickey=abc"))
        assertFalse(credential.canRefresh)
        assertEquals("musicid=1; musickey=abc", credential.toCookieHeader())
    }
}

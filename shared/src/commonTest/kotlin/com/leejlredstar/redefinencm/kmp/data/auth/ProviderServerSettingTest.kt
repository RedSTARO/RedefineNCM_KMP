package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.i18n.uiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The accounts page, login page and backups agree on valid server addresses. */
class ProviderServerSettingTest {
    private val setting = ProviderServerSetting(
        key = "server", default = "https://default.test/",
        labelText = uiText("服务器地址"), appliesWhenText = uiText("重启后生效"),
    )

    @Test
    fun anAddressIsTrimmedAndEndsInExactlyOneSlash() {
        assertEquals("http://192.168.1.2:3000/", setting.normalize("  http://192.168.1.2:3000 "))
        assertEquals("http://host/api/", setting.normalize("http://host/api///"))
        assertEquals("http://host/", setting.normalize("http://host/"))
    }

    @Test
    fun anEmptyFieldMeansTheDefault() {
        assertEquals("https://default.test/", setting.normalize(""))
        assertEquals("https://default.test/", setting.normalize("   "))
    }

    @Test
    fun validAddressesKeepTheirSubdirectoryAndPort() {
        assertEquals("https://default.test/", setting.normalize("  "))
        assertEquals("http://localhost:8080/api/ncm/", setting.normalize(" http://localhost:8080/api/ncm/// "))
        assertEquals("http://[::1]:3000/api/", setting.normalize("http://[::1]:3000/api"))
    }

    @Test
    fun incompleteOrNonBaseAddressesAreRejected() {
        for (address in listOf("localhost:8080", "api.example.test", "ftp://api.example.test/", "https://", "https://exa mple.test", "https://api.example.test/?id=1", "https://api.example.test/#/api", "http://localhost:99999/")) {
            assertFailsWith<IllegalArgumentException>(address) { setting.normalize(address) }
        }
    }
}

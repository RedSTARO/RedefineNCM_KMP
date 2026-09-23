package com.leejlredstar.redefinencm.kmp.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals

/** The accounts page and the login page store an address in the one form. */
class ProviderServerSettingTest {
    private val setting = ProviderServerSetting(
        key = "server",
        default = "https://default.test/",
        label = "服务器地址",
        appliesWhen = "重启后生效",
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
}

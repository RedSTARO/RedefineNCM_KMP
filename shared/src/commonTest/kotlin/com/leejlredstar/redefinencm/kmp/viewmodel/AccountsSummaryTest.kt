package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.LanguageSetting
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The settings page's line about accounts names every provider's state, then the local account. */
class AccountsSummaryTest {
    /** The expected copy below is the Chinese, so the test pins it whatever the machine's language. */
    @BeforeTest
    fun showChinese() {
        I18n.apply(LanguageSetting.ZH)
    }

    @AfterTest
    fun followTheSystemAgain() {
        I18n.apply(LanguageSetting.SYSTEM)
    }

    @Test
    fun eachProviderSaysWhoIsSignedInOrWhyNot() {
        val line = accountsSummaryLine(
            listOf(
                AccountSummaryEntry("网易云音乐", enabled = true, signedIn = true, accountName = "昵称"),
                AccountSummaryEntry("QQ音乐", enabled = false, signedIn = true, accountName = "123"),
            ),
            localName = "本地账号",
            localEnabled = true,
        )
        assertEquals("网易云音乐：昵称；QQ音乐：未启用；本地账号", line)
    }

    @Test
    fun aSignedInAccountWithoutANameSaysSoAndASignedOutOneSaysThat() {
        val line = accountsSummaryLine(
            listOf(
                AccountSummaryEntry("网易云音乐", enabled = true, signedIn = false, accountName = null),
                AccountSummaryEntry("QQ音乐", enabled = true, signedIn = true, accountName = " "),
            ),
            localName = "我的设备",
            localEnabled = true,
        )
        assertEquals("网易云音乐：未登录；QQ音乐：已登录；我的设备", line)
    }

    @Test
    fun aSwitchedOffLocalAccountSaysSoUnderItsOwnName() {
        val line = accountsSummaryLine(
            listOf(AccountSummaryEntry("网易云音乐", enabled = true, signedIn = true, accountName = "昵称")),
            localName = "我的设备",
            localEnabled = false,
        )
        assertEquals("网易云音乐：昵称；我的设备：未启用", line)
    }
}

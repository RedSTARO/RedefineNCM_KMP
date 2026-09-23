package com.leejlredstar.redefinencm.kmp.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

/** The settings page's line about accounts names every provider's state, then the local account. */
class AccountsSummaryTest {
    @Test
    fun eachProviderSaysWhoIsSignedInOrWhyNot() {
        val line = accountsSummaryLine(
            listOf(
                AccountSummaryEntry("网易云音乐", enabled = true, signedIn = true, accountName = "昵称"),
                AccountSummaryEntry("QQ音乐", enabled = false, signedIn = true, accountName = "123"),
            ),
            localName = "本地账号",
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
        )
        assertEquals("网易云音乐：未登录；QQ音乐：已登录；我的设备", line)
    }
}

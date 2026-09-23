package com.leejlredstar.redefinencm.kmp.i18n

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class I18nTest {
    /** Back to the process default, so no other test depends on the order the classes run in. */
    @AfterTest
    fun followTheSystemAgain() {
        I18n.apply(LanguageSetting.SYSTEM)
    }

    @Test
    fun anExplicitChoiceIgnoresTheSystem() {
        assertEquals(AppLanguage.JA, resolveLanguage(LanguageSetting.JA, listOf("zh-CN")))
        assertEquals(AppLanguage.EN, resolveLanguage(LanguageSetting.EN, listOf("ja-JP")))
        assertEquals(AppLanguage.ZH, resolveLanguage(LanguageSetting.ZH, emptyList()))
    }

    @Test
    fun followingTheSystemTakesItsFirstLanguageTheAppHas() {
        assertEquals(AppLanguage.JA, resolveLanguage(LanguageSetting.SYSTEM, listOf("fr-FR", "ja-JP", "en-US")))
        assertEquals(AppLanguage.EN, resolveLanguage(LanguageSetting.SYSTEM, listOf("en-GB", "zh-CN")))
    }

    @Test
    fun everyChineseScriptAndRegionGetsSimplifiedChinese() {
        for (tag in listOf("zh", "zh-CN", "zh-Hans-CN", "zh-Hant-TW", "zh-HK", "zh_TW")) {
            assertEquals(AppLanguage.ZH, resolveLanguage(LanguageSetting.SYSTEM, listOf(tag)), tag)
        }
    }

    @Test
    fun aSystemWithNoneOfTheThreeGetsEnglish() {
        assertEquals(AppLanguage.EN, resolveLanguage(LanguageSetting.SYSTEM, listOf("fr-FR", "ko-KR")))
        assertEquals(AppLanguage.EN, resolveLanguage(LanguageSetting.SYSTEM, emptyList()))
    }

    @Test
    fun anUnknownStoredValueFollowsTheSystem() {
        assertEquals(LanguageSetting.SYSTEM, LanguageSetting.fromWireValue(""))
        assertEquals(LanguageSetting.SYSTEM, LanguageSetting.fromWireValue("fr"))
        assertEquals(LanguageSetting.JA, LanguageSetting.fromWireValue("ja"))
    }

    @Test
    fun switchingLanguageSwitchesTheCopy() {
        I18n.apply(LanguageSetting.ZH)
        val chinese = strings.settingsLanguage
        I18n.apply(LanguageSetting.EN)
        assertEquals("Language", strings.settingsLanguage)
        I18n.apply(LanguageSetting.JA)
        assertEquals("言語", strings.settingsLanguage)
        assertNotEquals(chinese, strings.settingsLanguage)
    }

    @Test
    fun aHeldMessageIsWordedInTheLanguageShownWhenItIsRead() {
        val message = UiText { it.settingsLanguage }
        I18n.apply(LanguageSetting.EN)
        assertEquals("Language", message.text)
        I18n.apply(LanguageSetting.ZH)
        assertEquals("语言", message.text)
    }

    @Test
    fun messagesCompareByWhatTheySayInEveryLanguage() {
        assertEquals(UiText { it.settingsLanguage }, UiText { it.settingsLanguage })
        assertNotEquals(UiText { it.settingsLanguage }, uiText("语言"))
    }

    @Test
    fun largeCountsUseEachLanguagesUnits() {
        I18n.apply(LanguageSetting.ZH)
        assertEquals("9999", compactCount(9_999))
        assertEquals("12万", compactCount(125_000))
        assertEquals("3亿", compactCount(312_000_000))
        I18n.apply(LanguageSetting.JA)
        assertEquals("12万", compactCount(125_000))
        assertEquals("3億", compactCount(312_000_000))
        I18n.apply(LanguageSetting.EN)
        assertEquals("999", compactCount(999))
        assertEquals("125K", compactCount(125_000))
        assertEquals("312M", compactCount(312_000_000))
        assertEquals("2B", compactCount(2_500_000_000))
    }
}

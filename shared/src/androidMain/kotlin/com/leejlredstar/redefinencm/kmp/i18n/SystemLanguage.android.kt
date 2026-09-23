package com.leejlredstar.redefinencm.kmp.i18n

import android.os.LocaleList
import java.util.Locale

/**
 * The app's locale list, which is the system's unless the user set a language for this app in the
 * system settings. Host tests run against stub Android classes, so they fall back to the JVM's.
 */
actual fun systemLanguageTags(): List<String> = runCatching {
    val list = LocaleList.getDefault()
    (0 until list.size()).map { list.get(it).toLanguageTag() }
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(Locale.getDefault().toLanguageTag())

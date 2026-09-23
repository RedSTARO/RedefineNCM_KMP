package com.leejlredstar.redefinencm.kmp.i18n

import java.util.Locale

/** The display language the JVM took from the OS user settings, then its default locale. */
actual fun systemLanguageTags(): List<String> = listOf(
    Locale.getDefault(Locale.Category.DISPLAY).toLanguageTag(),
    Locale.getDefault().toLanguageTag(),
).distinct()

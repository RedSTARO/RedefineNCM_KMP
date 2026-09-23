package com.leejlredstar.redefinencm.kmp.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/** The languages in the order set under Settings > General > Language & Region. */
actual fun systemLanguageTags(): List<String> =
    NSLocale.preferredLanguages.mapNotNull { it as? String }

package com.leejlredstar.redefinencm.kmp.i18n

/** The browser's `navigator.languages`, most preferred first. */
actual fun systemLanguageTags(): List<String> =
    navigatorLanguages().split(',').map(String::trim).filter(String::isNotEmpty)

private fun navigatorLanguages(): String =
    js("((navigator.languages && navigator.languages.length) ? navigator.languages : [navigator.language || '']).join(',')")

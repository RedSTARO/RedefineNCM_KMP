package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.sp
import com.leejlredstar.redefinencm.kmp.i18n.AppLanguage

/**
 * Material 3 baseline and emphasized roles. Emphasis changes weight and tracking, not size.
 * Both sets carry the platform font and locale, including Web's bundled CJK family.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun expressiveTypography(fontFamily: FontFamily, localeList: LocaleList): Typography {
    fun style(size: Int, line: Int, tracking: Float = 0f, weight: FontWeight = FontWeight.Normal) =
        TextStyle(
            fontFamily = fontFamily,
            localeList = localeList,
            fontSize = size.sp,
            lineHeight = line.sp,
            letterSpacing = tracking.sp,
            fontWeight = weight,
        )

    val medium = FontWeight.Medium
    val bold = FontWeight.Bold
    return Typography(
        displayLarge = style(57, 64, -0.2f),
        displayMedium = style(45, 52),
        displaySmall = style(36, 44),
        headlineLarge = style(32, 40),
        headlineMedium = style(28, 36),
        headlineSmall = style(24, 32),
        titleLarge = style(22, 28),
        titleMedium = style(16, 24, 0.2f, medium),
        titleSmall = style(14, 20, 0.1f, medium),
        bodyLarge = style(16, 24, 0.5f),
        bodyMedium = style(14, 20, 0.2f),
        bodySmall = style(12, 16, 0.4f),
        labelLarge = style(14, 20, 0.1f, medium),
        labelMedium = style(12, 16, 0.5f, medium),
        labelSmall = style(11, 16, 0.5f, medium),
        displayLargeEmphasized = style(57, 64, weight = medium),
        displayMediumEmphasized = style(45, 52, weight = medium),
        displaySmallEmphasized = style(36, 44, weight = medium),
        headlineLargeEmphasized = style(32, 40, weight = medium),
        headlineMediumEmphasized = style(28, 36, weight = medium),
        headlineSmallEmphasized = style(24, 32, weight = medium),
        titleLargeEmphasized = style(22, 28, weight = medium),
        titleMediumEmphasized = style(16, 24, 0.15f, bold),
        titleSmallEmphasized = style(14, 20, 0.1f, bold),
        bodyLargeEmphasized = style(16, 24, 0.15f, medium),
        bodyMediumEmphasized = style(14, 20, 0.25f, medium),
        bodySmallEmphasized = style(12, 16, 0.4f, medium),
        labelLargeEmphasized = style(14, 20, 0.1f, bold),
        labelMediumEmphasized = style(12, 16, 0.5f, bold),
        labelSmallEmphasized = style(11, 16, 0.5f, bold),
    )
}

/** English UI still shows mostly Chinese song titles, so Chinese is its second locale. */
internal fun AppLanguage.textLocales(): LocaleList = when (this) {
    AppLanguage.ZH -> LocaleList("zh-CN")
    AppLanguage.JA -> LocaleList("ja-JP")
    AppLanguage.EN -> LocaleList(Locale("en-US"), Locale("zh-CN"))
}

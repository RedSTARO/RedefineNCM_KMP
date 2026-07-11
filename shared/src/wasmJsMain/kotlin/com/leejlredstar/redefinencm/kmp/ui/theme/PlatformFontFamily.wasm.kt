package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import redefinencm_kmp.shared.generated.resources.Res
import redefinencm_kmp.shared.generated.resources.noto_sans_sc_variable

/** Full Noto Sans SC coverage is required for dynamic Chinese song titles and lyrics. */
@Composable
internal actual fun platformFontFamily(): FontFamily = FontFamily(
    Font(Res.font.noto_sans_sc_variable),
)

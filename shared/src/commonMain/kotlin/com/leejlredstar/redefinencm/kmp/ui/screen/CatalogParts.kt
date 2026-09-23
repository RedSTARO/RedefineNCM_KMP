package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette

/** What an artist or album page has to show: still loading, loaded, or failed. */
internal sealed interface CatalogLoad<out T> {
    data object Loading : CatalogLoad<Nothing>
    data object Failed : CatalogLoad<Nothing>
    data class Loaded<T>(val value: T) : CatalogLoad<T>
}

/** The row with the back button at the top of an artist or album page. */
@Composable
internal fun CatalogBackRow(accentPalette: ContentAccentPalette, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(AppIcons.ArrowBack, contentDescription = strings.back, tint = accentPalette.onPageStart)
        }
    }
}

/**
 * A NetEase release time as a date, `2020-09-01`. The service stamps releases at midnight
 * China time, so the date is read at UTC+8; read at UTC it would fall a day early.
 */
internal fun formatReleaseDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val days = (epochMillis + ChinaOffsetMillis).floorDiv(MillisPerDay)
    // Days since 1970-01-01 to a civil date (Howard Hinnant's algorithm).
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val dayOfEra = z - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthIndex = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthIndex + 2) / 5 + 1
    val month = if (monthIndex < 10) monthIndex + 3 else monthIndex - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private const val MillisPerDay = 86_400_000L
private const val ChinaOffsetMillis = 8 * 3_600_000L

package com.leejlredstar.redefinencm.kmp.ui.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette

/** Shared pieces so every presenter's card reads as one page. */

@Composable
internal fun loginTextFieldColors(palette: ContentAccentPalette): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = palette.quietContainer,
        unfocusedContainerColor = palette.quietContainer,
        focusedTextColor = palette.onQuietContainer,
        unfocusedTextColor = palette.onQuietContainer,
        focusedLabelColor = palette.accent,
        unfocusedLabelColor = palette.secondaryOnQuietContainer,
        focusedBorderColor = palette.accent,
        unfocusedBorderColor = palette.onQuietContainer.copy(alpha = 0.18f),
        cursorColor = palette.accent,
    )

/** A line the screen reader announces: an outcome, or an error when [isError]. */
@Composable
internal fun LoginNotice(
    text: String,
    palette: ContentAccentPalette,
    isError: Boolean = false,
    centered: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else palette.container,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else palette.onContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text,
            style = if (isError) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            textAlign = if (centered) TextAlign.Center else null,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

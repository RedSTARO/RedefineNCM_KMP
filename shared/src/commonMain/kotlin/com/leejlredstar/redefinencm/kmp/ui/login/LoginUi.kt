package com.leejlredstar.redefinencm.kmp.ui.login

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/** Forms give the next step weight without forcing every action into a full-width capsule. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LoginPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
) {
    val shapes = ButtonDefaults.shapes(shape = MaterialTheme.shapes.medium)
    if (primary) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, shapes = shapes, contentPadding = ButtonDefaults.SmallContentPadding) {
            Text(label, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    } else {
        FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = shapes, contentPadding = ButtonDefaults.SmallContentPadding) {
            Text(label)
        }
    }
}

@Composable
internal fun LoginSupportingText(text: String, isError: Boolean = false) {
    Text(text, modifier = Modifier.semantics { if (isError) liveRegion = LiveRegionMode.Polite })
}

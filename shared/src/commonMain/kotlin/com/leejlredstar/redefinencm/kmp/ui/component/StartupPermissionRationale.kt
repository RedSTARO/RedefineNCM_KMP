package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme

/**
 * Says what the runtime permissions are for before the system asks for them.
 *
 * Without it, the notification and audio-library prompts would fire at launch, back to back and
 * on top of the login page, with nothing saying why a music player wants either.
 */
@Composable
fun StartupPermissionRationale(
    needsNotifications: Boolean,
    needsAudioLibrary: Boolean,
    onContinue: () -> Unit,
    onLater: () -> Unit,
) {
    RedefineNCMTheme {
        AlertDialog(
            onDismissRequest = onLater,
            title = { Text(strings.permissionRationaleTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (needsNotifications) {
                        Text(
                            strings.permissionRationaleNotifications,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (needsAudioLibrary) {
                        Text(
                            strings.permissionRationaleAudio,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        strings.permissionRationaleFooter,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onContinue) { Text(strings.continueAction) } },
            dismissButton = { TextButton(onClick = onLater) { Text(strings.notNow) } },
        )
    }
}

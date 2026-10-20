package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme
import com.leejlredstar.redefinencm.kmp.ui.theme.SystemBarIcons

/**
 * What a platform shows instead of the app when its settings cannot be read at startup.
 *
 * The app cannot start on defaults: the HTTP client, the player and the services keep what they
 * read first for the life of the process, so a wrong server or a missing account would stay until
 * the next launch. Retrying reads the settings again.
 */
@Composable
fun SettingsLoadFailure(onRetry: () -> Unit) {
    RedefineNCMTheme {
        SystemBarIcons(darkBackground = MaterialTheme.colorScheme.surface.luminance() < 0.5f)
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                ExpressiveStatePanel(
                    title = strings.settingsLoadFailed,
                    message = strings.settingsLoadFailedAtStartup,
                    icon = AppIcons.ErrorOutline,
                    tone = ExpressiveStateTone.Error,
                    actionLabel = strings.retry,
                    onAction = onRetry,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            }
        }
    }
}

package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredential
import com.leejlredstar.redefinencm.kmp.data.provider.LibraryAggregationMode
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.lyric.LyricSourceMode
import com.leejlredstar.redefinencm.kmp.lyric.supportsDynamicNowPlayingCover
import com.leejlredstar.redefinencm.kmp.notification.LyricSurfaceAlignment
import com.leejlredstar.redefinencm.kmp.notification.OptionalLyricSurface
import com.leejlredstar.redefinencm.kmp.notification.WindowedLyricSurface
import com.leejlredstar.redefinencm.kmp.notification.lyricSurface
import com.leejlredstar.redefinencm.kmp.player.AudioOutputDevice
import com.leejlredstar.redefinencm.kmp.player.SYSTEM_DEFAULT_AUDIO_OUTPUT_ID
import com.leejlredstar.redefinencm.kmp.player.audioOutputDeviceName
import com.leejlredstar.redefinencm.kmp.player.availableAudioOutputDevices
import com.leejlredstar.redefinencm.kmp.player.resolveAudioOutputSelection
import com.leejlredstar.redefinencm.kmp.player.supportsAudioOutputDeviceSelection
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.component.rememberConnectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemeMode
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemePreferences
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.dynamicColorSupported
import com.leejlredstar.redefinencm.kmp.util.BuildInfo
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.applySettingsBackup
import com.leejlredstar.redefinencm.kmp.util.encodeSettingsBackup
import com.leejlredstar.redefinencm.kmp.util.getBooleanAsync
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import com.leejlredstar.redefinencm.kmp.util.rememberExportFileLauncher
import com.leejlredstar.redefinencm.kmp.util.rememberImportFileLauncher
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

// The rows the settings and accounts pages are built from. They lived in SettingsScreen, and the
// accounts page reaching into that file for them made one page depend on the other.

/**
 * Group label for a settings section.
 *
 * Settings groups are not page titles. ExpressiveSectionTitle renders at `headlineSmall`, which
 * is the right weight above the home carousels but announced every one of the seven groups here
 * at 25sp, so each one opened a large empty band and the rows below it read as unrelated
 * floating cards. A short accent-coloured label ties a group to the rows underneath it and
 * leaves the page title as the only large type on screen.
 */
@Composable
internal fun SettingsSectionLabel(
    text: String,
    accentPalette: ContentAccentPalette,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = accentPalette.accent,
        modifier = Modifier
            .semantics { heading() }
            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 8.dp),
    )
}

@Composable
internal fun SettingsTextField(
    value: String,
    label: String,
    obscureText: Boolean = false,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    supportingText: String? = null,
    onDraftChange: (String) -> Unit,
    onCommit: (String) -> Unit,
) {
    val textState = remember { mutableStateOf(value) }
    val committedTextState = remember { mutableStateOf(value) }
    var text by textState
    var isFocused by remember { mutableStateOf(false) }
    var revealText by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val latestCommit = rememberUpdatedState(onCommit)

    LaunchedEffect(value, isFocused) {
        if (!isFocused && value != textState.value) {
            textState.value = value
            committedTextState.value = value
        }
    }

    fun commit() {
        val draft = textState.value
        if (draft != committedTextState.value) {
            committedTextState.value = draft
            onCommit(draft)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val draft = textState.value
            if (draft != committedTextState.value) latestCommit.value(draft)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onDraftChange(it)
        },
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (obscureText && !revealText) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (obscureText) {
            {
                TextButton(onClick = { revealText = !revealText }) {
                    Text(if (revealText) "隐藏" else "显示")
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                commit()
                focusManager.clearFocus()
            },
        ),
        shape = connectedListItemShape(index, count),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (isFocused && !focusState.isFocused) commit()
                isFocused = focusState.isFocused
            }
            .padding(vertical = 1.5.dp)
            .heightIn(min = 64.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = accentPalette.quietContainer,
            unfocusedContainerColor = accentPalette.quietContainer,
            focusedTextColor = accentPalette.onQuietContainer,
            unfocusedTextColor = accentPalette.onQuietContainer,
            focusedLabelColor = accentPalette.accent,
            unfocusedLabelColor = accentPalette.secondaryOnQuietContainer,
            focusedBorderColor = accentPalette.accent,
            unfocusedBorderColor = accentPalette.onQuietContainer.copy(alpha = 0.18f),
            cursorColor = accentPalette.accent,
        ),
    )
}

@Composable
internal fun SettingsValue(
    label: String,
    value: String,
    supportingText: String,
    accentPalette: ContentAccentPalette,
    index: Int = 0,
    count: Int = 1,
) {
    Surface(
        shape = connectedListItemShape(index = index, count = count),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = accentPalette.secondaryOnQuietContainer,
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = accentPalette.secondaryOnQuietContainer,
            )
        }
    }
}

@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    label: String,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    supportingText: String? = null,
    enabled: Boolean = true,
    onUpdate: (Boolean) -> Unit,
) {
    var state by remember(checked) { mutableStateOf(checked) }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = rememberConnectedListItemShape(index, count, interactionSource),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ExpressiveLayout.ConnectedItemGap)
            .toggleable(
                value = state,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = ripple(),
                onValueChange = { updated ->
                    state = updated
                    onUpdate(updated)
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentPalette.secondaryOnQuietContainer,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = state,
                enabled = enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = accentPalette.onAccent,
                    checkedTrackColor = accentPalette.accent,
                    checkedBorderColor = accentPalette.accent,
            uncheckedThumbColor = accentPalette.secondaryOnQuietContainer,
                    uncheckedTrackColor = accentPalette.onQuietContainer.copy(alpha = 0.12f),
                    uncheckedBorderColor = accentPalette.onQuietContainer.copy(alpha = 0.24f),
                ),
            )
        }
    }
}

@Composable
internal fun SettingsButton(
    label: String,
    accentPalette: ContentAccentPalette,
    leadingIcon: ImageVector? = null,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 1.5.dp),
        shape = connectedListItemShape(index, count),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = accentPalette.container,
            contentColor = accentPalette.onContainer,
        ),
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

/** Who is signed in to one provider, and the two things to do about it. */
@Composable
internal fun AccountCard(
    loggedIn: Boolean,
    nickname: String?,
    avatarUrl: String?,
    /** Under the name when signed in: "网易云音乐账号". */
    providerLabel: String,
    /** Under "未登录": what signing in is for. */
    signedOutHint: String,
    accentPalette: ContentAccentPalette,
    /** Null where this platform cannot sign in; [signedOutHint] then says why. */
    onLogin: (() -> Unit)?,
    onLogout: () -> Unit,
    /** The card's place in its group of rows. */
    index: Int = 0,
    count: Int = 1,
) {
    Surface(
        shape = connectedListItemShape(index = index, count = count),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ExpressiveLayout.ConnectedItemGap),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accentPalette.container,
                contentColor = accentPalette.onContainer,
                modifier = Modifier.size(44.dp),
            ) {
                if (loggedIn && !avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Person, contentDescription = null)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        !loggedIn -> "未登录"
                        nickname.isNullOrBlank() -> "已登录"
                        else -> nickname
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (loggedIn) providerLabel else signedOutHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentPalette.secondaryOnQuietContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (loggedIn) {
                onLogin?.let { TextButton(onClick = it) { Text("切换账号") } }
                TextButton(onClick = onLogout) { Text("退出") }
            } else if (onLogin != null) {
                FilledTonalButton(
                    onClick = onLogin,
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = accentPalette.container,
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(AppIcons.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("登录")
                }
            }
        }
    }
}

/** A row that opens another page. */
@Composable
internal fun SettingsLinkRow(
    label: String,
    supportingText: String,
    accentPalette: ContentAccentPalette,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = rememberConnectedListItemShape(
            index = 0,
            count = 1,
            interactionSource = interactionSource,
        ),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ExpressiveLayout.ConnectedItemGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ExpressiveLayout.MinimumTouchTarget)
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentPalette.secondaryOnQuietContainer,
                )
            }
            Icon(
                imageVector = AppIcons.KeyboardArrowRight,
                contentDescription = "打开",
                tint = accentPalette.secondaryOnQuietContainer,
            )
        }
    }
}

/** A row that shows or hides an advanced setting beneath it. */
@Composable
internal fun SettingsExpanderRow(
    label: String,
    supportingText: String,
    expanded: Boolean,
    accentPalette: ContentAccentPalette,
    /** The row's place in its group; the field it opens takes the next place. */
    index: Int,
    count: Int,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onToggle,
        shape = rememberConnectedListItemShape(
            index = index,
            count = count,
            interactionSource = interactionSource,
        ),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ExpressiveLayout.ConnectedItemGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ExpressiveLayout.MinimumTouchTarget)
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentPalette.secondaryOnQuietContainer,
                )
            }
            Icon(
                imageVector = if (expanded) AppIcons.KeyboardArrowDown else AppIcons.KeyboardArrowRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = accentPalette.secondaryOnQuietContainer,
            )
        }
    }
}

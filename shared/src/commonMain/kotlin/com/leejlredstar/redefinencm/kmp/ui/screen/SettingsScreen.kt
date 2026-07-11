package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.applySettingsBackup
import com.leejlredstar.redefinencm.kmp.util.encodeSettingsBackup
import com.leejlredstar.redefinencm.kmp.util.rememberExportFileLauncher
import com.leejlredstar.redefinencm.kmp.util.rememberImportFileLauncher
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    scaffoldPadding: PaddingValues,
    onOpenLogin: () -> Unit,
    settings: PlatformSettings = koinInject(),
    api: NCMApi = koinInject(),
    mainViewModel: MainViewModel = koinInject(),
) {
    var cookie by remember(settings) { mutableStateOf("") }
    var server by remember(settings) { mutableStateOf("") }
    var onlineQuality by remember(settings) { mutableStateOf(SoundQuality.STANDARD.name) }
    var dlQuality by remember(settings) { mutableStateOf(SoundQuality.STANDARD.name) }
    var replacePlaylist by remember(settings) { mutableStateOf(false) }
    var checkUpdate by remember(settings) { mutableStateOf(false) }
    var searchPrediction by remember(settings) { mutableStateOf(true) }
    var showDownloadStatus by remember(settings) { mutableStateOf(false) }
    var extraLyricSurfaceEnabled by remember(settings) { mutableStateOf(false) }
    var showTranslatedLyric by remember(settings) { mutableStateOf(false) }
    var showRomanLyric by remember(settings) { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var serverCheckStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reloadSettingsSnapshot() {
        cookie = settings.getString(SettingKeys.COOKIE, "")
        server = settings.getString(SettingKeys.SERVER, "")
        onlineQuality = settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.STANDARD.name)
        dlQuality = settings.getString(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)
        replacePlaylist = settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, false)
        checkUpdate = settings.getBoolean(SettingKeys.CHECK_UPDATE, false)
        searchPrediction = settings.getBoolean(SettingKeys.SEARCH_PREDICTION, true)
        showDownloadStatus = settings.getBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, false)
        extraLyricSurfaceEnabled = settings.getBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, false)
        LyricNotificationController.setOptionalSurfaceEnabled(extraLyricSurfaceEnabled)
        showTranslatedLyric = settings.getBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, false)
        showRomanLyric = settings.getBoolean(SettingKeys.SHOW_ROMAN_LYRIC, false)
    }

    fun flushSettings(
        onPersisted: () -> Unit = {},
        onFailure: () -> Unit = ::reloadSettingsSnapshot,
    ) {
        scope.launch {
            runCatching { settings.flush() }
                .onSuccess { onPersisted() }
                .onFailure { error ->
                    onFailure()
                    importStatus = "✗ 设置保存失败：${error.message ?: "未知错误"}"
                }
        }
    }

    fun persistSettings(
        write: () -> Unit,
        onWritten: () -> Unit = {},
        onPersisted: () -> Unit = {},
        onFailure: () -> Unit = ::reloadSettingsSnapshot,
    ) {
        val writeResult = runCatching(write)
        if (writeResult.isFailure) {
            onFailure()
            importStatus = "✗ 设置保存失败：${writeResult.exceptionOrNull()?.message ?: "未知错误"}"
            return
        }
        onWritten()
        flushSettings(onPersisted = onPersisted, onFailure = onFailure)
    }

    LaunchedEffect(settings) {
        cookie = settings.getStringAsync(SettingKeys.COOKIE, "")
        server = settings.getStringAsync(SettingKeys.SERVER, "")
        onlineQuality = settings.getStringAsync(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.STANDARD.name)
        dlQuality = settings.getStringAsync(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)
        replacePlaylist = settings.getBooleanAsync(SettingKeys.REPLACE_PLAYLIST, false)
        checkUpdate = settings.getBooleanAsync(SettingKeys.CHECK_UPDATE, false)
        searchPrediction = settings.getBooleanAsync(SettingKeys.SEARCH_PREDICTION, true)
        showDownloadStatus = settings.getBooleanAsync(SettingKeys.SHOW_DOWNLOAD_STATUS, false)
        extraLyricSurfaceEnabled = settings.getBooleanAsync(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, false)
        LyricNotificationController.setOptionalSurfaceEnabled(extraLyricSurfaceEnabled)
        showTranslatedLyric = settings.getBooleanAsync(SettingKeys.SHOW_TRANSLATED_LYRIC, false)
        showRomanLyric = settings.getBooleanAsync(SettingKeys.SHOW_ROMAN_LYRIC, false)
    }

    val launchImport = rememberImportFileLauncher { json ->
        scope.launch {
            if (applySettingsBackup(json, settings)) {
                val persisted = runCatching { settings.flush() }
                if (persisted.isFailure) {
                    reloadSettingsSnapshot()
                    importStatus = "✗ 设置保存失败：${persisted.exceptionOrNull()?.message ?: "未知错误"}"
                    return@launch
                }
                reloadSettingsSnapshot()
                importStatus = "✓ 导入成功"
            } else {
                importStatus = "✗ 导入失败，请检查文件格式"
            }
        }
    }
    val launchExport = rememberExportFileLauncher()
    val settingsPalette = contentAccentPalette(MaterialTheme.colorScheme.secondaryContainer)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = scaffoldPadding.calculateBottomPadding())
            .background(
                Brush.verticalGradient(
                    listOf(
                        settingsPalette.pageStart,
                        settingsPalette.pageMiddle,
                        settingsPalette.pageEnd,
                    ),
                ),
            ),
    ) {
        SettingsHero(settingsPalette)

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                ExpressiveSectionTitle("Server", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsTextField(
                    value = server,
                    label = "Server URL",
                    accentPalette = settingsPalette,
                    index = 0,
                    count = 2,
                    onDraftChange = { server = it },
                    onCommit = { raw ->
                        val normalized = normalizeServerInput(raw)
                        server = normalized
                        persistSettings({ settings.setString(SettingKeys.SERVER, normalized) })
                    },
                )
                // 原版 ServerItem：调 /inner/version/ 校验服务器可用性并显示版本
                SettingsButton("检查服务器 ($server)", settingsPalette, index = 1, count = 2) {
                    val checkedServer = normalizeServerInput(server)
                    if (checkedServer.isEmpty()) {
                        serverCheckStatus = "服务器地址不能为空"
                        return@SettingsButton
                    }
                    serverCheckStatus = "检查中…"
                    scope.launch {
                        serverCheckStatus = try {
                            val result = api.innerVersion("${checkedServer}inner/version/")
                            if (result.code == 200) "服务器可用，版本：${result.data.version}"
                            else "服务器不可用（code ${result.code}）"
                        } catch (e: Exception) {
                            "服务器不可用：${e.message}"
                        }
                    }
                }
                serverCheckStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.startsWith("服务器可用")) settingsPalette.accent
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    )
                }

                ExpressiveSectionTitle("Account", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsButton(
                    label = if (cookie.isBlank()) "扫码 / 登录" else "重新登录 / 换号",
                    leadingIcon = AppIcons.QrCode2,
                    accentPalette = settingsPalette,
                    index = 0,
                    count = 2,
                    onClick = onOpenLogin,
                )
                SettingsTextField(
                    value = cookie,
                    label = "Cookie",
                    accentPalette = settingsPalette,
                    index = 1,
                    count = 2,
                    onDraftChange = { cookie = it },
                    onCommit = { raw ->
                        val normalized = raw.trim()
                        cookie = normalized
                        persistSettings(
                            write = { settings.setString(SettingKeys.COOKIE, normalized) },
                            // Stop old-account work as soon as the process cookie changes. The
                            // refresh waits on the same settings barrier before resolving UID.
                            onWritten = { mainViewModel.refreshAccount() },
                            onFailure = {
                                reloadSettingsSnapshot()
                                mainViewModel.refreshAccount()
                            },
                        )
                    },
                )

                ExpressiveSectionTitle("Playback", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsDropdown(onlineQuality, "Music Quality (Online)", SoundQuality.entries, settingsPalette, index = 0, count = 5) { v ->
                    onlineQuality = v.name
                    persistSettings({ settings.setString(SettingKeys.ONLINE_PLAY_QUALITY, v.name) })
                }
                SettingsDropdown(dlQuality, "Music Quality (Download)", SoundQuality.entries, settingsPalette, index = 1, count = 5) { v ->
                    dlQuality = v.name
                    persistSettings({ settings.setString(SettingKeys.DOWNLOAD_QUALITY, v.name) })
                }
                SettingsSwitch(replacePlaylist, "Replace playlist on single song click", settingsPalette, index = 2, count = 5) { v ->
                    replacePlaylist = v
                    persistSettings({ settings.setBoolean(SettingKeys.REPLACE_PLAYLIST, v) })
                }
                SettingsSwitch(searchPrediction, "Search prediction", settingsPalette, index = 3, count = 5) { v ->
                    searchPrediction = v
                    persistSettings({ settings.setBoolean(SettingKeys.SEARCH_PREDICTION, v) })
                }
                SettingsSwitch(showDownloadStatus, "Show download status", settingsPalette, index = 4, count = 5) { v ->
                    showDownloadStatus = v
                    persistSettings({ settings.setBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, v) })
                }

                ExpressiveSectionTitle("Lyrics", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                val lyricSettingCount = if (LyricNotificationController.supportsOptionalSurfaceControl) 3 else 2
                if (LyricNotificationController.supportsOptionalSurfaceControl) {
                    SettingsSwitch(
                        extraLyricSurfaceEnabled,
                        LyricNotificationController.optionalSurfaceSettingLabel,
                        settingsPalette,
                        index = 0,
                        count = lyricSettingCount,
                    ) { enabled ->
                        extraLyricSurfaceEnabled = enabled
                        persistSettings(
                            write = { settings.setBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, enabled) },
                            onWritten = {
                                LyricNotificationController.setOptionalSurfaceEnabled(enabled)
                            },
                        )
                    }
                }
                SettingsSwitch(showTranslatedLyric, "显示翻译歌词", settingsPalette, index = if (lyricSettingCount == 3) 1 else 0, count = lyricSettingCount) { v ->
                    showTranslatedLyric = v
                    persistSettings({ settings.setBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, v) })
                }
                SettingsSwitch(showRomanLyric, "显示五十音 / 罗马音歌词", settingsPalette, index = lyricSettingCount - 1, count = lyricSettingCount) { v ->
                    showRomanLyric = v
                    persistSettings({ settings.setBoolean(SettingKeys.SHOW_ROMAN_LYRIC, v) })
                }

                ExpressiveSectionTitle("General", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsSwitch(checkUpdate, "Check update on startup", settingsPalette, index = 0, count = 1) { v ->
                    checkUpdate = v
                    persistSettings({ settings.setBoolean(SettingKeys.CHECK_UPDATE, v) })
                }

                ExpressiveSectionTitle("Backup", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsButton("Export settings", settingsPalette, index = 0, count = 2) { launchExport(encodeSettingsBackup(settings)) }
                SettingsButton("Import settings", settingsPalette, index = 1, count = 2) { launchImport() }
                importStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.startsWith("✓")) settingsPalette.accent
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    )
                }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SettingsHero(accentPalette: ContentAccentPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(188.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        accentPalette.pageStart,
                        accentPalette.container,
                        Color.Transparent,
                    ),
                ),
            )
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = accentPalette.onContainer,
            )
            Text(
                text = "Account, playback, cache and backup",
                style = MaterialTheme.typography.titleMedium,
                color = accentPalette.onQuietContainer.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    label: String,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    onDraftChange: (String) -> Unit,
    onCommit: (String) -> Unit,
) {
    val textState = remember { mutableStateOf(value) }
    val committedTextState = remember { mutableStateOf(value) }
    var text by textState
    var isFocused by remember { mutableStateOf(false) }
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
        singleLine = true,
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
            .height(64.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = accentPalette.quietContainer,
            unfocusedContainerColor = accentPalette.quietContainer,
            focusedTextColor = accentPalette.onQuietContainer,
            unfocusedTextColor = accentPalette.onQuietContainer,
            focusedLabelColor = accentPalette.accent,
            unfocusedLabelColor = accentPalette.onQuietContainer.copy(alpha = 0.72f),
            focusedBorderColor = accentPalette.accent,
            unfocusedBorderColor = accentPalette.onQuietContainer.copy(alpha = 0.18f),
            cursorColor = accentPalette.accent,
        ),
    )
}

private fun normalizeServerInput(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.isEmpty()) "" else "${trimmed.trimEnd('/')}/"
}

@Composable
private fun SettingsSwitch(
    checked: Boolean,
    label: String,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    onUpdate: (Boolean) -> Unit,
) {
    var state by remember(checked) { mutableStateOf(checked) }
    Surface(
        shape = connectedListItemShape(index, count),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = state,
                onCheckedChange = { state = it; onUpdate(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = accentPalette.onAccent,
                    checkedTrackColor = accentPalette.accent,
                    checkedBorderColor = accentPalette.accent,
                    uncheckedThumbColor = accentPalette.onQuietContainer.copy(alpha = 0.70f),
                    uncheckedTrackColor = accentPalette.onQuietContainer.copy(alpha = 0.12f),
                    uncheckedBorderColor = accentPalette.onQuietContainer.copy(alpha = 0.24f),
                ),
            )
        }
    }
}

@Composable
private fun SettingsDropdown(
    selectedName: String,
    label: String,
    options: List<SoundQuality>,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    onUpdate: (SoundQuality) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.find { it.name == selectedName } ?: options.first()
    Surface(
        onClick = { expanded = true },
        shape = connectedListItemShape(index, count),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentPalette.onQuietContainer.copy(alpha = 0.72f),
                )
                Text(text = current.toString(), style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(AppIcons.ArrowDropDown, contentDescription = null, tint = accentPalette.accent)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.toString()) },
                    onClick = { expanded = false; onUpdate(opt) },
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(
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

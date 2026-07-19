package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.lyric.supportsDynamicNowPlayingCover
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.BuildInfo
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.applySettingsBackup
import com.leejlredstar.redefinencm.kmp.util.encodeSettingsBackup
import com.leejlredstar.redefinencm.kmp.util.rememberExportFileLauncher
import com.leejlredstar.redefinencm.kmp.util.rememberImportFileLauncher
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.CancellationException
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
    nowPlayingViewModel: NowPlayingViewModel = koinInject(),
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
    var useDynamicCover by remember(settings) { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var serverCheckStatus by remember { mutableStateOf<String?>(null) }
    var settingsLoaded by remember(settings) { mutableStateOf(false) }
    var settingsLoadError by remember(settings) { mutableStateOf<String?>(null) }
    var settingsLoadRequest by remember(settings) { mutableIntStateOf(0) }
    var serverCheckGeneration by remember { mutableIntStateOf(0) }
    var showImportConfirmation by remember { mutableStateOf(false) }
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
        useDynamicCover = settings.getBoolean(SettingKeys.USE_DYNAMIC_COVER, false)
        nowPlayingViewModel.setUseDynamicCover(useDynamicCover)
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
        importStatus = null
        val writeResult = runCatching(write)
        if (writeResult.isFailure) {
            onFailure()
            importStatus = "✗ 设置保存失败：${writeResult.exceptionOrNull()?.message ?: "未知错误"}"
            return
        }
        onWritten()
        flushSettings(onPersisted = onPersisted, onFailure = onFailure)
    }

    LaunchedEffect(settings, settingsLoadRequest) {
        settingsLoaded = false
        settingsLoadError = null
        try {
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
            useDynamicCover = settings.getBooleanAsync(SettingKeys.USE_DYNAMIC_COVER, false)
            nowPlayingViewModel.setUseDynamicCover(useDynamicCover)
            settingsLoaded = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            settingsLoadError = failure.message ?: "设置读取失败"
        }
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

    ExpressivePage(
        accentPalette = settingsPalette,
        maxContentWidth = ExpressiveLayout.ReadingContentMaxWidth,
        contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsHero(settingsPalette)

            if (settingsLoadError != null) {
                ExpressiveStatePanel(
                    title = "设置读取失败",
                    message = settingsLoadError.orEmpty(),
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    accentPalette = settingsPalette,
                    actionLabel = "重试",
                    onAction = { settingsLoadRequest += 1 },
                    modifier = Modifier.padding(20.dp),
                )
            } else if (!settingsLoaded) {
                ExpressiveLoadingState(
                    label = "正在加载设置…",
                    accentColor = settingsPalette.accent,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                importStatus?.let { status ->
                    val isSuccess = status.startsWith("✓")
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = if (isSuccess) {
                            settingsPalette.container
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        contentColor = if (isSuccess) {
                            settingsPalette.onContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                ExpressiveSectionTitle("服务器", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsTextField(
                    value = server,
                    label = "服务器地址",
                    accentPalette = settingsPalette,
                    index = 0,
                    count = 2,
                    onDraftChange = {
                        server = it
                        serverCheckGeneration += 1
                        serverCheckStatus = null
                    },
                    onCommit = { raw ->
                        val normalized = normalizeServerInput(raw)
                        server = normalized
                        persistSettings({ settings.setString(SettingKeys.SERVER, normalized) })
                    },
                )
                // 原版 ServerItem：调 /inner/version/ 校验服务器可用性并显示版本
                SettingsButton("检查服务器", settingsPalette, index = 1, count = 2) {
                    val checkedServer = normalizeServerInput(server)
                    val checkGeneration = ++serverCheckGeneration
                    if (checkedServer.isEmpty()) {
                        serverCheckStatus = "服务器地址不能为空"
                        return@SettingsButton
                    }
                    serverCheckStatus = "检查中…"
                    scope.launch {
                        val resultStatus = try {
                            val result = api.innerVersion("${checkedServer}inner/version/")
                            if (result.code == 200) "服务器可用，版本：${result.data.version}"
                            else "服务器不可用（code ${result.code}）"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            "服务器不可用：${e.message}"
                        }
                        if (
                            checkGeneration == serverCheckGeneration &&
                            normalizeServerInput(server) == checkedServer
                        ) {
                            serverCheckStatus = resultStatus
                        }
                    }
                }
                serverCheckStatus?.let { status ->
                    val success = status.startsWith("服务器可用")
                    val checking = status.startsWith("检查中")
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = when {
                            success -> settingsPalette.container
                            checking -> settingsPalette.quietContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        },
                        contentColor = when {
                            success -> settingsPalette.onContainer
                            checking -> settingsPalette.onQuietContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }

                ExpressiveSectionTitle("账号", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
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
                    obscureText = true,
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

                ExpressiveSectionTitle("播放", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                val playbackSettingCount = if (supportsDynamicNowPlayingCover) 6 else 5
                SettingsDropdown(onlineQuality, "在线播放音质", SoundQuality.entries, settingsPalette, index = 0, count = playbackSettingCount) { v ->
                    onlineQuality = v.name
                    persistSettings({ settings.setString(SettingKeys.ONLINE_PLAY_QUALITY, v.name) })
                }
                SettingsDropdown(dlQuality, "下载音质", SoundQuality.entries, settingsPalette, index = 1, count = playbackSettingCount) { v ->
                    dlQuality = v.name
                    persistSettings({ settings.setString(SettingKeys.DOWNLOAD_QUALITY, v.name) })
                }
                SettingsSwitch(replacePlaylist, "点击单曲时替换播放队列", settingsPalette, index = 2, count = playbackSettingCount) { v ->
                    replacePlaylist = v
                    persistSettings({ settings.setBoolean(SettingKeys.REPLACE_PLAYLIST, v) })
                }
                SettingsSwitch(searchPrediction, "搜索联想", settingsPalette, index = 3, count = playbackSettingCount) { v ->
                    searchPrediction = v
                    persistSettings({ settings.setBoolean(SettingKeys.SEARCH_PREDICTION, v) })
                }
                SettingsSwitch(showDownloadStatus, "显示下载状态", settingsPalette, index = 4, count = playbackSettingCount) { v ->
                    showDownloadStatus = v
                    persistSettings({ settings.setBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, v) })
                }
                if (supportsDynamicNowPlayingCover) {
                    SettingsSwitch(
                        useDynamicCover,
                        "播放页使用歌曲动态封面",
                        settingsPalette,
                        index = 5,
                        count = playbackSettingCount,
                    ) { enabled ->
                        useDynamicCover = enabled
                        persistSettings(
                            write = { settings.setBoolean(SettingKeys.USE_DYNAMIC_COVER, enabled) },
                            onWritten = { nowPlayingViewModel.setUseDynamicCover(enabled) },
                        )
                    }
                }

                ExpressiveSectionTitle("歌词", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
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

                ExpressiveSectionTitle("通用", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsSwitch(checkUpdate, "启动时检查更新", settingsPalette, index = 0, count = 1) { v ->
                    checkUpdate = v
                    persistSettings({ settings.setBoolean(SettingKeys.CHECK_UPDATE, v) })
                }

                ExpressiveSectionTitle("应用", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsValue(
                    label = "版本",
                    value = BuildInfo.VERSION_NAME,
                    supportingText = "Build ${BuildInfo.VERSION_CODE}",
                    accentPalette = settingsPalette,
                )

                ExpressiveSectionTitle("备份", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsButton("导出设置", settingsPalette, index = 0, count = 2) { launchExport(encodeSettingsBackup(settings)) }
                SettingsButton("导入设置", settingsPalette, index = 1, count = 2) {
                    showImportConfirmation = true
                }

                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }

    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            title = { Text("导入并覆盖当前设置？") },
            text = {
                Text("导入文件会覆盖账号 Cookie、服务器地址、播放与歌词偏好。建议先导出当前设置作为备份。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmation = false
                        launchImport()
                    },
                ) {
                    Text("选择文件")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmation = false }) {
                    Text("取消")
                }
            },
        )
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
                text = "设置",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = accentPalette.onPageStart,
            )
            Text(
                text = "账号、播放、歌词与备份",
                style = MaterialTheme.typography.titleMedium,
                color = accentPalette.secondaryOnPageStart,
            )
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    label: String,
    obscureText: Boolean = false,
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

private fun normalizeServerInput(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.isEmpty()) "" else "${trimmed.trimEnd('/')}/"
}

@Composable
private fun SettingsValue(
    label: String,
    value: String,
    supportingText: String,
    accentPalette: ContentAccentPalette,
) {
    Surface(
        shape = connectedListItemShape(index = 0, count = 1),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp)
            .toggleable(
                value = state,
                role = Role.Switch,
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
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = state,
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
                    color = accentPalette.secondaryOnQuietContainer,
                )
                Text(text = current.toString(), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(
                AppIcons.ArrowDropDown,
                contentDescription = null,
                tint = accentPalette.accent,
                modifier = Modifier.padding(12.dp),
            )
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

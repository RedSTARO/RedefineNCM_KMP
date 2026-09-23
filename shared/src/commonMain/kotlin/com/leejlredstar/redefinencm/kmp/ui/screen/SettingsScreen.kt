package com.leejlredstar.redefinencm.kmp.ui.screen

import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import com.leejlredstar.redefinencm.kmp.util.getBooleanAsync
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.lyric.LyricSourceMode
import com.leejlredstar.redefinencm.kmp.lyric.supportsDynamicNowPlayingCover
import com.leejlredstar.redefinencm.kmp.notification.OptionalLyricSurface
import com.leejlredstar.redefinencm.kmp.notification.WindowedLyricSurface
import com.leejlredstar.redefinencm.kmp.notification.lyricSurface
import com.leejlredstar.redefinencm.kmp.notification.LyricSurfaceAlignment
import com.leejlredstar.redefinencm.kmp.player.AudioOutputDevice
import com.leejlredstar.redefinencm.kmp.player.SYSTEM_DEFAULT_AUDIO_OUTPUT_ID
import com.leejlredstar.redefinencm.kmp.player.audioOutputDeviceName
import com.leejlredstar.redefinencm.kmp.player.availableAudioOutputDevices
import com.leejlredstar.redefinencm.kmp.player.resolveAudioOutputSelection
import com.leejlredstar.redefinencm.kmp.player.supportsAudioOutputDeviceSelection
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.component.rememberConnectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsSectionLabel
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsTextField
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsValue
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsSwitch
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsButton
import com.leejlredstar.redefinencm.kmp.ui.component.AccountCard
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsLinkRow
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsExpanderRow
import com.leejlredstar.redefinencm.kmp.util.BuildInfo
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.applySettingsBackup
import com.leejlredstar.redefinencm.kmp.util.encodeSettingsBackup
import com.leejlredstar.redefinencm.kmp.util.decodeBackupLocalLibrary
import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryStore
import com.leejlredstar.redefinencm.kmp.util.rememberExportFileLauncher
import com.leejlredstar.redefinencm.kmp.util.rememberImportFileLauncher
import com.leejlredstar.redefinencm.kmp.viewmodel.AccountsViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemeMode
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemePreferences
import com.leejlredstar.redefinencm.kmp.ui.theme.dynamicColorSupported

/**
 * The lyric surface's capabilities, as this target has them.
 *
 * A capability is a type, so no target has to declare members for what it cannot do: a target
 * that cannot switch its lyric surface off is not an [OptionalLyricSurface], and these are null
 * there.
 */
private val optionalLyricSurface: OptionalLyricSurface? = lyricSurface as? OptionalLyricSurface

private val windowedLyricSurface: WindowedLyricSurface? = lyricSurface as? WindowedLyricSurface

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    scaffoldPadding: PaddingValues,
    /** Opens the accounts page, where every provider's account and the local one live. */
    onOpenAccounts: () -> Unit,
    /** Settings is a page opened from the library and the sidebar, so it has a way back. */
    onBack: (() -> Unit)? = null,
    settings: PlatformSettings = koinInject(),
    mainViewModel: MainViewModel = koinInject(),
    accountsViewModel: AccountsViewModel = koinInject(),
    localLibrary: LocalLibraryStore = koinInject(),
    nowPlayingViewModel: NowPlayingViewModel = koinInject(),
) {
    var onlineQuality by remember(settings) { mutableStateOf(SoundQuality.STANDARD.name) }
    var dlQuality by remember(settings) { mutableStateOf(SoundQuality.STANDARD.name) }
    var replacePlaylist by remember(settings) { mutableStateOf(SettingKeys.REPLACE_PLAYLIST_DEFAULT) }
    var checkUpdate by remember(settings) { mutableStateOf(false) }
    var searchPrediction by remember(settings) { mutableStateOf(true) }
    var showDownloadStatus by remember(settings) { mutableStateOf(SettingKeys.SHOW_DOWNLOAD_STATUS_DEFAULT) }
    var closeToTray by remember(settings) { mutableStateOf(SettingKeys.DESKTOP_CLOSE_TO_TRAY_DEFAULT) }
    val themeMode by ThemePreferences.mode.collectAsState()
    val dynamicColor by ThemePreferences.dynamicColor.collectAsState()
    val isDesktop = remember { getPlatform().isDesktop }
    var extraLyricSurfaceEnabled by remember(settings) { mutableStateOf(false) }
    var desktopLyricLocked by remember(settings) { mutableStateOf(false) }
    // The lyric window's own toolbar and the tray menu also close and lock it; the switches
    // follow the window, not only what was stored when this page opened.
    if (windowedLyricSurface != null) {
        LaunchedEffect(windowedLyricSurface) {
            windowedLyricSurface.isEnabled.collect { extraLyricSurfaceEnabled = it }
        }
        LaunchedEffect(windowedLyricSurface) {
            windowedLyricSurface.isWindowLocked.collect { desktopLyricLocked = it }
        }
    }
    var desktopLyricAlignment by remember(settings) {
        mutableStateOf(LyricSurfaceAlignment.DEFAULT)
    }
    var showTranslatedLyric by remember(settings) { mutableStateOf(SettingKeys.SHOW_TRANSLATED_LYRIC_DEFAULT) }
    var showRomanLyric by remember(settings) { mutableStateOf(false) }
    var lyricSourceMode by remember(settings) {
        mutableStateOf(LyricSourceMode.DEFAULT.wireValue)
    }
    var useDynamicCover by remember(settings) { mutableStateOf(false) }
    var audioOutputDeviceId by remember(settings) {
        mutableStateOf(SYSTEM_DEFAULT_AUDIO_OUTPUT_ID)
    }
    var settingsLoaded by remember(settings) { mutableStateOf(false) }
    var settingsLoadError by remember(settings) { mutableStateOf<String?>(null) }
    var settingsLoadRequest by remember(settings) { mutableIntStateOf(0) }
    var lyricSourceWriteGeneration by remember { mutableIntStateOf(0) }
    var lyricDisplayWriteGeneration by remember { mutableIntStateOf(0) }
    var showImportConfirmation by remember { mutableStateOf(false) }
    val accountsSummary by accountsViewModel.summary.collectAsState()
    // Results of saving, importing and exporting appear at the bottom, beside the controls that
    // cause them; a banner at the top of the page would be off screen by the time the backup
    // buttons are reached.
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settingsMessage) {
        val message = settingsMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        settingsMessage = null
    }
    val scope = rememberCoroutineScope()

    fun reloadSettingsSnapshot() {
        // The provider switches and addresses live on the accounts page, which reads its own.
        accountsViewModel.reload()
        onlineQuality = settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.STANDARD.name)
        dlQuality = settings.getString(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)
        replacePlaylist = settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT)
        checkUpdate = settings.getBoolean(SettingKeys.CHECK_UPDATE, false)
        searchPrediction = settings.getBoolean(SettingKeys.SEARCH_PREDICTION, true)
        showDownloadStatus = settings.getBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, SettingKeys.SHOW_DOWNLOAD_STATUS_DEFAULT)
        closeToTray = settings.getBoolean(SettingKeys.DESKTOP_CLOSE_TO_TRAY, SettingKeys.DESKTOP_CLOSE_TO_TRAY_DEFAULT)
        extraLyricSurfaceEnabled = settings.getBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, false)
        optionalLyricSurface?.setEnabled(extraLyricSurfaceEnabled)
        desktopLyricLocked = settings.getBoolean(SettingKeys.DESKTOP_LYRIC_LOCKED, false)
        desktopLyricAlignment = LyricSurfaceAlignment.fromWireValueOrDefault(
            settings.getString(SettingKeys.DESKTOP_LYRIC_ALIGNMENT, ""),
        )
        windowedLyricSurface?.setLocked(desktopLyricLocked)
        windowedLyricSurface?.setAlignment(desktopLyricAlignment)
        showTranslatedLyric = settings.getBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, SettingKeys.SHOW_TRANSLATED_LYRIC_DEFAULT)
        showRomanLyric = settings.getBoolean(SettingKeys.SHOW_ROMAN_LYRIC, false)
        lyricSourceMode = LyricSourceMode.fromStoredWireValue(
            settings.getString(
                SettingKeys.LYRIC_SOURCE_MODE,
                LyricSourceMode.DEFAULT.wireValue,
            ),
        ).wireValue
        nowPlayingViewModel.setLyricDisplayOptions(
            showTranslation = showTranslatedLyric,
            showRomanization = showRomanLyric,
        )
        nowPlayingViewModel.setLyricSourceMode(
            LyricSourceMode.fromStoredWireValue(lyricSourceMode),
        )
        useDynamicCover = settings.getBoolean(SettingKeys.USE_DYNAMIC_COVER, false)
        nowPlayingViewModel.setUseDynamicCover(useDynamicCover)
        audioOutputDeviceId = settings.getString(
            SettingKeys.AUDIO_OUTPUT_DEVICE,
            SYSTEM_DEFAULT_AUDIO_OUTPUT_ID,
        )
    }

    fun flushSettings(
        onPersisted: () -> Unit = {},
        onFailure: () -> Unit = ::reloadSettingsSnapshot,
    ) {
        // Start undispatched so Android enqueues its DataStore barrier before another UI write
        // can overtake it and make an older flush consume a newer write failure.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching { settings.flush() }
                .onSuccess { onPersisted() }
                .onFailure { error ->
                    onFailure()
                    settingsMessage = "设置保存失败：${error.message ?: "未知错误"}"
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
            settingsMessage = "设置保存失败：${writeResult.exceptionOrNull()?.message ?: "未知错误"}"
            return
        }
        onWritten()
        flushSettings(onPersisted = onPersisted, onFailure = onFailure)
    }

    LaunchedEffect(settings, settingsLoadRequest) {
        settingsLoaded = false
        settingsLoadError = null
        try {
            onlineQuality = settings.getStringAsync(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.STANDARD.name)
            dlQuality = settings.getStringAsync(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)
            replacePlaylist = settings.getBooleanAsync(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT)
            checkUpdate = settings.getBooleanAsync(SettingKeys.CHECK_UPDATE, false)
            searchPrediction = settings.getBooleanAsync(SettingKeys.SEARCH_PREDICTION, true)
            showDownloadStatus = settings.getBooleanAsync(SettingKeys.SHOW_DOWNLOAD_STATUS, SettingKeys.SHOW_DOWNLOAD_STATUS_DEFAULT)
            closeToTray = settings.getBooleanAsync(
                SettingKeys.DESKTOP_CLOSE_TO_TRAY,
                SettingKeys.DESKTOP_CLOSE_TO_TRAY_DEFAULT,
            )
            extraLyricSurfaceEnabled = settings.getBooleanAsync(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, false)
            optionalLyricSurface?.setEnabled(extraLyricSurfaceEnabled)
            desktopLyricLocked = settings.getBooleanAsync(SettingKeys.DESKTOP_LYRIC_LOCKED, false)
            desktopLyricAlignment = LyricSurfaceAlignment.fromWireValueOrDefault(
                settings.getStringAsync(SettingKeys.DESKTOP_LYRIC_ALIGNMENT, ""),
            )
            windowedLyricSurface?.setLocked(desktopLyricLocked)
            windowedLyricSurface?.setAlignment(desktopLyricAlignment)
            showTranslatedLyric = settings.getBooleanAsync(SettingKeys.SHOW_TRANSLATED_LYRIC, SettingKeys.SHOW_TRANSLATED_LYRIC_DEFAULT)
            showRomanLyric = settings.getBooleanAsync(SettingKeys.SHOW_ROMAN_LYRIC, false)
            lyricSourceMode = LyricSourceMode.fromStoredWireValue(
                settings.getStringAsync(
                    SettingKeys.LYRIC_SOURCE_MODE,
                    LyricSourceMode.DEFAULT.wireValue,
                ),
            ).wireValue
            nowPlayingViewModel.setLyricDisplayOptions(
                showTranslation = showTranslatedLyric,
                showRomanization = showRomanLyric,
            )
            nowPlayingViewModel.setLyricSourceMode(
                LyricSourceMode.fromStoredWireValue(lyricSourceMode),
            )
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
                decodeBackupLocalLibrary(json)?.let { playlists ->
                    localLibrary.importPlaylists(playlists).onFailure { failure ->
                        settingsMessage = "本地歌单导入失败：${failure.message ?: "未知错误"}"
                    }
                }
                lyricSourceWriteGeneration += 1
                lyricDisplayWriteGeneration += 1
                // Apply privacy-sensitive source changes from the process snapshot immediately.
                // Android rolls that snapshot back before flush() reports a failed durable write.
                reloadSettingsSnapshot()
                val persisted = runCatching { settings.flush() }
                if (persisted.isFailure) {
                    reloadSettingsSnapshot()
                    settingsMessage = "设置保存失败：${persisted.exceptionOrNull()?.message ?: "未知错误"}"
                    return@launch
                }
                reloadSettingsSnapshot()
                settingsMessage = "设置已导入"
            } else {
                lyricSourceWriteGeneration += 1
                lyricDisplayWriteGeneration += 1
                // A platform write can fail after earlier backup fields were already applied.
                // Re-read the process snapshot so a partial source change is never left latent.
                reloadSettingsSnapshot()
                settingsMessage = "导入失败：文件不是有效的设置备份"
            }
        }
    }
    val launchExport = rememberExportFileLauncher()
    val settingsPalette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)

    // The page title is a LargeFlexibleTopAppBar rather than a hand-rolled hero Box. The bar
    // owns the collapse: it starts large and shrinks to a compact title as the page scrolls,
    // which a fixed 188dp gradient header cannot do. Its container is transparent so
    // ExpressivePage's gradient reads through, and the Scaffold here exists only to give the
    // bar somewhere to live and to hand back its measured height.
    val appBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    ExpressivePage(
        accentPalette = settingsPalette,
        maxContentWidth = ExpressiveLayout.ReadingContentMaxWidth,
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(appBarScrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text("设置") },
                    subtitle = { Text("账号、播放、歌词、下载与服务器") },
                    navigationIcon = {
                        onBack?.let {
                            IconButton(onClick = it) {
                                Icon(AppIcons.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    scrollBehavior = appBarScrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        // Transparent while expanded so ExpressivePage's gradient reads through,
                        // but the collapsed bar stays pinned over the scrolling list and has to
                        // be opaque or the title sits on top of the rows passing beneath it.
                        containerColor = Color.Transparent,
                        scrolledContainerColor = settingsPalette.pageStart,
                        titleContentColor = settingsPalette.onPageStart,
                    ),
                )
            },
        ) { appBarPadding ->
        // The clearance is padding *inside* the scrolling Column, not on ExpressivePage: padding
        // the container would shrink the viewport and stop rows above the floating toolbar,
        // whereas this lets them scroll underneath it while still being reachable at the end.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = appBarPadding.calculateTopPadding(),
                    bottom = scaffoldPadding.calculateBottomPadding(),
                ),
        ) {

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
                // Account first: who is signed in and how to change that are what people open
                // settings for most. The accounts and each provider's switch, backend address
                // and raw credential live on their own page; this row says who is signed in where
                // and opens it.
                SettingsSectionLabel("账号", settingsPalette)
                SettingsLinkRow(
                    label = "账号与平台",
                    supportingText = accountsSummary,
                    accentPalette = settingsPalette,
                    onClick = onOpenAccounts,
                )

                SettingsSectionLabel("播放", settingsPalette)
                // The output-device row sits with the quality dropdowns because it is the other
                // half of "what comes out of the speakers", not a behaviour toggle.
                val outputDeviceRows = if (supportsAudioOutputDeviceSelection) 1 else 0
                val dynamicCoverRows = if (supportsDynamicNowPlayingCover) 1 else 0
                val playbackSettingCount = 2 + outputDeviceRows + dynamicCoverRows
                SettingsDropdown(
                    onlineQuality,
                    "在线播放音质",
                    SoundQuality.entries,
                    settingsPalette,
                    index = 0,
                    count = playbackSettingCount,
                    supportingText = QualityAvailabilityNote,
                ) { v ->
                    onlineQuality = v.name
                    persistSettings({ settings.setString(SettingKeys.ONLINE_PLAY_QUALITY, v.name) })
                }
                if (supportsAudioOutputDeviceSelection) {
                    AudioOutputDeviceDropdown(
                        selectedId = audioOutputDeviceId,
                        accentPalette = settingsPalette,
                        index = 1,
                        count = playbackSettingCount,
                    ) { deviceId ->
                        audioOutputDeviceId = deviceId
                        persistSettings(
                            write = {
                                settings.setString(SettingKeys.AUDIO_OUTPUT_DEVICE, deviceId)
                            },
                            // Move the track that is playing now, not just the next one.
                            onWritten = { nowPlayingViewModel.reopenAudioOutput() },
                        )
                    }
                }
                // Both states replace the queue; what differs is how much of the list goes in, so
                // that is what the label says. It applies to every list: playlists, the daily
                // recommendation, search results and downloads.
                SettingsSwitch(
                    replacePlaylist,
                    "点按列表中的歌曲时播放整个列表",
                    settingsPalette,
                    index = 1 + outputDeviceRows,
                    count = playbackSettingCount,
                    supportingText = "关闭时只播放点按的那一首",
                ) { v ->
                    replacePlaylist = v
                    persistSettings({ settings.setBoolean(SettingKeys.REPLACE_PLAYLIST, v) })
                }
                if (supportsDynamicNowPlayingCover) {
                    SettingsSwitch(
                        useDynamicCover,
                        "播放页使用歌曲动态封面",
                        settingsPalette,
                        index = 2 + outputDeviceRows,
                        count = playbackSettingCount,
                    ) { enabled ->
                        useDynamicCover = enabled
                        persistSettings(
                            write = { settings.setBoolean(SettingKeys.USE_DYNAMIC_COVER, enabled) },
                            onWritten = { nowPlayingViewModel.setUseDynamicCover(enabled) },
                        )
                    }
                }

                SettingsSectionLabel("歌词", settingsPalette)
                val surfaceRows = if (optionalLyricSurface != null) 1 else 0
                val surfaceLayoutRows = if (windowedLyricSurface != null) 2 else 0
                val lyricSettingCount = 3 + surfaceRows + surfaceLayoutRows
                LyricSourceDropdown(
                    selectedWireValue = lyricSourceMode,
                    accentPalette = settingsPalette,
                    index = 0,
                    count = lyricSettingCount,
                ) { mode ->
                    val writeGeneration = ++lyricSourceWriteGeneration
                    lyricSourceMode = mode.wireValue
                    persistSettings(
                        write = {
                            settings.setString(SettingKeys.LYRIC_SOURCE_MODE, mode.wireValue)
                        },
                        onWritten = {
                            if (writeGeneration == lyricSourceWriteGeneration) {
                                nowPlayingViewModel.setLyricSourceMode(mode)
                            }
                        },
                        onFailure = {
                            if (writeGeneration == lyricSourceWriteGeneration) {
                                reloadSettingsSnapshot()
                            }
                        },
                    )
                }
                if (optionalLyricSurface != null) {
                    SettingsSwitch(
                        extraLyricSurfaceEnabled,
                        optionalLyricSurface.settingLabel,
                        settingsPalette,
                        index = 1,
                        count = lyricSettingCount,
                    ) { enabled ->
                        extraLyricSurfaceEnabled = enabled
                        persistSettings(
                            write = { settings.setBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, enabled) },
                            onWritten = {
                                optionalLyricSurface.setEnabled(enabled)
                            },
                        )
                    }
                }
                if (windowedLyricSurface != null) {
                    SettingsSwitch(
                        desktopLyricLocked,
                        "锁定桌面歌词",
                        settingsPalette,
                        index = 1 + surfaceRows,
                        count = lyricSettingCount,
                        supportingText = "锁定后窗口固定在原处，不能拖动或调整大小；Windows 上鼠标会穿透到下面的窗口。" +
                            "解锁可以在这里，也可以在系统托盘图标的菜单里。",
                    ) { locked ->
                        desktopLyricLocked = locked
                        persistSettings(
                            write = { settings.setBoolean(SettingKeys.DESKTOP_LYRIC_LOCKED, locked) },
                            onWritten = {
                                windowedLyricSurface.setLocked(locked)
                            },
                        )
                    }
                    LyricSurfaceAlignmentDropdown(
                        selected = desktopLyricAlignment,
                        accentPalette = settingsPalette,
                        index = 2 + surfaceRows,
                        count = lyricSettingCount,
                    ) { alignment ->
                        desktopLyricAlignment = alignment
                        persistSettings(
                            write = {
                                settings.setString(
                                    SettingKeys.DESKTOP_LYRIC_ALIGNMENT,
                                    alignment.wireValue,
                                )
                            },
                            onWritten = {
                                windowedLyricSurface.setAlignment(alignment)
                            },
                        )
                    }
                }
                SettingsSwitch(
                    showTranslatedLyric,
                    "显示翻译歌词",
                    settingsPalette,
                    index = lyricSettingCount - 2,
                    count = lyricSettingCount,
                ) { v ->
                    val writeGeneration = ++lyricDisplayWriteGeneration
                    showTranslatedLyric = v
                    persistSettings(
                        write = {
                            settings.setBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, v)
                        },
                        onPersisted = {
                            if (writeGeneration == lyricDisplayWriteGeneration) {
                                nowPlayingViewModel.setLyricDisplayOptions(
                                    showTranslation = v,
                                    showRomanization = showRomanLyric,
                                )
                            }
                        },
                    )
                }
                SettingsSwitch(
                    showRomanLyric,
                    "显示罗马音歌词",
                    settingsPalette,
                    index = lyricSettingCount - 1,
                    count = lyricSettingCount,
                    supportingText = "日语等歌曲的读音标注",
                ) { v ->
                    val writeGeneration = ++lyricDisplayWriteGeneration
                    showRomanLyric = v
                    persistSettings(
                        write = { settings.setBoolean(SettingKeys.SHOW_ROMAN_LYRIC, v) },
                        onPersisted = {
                            if (writeGeneration == lyricDisplayWriteGeneration) {
                                nowPlayingViewModel.setLyricDisplayOptions(
                                    showTranslation = showTranslatedLyric,
                                    showRomanization = v,
                                )
                            }
                        },
                    )
                }

                SettingsSectionLabel("下载", settingsPalette)
                SettingsDropdown(
                    dlQuality,
                    "下载音质",
                    SoundQuality.entries,
                    settingsPalette,
                    index = 0,
                    count = 2,
                    supportingText = QualityAvailabilityNote,
                ) { v ->
                    dlQuality = v.name
                    persistSettings({ settings.setString(SettingKeys.DOWNLOAD_QUALITY, v.name) })
                }
                SettingsSwitch(
                    showDownloadStatus,
                    "在歌曲列表中标出下载状态",
                    settingsPalette,
                    index = 1,
                    count = 2,
                    supportingText = "已下载、正在下载和下载失败的歌曲会带标记",
                ) { v ->
                    showDownloadStatus = v
                    persistSettings({ settings.setBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, v) })
                }

                SettingsSectionLabel("通用", settingsPalette)
                val trayRows = if (isDesktop) 1 else 0
                val themeRows = if (dynamicColorSupported) 2 else 1
                val generalCount = 3 + trayRows + themeRows
                // The user picks light or dark here, or leaves it to the system.
                SettingsDropdownRow(
                    label = "主题",
                    valueLabel = themeMode.displayName,
                    options = ThemeMode.entries,
                    optionLabel = ThemeMode::displayName,
                    accentPalette = settingsPalette,
                    index = 0,
                    count = generalCount,
                ) { mode ->
                    ThemePreferences.setMode(mode)
                    persistSettings({ settings.setString(SettingKeys.THEME_MODE, mode.wireValue) })
                }
                if (dynamicColorSupported) {
                    SettingsSwitch(
                        dynamicColor,
                        "使用壁纸颜色",
                        settingsPalette,
                        index = 1,
                        count = generalCount,
                        supportingText = "界面配色取自系统壁纸（Android 12 及以上）",
                    ) { v ->
                        ThemePreferences.setDynamicColor(v)
                        persistSettings({ settings.setBoolean(SettingKeys.DYNAMIC_COLOR, v) })
                    }
                }
                SettingsSwitch(
                    searchPrediction,
                    "搜索联想",
                    settingsPalette,
                    index = themeRows,
                    count = generalCount,
                    supportingText = "输入时显示搜索建议",
                ) { v ->
                    searchPrediction = v
                    persistSettings({ settings.setBoolean(SettingKeys.SEARCH_PREDICTION, v) })
                }
                if (isDesktop) {
                    SettingsSwitch(
                        closeToTray,
                        "关闭主窗口时留在托盘",
                        settingsPalette,
                        index = themeRows + 1,
                        count = generalCount,
                        supportingText = "播放不会中断；点按托盘图标重新打开窗口，在托盘菜单里选「退出」才会退出",
                    ) { v ->
                        closeToTray = v
                        persistSettings({ settings.setBoolean(SettingKeys.DESKTOP_CLOSE_TO_TRAY, v) })
                    }
                }
                SettingsSwitch(
                    checkUpdate,
                    "启动时检查更新",
                    settingsPalette,
                    index = themeRows + 1 + trayRows,
                    count = generalCount,
                ) { v ->
                    checkUpdate = v
                    persistSettings({ settings.setBoolean(SettingKeys.CHECK_UPDATE, v) })
                }
                SettingsButton(
                    "立即检查更新",
                    settingsPalette,
                    index = themeRows + 2 + trayRows,
                    count = generalCount,
                ) {
                    mainViewModel.checkForUpdatesNow()
                }

                SettingsSectionLabel("备份", settingsPalette)
                // Deliberately two buttons, not a ButtonGroup. ButtonGroupScope.clickableItem
                // takes `label: String` plus an `icon` composable and rendered nothing at all
                // here (verified on device with and without an icon, scrolled to the end of the
                // list), so the group is not usable for two text-labelled actions in this
                // Compose Multiplatform build.
                SettingsButton("导出设置", settingsPalette, index = 0, count = 2) {
                    // The local account's playlists travel with the settings (AGENTS.md D6).
                    scope.launch {
                        launchExport(encodeSettingsBackup(settings, localLibrary.snapshot()))
                    }
                }
                SettingsButton("导入设置", settingsPalette, index = 1, count = 2) {
                    showImportConfirmation = true
                }

                SettingsSectionLabel("关于", settingsPalette)
                SettingsValue(
                    label = "版本",
                    value = BuildInfo.VERSION_NAME,
                    supportingText = "Build ${BuildInfo.VERSION_CODE}",
                    accentPalette = settingsPalette,
                    index = 0,
                    count = 2,
                )
                SettingsValue(
                    label = "开源许可",
                    value = "AMLL 歌词渲染与解析组件",
                    supportingText = "以 AGPL-3.0-only 发布；许可证全文随应用资源提供。",
                    accentPalette = settingsPalette,
                    index = 1,
                    count = 2,
                )

                    Spacer(Modifier.height(48.dp))
                }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = scaffoldPadding.calculateBottomPadding()),
        )
    }

    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            title = { Text("导入并覆盖当前设置？") },
            text = {
                Text("导入文件会覆盖服务器地址、播放与歌词偏好；登录状态不在备份里，不受影响。建议先导出当前设置作为备份。")
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


/**
 * A settings row that opens a menu.
 *
 * Setting name leading, current value trailing: the M3 list-item arrangement. A label-over-value
 * stack with a caret reads as a filled text field, which makes such rows look like inputs among
 * the switch rows instead of like the same kind of row.
 *
 * Every dropdown in this screen draws through here rather than keeping its own copy of the
 * surface, the row and the menu; separate copies drift apart.
 *
 * @param valueLabel what the row shows for the current choice. The caller supplies it because a
 *   choice is not always one of [options]: a saved audio device can be gone.
 * @param menuLabel the menu has room to say what the compact row cannot.
 * @param onExpandedChange for options that are enumerated fresh each time the menu opens.
 */
@Composable
private fun <T> SettingsDropdownRow(
    label: String,
    valueLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    supportingText: List<String> = emptyList(),
    menuLabel: (T) -> String = optionLabel,
    onExpandedChange: (Boolean) -> Unit = {},
    onUpdate: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(open: Boolean) {
        expanded = open
        onExpandedChange(open)
    }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = { setExpanded(true) },
        shape = rememberConnectedListItemShape(index, count, interactionSource),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ExpressiveLayout.ConnectedItemGap),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        // At most four tenths of the row for the value, so a long value is cut short instead of
        // squeezing the label into a column one character wide on a narrow window.
        val valueMaxWidth = maxWidth * 0.4f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ExpressiveLayout.MinimumTouchTarget)
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                supportingText.forEach { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentPalette.secondaryOnQuietContainer,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = accentPalette.secondaryOnQuietContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = valueMaxWidth),
            )
            // Up/down arrows: this row opens a menu in place. A right chevron would promise a
            // new page and then open a menu.
            Icon(
                AppIcons.UnfoldMore,
                contentDescription = null,
                tint = accentPalette.secondaryOnQuietContainer,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(menuLabel(option)) },
                    onClick = {
                        setExpanded(false)
                        onUpdate(option)
                    },
                )
            }
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
    supportingText: String? = null,
    onUpdate: (SoundQuality) -> Unit,
) {
    val current = options.find { it.name == selectedName } ?: options.first()
    SettingsDropdownRow(
        label = label,
        valueLabel = current.toString(),
        options = options,
        optionLabel = { it.toString() },
        accentPalette = accentPalette,
        index = index,
        count = count,
        supportingText = listOfNotNull(supportingText),
        onUpdate = onUpdate,
    )
}

/**
 * What a quality above the account's entitlement does. The list offers every tier; without this
 * the VIP-only ones would look as if they play like the rest.
 */
private const val QualityAvailabilityNote = "账号没有对应权限时，会得到能播放的最高音质"

/** Shown for the "no explicit choice" entry and whenever the chosen device cannot be resolved. */
private const val DefaultAudioOutputLabel = "系统默认"

/** The menu row has space to say what the compact value cannot. */
private const val DefaultAudioOutputMenuLabel = "系统默认（跟随系统输出设备）"

/**
 * Picks which output device desktop playback opens.
 *
 * Its own composable rather than a [SettingsDropdown] call because the options are enumerated
 * from the OS at display time, not a fixed enum: the list changes when a headset or USB DAC is
 * plugged in, and a saved device can disappear entirely.
 */
@Composable
private fun AudioOutputDeviceDropdown(
    selectedId: String,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    onUpdate: (String) -> Unit,
) {
    // null until the first enumeration lands, so an unresolved id is not mistaken for a
    // missing device while the list is still empty.
    var devices by remember { mutableStateOf<List<AudioOutputDevice>?>(null) }
    // The list is re-read whenever the menu opens or closes: a headset or USB DAC can appear
    // between one glance at this row and the next.
    var enumerations by remember { mutableIntStateOf(0) }
    LaunchedEffect(enumerations) {
        devices = withContext(Dispatchers.Default) { availableAudioOutputDevices() }
    }
    val known = devices
    val selected = known?.let { resolveAudioOutputSelection(selectedId, it) }
    val selectedLabel = when {
        selectedId == SYSTEM_DEFAULT_AUDIO_OUTPUT_ID -> DefaultAudioOutputLabel
        selected != null -> selected.displayName
        known == null -> audioOutputDeviceName(selectedId)
        // The device is gone; playback already falls back to the default output, so say so
        // instead of showing a name that nothing is coming out of.
        else -> "${audioOutputDeviceName(selectedId)}（不可用）"
    }
    SettingsDropdownRow(
        label = "音频输出设备",
        valueLabel = selectedLabel,
        options = listOf(SYSTEM_DEFAULT_AUDIO_OUTPUT_ID) + known.orEmpty().map { it.id },
        optionLabel = { id ->
            if (id == SYSTEM_DEFAULT_AUDIO_OUTPUT_ID) {
                DefaultAudioOutputLabel
            } else {
                known.orEmpty().firstOrNull { it.id == id }?.displayName
                    ?: audioOutputDeviceName(id)
            }
        },
        menuLabel = { id ->
            if (id == SYSTEM_DEFAULT_AUDIO_OUTPUT_ID) {
                DefaultAudioOutputMenuLabel
            } else {
                known.orEmpty().firstOrNull { it.id == id }?.displayName
                    ?: audioOutputDeviceName(id)
            }
        },
        accentPalette = accentPalette,
        index = index,
        count = count,
        onExpandedChange = { enumerations += 1 },
        onUpdate = onUpdate,
    )
}

/** Where the desktop lyric window's two lines sit. */
@Composable
private fun LyricSurfaceAlignmentDropdown(
    selected: LyricSurfaceAlignment,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    onUpdate: (LyricSurfaceAlignment) -> Unit,
) {
    SettingsDropdownRow(
        label = "桌面歌词对齐",
        valueLabel = selected.displayName,
        options = LyricSurfaceAlignment.entries,
        optionLabel = LyricSurfaceAlignment::displayName,
        accentPalette = accentPalette,
        index = index,
        count = count,
        onUpdate = onUpdate,
    )
}

@Composable
private fun LyricSourceDropdown(
    selectedWireValue: String,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    onUpdate: (LyricSourceMode) -> Unit,
) {
    SettingsDropdownRow(
        label = "歌词来源",
        valueLabel = LyricSourceMode.fromWireValue(selectedWireValue).displayName,
        options = LyricSourceMode.entries,
        optionLabel = LyricSourceMode::displayName,
        accentPalette = accentPalette,
        index = index,
        count = count,
        supportingText = listOf(
            "AMLL 歌词库由社区维护，逐字歌词更全；只按歌曲 ID 查询，不会发送账号信息。",
        ),
        onUpdate = onUpdate,
    )
}

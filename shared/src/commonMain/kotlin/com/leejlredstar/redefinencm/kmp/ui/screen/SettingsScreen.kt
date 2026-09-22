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
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
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
import com.leejlredstar.redefinencm.kmp.util.BuildInfo
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethodRegistry
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredential
import com.leejlredstar.redefinencm.kmp.data.provider.LibraryAggregationMode
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.applySettingsBackup
import com.leejlredstar.redefinencm.kmp.util.encodeSettingsBackup
import com.leejlredstar.redefinencm.kmp.util.rememberExportFileLauncher
import com.leejlredstar.redefinencm.kmp.util.rememberImportFileLauncher
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
import com.leejlredstar.redefinencm.kmp.di.DEFAULT_NCM_SERVER as DefaultNcmServer
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemeMode
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemePreferences
import com.leejlredstar.redefinencm.kmp.ui.theme.dynamicColorSupported

/**
 * The lyric surface's capabilities, as this target actually has them.
 *
 * Settings used to ask the surface two booleans about itself and then call members every target
 * had to declare. A capability is a type now: a target that cannot switch its lyric surface off
 * simply is not an [OptionalLyricSurface], and these are null there.
 */
private val optionalLyricSurface: OptionalLyricSurface? = lyricSurface as? OptionalLyricSurface

private val windowedLyricSurface: WindowedLyricSurface? = lyricSurface as? WindowedLyricSurface


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    scaffoldPadding: PaddingValues,
    /** Opens the login page for one provider. */
    onOpenLogin: (MusicProviderId) -> Unit,
    /** Settings is a page opened from the library and the sidebar, so it has a way back. */
    onBack: (() -> Unit)? = null,
    settings: PlatformSettings = koinInject(),
    api: NCMApi = koinInject(),
    mainViewModel: MainViewModel = koinInject(),
    nowPlayingViewModel: NowPlayingViewModel = koinInject(),
    loginMethods: LoginMethodRegistry = koinInject(),
) {
    var cookie by remember(settings) { mutableStateOf("") }
    var server by remember(settings) { mutableStateOf("") }
    var qqEnabled by remember(settings) { mutableStateOf(false) }
    var qqServer by remember(settings) { mutableStateOf(SettingKeys.QQ_SERVER_DEFAULT) }
    var qqCookie by remember(settings) { mutableStateOf("") }
    var aggregationMode by remember(settings) { mutableStateOf(LibraryAggregationMode.Default) }
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
    var serverCheckStatus by remember { mutableStateOf<String?>(null) }
    var settingsLoaded by remember(settings) { mutableStateOf(false) }
    var settingsLoadError by remember(settings) { mutableStateOf<String?>(null) }
    var settingsLoadRequest by remember(settings) { mutableIntStateOf(0) }
    var serverCheckGeneration by remember { mutableIntStateOf(0) }
    var lyricSourceWriteGeneration by remember { mutableIntStateOf(0) }
    var lyricDisplayWriteGeneration by remember { mutableIntStateOf(0) }
    var showImportConfirmation by remember { mutableStateOf(false) }
    var logoutConfirmationFor by remember { mutableStateOf<MusicProviderId?>(null) }
    var showCookieField by remember { mutableStateOf(false) }
    var showQQCredentialField by remember { mutableStateOf(false) }
    // The pasted-credential method for QQ Music, if one is registered; it owns the field's label
    // and the rules for what pasted text is accepted.
    val qqCredentialMethod = remember(loginMethods) { loginMethods.textMethod(MusicProviderId.QQ) }
    val userDetail by mainViewModel.userDetail.collectAsState()
    // Results of saving, importing and exporting appear at the bottom, beside the controls that
    // cause them; a banner at the top of the page was off screen by the time the backup buttons
    // were reached.
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settingsMessage) {
        val message = settingsMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        settingsMessage = null
    }
    val scope = rememberCoroutineScope()

    fun reloadSettingsSnapshot() {
        cookie = settings.getString(SettingKeys.COOKIE, "")
        server = settings.getString(SettingKeys.SERVER, DefaultNcmServer)
        qqEnabled = settings.getBoolean(SettingKeys.QQ_ENABLED, false)
        qqServer = settings.getString(SettingKeys.QQ_SERVER, SettingKeys.QQ_SERVER_DEFAULT)
        qqCookie = settings.getString(SettingKeys.QQ_COOKIE, "")
        aggregationMode = LibraryAggregationMode.fromWireValueOrDefault(
            settings.getString(SettingKeys.LIBRARY_AGGREGATION_MODE, ""),
        )
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
            cookie = settings.getStringAsync(SettingKeys.COOKIE, "")
            server = settings.getStringAsync(SettingKeys.SERVER, DefaultNcmServer)
            qqEnabled = settings.getBooleanAsync(SettingKeys.QQ_ENABLED, false)
            qqServer = settings.getStringAsync(SettingKeys.QQ_SERVER, SettingKeys.QQ_SERVER_DEFAULT)
            qqCookie = settings.getStringAsync(SettingKeys.QQ_COOKIE, "")
            aggregationMode = LibraryAggregationMode.fromWireValueOrDefault(
                settings.getStringAsync(SettingKeys.LIBRARY_AGGREGATION_MODE, ""),
            )
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

    // The page title is a real LargeFlexibleTopAppBar rather than a hand-rolled hero Box. The
    // bar owns the collapse: it starts large and shrinks to a compact title as the page scrolls,
    // which a fixed 188dp gradient header could not do. Its container is transparent so
    // ExpressivePage's gradient still reads through, and the Scaffold here exists only to give
    // the bar somewhere to live and to hand back its measured height.
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
                // settings for most. The raw cookie is still here, folded away as an advanced
                // option instead of sitting in the open where one stray keystroke replaced it.
                SettingsSectionLabel("账号", settingsPalette)
                AccountCard(
                    loggedIn = cookie.isNotBlank(),
                    nickname = userDetail?.profile?.nickname,
                    avatarUrl = userDetail?.profile?.avatarUrl,
                    providerLabel = "网易云音乐账号",
                    signedOutHint = "登录后可以查看歌单、每日推荐和喜欢的音乐",
                    accentPalette = settingsPalette,
                    onLogin = { onOpenLogin(MusicProviderId.NETEASE) },
                    onLogout = { logoutConfirmationFor = MusicProviderId.NETEASE },
                )
                SettingsExpanderRow(
                    label = "手动填写 Cookie",
                    supportingText = "高级：已有登录 Cookie 时可以直接粘贴",
                    expanded = showCookieField,
                    accentPalette = settingsPalette,
                    onToggle = { showCookieField = !showCookieField },
                )
                if (showCookieField) {
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
                }

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
                    "点击列表中的歌曲时播放整个列表",
                    settingsPalette,
                    index = 1 + outputDeviceRows,
                    count = playbackSettingCount,
                    supportingText = "关闭时只播放点中的那一首",
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
                // The app followed the system's light or dark setting with no say in it.
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
                        supportingText = "播放不会中断；点托盘图标重新打开窗口，在托盘菜单里选「退出」才会退出",
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

                // A one-time technical setup, so it comes after everything used day to day.
                SettingsSectionLabel("服务器", settingsPalette)
                SettingsTextField(
                    value = server,
                    label = "服务器地址",
                    accentPalette = settingsPalette,
                    index = 0,
                    count = 2,
                    supportingText = "修改后需要重启应用才会生效；清空则恢复默认服务器",
                    onDraftChange = {
                        server = it
                        serverCheckGeneration += 1
                        serverCheckStatus = null
                    },
                    onCommit = { raw ->
                        // An empty address is not a setting: it left every request without a
                        // host after the next launch. Clearing the field means "the default".
                        val normalized = normalizeServerInput(raw).ifEmpty { DefaultNcmServer }
                        server = normalized
                        persistSettings(
                            write = { settings.setString(SettingKeys.SERVER, normalized) },
                            onPersisted = { settingsMessage = "服务器地址已保存，重启应用后生效" },
                        )
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
                            else "服务器有响应，但返回了错误（代码 ${result.code}）"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            // The raw exception names a socket or parser, not what to do about it.
                            "无法连接到这个服务器，请检查地址是否正确、服务是否在运行"
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

                SettingsSectionLabel("多平台", settingsPalette)
                // QQ Music has no public API, so it needs a gateway the user self-hosts (the web app
                // of L-1124/QQMusicApi), the same arrangement as the NetEase server above. The
                // account is held here in the gateway's own cookie form and sent on every QQ
                // request, so the Android build can sign in without reaching the gateway's config
                // file — see AGENTS.md D6.
                SettingsSwitch(
                    checked = qqEnabled,
                    label = "启用 QQ 音乐",
                    accentPalette = settingsPalette,
                    index = 0,
                    count = if (qqEnabled) 3 else 1,
                    supportingText = "需要自建 QQMusicApi 网关；未登录时按 QQ 对未登录用户的限制播放",
                ) { value ->
                    qqEnabled = value
                    persistSettings({ settings.setBoolean(SettingKeys.QQ_ENABLED, value) })
                }
                if (qqEnabled) {
                    SettingsTextField(
                        value = qqServer,
                        label = "QQ 音乐后端地址",
                        accentPalette = settingsPalette,
                        index = 1,
                        count = 3,
                        onDraftChange = { qqServer = it },
                        onCommit = { raw ->
                            val normalized = normalizeServerInput(raw)
                                .ifEmpty { SettingKeys.QQ_SERVER_DEFAULT }
                            qqServer = normalized
                            persistSettings({ settings.setString(SettingKeys.QQ_SERVER, normalized) })
                        },
                    )
                    // Both aggregation views ship; this picks which one the library and search use.
                    SettingsSwitch(
                        checked = aggregationMode == LibraryAggregationMode.PER_PROVIDER,
                        label = "按平台分组显示",
                        accentPalette = settingsPalette,
                        index = 2,
                        count = 3,
                        supportingText = "关闭时各平台结果混合为一个列表",
                    ) { value ->
                        val mode = if (value) {
                            LibraryAggregationMode.PER_PROVIDER
                        } else {
                            LibraryAggregationMode.MERGED
                        }
                        aggregationMode = mode
                        persistSettings({
                            settings.setString(
                                SettingKeys.LIBRARY_AGGREGATION_MODE,
                                mode.wireValue,
                            )
                        })
                    }

                    // The QQ account, laid out like the NetEase one above: who is signed in, the
                    // login page for changing that, and the raw credential folded away beneath.
                    // The stored form is the gateway's; it carries no nickname, so the account
                    // number stands in for one.
                    val qqAccount = remember(qqCookie) { QQCredential.parse(qqCookie) }
                    Spacer(Modifier.height(ExpressiveLayout.ConnectedItemGap * 3))
                    AccountCard(
                        loggedIn = qqAccount != null,
                        nickname = qqAccount?.musicId?.toString(),
                        avatarUrl = null,
                        providerLabel = "QQ 音乐账号",
                        signedOutHint = "登录后搜索和播放使用你账号的权益",
                        accentPalette = settingsPalette,
                        onLogin = { onOpenLogin(MusicProviderId.QQ) },
                        onLogout = { logoutConfirmationFor = MusicProviderId.QQ },
                    )
                    if (qqCredentialMethod != null) {
                        SettingsExpanderRow(
                            label = "手动填写凭证",
                            supportingText = "高级：${qqCredentialMethod.supportingText}",
                            expanded = showQQCredentialField,
                            accentPalette = settingsPalette,
                            onToggle = { showQQCredentialField = !showQQCredentialField },
                        )
                    }
                    if (showQQCredentialField && qqCredentialMethod != null) {
                        // Obscured and kept out of the settings backup, exactly like the NetEase one.
                        SettingsTextField(
                            value = qqCookie,
                            label = qqCredentialMethod.fieldLabel,
                            obscureText = true,
                            accentPalette = settingsPalette,
                            index = 1,
                            count = 2,
                            onDraftChange = { qqCookie = it },
                            onCommit = { raw ->
                                qqCredentialMethod.normalize(raw)
                                    .onSuccess { normalized ->
                                        qqCookie = normalized
                                        persistSettings({
                                            settings.setString(SettingKeys.QQ_COOKIE, normalized)
                                        })
                                    }
                                    .onFailure { failure ->
                                        reloadSettingsSnapshot()
                                        settingsMessage = failure.message ?: "凭证无法识别"
                                    }
                            },
                        )
                    }
                }

                SettingsSectionLabel("备份", settingsPalette)
                // Deliberately two buttons, not a ButtonGroup. ButtonGroupScope.clickableItem
                // takes `label: String` plus an `icon` composable and rendered nothing at all
                // here — verified on device with and without an icon, scrolled to the end of the
                // list — so the group is not usable for two text-labelled actions in this
                // Compose Multiplatform build.
                SettingsButton("导出设置", settingsPalette, index = 0, count = 2) { launchExport(encodeSettingsBackup(settings)) }
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

    logoutConfirmationFor?.let { provider ->
        AlertDialog(
            onDismissRequest = { logoutConfirmationFor = null },
            icon = { Icon(AppIcons.Logout, contentDescription = null) },
            title = { Text("退出${provider.displayName}登录？") },
            text = {
                Text(
                    when (provider) {
                        MusicProviderId.NETEASE ->
                            "退出后「我的」、每日推荐和喜欢等功能将不可用，已下载的歌曲会保留。"
                        MusicProviderId.QQ ->
                            "退出后 QQ 音乐按未登录状态搜索和播放。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        logoutConfirmationFor = null
                        when (provider) {
                            MusicProviderId.NETEASE -> {
                                cookie = ""
                                persistSettings(
                                    write = { settings.setString(SettingKeys.COOKIE, "") },
                                    onWritten = { mainViewModel.refreshAccount() },
                                    onPersisted = { settingsMessage = "已退出登录" },
                                )
                            }
                            MusicProviderId.QQ -> {
                                qqCookie = ""
                                persistSettings(
                                    write = { settings.setString(SettingKeys.QQ_COOKIE, "") },
                                    onPersisted = { settingsMessage = "已退出 QQ 音乐" },
                                )
                            }
                        }
                    },
                ) { Text("退出登录") }
            },
            dismissButton = {
                TextButton(onClick = { logoutConfirmationFor = null }) { Text("取消") }
            },
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
private fun SettingsSectionLabel(
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
private fun SettingsSwitch(
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

/**
 * A settings row that opens a menu.
 *
 * Setting name leading, current value trailing — the M3 list-item arrangement. The old
 * label-over-value stack with a caret read as a filled text field, so those rows looked like
 * inputs sitting among the switch rows instead of like the same kind of row.
 *
 * Every dropdown in this screen draws through here. Four of them had their own copy of the
 * surface, the row and the menu, and the copies had drifted: one still used the rejected stacked
 * arrangement, sat on the pre-morph shape helper, and had no minimum touch target.
 *
 * @param valueLabel what the row shows for the current choice. The caller supplies it because a
 *   choice is not always one of [options] — a saved audio device can be gone.
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
            // Up/down arrows: this row opens a menu in place. A right chevron promised a new
            // page and then opened a menu.
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
 * the VIP-only ones looked like they would simply play.
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

/** Who is signed in to one provider, and the two things to do about it. */
@Composable
private fun AccountCard(
    loggedIn: Boolean,
    nickname: String?,
    avatarUrl: String?,
    /** Under the name when signed in: "网易云音乐账号". */
    providerLabel: String,
    /** Under "未登录": what signing in is for. */
    signedOutHint: String,
    accentPalette: ContentAccentPalette,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Surface(
        shape = connectedListItemShape(index = 0, count = 2),
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
                TextButton(onClick = onLogin) { Text("切换账号") }
                TextButton(onClick = onLogout) { Text("退出") }
            } else {
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

/** A row that shows or hides an advanced setting beneath it. */
@Composable
private fun SettingsExpanderRow(
    label: String,
    supportingText: String,
    expanded: Boolean,
    accentPalette: ContentAccentPalette,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onToggle,
        shape = rememberConnectedListItemShape(
            index = if (expanded) 1 else 1,
            count = if (expanded) 3 else 2,
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

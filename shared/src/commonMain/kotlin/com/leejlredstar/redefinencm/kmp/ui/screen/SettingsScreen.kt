package com.leejlredstar.redefinencm.kmp.ui.screen

import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.LanguageSetting
import com.leejlredstar.redefinencm.kmp.i18n.strings
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
import com.leejlredstar.redefinencm.kmp.transition.BeatAcceleratorState
import com.leejlredstar.redefinencm.kmp.transition.MAX_CROSSFADE_SECONDS
import com.leejlredstar.redefinencm.kmp.transition.MIN_CROSSFADE_SECONDS
import com.leejlredstar.redefinencm.kmp.transition.SongTransitionCoordinator
import com.leejlredstar.redefinencm.kmp.transition.SongTransitionMode
import com.leejlredstar.redefinencm.kmp.transition.SongTransitionPreferences
import com.leejlredstar.redefinencm.kmp.transition.TransitionCapability
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import kotlin.math.roundToInt

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
    songTransitions: SongTransitionCoordinator = koinInject(),
) {
    var onlineQuality by remember(settings) { mutableStateOf(SoundQuality.STANDARD.name) }
    var dlQuality by remember(settings) { mutableStateOf(SoundQuality.STANDARD.name) }
    var replacePlaylist by remember(settings) { mutableStateOf(SettingKeys.REPLACE_PLAYLIST_DEFAULT) }
    var checkUpdate by remember(settings) { mutableStateOf(false) }
    var searchPrediction by remember(settings) { mutableStateOf(true) }
    var showDownloadStatus by remember(settings) { mutableStateOf(SettingKeys.SHOW_DOWNLOAD_STATUS_DEFAULT) }
    var closeToTray by remember(settings) { mutableStateOf(SettingKeys.DESKTOP_CLOSE_TO_TRAY_DEFAULT) }
    val themeMode by ThemePreferences.mode.collectAsState()
    val languageSetting by I18n.setting.collectAsState()
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
        // An imported backup can carry another language; show it at once.
        I18n.load(settings)
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
        songTransitions.reloadPreferences()
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
                    settingsMessage = strings.settingsSaveFailed(error.message ?: strings.unknownError)
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
            settingsMessage = strings.settingsSaveFailed(writeResult.exceptionOrNull()?.message ?: strings.unknownError)
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
            settingsLoadError = failure.message ?: strings.settingsLoadFailed
        }
    }

    val launchImport = rememberImportFileLauncher { json ->
        scope.launch {
            if (applySettingsBackup(json, settings)) {
                decodeBackupLocalLibrary(json)?.let { playlists ->
                    localLibrary.importPlaylists(playlists).onFailure { failure ->
                        settingsMessage = strings.localPlaylistsImportFailed(failure.message ?: strings.unknownError)
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
                    settingsMessage = strings.settingsSaveFailed(persisted.exceptionOrNull()?.message ?: strings.unknownError)
                    return@launch
                }
                reloadSettingsSnapshot()
                settingsMessage = strings.settingsImported
            } else {
                lyricSourceWriteGeneration += 1
                lyricDisplayWriteGeneration += 1
                // A platform write can fail after earlier backup fields were already applied.
                // Re-read the process snapshot so a partial source change is never left latent.
                reloadSettingsSnapshot()
                settingsMessage = strings.settingsImportInvalidFile
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
                    title = { Text(strings.settings) },
                    subtitle = { Text(strings.settingsSubtitle) },
                    navigationIcon = {
                        onBack?.let {
                            IconButton(onClick = it) {
                                Icon(AppIcons.ArrowBack, contentDescription = strings.back)
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
                    title = strings.settingsLoadFailed,
                    message = settingsLoadError.orEmpty(),
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    accentPalette = settingsPalette,
                    actionLabel = strings.retry,
                    onAction = { settingsLoadRequest += 1 },
                    modifier = Modifier.padding(20.dp),
                )
            } else if (!settingsLoaded) {
                ExpressiveLoadingState(
                    label = strings.settingsLoading,
                    accentColor = settingsPalette.accent,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Account first: who is signed in and how to change that are what people open
                // settings for most. The accounts and each provider's switch, backend address
                // and raw credential live on their own page; this row says who is signed in where
                // and opens it.
                SettingsSectionLabel(strings.settingsSectionAccounts, settingsPalette)
                SettingsLinkRow(
                    label = strings.accountsAndServices,
                    supportingText = accountsSummary,
                    accentPalette = settingsPalette,
                    onClick = onOpenAccounts,
                )

                SettingsSectionLabel(strings.play, settingsPalette)
                // The output-device row sits with the quality dropdowns because it is the other
                // half of "what comes out of the speakers", not a behaviour toggle.
                val outputDeviceRows = if (supportsAudioOutputDeviceSelection) 1 else 0
                val dynamicCoverRows = if (supportsDynamicNowPlayingCover) 1 else 0
                val playbackSettingCount = 2 + outputDeviceRows + dynamicCoverRows
                SettingsDropdown(
                    onlineQuality,
                    strings.streamingQuality,
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
                    strings.playWholeListOnTap,
                    settingsPalette,
                    index = 1 + outputDeviceRows,
                    count = playbackSettingCount,
                    supportingText = strings.playWholeListOnTapHint,
                ) { v ->
                    replacePlaylist = v
                    persistSettings({ settings.setBoolean(SettingKeys.REPLACE_PLAYLIST, v) })
                }
                if (supportsDynamicNowPlayingCover) {
                    SettingsSwitch(
                        useDynamicCover,
                        strings.useAnimatedCover,
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

                SongTransitionSection(
                    coordinator = songTransitions,
                    accentPalette = settingsPalette,
                    onChange = { write -> persistSettings(write) },
                )

                SettingsSectionLabel(strings.lyrics, settingsPalette)
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
                        strings.lockDesktopLyrics,
                        settingsPalette,
                        index = 1 + surfaceRows,
                        count = lyricSettingCount,
                        supportingText = strings.desktopLyricsLockHint,
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
                    strings.showTranslatedLyrics,
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
                    strings.showRomanization,
                    settingsPalette,
                    index = lyricSettingCount - 1,
                    count = lyricSettingCount,
                    supportingText = strings.showRomanizationHint,
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

                SettingsSectionLabel(strings.downloads, settingsPalette)
                SettingsDropdown(
                    dlQuality,
                    strings.downloadQuality,
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
                    strings.showDownloadStatus,
                    settingsPalette,
                    index = 1,
                    count = 2,
                    supportingText = strings.showDownloadStatusHint,
                ) { v ->
                    showDownloadStatus = v
                    persistSettings({ settings.setBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, v) })
                }

                SettingsSectionLabel(strings.settingsSectionGeneral, settingsPalette)
                val trayRows = if (isDesktop) 1 else 0
                val languageRows = 1
                val themeRows = languageRows + if (dynamicColorSupported) 2 else 1
                val generalCount = 3 + trayRows + themeRows
                // One of the three languages, or the system's (English when it is none of them).
                SettingsDropdownRow(
                    label = strings.settingsLanguage,
                    valueLabel = languageSetting.displayName,
                    options = LanguageSetting.entries,
                    optionLabel = LanguageSetting::displayName,
                    accentPalette = settingsPalette,
                    index = 0,
                    count = generalCount,
                ) { setting ->
                    I18n.apply(setting)
                    persistSettings({ settings.setString(SettingKeys.APP_LANGUAGE, setting.wireValue) })
                }
                // The user picks light or dark here, or leaves it to the system.
                SettingsDropdownRow(
                    label = strings.theme,
                    valueLabel = themeMode.displayName,
                    options = ThemeMode.entries,
                    optionLabel = ThemeMode::displayName,
                    accentPalette = settingsPalette,
                    index = languageRows,
                    count = generalCount,
                ) { mode ->
                    ThemePreferences.setMode(mode)
                    persistSettings({ settings.setString(SettingKeys.THEME_MODE, mode.wireValue) })
                }
                if (dynamicColorSupported) {
                    SettingsSwitch(
                        dynamicColor,
                        strings.useWallpaperColors,
                        settingsPalette,
                        index = languageRows + 1,
                        count = generalCount,
                        supportingText = strings.useWallpaperColorsHint,
                    ) { v ->
                        ThemePreferences.setDynamicColor(v)
                        persistSettings({ settings.setBoolean(SettingKeys.DYNAMIC_COLOR, v) })
                    }
                }
                SettingsSwitch(
                    searchPrediction,
                    strings.searchSuggestions,
                    settingsPalette,
                    index = themeRows,
                    count = generalCount,
                    supportingText = strings.searchSuggestionsHint,
                ) { v ->
                    searchPrediction = v
                    persistSettings({ settings.setBoolean(SettingKeys.SEARCH_PREDICTION, v) })
                }
                if (isDesktop) {
                    SettingsSwitch(
                        closeToTray,
                        strings.closeToTray,
                        settingsPalette,
                        index = themeRows + 1,
                        count = generalCount,
                        supportingText = strings.closeToTrayHint,
                    ) { v ->
                        closeToTray = v
                        persistSettings({ settings.setBoolean(SettingKeys.DESKTOP_CLOSE_TO_TRAY, v) })
                    }
                }
                SettingsSwitch(
                    checkUpdate,
                    strings.checkUpdatesOnStartup,
                    settingsPalette,
                    index = themeRows + 1 + trayRows,
                    count = generalCount,
                ) { v ->
                    checkUpdate = v
                    persistSettings({ settings.setBoolean(SettingKeys.CHECK_UPDATE, v) })
                }
                SettingsButton(
                    strings.checkUpdatesNow,
                    settingsPalette,
                    index = themeRows + 2 + trayRows,
                    count = generalCount,
                ) {
                    mainViewModel.checkForUpdatesNow()
                }

                SettingsSectionLabel(strings.backup, settingsPalette)
                // Deliberately two buttons, not a ButtonGroup. ButtonGroupScope.clickableItem
                // takes `label: String` plus an `icon` composable and rendered nothing at all
                // here (verified on device with and without an icon, scrolled to the end of the
                // list), so the group is not usable for two text-labelled actions in this
                // Compose Multiplatform build.
                SettingsButton(strings.exportSettings, settingsPalette, index = 0, count = 2) {
                    // The local account's playlists travel with the settings (AGENTS.md D6).
                    scope.launch {
                        launchExport(encodeSettingsBackup(settings, localLibrary.snapshot()))
                    }
                }
                SettingsButton(strings.importSettings, settingsPalette, index = 1, count = 2) {
                    showImportConfirmation = true
                }

                SettingsSectionLabel(strings.about, settingsPalette)
                SettingsValue(
                    label = strings.version,
                    value = BuildInfo.VERSION_NAME,
                    supportingText = "Build ${BuildInfo.VERSION_CODE}",
                    accentPalette = settingsPalette,
                    index = 0,
                    count = 2,
                )
                SettingsValue(
                    label = strings.openSourceLicense,
                    value = strings.amllComponentName,
                    supportingText = strings.amllLicenseNote,
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
            title = { Text(strings.importSettingsConfirmTitle) },
            text = {
                Text(strings.importSettingsConfirmMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmation = false
                        launchImport()
                    },
                ) {
                    Text(strings.chooseFile)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmation = false }) {
                    Text(strings.cancel)
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
                text = strings.settings,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = accentPalette.onPageStart,
            )
            Text(
                text = strings.settingsHeroSubtitle,
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
private val QualityAvailabilityNote: String get() = strings.audioQualityAvailabilityNote

/** Shown for the "no explicit choice" entry and whenever the chosen device cannot be resolved. */
private val DefaultAudioOutputLabel: String get() = strings.audioOutputSystemDefault

/** The menu row has space to say what the compact value cannot. */
private val DefaultAudioOutputMenuLabel: String get() = strings.audioOutputSystemDefaultMenu

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
        else -> strings.audioOutputDeviceUnavailable(audioOutputDeviceName(selectedId))
    }
    SettingsDropdownRow(
        label = strings.audioOutputDevice,
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
        label = strings.desktopLyricsAlignment,
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
        label = strings.lyricsSource,
        valueLabel = LyricSourceMode.fromWireValue(selectedWireValue).displayName,
        options = LyricSourceMode.entries,
        optionLabel = LyricSourceMode::displayName,
        accentPalette = accentPalette,
        index = index,
        count = count,
        supportingText = listOf(
            strings.lyricsSourceAmllNote,
        ),
        onUpdate = onUpdate,
    )
}

private val SongTransitionMode.displayName: String
    get() = when (this) {
        SongTransitionMode.OFF -> strings.transitionOff
        SongTransitionMode.CROSSFADE -> strings.transitionCrossfade
        SongTransitionMode.SMART -> strings.transitionSmart
    }

private val SongTransitionMode.menuLabel: String
    get() = when (this) {
        SongTransitionMode.OFF -> strings.transitionOff
        SongTransitionMode.CROSSFADE -> strings.transitionCrossfadeMenu
        SongTransitionMode.SMART -> strings.transitionSmartMenu
    }

/**
 * How one song hands over to the next: not at all, a fixed crossfade, or a smart transition, and
 * for smart transitions what the beat model runs on here.
 *
 * The coordinator persists and applies a change itself; [onChange] only carries the write through
 * the page's own save path, so a failed durable write reports here like every other row.
 */
@Composable
private fun SongTransitionSection(
    coordinator: SongTransitionCoordinator,
    accentPalette: ContentAccentPalette,
    onChange: (write: () -> Unit) -> Unit,
) {
    val preferences by coordinator.preferences.collectAsState()
    val accelerator by coordinator.accelerator.collectAsState()
    val current = preferences ?: SongTransitionPreferences()
    val capability = coordinator.capability
    val scope = rememberCoroutineScope()
    val rows = if (current.mode == SongTransitionMode.OFF) 1 else 2

    SettingsSectionLabel(strings.songTransitions, accentPalette)
    SettingsDropdownRow(
        label = strings.betweenSongs,
        valueLabel = current.mode.displayName,
        options = SongTransitionMode.entries,
        optionLabel = { it.displayName },
        menuLabel = { it.menuLabel },
        accentPalette = accentPalette,
        index = 0,
        count = rows,
        supportingText = listOfNotNull(songTransitionNote(current.mode, capability)),
        onUpdate = { mode -> onChange { coordinator.setMode(mode) } },
    )
    when (current.mode) {
        SongTransitionMode.OFF -> Unit
        SongTransitionMode.CROSSFADE -> CrossfadeLengthRow(
            seconds = current.crossfadeSeconds,
            accentPalette = accentPalette,
            index = 1,
            count = rows,
            onUpdate = { seconds -> onChange { coordinator.setCrossfadeSeconds(seconds) } },
        )
        SongTransitionMode.SMART -> BeatAnalysisRow(
            state = accelerator,
            accentPalette = accentPalette,
            index = 1,
            count = rows,
            onProbe = { scope.launch { coordinator.probeAccelerator() } },
        )
    }
}

private fun songTransitionNote(mode: SongTransitionMode, capability: TransitionCapability): String? = when {
    mode == SongTransitionMode.OFF -> null
    capability == TransitionCapability.NONE -> strings.transitionUnsupported
    mode == SongTransitionMode.CROSSFADE -> strings.transitionCrossfadeNote
    capability == TransitionCapability.CROSSFADE ->
        strings.transitionSmartFixedTempoNote
    else -> strings.transitionSmartNote
}

@Composable
private fun CrossfadeLengthRow(
    seconds: Long,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    onUpdate: (Long) -> Unit,
) {
    var dragged by remember(seconds) { mutableStateOf(seconds.toFloat()) }
    Surface(
        shape = connectedListItemShape(index = index, count = count),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ExpressiveLayout.ConnectedItemGap),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.transitionLength, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = strings.secondsValue(dragged.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnQuietContainer,
                )
            }
            Slider(
                value = dragged,
                onValueChange = { dragged = it },
                onValueChangeFinished = { onUpdate(dragged.roundToInt().toLong()) },
                valueRange = MIN_CROSSFADE_SECONDS.toFloat()..MAX_CROSSFADE_SECONDS.toFloat(),
                steps = (MAX_CROSSFADE_SECONDS - MIN_CROSSFADE_SECONDS - 1).toInt(),
                colors = SliderDefaults.colors(
                    thumbColor = accentPalette.accent,
                    activeTrackColor = accentPalette.accent,
                    inactiveTrackColor = accentPalette.onQuietContainer.copy(alpha = 0.16f),
                ),
            )
        }
    }
}

/** What the beat model runs on here, loaded on demand so opening Settings costs nothing. */
@Composable
private fun BeatAnalysisRow(
    state: BeatAcceleratorState,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    onProbe: () -> Unit,
) {
    val (value, note) = when (state) {
        BeatAcceleratorState.NotLoaded ->
            strings.beatModelNotLoaded to strings.beatModelNotLoadedNote
        BeatAcceleratorState.Loading -> strings.beatModelLoading to ""
        is BeatAcceleratorState.Ready ->
            "${state.accelerator.label} · ${state.deviceLabel}" to strings.beatModelReadyNote
        is BeatAcceleratorState.Unavailable ->
            strings.beatModelUnavailable to strings.beatModelUnavailableNote(state.reason)
    }
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
            Column(Modifier.weight(1f)) {
                Text(strings.beatAnalysis, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnQuietContainer,
                )
                val details = listOf(
                    note,
                    strings.beatAnalysisSourceNote,
                ).filter { it.isNotEmpty() }
                details.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentPalette.secondaryOnQuietContainer,
                    )
                }
            }
            if (state == BeatAcceleratorState.NotLoaded) {
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onProbe) { Text(strings.checkNow) }
            }
        }
    }
}

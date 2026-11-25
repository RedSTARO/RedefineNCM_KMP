package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.data.MetadataCache
import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryStore
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.LanguageSetting
import com.leejlredstar.redefinencm.kmp.i18n.strings
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
import com.leejlredstar.redefinencm.kmp.transition.BeatAcceleratorState
import com.leejlredstar.redefinencm.kmp.transition.MAX_CROSSFADE_SECONDS
import com.leejlredstar.redefinencm.kmp.transition.MIN_CROSSFADE_SECONDS
import com.leejlredstar.redefinencm.kmp.transition.SongTransitionCoordinator
import com.leejlredstar.redefinencm.kmp.transition.SongTransitionMode
import com.leejlredstar.redefinencm.kmp.transition.SongTransitionPreferences
import com.leejlredstar.redefinencm.kmp.transition.TransitionCapability
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveFeatureIcon
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageBar
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsButton
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsLinkRow
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsSectionLabel
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsSwitch
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsValue
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.component.preferenceItem
import com.leejlredstar.redefinencm.kmp.ui.component.rememberConnectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemeMode
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemePreferences
import com.leejlredstar.redefinencm.kmp.ui.theme.dynamicColorSupported
import com.leejlredstar.redefinencm.kmp.util.BuildInfo
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SettingsBackupImportResult
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.applySettingsBackup
import com.leejlredstar.redefinencm.kmp.util.cellularPlaybackQuality
import com.leejlredstar.redefinencm.kmp.util.consumeSettingsImportResult
import com.leejlredstar.redefinencm.kmp.util.encodeSettingsBackup
import com.leejlredstar.redefinencm.kmp.util.getBooleanAsync
import com.leejlredstar.redefinencm.kmp.util.getLongAsync
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import com.leejlredstar.redefinencm.kmp.util.importSettingsBackup
import com.leejlredstar.redefinencm.kmp.util.redactUrlUserInfo
import com.leejlredstar.redefinencm.kmp.util.rememberExportFileLauncher
import com.leejlredstar.redefinencm.kmp.util.rememberImportFileLauncher
import com.leejlredstar.redefinencm.kmp.util.rememberNotificationAccess
import com.leejlredstar.redefinencm.kmp.util.settingsImportCount
import com.leejlredstar.redefinencm.kmp.util.settingsImportResult
import com.leejlredstar.redefinencm.kmp.util.sourceCodeUrl
import com.leejlredstar.redefinencm.kmp.util.wifiPlaybackQuality
import com.leejlredstar.redefinencm.kmp.viewmodel.AccountsViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

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
    /** Opens the third-party notices and license texts. */
    onOpenLicenses: () -> Unit = {},
    /** Settings is a page opened from the library and the sidebar, so it has a way back. */
    onBack: (() -> Unit)? = null,
    settings: PlatformSettings = koinInject(),
    mainViewModel: MainViewModel = koinInject(),
    accountsViewModel: AccountsViewModel = koinInject(),
    localLibrary: LocalLibraryStore = koinInject(),
    nowPlayingViewModel: NowPlayingViewModel = koinInject(),
    songTransitions: SongTransitionCoordinator = koinInject(),
    metadataCache: MetadataCache = koinInject(),
) {
    val cacheBytes by metadataCache.bytes.collectAsState()
    var cacheLimit by remember(settings) { mutableStateOf(MetadataCache.DefaultLimitMib) }
    var cacheRefreshRequest by remember { mutableIntStateOf(0) }
    var cacheClearing by remember { mutableStateOf(false) }
    var cacheReadFailed by remember { mutableStateOf(false) }
    var wifiQuality by remember(settings) { mutableStateOf(SettingKeys.ONLINE_PLAY_QUALITY_WIFI_DEFAULT.name) }
    var cellularQuality by remember(settings) {
        mutableStateOf(SettingKeys.ONLINE_PLAY_QUALITY_CELLULAR_DEFAULT.name)
    }
    var dlQuality by remember(settings) { mutableStateOf(SettingKeys.DOWNLOAD_QUALITY_DEFAULT.name) }
    var replacePlaylist by remember(settings) { mutableStateOf(SettingKeys.REPLACE_PLAYLIST_DEFAULT) }
    var checkUpdate by remember(settings) { mutableStateOf(false) }
    var searchPrediction by remember(settings) { mutableStateOf(true) }
    var showDownloadStatus by remember(settings) { mutableStateOf(SettingKeys.SHOW_DOWNLOAD_STATUS_DEFAULT) }
    var closeToTray by remember(settings) { mutableStateOf(SettingKeys.DESKTOP_CLOSE_TO_TRAY_DEFAULT) }
    val themeMode by ThemePreferences.mode.collectAsState()
    val languageSetting by I18n.setting.collectAsState()
    val dynamicColor by ThemePreferences.dynamicColor.collectAsState()
    val isDesktop = remember { getPlatform().isDesktop }
    // Close to the tray only where there is a tray to close to.
    val hasSystemTray = remember { getPlatform().hasSystemTray }
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
        mutableStateOf(LyricSourceMode.DEFAULT)
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
    val pendingImports by settingsImportCount.collectAsState()
    val importingBackup = pendingImports > 0
    val pendingImportResult by settingsImportResult.collectAsState()
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
        // An imported backup can carry another language or theme; show them at once. After a failed
        // write the stored values are the old ones, so this also takes back what the row showed.
        I18n.load(settings)
        ThemePreferences.load(settings)
        wifiQuality = settings.wifiPlaybackQuality().name
        cellularQuality = settings.cellularPlaybackQuality().name
        dlQuality = settings.getString(SettingKeys.DOWNLOAD_QUALITY, SettingKeys.DOWNLOAD_QUALITY_DEFAULT.name)
        cacheLimit = MetadataCache.normalizeLimit(settings.getLong(SettingKeys.METADATA_CACHE_LIMIT_MIB, MetadataCache.DefaultLimitMib))
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
        )
        nowPlayingViewModel.setLyricDisplayOptions(
            showTranslation = showTranslatedLyric,
            showRomanization = showRomanLyric,
        )
        nowPlayingViewModel.setLyricSourceMode(lyricSourceMode)
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
            settings.awaitLoaded()
            wifiQuality = settings.wifiPlaybackQuality().name
            cellularQuality = settings.cellularPlaybackQuality().name
            dlQuality = settings.getStringAsync(SettingKeys.DOWNLOAD_QUALITY, SettingKeys.DOWNLOAD_QUALITY_DEFAULT.name)
            cacheLimit = MetadataCache.normalizeLimit(settings.getLongAsync(SettingKeys.METADATA_CACHE_LIMIT_MIB, MetadataCache.DefaultLimitMib))
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
            )
            nowPlayingViewModel.setLyricDisplayOptions(
                showTranslation = showTranslatedLyric,
                showRomanization = showRomanLyric,
            )
            nowPlayingViewModel.setLyricSourceMode(lyricSourceMode)
            useDynamicCover = settings.getBooleanAsync(SettingKeys.USE_DYNAMIC_COVER, false)
            nowPlayingViewModel.setUseDynamicCover(useDynamicCover)
            settingsLoaded = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            settingsLoadError = failure.message ?: strings.settingsLoadFailed
        }
    }

    LaunchedEffect(metadataCache, settingsLoaded, cacheRefreshRequest) {
        if (!settingsLoaded) return@LaunchedEffect
        cacheReadFailed = false
        try {
            metadataCache.refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            cacheReadFailed = true
            settingsMessage = strings.metadataCacheFailed
        }
    }

    LaunchedEffect(pendingImportResult) {
        val result = pendingImportResult ?: return@LaunchedEffect
        settingsMessage = when (result) {
            SettingsBackupImportResult.Imported -> strings.settingsImported
            SettingsBackupImportResult.Rejected -> strings.settingsImportNotApplied
            is SettingsBackupImportResult.SettingsSaveFailed -> strings.settingsSaveFailed(
                redactUrlUserInfo(result.cause.message ?: strings.unknownError),
            )
            is SettingsBackupImportResult.LibraryFailed -> strings.settingsImportedLibraryFailed(
                redactUrlUserInfo(result.cause.message ?: strings.unknownError),
            )
        }
        if (result is SettingsBackupImportResult.Imported || result is SettingsBackupImportResult.LibraryFailed) {
            cacheRefreshRequest += 1
        }
        consumeSettingsImportResult(result)
    }

    val launchImport = rememberImportFileLauncher(
        onFailed = { settingsMessage = strings.settingsImportReadFailed },
    ) { json ->
        scope.launch {
            importSettingsBackup(
                json = json,
                applySettings = { applySettingsBackup(it, settings) },
                flushSettings = settings::flush,
                onSettingsApplied = {
                    lyricSourceWriteGeneration += 1
                    lyricDisplayWriteGeneration += 1
                    reloadSettingsSnapshot()
                },
                reloadSettings = ::reloadSettingsSnapshot,
                importLibrary = localLibrary::importPlaylists,
            )
        }
    }
    val launchExport = rememberExportFileLauncher(onFailed = { settingsMessage = strings.settingsExportFailed })
    // Keep observers and the permission launcher under the screen, independent of which
    // category's items happen to be on screen when they complete.
    val transitionPreferences by songTransitions.preferences.collectAsState()
    val transitionAccelerator by songTransitions.accelerator.collectAsState()
    val transitionModelDownload = songTransitions.modelDownload
    val transitionDownloadProgress = transitionModelDownload?.collectAsState()?.value
    val notificationAccess = if (optionalLyricSurface != null) rememberNotificationAccess() else null
    val uriHandler = LocalUriHandler.current

    SettingsLayout(
        onBack = onBack,
        scaffoldPadding = scaffoldPadding,
        snackbarHostState = snackbarHostState,
        loaded = settingsLoaded,
        loadError = settingsLoadError,
        onRetry = { settingsLoadRequest += 1 },
    ) { category ->
        when (category) {
            SettingsCategory.Accounts -> {
                SettingsLinkRow(
                    label = strings.accountsAndServices,
                    supportingText = accountsSummary,
                    leadingIcon = AppIcons.Person,
                    onClick = onOpenAccounts,
                )
            }

            SettingsCategory.Playback -> {
                // The output-device row sits with the quality dropdowns because it is the other
                // half of "what comes out of the speakers", not a behaviour toggle.
                val outputDeviceRows = if (supportsAudioOutputDeviceSelection) 1 else 0
                val dynamicCoverRows = if (supportsDynamicNowPlayingCover) 1 else 0
                // The desktop always counts as Wi-Fi, so a mobile-data quality could never apply
                // there; it has one quality, stored as the Wi-Fi one.
                val cellularRows = if (isDesktop) 0 else 1
                val playbackSettingCount = 2 + cellularRows + outputDeviceRows + dynamicCoverRows
                SettingsDropdown(
                    wifiQuality,
                    if (isDesktop) strings.streamingQuality else strings.streamingQualityWifi,
                    SoundQuality.entries,
                    index = 0,
                    count = playbackSettingCount,
                    supportingText = QualityAvailabilityNote,
                ) { v ->
                    wifiQuality = v.name
                    persistSettings({ settings.setString(SettingKeys.ONLINE_PLAY_QUALITY_WIFI, v.name) })
                }
                if (cellularRows > 0) {
                    SettingsDropdown(
                        cellularQuality,
                        strings.streamingQualityCellular,
                        SoundQuality.entries,
                        index = 1,
                        count = playbackSettingCount,
                        supportingText = QualityAvailabilityNote,
                    ) { v ->
                        cellularQuality = v.name
                        persistSettings({ settings.setString(SettingKeys.ONLINE_PLAY_QUALITY_CELLULAR, v.name) })
                    }
                }
                if (supportsAudioOutputDeviceSelection) {
                    AudioOutputDeviceDropdown(
                        selectedId = audioOutputDeviceId,
                        index = 1 + cellularRows,
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
                    index = 1 + cellularRows + outputDeviceRows,
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
                        index = 2 + cellularRows + outputDeviceRows,
                        count = playbackSettingCount,
                    ) { enabled ->
                        useDynamicCover = enabled
                        persistSettings(
                            write = { settings.setBoolean(SettingKeys.USE_DYNAMIC_COVER, enabled) },
                            onWritten = { nowPlayingViewModel.setUseDynamicCover(enabled) },
                        )
                    }
                }
            }

            SettingsCategory.Transition -> {
                SongTransitionSettings(
                    current = transitionPreferences ?: SongTransitionPreferences(),
                    accelerator = transitionAccelerator,
                    downloadProgress = transitionDownloadProgress,
                    downloadsModel = transitionModelDownload != null,
                    capability = songTransitions.capability,
                    onModeChange = { mode -> persistSettings({ songTransitions.setMode(mode) }) },
                    onCrossfadeChange = { seconds -> persistSettings({ songTransitions.setCrossfadeSeconds(seconds) }) },
                    onProbe = { scope.launch { songTransitions.probeAccelerator() } },
                )
            }

            SettingsCategory.Lyrics -> {
                val surfaceRows = if (optionalLyricSurface != null) 1 else 0
                val surfaceLayoutRows = if (windowedLyricSurface != null) 2 else 0
                val lyricSettingCount = 3 + surfaceRows + surfaceLayoutRows
                LyricSourceDropdown(
                    selected = lyricSourceMode,
                    index = 0,
                    count = lyricSettingCount,
                ) { mode ->
                    val writeGeneration = ++lyricSourceWriteGeneration
                    lyricSourceMode = mode
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
                        index = 1,
                        count = lyricSettingCount,
                        // The switch stays as chosen; this says why nothing appears.
                        supportingText = strings.lyricNotificationBlocked
                            .takeIf { extraLyricSurfaceEnabled && notificationAccess?.allowed == false },
                    ) { enabled ->
                        extraLyricSurfaceEnabled = enabled
                        // Asked here, where the reason is plain, rather than at every launch.
                        if (enabled) notificationAccess?.request()
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
            }

            SettingsCategory.Downloads -> {
                SettingsDropdown(
                    dlQuality,
                    strings.downloadQuality,
                    SoundQuality.entries,
                    index = 0,
                    count = 2,
                    supportingText = strings.downloadQualityHint,
                ) { v ->
                    dlQuality = v.name
                    persistSettings({ settings.setString(SettingKeys.DOWNLOAD_QUALITY, v.name) })
                }
                SettingsSwitch(
                    showDownloadStatus,
                    strings.showDownloadStatus,
                    index = 1,
                    count = 2,
                    supportingText = strings.showDownloadStatusHint,
                ) { v ->
                    showDownloadStatus = v
                    persistSettings({ settings.setBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, v) })
                }
            }

            SettingsCategory.General -> {
                val trayRows = if (hasSystemTray) 1 else 0
                val languageRows = 1
                val themeRows = languageRows + if (dynamicColorSupported) 2 else 1
                val generalCount = 3 + trayRows + themeRows
                // One of the three languages, or the system's (English when it is none of them).
                SettingsDropdownRow(
                    label = strings.settingsLanguage,
                    valueLabel = languageSetting.displayName,
                    selectedOption = languageSetting,
                    options = LanguageSetting.entries,
                    optionLabel = LanguageSetting::displayName,
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
                    selectedOption = themeMode,
                    options = ThemeMode.entries,
                    optionLabel = ThemeMode::displayName,
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
                    index = themeRows,
                    count = generalCount,
                    supportingText = strings.searchSuggestionsHint,
                ) { v ->
                    searchPrediction = v
                    persistSettings({ settings.setBoolean(SettingKeys.SEARCH_PREDICTION, v) })
                }
                if (hasSystemTray) {
                    SettingsSwitch(
                        closeToTray,
                        strings.closeToTray,
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
                    index = themeRows + 1 + trayRows,
                    count = generalCount,
                ) { v ->
                    checkUpdate = v
                    persistSettings({ settings.setBoolean(SettingKeys.CHECK_UPDATE, v) })
                }
                SettingsButton(
                    strings.checkUpdatesNow,
                    leadingIcon = AppIcons.Refresh,
                    index = themeRows + 2 + trayRows,
                    count = generalCount,
                ) {
                    mainViewModel.checkForUpdatesNow()
                }
            }

            SettingsCategory.Cache -> {
                SettingsValue(
                    label = strings.metadataCacheUsage,
                    value = if (cacheReadFailed) strings.metadataCacheUnavailable else cacheBytes?.let {
                        strings.metadataCacheSizeKiB((it + 1_023) / 1_024)
                    } ?: strings.loading,
                    supportingText = strings.metadataCacheHint,
                    index = 0,
                    count = 3,
                )
                SettingsDropdownRow(
                    label = strings.metadataCacheLimit,
                    valueLabel = strings.metadataCacheSizeMiB(cacheLimit),
                    selectedOption = cacheLimit,
                    options = MetadataCache.LimitChoicesMib,
                    optionLabel = { strings.metadataCacheSizeMiB(it) },
                    index = 1,
                    count = 3,
                    supportingText = listOf(strings.metadataCacheLimitHint),
                ) { value ->
                    cacheLimit = value
                    persistSettings(
                        write = { settings.setLong(SettingKeys.METADATA_CACHE_LIMIT_MIB, value) },
                        onPersisted = { cacheRefreshRequest += 1 },
                    )
                }
                SettingsButton(
                    if (cacheClearing) strings.metadataCacheClearing else strings.metadataCacheClear,
                    leadingIcon = AppIcons.Delete,
                    index = 2,
                    count = 3,
                    enabled = !cacheClearing,
                ) {
                    cacheClearing = true
                    scope.launch {
                        try {
                            metadataCache.clear()
                            cacheReadFailed = false
                            settingsMessage = strings.metadataCacheCleared
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            settingsMessage = strings.metadataCacheFailed
                        } finally {
                            cacheClearing = false
                        }
                    }
                }
            }

            SettingsCategory.Backup -> {
                SettingsButton(
                    label = strings.exportSettings,
                    leadingIcon = AppIcons.OpenInNew,
                    index = 0,
                    count = 2,
                    enabled = !importingBackup,
                ) {
                    // The local account's playlists travel with the settings (AGENTS.md D6).
                    scope.launch {
                        try {
                            launchExport(encodeSettingsBackup(settings, localLibrary.snapshot()))
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { settingsMessage = strings.settingsExportFailed }
                    }
                }
                SettingsButton(
                    if (importingBackup) strings.settingsImporting else strings.importSettings,
                    leadingIcon = AppIcons.FolderOpen,
                    index = 1,
                    count = 2,
                    enabled = !importingBackup,
                ) {
                    showImportConfirmation = true
                }
            }

            SettingsCategory.About -> {
                SettingsValue(
                    label = strings.version,
                    value = BuildInfo.VERSION_NAME,
                    supportingText = strings.buildNumber(BuildInfo.VERSION_CODE),
                    index = 0,
                    count = 3,
                )
                // The texts are packaged with the app; this is the way to read them.
                SettingsLinkRow(
                    label = strings.openSourceLicense,
                    supportingText = strings.openSourceLicensesHint,
                    leadingIcon = AppIcons.Info,
                    index = 1,
                    count = 3,
                    onClick = onOpenLicenses,
                )
                // The source this very build was made from: what AGPL asks a network service to
                // offer the people using it, and what a bug report can point at.
                SettingsLinkRow(
                    label = strings.sourceCode,
                    supportingText = strings.sourceCodeHint,
                    leadingIcon = AppIcons.Link,
                    external = true,
                    index = 2,
                    count = 3,
                    onClick = { runCatching { uriHandler.openUri(sourceCodeUrl(BuildInfo.COMMIT_HASH)) } },
                )
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsLayout(
    onBack: (() -> Unit)?,
    scaffoldPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    loaded: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    categoryContent: @Composable (SettingsCategory) -> Unit,
) {
    // Do not measure the loading/error UI with this state. That would clamp the restored
    // position when returning from Accounts or Licenses while settings are still loading.
    val listState = rememberLazyListState()
    val ready = loaded && loadError == null
    val scope = rememberCoroutineScope()
    var jumpJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var landing by remember { mutableStateOf<SettingsIndexLanding?>(null) }
    val activeCategory by remember(listState) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            activeSettingsCategory(
                firstVisibleIndex = first,
                firstVisibleOffset = listState.firstVisibleItemScrollOffset,
                lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first,
                canScrollForward = listState.canScrollForward,
                landing = landing,
            )
        }
    }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                landing?.let { if (it.listIndex != index || it.scrollOffset != offset) landing = null }
            }
    }
    val reducedMotion = rememberReducedMotionEnabled()
    fun jumpTo(category: SettingsCategory) {
        jumpJob?.cancel()
        landing = null
        jumpJob = scope.launch {
            if (reducedMotion) listState.scrollToItem(category.listIndex)
            else listState.animateScrollToItem(category.listIndex)
            if (category.listIndex in listState.layoutInfo.visibleItemsInfo.map { it.index }) {
                landing = SettingsIndexLanding(category, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            }
        }
    }

    ExpressivePage(contentWindowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = LocalExpressivePageGutter.current
            val indexedBeside = maxWidth >= 840.dp
            val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    ExpressivePageBar(title = strings.settings, onBack = onBack, scrollBehavior = scrollBehavior) {
                        if (ready && !indexedBeside) SettingsIndexMenu(activeCategory, ::jumpTo)
                    }
                },
            ) { barPadding ->
                Row(
                    modifier = Modifier.fillMaxSize().padding(top = barPadding.calculateTopPadding()),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (ready && indexedBeside) {
                        SettingsIndexPane(
                            active = activeCategory,
                            onChoose = ::jumpTo,
                            modifier = Modifier.width(244.dp).padding(start = gutter, end = 8.dp, top = 16.dp),
                            bottomPadding = scaffoldPadding,
                        )
                    }
                    Box(
                        Modifier.widthIn(max = 760.dp).weight(1f, fill = false).fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (ready) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = gutter,
                                    end = gutter,
                                    top = 16.dp,
                                    bottom = scaffoldPadding.calculateBottomPadding() + 24.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                item(key = "settings-hero") { SettingsHero() }
                                items(SettingsCategory.entries, key = { it.name }) { category ->
                                    Column(Modifier.fillMaxWidth()) {
                                        SettingsSectionLabel(category.label)
                                        categoryContent(category)
                                    }
                                }
                            }
                        } else {
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                    .padding(horizontal = gutter)
                                    .padding(top = 16.dp, bottom = scaffoldPadding.calculateBottomPadding() + 16.dp),
                            ) {
                                if (loadError == null) {
                                    ExpressiveLoadingState(label = strings.settingsLoading)
                                } else {
                                    ExpressiveStatePanel(
                                        title = strings.settingsLoadFailed,
                                        message = loadError,
                                        icon = AppIcons.Refresh,
                                        tone = ExpressiveStateTone.Error,
                                        actionLabel = strings.retry,
                                        onAction = onRetry,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = scaffoldPadding.calculateBottomPadding()),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsHero() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(scheme.primaryContainer, lerp(scheme.primaryContainer, scheme.surfaceContainerHigh, 0.45f))),
                RoundedCornerShape(ExpressiveLayout.FeatureCorner),
            )
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpressiveFeatureIcon(AppIcons.Settings, containerColor = scheme.primary, contentColor = scheme.onPrimary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = strings.settings,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = scheme.onPrimaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Text(strings.settingsHeroSubtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsIndexMenu(active: SettingsCategory, onChoose: (SettingsCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(AppIcons.QueueMusic, contentDescription = strings.settingsCategories)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = strings.settingsCategories,
                style = MaterialTheme.typography.labelLargeEmphasized,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
            SettingsCategory.entries.forEach { category ->
                val selected = category == active
                DropdownMenuItem(
                    text = {
                        Text(category.label, style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge)
                    },
                    leadingIcon = { Icon(category.icon, contentDescription = null) },
                    trailingIcon = if (selected) ({ Icon(AppIcons.Check, contentDescription = null) }) else null,
                    modifier = Modifier.semantics { this.selected = selected },
                    onClick = { expanded = false; onChoose(category) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsIndexPane(
    active: SettingsCategory,
    onChoose: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: PaddingValues,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == active.listIndex }) {
            listState.scrollToItem(active.listIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.selectableGroup(),
        contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(ExpressiveLayout.ConnectedItemGap),
    ) {
        item { SettingsSectionLabel(strings.settingsCategories) }
        items(SettingsCategory.entries, key = { it.name }) { category ->
            val selected = category == active
            Surface(
                selected = selected,
                onClick = { onChoose(category) },
                shape = connectedListItemShape(category.ordinal, SettingsCategory.entries.size),
                color = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
                contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(category.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        category.label,
                        style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Shows the current value in the row, and marks the current option in the opened menu. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> SettingsDropdownRow(
    label: String,
    valueLabel: String,
    selectedOption: T,
    options: List<T>,
    optionLabel: (T) -> String,
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
    val scheme = MaterialTheme.colorScheme
    val valueStyle = MaterialTheme.typography.bodyMediumEmphasized
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Surface(
        onClick = { setExpanded(true) },
        shape = rememberConnectedListItemShape(index, count, interactionSource),
        color = scheme.surfaceContainerHigh,
        interactionSource = interactionSource,
        modifier = Modifier.preferenceItem(),
    ) {
        Box {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                // Budget both the value and the affordance before deciding whether there is
                // enough room beside the label. Long translations and large type stack instead.
                val valueMaxWidth = (maxWidth - 24.dp).coerceAtLeast(0.dp) * 0.34f
                val valueFits = with(density) {
                    textMeasurer.measure(valueLabel, valueStyle, maxLines = 1).size.width.toDp() <= valueMaxWidth
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (supportingText.isEmpty()) 56.dp else 72.dp)
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        if (!valueFits) Text(valueLabel, style = valueStyle, color = scheme.primary)
                        supportingText.forEach { note ->
                            Text(note, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                    if (valueFits) Text(valueLabel, style = valueStyle, color = scheme.primary, maxLines = 1)
                    Icon(AppIcons.UnfoldMore, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
                options.forEach { option ->
                    val selected = option == selectedOption
                    DropdownMenuItem(
                        text = {
                            Text(
                                menuLabel(option),
                                style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                                color = if (selected) scheme.primary else scheme.onSurface,
                            )
                        },
                        trailingIcon = if (selected) ({ Icon(AppIcons.Check, contentDescription = null, tint = scheme.primary) }) else null,
                        modifier = Modifier.semantics { this.selected = selected },
                        onClick = { setExpanded(false); onUpdate(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDropdown(
    selectedName: String,
    label: String,
    options: List<SoundQuality>,
    index: Int,
    count: Int,
    supportingText: String? = null,
    onUpdate: (SoundQuality) -> Unit,
) {
    val current = options.find { it.name == selectedName } ?: options.first()
    SettingsDropdownRow(
        label = label,
        valueLabel = current.toString(),
        selectedOption = current,
        options = options,
        optionLabel = { it.toString() },
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
        selectedOption = selected?.id ?: SYSTEM_DEFAULT_AUDIO_OUTPUT_ID,
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
    index: Int,
    count: Int,
    onUpdate: (LyricSurfaceAlignment) -> Unit,
) {
    SettingsDropdownRow(
        label = strings.desktopLyricsAlignment,
        valueLabel = selected.displayName,
        selectedOption = selected,
        options = LyricSurfaceAlignment.entries,
        optionLabel = LyricSurfaceAlignment::displayName,
        index = index,
        count = count,
        onUpdate = onUpdate,
    )
}

@Composable
private fun LyricSourceDropdown(
    selected: LyricSourceMode,
    index: Int,
    count: Int,
    onUpdate: (LyricSourceMode) -> Unit,
) {
    SettingsDropdownRow(
        label = strings.lyricsSource,
        valueLabel = selected.displayName,
        selectedOption = selected,
        options = LyricSourceMode.entries,
        optionLabel = LyricSourceMode::displayName,
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

/** The transition coordinator still owns persistence and all operation state. */
@Composable
private fun SongTransitionSettings(
    current: SongTransitionPreferences,
    accelerator: BeatAcceleratorState,
    downloadProgress: Float?,
    downloadsModel: Boolean,
    capability: TransitionCapability,
    onModeChange: (SongTransitionMode) -> Unit,
    onCrossfadeChange: (Long) -> Unit,
    onProbe: () -> Unit,
) {
    val rows = if (current.mode == SongTransitionMode.OFF) 1 else 2
    SettingsDropdownRow(
        label = strings.betweenSongs,
        valueLabel = current.mode.displayName,
        selectedOption = current.mode,
        options = SongTransitionMode.entries,
        optionLabel = { it.displayName },
        menuLabel = { it.menuLabel },
        index = 0,
        count = rows,
        supportingText = listOfNotNull(songTransitionNote(current.mode, capability)),
        onUpdate = onModeChange,
    )
    when (current.mode) {
        SongTransitionMode.OFF -> Unit
        SongTransitionMode.CROSSFADE -> CrossfadeLengthRow(
            seconds = current.crossfadeSeconds,
            index = 1,
            count = rows,
            onUpdate = onCrossfadeChange,
        )
        SongTransitionMode.SMART -> BeatAnalysisRow(
            state = accelerator,
            downloadProgress = downloadProgress,
            downloadsModel = downloadsModel,
            index = 1,
            count = rows,
            onProbe = onProbe,
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
    index: Int,
    count: Int,
    onUpdate: (Long) -> Unit,
) {
    var dragged by remember(seconds) { mutableStateOf(seconds.toFloat()) }
    Surface(
        shape = connectedListItemShape(index = index, count = count),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.preferenceItem(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.transitionLength, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = strings.secondsValue(dragged.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = dragged,
                onValueChange = { dragged = it },
                onValueChangeFinished = { onUpdate(dragged.roundToInt().toLong()) },
                valueRange = MIN_CROSSFADE_SECONDS.toFloat()..MAX_CROSSFADE_SECONDS.toFloat(),
                steps = (MAX_CROSSFADE_SECONDS - MIN_CROSSFADE_SECONDS - 1).toInt(),
            )
        }
    }
}

/** What the beat model runs on here, loaded on demand so opening Settings costs nothing. */
@Composable
private fun BeatAnalysisRow(
    state: BeatAcceleratorState,
    downloadProgress: Float?,
    downloadsModel: Boolean,
    index: Int,
    count: Int,
    onProbe: () -> Unit,
) {
    val (value, note) = when (state) {
        BeatAcceleratorState.NotLoaded ->
            strings.beatModelNotLoaded to strings.beatModelNotLoadedNote
        BeatAcceleratorState.Loading -> if (downloadProgress != null) {
            strings.beatModelDownloading((downloadProgress * 100).roundToInt()) to ""
        } else {
            strings.beatModelLoading to ""
        }
        is BeatAcceleratorState.Ready ->
            "${state.accelerator.label} · ${state.deviceLabel}" to strings.beatModelReadyNote
        is BeatAcceleratorState.Unavailable ->
            strings.beatModelUnavailable to strings.beatModelUnavailableNote(state.reason)
    }
    Surface(
        shape = connectedListItemShape(index = index, count = count),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.preferenceItem(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(strings.beatAnalysis, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            val details = listOf(
                note,
                if (downloadsModel) strings.beatModelDownloadNote else "",
                strings.beatAnalysisSourceNote,
            ).filter { it.isNotEmpty() }
            details.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val actionLabel = when {
                state == BeatAcceleratorState.NotLoaded -> strings.checkNow
                state is BeatAcceleratorState.Unavailable && state.retryable -> strings.retry
                else -> null
            }
            if (actionLabel != null) TextButton(onClick = onProbe) { Text(actionLabel) }
        }
    }
}

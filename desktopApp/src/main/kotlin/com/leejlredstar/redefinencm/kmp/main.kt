package com.leejlredstar.redefinencm.kmp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.notification.DesktopFloatingWindowNative
import com.leejlredstar.redefinencm.kmp.notification.DesktopLyricWindow
import com.leejlredstar.redefinencm.kmp.notification.FloatingLyricData
import com.leejlredstar.redefinencm.kmp.notification.LyricSurfaceAlignment
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.SYSTEM_DEFAULT_AUDIO_OUTPUT_ID
import com.leejlredstar.redefinencm.kmp.smtc.DesktopMediaControls
import com.leejlredstar.redefinencm.kmp.ui.component.DesktopDynamicCoverWindowLifecycle
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemePreferences
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.mouseBackNavigation
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.koin.core.context.GlobalContext
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    val settings = GlobalContext.get().get<PlatformSettings>()
    startFromTheSystemAudioOutput(settings::getString, settings::setString)
    DesktopLyricWindow.setEnabled(
        settings.getBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, false),
    )
    ThemePreferences.load(settings)
    DesktopLyricWindow.setLocked(
        settings.getBoolean(SettingKeys.DESKTOP_LYRIC_LOCKED, false),
    )
    DesktopLyricWindow.setAlignment(
        LyricSurfaceAlignment.fromWireValueOrDefault(
            settings.getString(SettingKeys.DESKTOP_LYRIC_ALIGNMENT, ""),
        ),
    )
    DesktopLyricWindow.setTextScale(
        settings.getString(SettingKeys.DESKTOP_LYRIC_TEXT_SCALE, "1").toFloatOrNull() ?: 1f,
    )
    launchDesktopApplication(settings)
}

/**
 * Drops any output device pinned in a previous session so this launch follows the system.
 *
 * A chosen device is a per-session override and is not carried across restarts. The OS renames
 * and reorders endpoints as hardware comes and goes: a monitor that reconnects as
 * `1 - Display (2- ...)` no longer matches the `1 - Display (...)` that was stored. A pin that
 * survives a restart keeps sending audio at whatever was chosen last week, and playing into a
 * device nobody is listening to is indistinguishable from a broken player. The safe start is the
 * device the OS is currently using.
 *
 * Only [JvmMediaPlayer][com.leejlredstar.redefinencm.kmp.player.JvmMediaPlayer] reads this key,
 * and only when opening a stream, which cannot happen before this returns: queue restoration
 * runs with `autoplay = false`.
 */
internal fun startFromTheSystemAudioOutput(
    getString: (key: String, default: String) -> String,
    setString: (key: String, value: String) -> Unit,
) {
    val pinned = getString(SettingKeys.AUDIO_OUTPUT_DEVICE, SYSTEM_DEFAULT_AUDIO_OUTPUT_ID)
    if (pinned == SYSTEM_DEFAULT_AUDIO_OUTPUT_ID) return
    runCatching {
        setString(SettingKeys.AUDIO_OUTPUT_DEVICE, SYSTEM_DEFAULT_AUDIO_OUTPUT_ID)
    }.onFailure { error ->
        // A store that will not take the reset leaves the pin in place, so say which device
        // playback is still aimed at; otherwise the silence has no explanation.
        System.err.println(
            "Could not clear the pinned audio output device, keeping '$pinned': ${error.message}",
        )
    }
}

/** A window's saved place: position and size in dp, and whether it was maximized. */
internal data class SavedWindowBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val maximized: Boolean = false,
) {
    fun encode(): String = "$x,$y,$width,$height,$maximized"

    companion object {
        fun decode(raw: String): SavedWindowBounds? {
            val parts = raw.split(',')
            if (parts.size < 4) return null
            val numbers = parts.take(4).map { it.toFloatOrNull() ?: return null }
            return SavedWindowBounds(
                x = numbers[0],
                y = numbers[1],
                width = numbers[2],
                height = numbers[3],
                maximized = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: false,
            )
        }
    }
}

/**
 * Whether a saved window would still land on a screen. A monitor unplugged since the last run
 * would otherwise reopen the window somewhere nobody can see it.
 */
private fun SavedWindowBounds.isOnAScreen(): Boolean = runCatching {
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    val titleBar = Rectangle(x.toInt(), y.toInt(), width.toInt().coerceAtLeast(1), 32)
    screens.any { it.defaultConfiguration.bounds.intersects(titleBar) }
}.getOrDefault(false)

private fun PlatformSettings.savedBounds(key: String): SavedWindowBounds? =
    SavedWindowBounds.decode(getString(key, ""))?.takeIf { it.isOnAScreen() }

private fun WindowState.currentBounds(): SavedWindowBounds? {
    val position = position
    if (!position.isSpecified) return null
    return SavedWindowBounds(
        x = position.x.value,
        y = position.y.value,
        width = size.width.value,
        height = size.height.value,
        maximized = placement == WindowPlacement.Maximized,
    )
}

/** Saves a window's place whenever it settles after a move or resize. */
@OptIn(FlowPreview::class)
@Composable
private fun RememberWindowBounds(
    state: WindowState,
    settings: PlatformSettings,
    key: String,
) {
    LaunchedEffect(state, key) {
        snapshotFlow { state.currentBounds() }
            .debounce(600)
            .collect { bounds ->
                bounds ?: return@collect
                // A maximized window reports the screen's size; keep the size it returns to.
                val previous = SavedWindowBounds.decode(settings.getString(key, ""))
                val toSave = if (bounds.maximized && previous != null) {
                    previous.copy(maximized = true)
                } else {
                    bounds
                }
                runCatching { settings.setString(key, toSave.encode()) }
            }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun launchDesktopApplication(settings: PlatformSettings) = application {
    // Where the window was left last time, if that place is still on a screen.
    val savedBounds = remember { settings.savedBounds(SettingKeys.DESKTOP_WINDOW_BOUNDS) }
    val mainWindowState = rememberWindowState(
        placement = if (savedBounds?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
        size = savedBounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1280.dp, 820.dp),
        position = savedBounds?.let { WindowPosition(it.x.dp, it.y.dp) }
            ?: WindowPosition(Alignment.Center),
    )
    RememberWindowBounds(mainWindowState, settings, SettingKeys.DESKTOP_WINDOW_BOUNDS)
    val player = remember { GlobalContext.get().get<PlatformPlayer>() }
    // macOS keeps its own title bar and traffic lights; the Windows-style chrome is drawn on the
    // other systems only.
    val systemChrome = remember { DesktopHost.usesSystemWindowChrome }
    val mainWindowHolder = remember { arrayOfNulls<ComposeWindow>(1) }
    var mainWindowVisible by remember { mutableStateOf(true) }
    var showWindowRequest by remember { mutableStateOf(0) }
    val trayState = rememberTrayState()
    var trayNoticeShown by remember { mutableStateOf(false) }
    // With the setting on (the default), closing the window keeps the app and its music
    // running in the tray; the tray menu's 退出 is what quits. With it off, closing quits.
    val closeMainWindow: () -> Unit = {
        val toTray = settings.getBoolean(
            SettingKeys.DESKTOP_CLOSE_TO_TRAY,
            SettingKeys.DESKTOP_CLOSE_TO_TRAY_DEFAULT,
        )
        if (toTray) {
            mainWindowVisible = false
            if (!trayNoticeShown) {
                trayNoticeShown = true
                trayState.sendNotification(
                    Notification(
                        title = "RedefineNCM 仍在运行",
                        message = "点按托盘图标打开窗口；在托盘菜单里选「退出」才会退出。",
                        type = Notification.Type.Info,
                    ),
                )
            }
        } else {
            exitApplication()
        }
    }

    Window(
        onCloseRequest = closeMainWindow,
        visible = mainWindowVisible,
        state = mainWindowState,
        title = "RedefineNCM",
        decoration = if (systemChrome) WindowDecoration.SystemDefault else WindowDecoration.Undecorated(),
        resizable = true,
        onKeyEvent = { event -> handleDesktopShortcut(event, player) },
    ) {
        mainWindowHolder[0] = window
        LaunchedEffect(showWindowRequest) {
            if (showWindowRequest > 0) {
                window.toFront()
                window.requestFocus()
            }
        }
        val mediaControls = remember(player) { DesktopMediaControls(player) }
        DisposableEffect(window, mediaControls) {
            // Below this the layout has nowhere to go, so the window stops shrinking here.
            window.minimumSize = Dimension(360, 480)
            val dynamicCoverWindowBinding =
                DesktopDynamicCoverWindowLifecycle.bind(window)
            mediaControls.start(window)
            onDispose {
                dynamicCoverWindowBinding.close()
                mediaControls.stop()
            }
        }

        val toggleMaximize = {
            mainWindowState.placement = if (
                mainWindowState.placement == WindowPlacement.Maximized
            ) {
                WindowPlacement.Floating
            } else {
                WindowPlacement.Maximized
            }
        }
        RedefineNCMTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (!systemChrome) {
                        Win10WindowChrome(
                            isMaximized = mainWindowState.placement == WindowPlacement.Maximized,
                            onMinimize = { mainWindowState.isMinimized = true },
                            onToggleMaximize = toggleMaximize,
                            onClose = closeMainWindow,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .mouseBackNavigation(),
                    ) {
                        App()
                    }
                }
            }
        }
    }

    AppTray(
        player = player,
        settings = settings,
        state = trayState,
        onShowWindow = {
            mainWindowVisible = true
            mainWindowState.isMinimized = false
            showWindowRequest++
        },
        onExit = ::exitApplication,
    )

    // Desktop floating-lyrics window (goal #2: the desktop equivalent of the Android
    // notification / iOS Live Activity). It is a second, frameless, always-on-top window
    // driven entirely by DesktopLyricWindow: the playback
    // pipeline calls updateLyric(...) + show(), and this window mirrors that state.
    FloatingLyricWindow(settings, player)
}

/**
 * Keys that do something anywhere in the main window, when no control has used the key first:
 * a focused text field keeps its space and arrows.
 */
private fun handleDesktopShortcut(event: KeyEvent, player: PlatformPlayer): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val shortcut = appShortcutFor(
        key = event.key,
        command = event.isCtrlPressed || event.isMetaPressed,
        shift = event.isShiftPressed,
    ) ?: return false
    return shortcut.perform(player)
}

/**
 * The tray icon: playback control while the window is out of the way, and the one place a locked
 * desktop lyric can be unlocked without opening settings. A locked lyric window lets every click
 * through, so it cannot offer that itself.
 */
@Composable
private fun ApplicationScope.AppTray(
    player: PlatformPlayer,
    settings: PlatformSettings,
    state: TrayState,
    onShowWindow: () -> Unit,
    onExit: () -> Unit,
) {
    val isPlaying by player.isPlaying.collectAsState()
    val hasMedia = player.currentMedia.collectAsState().value != null
    val lyricEnabled by DesktopLyricWindow.isEnabled.collectAsState()
    val lyricLocked by DesktopLyricWindow.isWindowLocked.collectAsState()
    Tray(
        icon = rememberVectorPainter(TrayIcon),
        state = state,
        tooltip = "RedefineNCM",
        onAction = onShowWindow,
        menu = {
            Item("显示主窗口", onClick = onShowWindow)
            Separator()
            Item(if (isPlaying) "暂停" else "播放", enabled = hasMedia, onClick = player::togglePlayPause)
            Item("上一首", enabled = hasMedia, onClick = player::seekToPrevious)
            Item("下一首", enabled = hasMedia, onClick = player::seekToNext)
            Separator()
            Item(
                if (lyricEnabled) "关闭桌面歌词" else "打开桌面歌词",
                onClick = { setDesktopLyricEnabled(settings, !lyricEnabled) },
            )
            if (lyricEnabled) {
                Item(
                    if (lyricLocked) "解锁桌面歌词" else "锁定桌面歌词",
                    onClick = { setDesktopLyricLocked(settings, !lyricLocked) },
                )
            }
            Separator()
            Item("退出", onClick = onExit)
        },
    )
}

private fun setDesktopLyricEnabled(settings: PlatformSettings, enabled: Boolean) {
    DesktopLyricWindow.setEnabled(enabled)
    runCatching { settings.setBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, enabled) }
}

private fun setDesktopLyricLocked(settings: PlatformSettings, locked: Boolean) {
    DesktopLyricWindow.setLocked(locked)
    runCatching { settings.setBoolean(SettingKeys.DESKTOP_LYRIC_LOCKED, locked) }
}

private fun setDesktopLyricTextScale(settings: PlatformSettings, scale: Float) {
    DesktopLyricWindow.setTextScale(scale)
    runCatching {
        settings.setString(
            SettingKeys.DESKTOP_LYRIC_TEXT_SCALE,
            DesktopLyricWindow.textScale.value.toString(),
        )
    }
}

/** A white note on the app's accent disc, legible on both light and dark taskbars. */
private val TrayIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TrayIcon",
        defaultWidth = 32.dp,
        defaultHeight = 32.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = addPathNodes("M12 1a11 11 0 1 1 0 22a11 11 0 1 1 0-22z"),
            fill = SolidColor(Color(0xFF006B5B)),
        )
        addPath(
            pathData = addPathNodes(
                "M12.5 6v7.05c-.44-.25-.95-.4-1.5-.4-1.66 0-3 1.12-3 2.5s1.34 2.5 3 2.5 3-1.12 3-2.5V9h3V6h-4.5z",
            ),
            fill = SolidColor(Color.White),
        )
    }.build()
}

@Composable
private fun ApplicationScope.FloatingLyricWindow(settings: PlatformSettings, player: PlatformPlayer) {
    val visible by DesktopLyricWindow.isWindowVisible.collectAsState()
    val savedBounds = remember { settings.savedBounds(SettingKeys.DESKTOP_LYRIC_WINDOW_BOUNDS) }
    // Where the user last dragged and sized it, instead of back to the bottom centre every launch.
    val windowState = rememberWindowState(
        size = savedBounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(760.dp, 112.dp),
        position = savedBounds?.let { WindowPosition(it.x.dp, it.y.dp) }
            ?: WindowPosition(Alignment.BottomCenter),
    )
    RememberWindowBounds(windowState, settings, SettingKeys.DESKTOP_LYRIC_WINDOW_BOUNDS)
    if (!visible) return

    val data by DesktopLyricWindow.floatingLyricData.collectAsState()
    val locked by DesktopLyricWindow.isWindowLocked.collectAsState()
    val alignment by DesktopLyricWindow.windowAlignment.collectAsState()
    val textScale by DesktopLyricWindow.textScale.collectAsState()

    Window(
        onCloseRequest = { DesktopLyricWindow.hide() },
        state = windowState,
        title = "桌面歌词",
        undecorated = true,   // frameless
        transparent = true,   // translucent (requires undecorated)
        alwaysOnTop = true,
        resizable = !locked,
    ) {
        // A locked window is parked: no drag area, no resize, and on Windows no pointer at all,
        // so clicks fall through to what is underneath. The tray menu and settings unlock it.
        DisposableEffect(window, locked) {
            DesktopFloatingWindowNative.setClickThrough(window, locked)
            onDispose { }
        }
        RedefineNCMTheme {
            if (locked) {
                FloatingLyricContent(data, alignment, textScale)
            } else {
                val hoverSource = remember { MutableInteractionSource() }
                val hovered by hoverSource.collectIsHoveredAsState()
                Box(Modifier.fillMaxSize().hoverable(hoverSource)) {
                    WindowDraggableArea(modifier = Modifier.fillMaxSize()) {
                        FloatingLyricContent(data, alignment, textScale)
                    }
                    // Controls appear under the pointer, the way desktop lyrics usually offer
                    // them, so they are reachable without opening the settings page.
                    AnimatedVisibility(
                        visible = hovered,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    ) {
                        FloatingLyricToolbar(
                            onPrevious = player::seekToPrevious,
                            onNext = player::seekToNext,
                            onSmaller = { setDesktopLyricTextScale(settings, textScale - 0.1f) },
                            onLarger = { setDesktopLyricTextScale(settings, textScale + 0.1f) },
                            onLock = { setDesktopLyricLocked(settings, true) },
                            onClose = { setDesktopLyricEnabled(settings, false) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingLyricToolbar(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onLock: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
    ) {
        Row {
            ToolbarButton(AppIcons.SkipPrevious, "上一首", onPrevious)
            ToolbarButton(AppIcons.SkipNext, "下一首", onNext)
            ToolbarButton(AppIcons.Remove, "缩小歌词", onSmaller)
            ToolbarButton(AppIcons.Add, "放大歌词", onLarger)
            ToolbarButton(AppIcons.Lock, "锁定桌面歌词（可在托盘菜单解锁）", onLock)
            ToolbarButton(AppIcons.Clear, "关闭桌面歌词", onClose)
        }
    }
}

@Composable
private fun ToolbarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

/**
 * The two lyric lines, and nothing else.
 *
 * There is no container to sit on: the window is transparent, so this draws straight onto
 * whatever wallpaper or window happens to be underneath. That rules out theme colours
 * (`onSurface` is near-black under the light theme and would vanish over a dark desktop), so
 * the text is a fixed light pair with a drop shadow, which keeps it readable over an arbitrary
 * background.
 *
 * The column still fills the window even though the text does not: [WindowDraggableArea]
 * only drags where its content draws, and a column sized to two glyph runs would leave the
 * window with almost nothing to grab.
 */
@Composable
private fun FloatingLyricContent(
    data: FloatingLyricData?,
    alignment: LyricSurfaceAlignment,
    textScale: Float,
) {
    val lyricShadow = Shadow(
        color = Color.Black.copy(alpha = 0.75f),
        offset = Offset(0f, 2f),
        blurRadius = 8f,
    )
    val textAlign = when (alignment) {
        LyricSurfaceAlignment.START -> TextAlign.Start
        LyricSurfaceAlignment.CENTER -> TextAlign.Center
        LyricSurfaceAlignment.END -> TextAlign.End
    }
    val currentStyle = MaterialTheme.typography.headlineSmall
    val nextStyle = MaterialTheme.typography.bodyMedium
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { paneTitle = "桌面歌词" },
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = when (alignment) {
            LyricSurfaceAlignment.START -> Alignment.Start
            LyricSurfaceAlignment.CENTER -> Alignment.CenterHorizontally
            LyricSurfaceAlignment.END -> Alignment.End
        },
    ) {
        Crossfade(
            targetState = data?.currentLyric?.ifBlank { data.title }.orEmpty(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "current lyric",
        ) { lyric ->
            Text(
                text = lyric.ifBlank { "暂无歌词" },
                style = currentStyle.copy(
                    shadow = lyricShadow,
                    fontSize = currentStyle.fontSize * textScale,
                    lineHeight = currentStyle.lineHeight * textScale,
                ),
                color = Color.White,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        // Nothing when there is no next line (the last line, an instrumental break); do not
        // fill the slot with a placeholder sentence.
        val next = data?.nextLyric?.takeIf { it.isNotBlank() }
        if (next != null) {
            Text(
                text = next,
                style = nextStyle.copy(
                    shadow = lyricShadow,
                    fontSize = nextStyle.fontSize * textScale,
                    lineHeight = nextStyle.lineHeight * textScale,
                ),
                color = Color.White.copy(alpha = 0.72f),
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

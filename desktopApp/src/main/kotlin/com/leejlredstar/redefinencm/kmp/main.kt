package com.leejlredstar.redefinencm.kmp

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.notification.DesktopFloatingWindowNative
import com.leejlredstar.redefinencm.kmp.notification.FloatingLyricData
import com.leejlredstar.redefinencm.kmp.notification.LyricSurfaceAlignment
import com.leejlredstar.redefinencm.kmp.notification.DesktopLyricWindow
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.SYSTEM_DEFAULT_AUDIO_OUTPUT_ID
import com.leejlredstar.redefinencm.kmp.smtc.DesktopMediaControls
import com.leejlredstar.redefinencm.kmp.ui.component.DesktopDynamicCoverWindowLifecycle
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    val settings = GlobalContext.get().get<PlatformSettings>()
    startFromTheSystemAudioOutput(settings::getString, settings::setString)
    DesktopLyricWindow.setEnabled(
        settings.getBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, false),
    )
    DesktopLyricWindow.setLocked(
        settings.getBoolean(SettingKeys.DESKTOP_LYRIC_LOCKED, false),
    )
    DesktopLyricWindow.setAlignment(
        LyricSurfaceAlignment.fromWireValueOrDefault(
            settings.getString(SettingKeys.DESKTOP_LYRIC_ALIGNMENT, ""),
        ),
    )
    launchDesktopApplication()
}

/**
 * Drops any output device pinned in a previous session so this launch follows the system.
 *
 * A chosen device is a per-session override, not a preference worth carrying across restarts.
 * The OS renames and reorders endpoints as hardware comes and goes — a monitor that reconnects
 * as `1 - Display (2- ...)` no longer matches the `1 - Display (...)` that was stored — and a
 * pin that survives a restart keeps sending audio at whatever was chosen last week. Playing into
 * a device nobody is listening to is indistinguishable from a broken player, so the safe start
 * is the one the OS is currently using.
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
        // playback is still aimed at rather than letting the silence be a mystery again.
        System.err.println(
            "Could not clear the pinned audio output device, keeping '$pinned': ${error.message}",
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun launchDesktopApplication() = application {
    val mainWindowState = rememberWindowState(
        size = DpSize(1280.dp, 820.dp),
        position = WindowPosition(Alignment.Center),
    )
    Window(
        onCloseRequest = ::exitApplication,
        state = mainWindowState,
        title = "RedefineNCM",
        decoration = WindowDecoration.Undecorated(),
        resizable = true,
    ) {
        val player = remember { GlobalContext.get().get<PlatformPlayer>() }
        val mediaControls = remember(player) { DesktopMediaControls(player) }
        DisposableEffect(window, mediaControls) {
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
                    Win10WindowChrome(
                        isMaximized = mainWindowState.placement == WindowPlacement.Maximized,
                        onMinimize = { mainWindowState.isMinimized = true },
                        onToggleMaximize = toggleMaximize,
                        onClose = ::exitApplication,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        App()
                    }
                }
            }
        }
    }

    // Desktop floating-lyrics window (goal #2: the desktop equivalent of the Android
    // notification / iOS Live Activity). It is a second, frameless, always-on-top window
    // driven entirely by DesktopLyricWindow: the playback
    // pipeline calls updateLyric(...) + show(), and this window mirrors that state.
    FloatingLyricWindow()
}

@Composable
private fun ApplicationScope.FloatingLyricWindow() {
    val visible by DesktopLyricWindow.isWindowVisible.collectAsState()
    val windowState = rememberWindowState(
        size = DpSize(760.dp, 96.dp),
        position = WindowPosition(Alignment.BottomCenter),
    )
    if (!visible) return

    val data by DesktopLyricWindow.floatingLyricData.collectAsState()
    val locked by DesktopLyricWindow.isWindowLocked.collectAsState()
    val alignment by DesktopLyricWindow.windowAlignment.collectAsState()

    Window(
        onCloseRequest = { DesktopLyricWindow.hide() },
        state = windowState,
        title = "桌面歌词",
        undecorated = true,   // frameless
        transparent = true,   // translucent (requires undecorated)
        alwaysOnTop = true,
        resizable = !locked,
    ) {
        // A locked window is parked: no drag area, no resize, and on Windows no pointer at all —
        // clicks fall through to what is underneath. Settings is the only way to unlock it.
        DisposableEffect(window, locked) {
            DesktopFloatingWindowNative.setClickThrough(window, locked)
            onDispose { }
        }
        RedefineNCMTheme {
            if (locked) {
                FloatingLyricContent(data, alignment)
            } else {
                WindowDraggableArea(modifier = Modifier.fillMaxSize()) {
                    FloatingLyricContent(data, alignment)
                }
            }
        }
    }
}

/**
 * The two lyric lines, and nothing else.
 *
 * There is no container to sit on: the window is transparent, so this draws straight onto
 * whatever wallpaper or window happens to be underneath. That rules out theme colours —
 * `onSurface` is near-black under the light theme and would vanish over a dark desktop — so
 * the text is a fixed light pair carried by a drop shadow, which is what keeps it readable
 * over an arbitrary background.
 *
 * The column still fills the window even though the text does not: [WindowDraggableArea]
 * only drags where its content draws, and a column sized to two glyph runs would leave the
 * window with almost nothing to grab.
 */
@Composable
private fun FloatingLyricContent(
    data: FloatingLyricData?,
    alignment: LyricSurfaceAlignment,
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
                style = MaterialTheme.typography.headlineSmall.copy(shadow = lyricShadow),
                color = Color.White,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(
            text = data?.nextLyric?.ifBlank { "下一句歌词将在这里显示" }
                ?: "下一句歌词将在这里显示",
            style = MaterialTheme.typography.bodyMedium.copy(shadow = lyricShadow),
            color = Color.White.copy(alpha = 0.72f),
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

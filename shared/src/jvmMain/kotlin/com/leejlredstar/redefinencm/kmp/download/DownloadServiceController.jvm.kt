package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.AppNavigationRequests
import java.awt.Color
import java.awt.EventQueue
import java.awt.Font
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Window
import java.awt.image.BufferedImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

actual object DownloadServiceController {
    private val trayEntry = DesktopDownloadTrayEntry()

    actual fun ensureRunning() {
        trayEntry.ensureRunning()
    }
}

private class DesktopDownloadTrayEntry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false
    private var trayIcon: TrayIcon? = null
    private var lastMessageKind: DesktopDownloadMessageKind? = null

    private var openItem: MenuItem? = null
    private var pauseAllItem: MenuItem? = null
    private var resumeAllItem: MenuItem? = null
    private var cancelAllItem: MenuItem? = null

    fun ensureRunning() {
        if (started) return
        started = true
        if (!isTrayAvailable()) return

        val downloadManager = KoinPlatform.getKoin().get<SongDownloadManager>()
        EventQueue.invokeLater { ensureTrayIcon(downloadManager) }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                trayIcon?.let { SystemTray.getSystemTray().remove(it) }
            }
        )
        scope.launch {
            downloadManager.tasks.collectLatest { tasks ->
                EventQueue.invokeLater { render(downloadManager, tasks) }
            }
        }
    }

    private fun render(
        downloadManager: SongDownloadManager,
        tasks: List<SongDownloadTask>,
    ) {
        if (!isTrayAvailable()) return
        if (tasks.isEmpty()) {
            removeTrayIcon()
            lastMessageKind = null
            return
        }

        val icon = ensureTrayIcon(downloadManager)
        val state = DesktopDownloadTrayState.from(tasks)
        icon.toolTip = "${state.title} - ${state.text}"
        updateMenu(tasks)

        if (state.messageKind != lastMessageKind) {
            icon.displayMessage(state.title, state.text, state.messageType)
            lastMessageKind = state.messageKind
        }
    }

    private fun ensureTrayIcon(downloadManager: SongDownloadManager): TrayIcon {
        trayIcon?.let { return it }
        val icon = TrayIcon(createIconImage(), "RedefineNCM downloads", createPopupMenu(downloadManager)).apply {
            isImageAutoSize = true
            addActionListener { openDownloadsAndFocusMainWindow() }
        }
        SystemTray.getSystemTray().add(icon)
        trayIcon = icon
        return icon
    }

    private fun createPopupMenu(downloadManager: SongDownloadManager): PopupMenu =
        PopupMenu().apply {
            openItem = MenuItem("打开下载管理").also { item ->
                item.addActionListener { openDownloadsAndFocusMainWindow() }
                add(item)
            }
            addSeparator()
            pauseAllItem = MenuItem("暂停全部").also { item ->
                item.addActionListener { downloadManager.pauseAll() }
                add(item)
            }
            resumeAllItem = MenuItem("继续全部").also { item ->
                item.addActionListener { downloadManager.resumeAll() }
                add(item)
            }
            cancelAllItem = MenuItem("取消全部").also { item ->
                item.addActionListener { downloadManager.cancelAll() }
                add(item)
            }
        }

    private fun updateMenu(tasks: List<SongDownloadTask>) {
        openItem?.isEnabled = tasks.isNotEmpty()
        pauseAllItem?.isEnabled = tasks.any { it.isActive }
        resumeAllItem?.isEnabled = tasks.any { it.status == DownloadTaskStatus.Paused }
        cancelAllItem?.isEnabled = tasks.any { it.isActive || it.status == DownloadTaskStatus.Paused }
    }

    private fun removeTrayIcon() {
        val icon = trayIcon ?: return
        SystemTray.getSystemTray().remove(icon)
        trayIcon = null
    }

    private fun openDownloadsAndFocusMainWindow() {
        AppNavigationRequests.openDownloads()
        EventQueue.invokeLater {
            val frame = Window.getWindows()
                .filterIsInstance<Frame>()
                .firstOrNull { it.title == MainWindowTitle }
                ?: return@invokeLater
            if ((frame.extendedState and Frame.ICONIFIED) != 0) {
                frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv()
            }
            frame.isVisible = true
            frame.toFront()
            frame.requestFocus()
        }
    }

    private fun isTrayAvailable(): Boolean =
        !GraphicsEnvironment.isHeadless() && SystemTray.isSupported()

    private fun createIconImage(): BufferedImage {
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.use {
            it.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            it.color = Color(0xDF, 0x32, 0x5A)
            it.fillRoundRect(2, 2, 28, 28, 10, 10)
            it.color = Color.WHITE
            it.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
            val text = "D"
            val metrics = it.fontMetrics
            val x = (image.width - metrics.stringWidth(text)) / 2
            val y = (image.height - metrics.height) / 2 + metrics.ascent
            it.drawString(text, x, y)
        }
        return image
    }
}

private const val MainWindowTitle = "RedefineNCM"

private data class DesktopDownloadTrayState(
    val title: String,
    val text: String,
    val messageKind: DesktopDownloadMessageKind,
    val messageType: TrayIcon.MessageType,
) {
    companion object {
        fun from(tasks: List<SongDownloadTask>): DesktopDownloadTrayState {
            val activeTask = tasks.firstOrNull { it.isActive }
            val total = tasks.size
            val completed = tasks.count { it.status == DownloadTaskStatus.Completed }
            val failed = tasks.count {
                it.status == DownloadTaskStatus.Failed || it.status == DownloadTaskStatus.Cancelled
            }
            val paused = tasks.count { it.status == DownloadTaskStatus.Paused }
            return when {
                activeTask != null -> DesktopDownloadTrayState(
                    title = "正在下载：${activeTask.title}",
                    text = "$completed/$total 完成 · ${activeTask.artist}",
                    messageKind = DesktopDownloadMessageKind.Active,
                    messageType = TrayIcon.MessageType.INFO,
                )
                paused > 0 -> DesktopDownloadTrayState(
                    title = "下载已暂停",
                    text = "$paused 项暂停 · $completed/$total 完成",
                    messageKind = DesktopDownloadMessageKind.Paused,
                    messageType = TrayIcon.MessageType.WARNING,
                )
                failed > 0 -> DesktopDownloadTrayState(
                    title = "下载完成，$failed 项失败",
                    text = "$completed/$total 完成",
                    messageKind = DesktopDownloadMessageKind.Failed,
                    messageType = TrayIcon.MessageType.ERROR,
                )
                else -> DesktopDownloadTrayState(
                    title = "下载完成",
                    text = "$completed/$total 完成",
                    messageKind = DesktopDownloadMessageKind.Completed,
                    messageType = TrayIcon.MessageType.INFO,
                )
            }
        }
    }
}

private enum class DesktopDownloadMessageKind {
    Active,
    Paused,
    Failed,
    Completed,
}

private inline fun Graphics2D.use(block: (Graphics2D) -> Unit) {
    try {
        block(this)
    } finally {
        dispose()
    }
}

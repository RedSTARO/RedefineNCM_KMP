package com.leejlredstar.redefinencm.kmp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import com.leejlredstar.redefinencm.kmp.data.db.DatabaseSchemaTooNewException
import com.leejlredstar.redefinencm.kmp.data.db.putDatabaseAside
import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.awt.Component
import java.awt.Desktop
import java.awt.Dialog
import java.awt.desktop.AppReopenedListener
import java.awt.Dimension
import java.awt.Window
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.system.exitProcess

/** A later launch asking the running app for its window; see [DesktopSingleInstance]. */
internal object DesktopActivation {
    private val requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val showWindowRequests: SharedFlow<Unit> = requests.asSharedFlow()

    /**
     * Asks the main window to show itself, and says whether one took the request. Before the
     * window exists (the app still starting, perhaps waiting on a dialog that opened behind other
     * windows) nothing collects the request: the dialogs are brought to the front instead, and
     * the answer is no.
     */
    fun requestShowWindow(): Boolean {
        if (requests.subscriptionCount.value > 0) return requests.tryEmit(Unit)
        SwingUtilities.invokeLater {
            Window.getWindows().filter { it.isShowing && it is Dialog }.forEach { it.toFront() }
        }
        return false
    }
}

/**
 * Brings the window back when macOS asks the running app to reopen.
 *
 * macOS never starts a second process for an app that is running: a click on its Dock icon, or
 * opening it again from Finder, is an event sent to this one. With the window closed to the tray
 * that click did nothing, because the single-instance handover only hears a second process.
 * Elsewhere the event is not supported and this does nothing.
 */
internal fun showWindowOnReopen() {
    runCatching {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) return
        desktop.addAppEventListener(AppReopenedListener { DesktopActivation.requestShowWindow() })
    }.onFailure { System.err.println("Reopen events unavailable: ${it.message}") }
}

/** What the user chose when the local data could not be opened. */
internal enum class LocalDataChoice { StartOver, Quit }

/**
 * Opens the local database before any window exists, and says so when that fails.
 *
 * Left to first use, the failure surfaced inside whichever coroutine or page asked first: a
 * window whose pages never filled in, or an exception dialog naming a Koin definition. A file
 * from a newer version and a damaged file are the two cases a user can meet, and both can be put
 * aside under another name so the app starts with empty data and nothing is deleted.
 *
 * Returns whether the app can go on.
 */
internal fun openLocalDataOrExplain(
    databaseFile: File,
    open: () -> Unit,
    ask: (title: String, message: String) -> LocalDataChoice = ::askAboutLocalData,
    tell: (title: String, message: String) -> Unit = ::showDesktopError,
    now: () -> LocalDateTime = LocalDateTime::now,
): Boolean {
    while (true) {
        val failure = runCatching(open).exceptionOrNull() ?: return true
        System.err.println("Local data could not be opened: ${databaseFile.absolutePath}")
        failure.printStackTrace()
        // A fresh file cannot fix where the file lives, its lock, the disk or SQLite itself, and
        // offering one put a healthy database aside and asked the same question again.
        if (startingOverCannotHelp(failure)) {
            tell(
                strings.desktopDataOpenFailedTitle,
                strings.desktopDataCannotOpen(databaseFile.absolutePath, failure.rootReason()),
            )
            return false
        }
        val message = if (failure.causes().any { it is DatabaseSchemaTooNewException }) {
            strings.desktopDataTooNew(databaseFile.absolutePath)
        } else {
            strings.desktopDataUnreadable(databaseFile.absolutePath, failure.rootReason())
        }
        if (ask(strings.desktopDataOpenFailedTitle, message) != LocalDataChoice.StartOver) return false
        val kept = putAside(databaseFile, now())
        if (kept == null) {
            tell(strings.desktopDataOpenFailedTitle, strings.desktopDataRenameFailed(databaseFile.absolutePath))
            return false
        }
        System.err.println("Local data kept as ${kept.absolutePath}; starting with empty data")
    }
}

private fun Throwable.causes(): List<Throwable> = generateSequence(this) { it.cause }.take(16).toList()

/**
 * Whether [failure] is about something other than the file's contents: SQLite's native library
 * not loading, or SQLite refusing the file's place (no permission, read-only, locked, disk full,
 * an I/O error). Starting over with an empty file meets the same failure.
 */
internal fun startingOverCannotHelp(failure: Throwable): Boolean = failure.causes().any { cause ->
    cause is LinkageError ||
        cause is SecurityException ||
        cause is java.nio.file.AccessDeniedException ||
        (cause.javaClass.name == "org.sqlite.SQLiteException" &&
            NonContentSqliteErrors.any { code -> cause.message.orEmpty().contains("[$code") })
}

/** sqlite-jdbc puts the result code in brackets at the start of its message. */
private val NonContentSqliteErrors = listOf(
    "SQLITE_CANTOPEN",
    "SQLITE_READONLY",
    "SQLITE_PERM",
    "SQLITE_BUSY",
    "SQLITE_LOCKED",
    "SQLITE_FULL",
    "SQLITE_IOERR",
    "SQLITE_NOMEM",
)

/** The innermost cause in its own words: wrappers only say which definition or call failed. */
private fun Throwable.rootReason(): String =
    causes().last().let { it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName }

/** What the dialog says when startup fails for a reason other than the local data. */
internal fun startFailureMessage(failure: Throwable, logFile: File?): String = buildString {
    append(strings.desktopStartFailed(failure.rootReason()))
    if (logFile != null) append("\n\n").append(strings.desktopLogHint(logFile.absolutePath))
}

private val asideStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/** Puts the database aside under a `.bak` name stamped with [at]; see [putDatabaseAside]. */
internal fun putAside(databaseFile: File, at: LocalDateTime): File? =
    putDatabaseAside(databaseFile, asideStamp.format(at))

private fun askAboutLocalData(title: String, message: String): LocalDataChoice {
    val options = arrayOf(strings.desktopDataStartOver, strings.quit)
    val picked = onEventThread {
        JOptionPane.showOptionDialog(
            null,
            wrapped(message),
            title,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            options,
            options[1],
        )
    }
    return if (picked == 0) LocalDataChoice.StartOver else LocalDataChoice.Quit
}

/** A plain system dialog, for failures that happen before or outside the Compose windows. */
internal fun showDesktopError(title: String, message: String, parent: Component? = null) {
    onEventThread {
        JOptionPane.showMessageDialog(parent, wrapped(message), title, JOptionPane.ERROR_MESSAGE)
    }
}

/** JOptionPane does not wrap a long line on its own; a read-only text area does. */
private fun wrapped(message: String): JTextArea = JTextArea(message).apply {
    isEditable = false
    isOpaque = false
    lineWrap = true
    wrapStyleWord = true
    font = UIManager.getFont("OptionPane.messageFont") ?: JLabel().font
    // A wrapping text area only knows how tall it is once it has a width; without one the
    // dialog is sized for a single line and cuts off the rest, buttons included.
    setSize(MESSAGE_WIDTH, Short.MAX_VALUE.toInt())
    preferredSize = Dimension(MESSAGE_WIDTH, preferredSize.height)
}

private const val MESSAGE_WIDTH = 440

private fun <T> onEventThread(block: () -> T): T {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    if (SwingUtilities.isEventDispatchThread()) return block()
    var result: Result<T>? = null
    SwingUtilities.invokeAndWait { result = runCatching(block) }
    return result!!.getOrThrow()
}

/**
 * What happens when a window's composition, layout, drawing or input handling throws.
 *
 * Compose's own handler shows the raw exception text and then asks the window to close. With
 * "stay in the tray" on, that hides a window which can no longer draw and leaves the app running
 * behind it. Here the stack trace goes to the log, the user is told once where the log is, and
 * the app quits, so the next start is a clean one.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal class DesktopCrashHandlerFactory(
    private val logFile: File?,
    private val quit: () -> Unit = { exitProcess(1) },
) : WindowExceptionHandlerFactory {
    private val reported = AtomicBoolean(false)

    override fun exceptionHandler(window: Window): WindowExceptionHandler =
        WindowExceptionHandler { throwable ->
            if (reported.compareAndSet(false, true)) {
                System.err.println("The window failed and the app will quit:")
                throwable.printStackTrace()
                SwingUtilities.invokeLater {
                    val message = buildString {
                        append(strings.desktopCrashMessage)
                        if (logFile != null) append("\n\n").append(strings.desktopLogHint(logFile.absolutePath))
                    }
                    runCatching {
                        showDesktopError(strings.desktopCrashTitle, message, window.takeIf { it.isDisplayable })
                    }
                    quit()
                }
            }
            throw throwable
        }
}

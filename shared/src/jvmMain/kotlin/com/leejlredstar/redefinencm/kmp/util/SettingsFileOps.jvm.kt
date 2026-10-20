package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberImportFileLauncher(onFailed: () -> Unit, onImported: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnImported = rememberUpdatedState(onImported)
    val currentOnFailed = rememberUpdatedState(onFailed)
    return {
        // JFileChooser must run on the EDT; invokeAndWait blocks the IO thread, not the EDT.
        scope.launch(Dispatchers.IO) {
            var selected: File? = null
            SwingUtilities.invokeAndWait {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter(strings.settingsFileFilter, "json")
                    dialogTitle = strings.chooseSettingsFile
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    selected = chooser.selectedFile
                }
            }
            val file = selected ?: return@launch
            // The chooser approves a typed name that does not exist; a failure here used to reach
            // the window's crash handler, which closes the app.
            val json = readSettingsFile(file)
            withContext(Dispatchers.Main) {
                json.fold(onSuccess = { currentOnImported.value(it) }, onFailure = { currentOnFailed.value() })
            }
        }
    }
}

@Composable
actual fun rememberExportFileLauncher(onFailed: () -> Unit): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnFailed = rememberUpdatedState(onFailed)
    return { json ->
        scope.launch(Dispatchers.IO) {
            var target: File? = null
            SwingUtilities.invokeAndWait {
                val chooser = object : JFileChooser() {
                    // A save dialog that replaced a file without asking would lose it silently.
                    override fun approveSelection() {
                        val file = withJsonExtension(selectedFile)
                        if (file.exists() && JOptionPane.showConfirmDialog(
                                this,
                                strings.settingsFileReplaceConfirm(file.name),
                                dialogTitle,
                                JOptionPane.YES_NO_OPTION,
                            ) != JOptionPane.YES_OPTION
                        ) return
                        super.approveSelection()
                    }
                }.apply {
                    fileFilter = FileNameExtensionFilter(strings.settingsFileFilter, "json")
                    dialogTitle = strings.saveSettingsFile
                    selectedFile = File("RedefineNCM_KMP_settings.json")
                }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    target = withJsonExtension(chooser.selectedFile)
                }
            }
            val file = target ?: return@launch
            writeSettingsFileAtomically(file, json).onFailure {
                withContext(Dispatchers.Main) { currentOnFailed.value() }
            }
        }
    }
}

private fun withJsonExtension(file: File): File =
    if (file.name.endsWith(".json", ignoreCase = true)) file else File("${file.absolutePath}.json")

internal fun readSettingsFile(file: File): Result<String> = runCatching {
    require(file.isFile) { "Not a file: $file" }
    file.readText()
}

/**
 * Writes [text] to [target] through a file beside it, renamed over the target once complete, so
 * a failed write (a full disk, a read-only folder) leaves any earlier backup as it was.
 */
internal fun writeSettingsFileAtomically(target: File, text: String): Result<Unit> {
    val directory = target.absoluteFile.parentFile
    var temporary: File? = null
    return runCatching {
        val written = File.createTempFile(".${target.name}", ".tmp", directory).also { temporary = it }
        written.outputStream().use { output ->
            output.write(text.toByteArray())
            output.fd.sync()
        }
        try {
            Files.move(
                written.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(written.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        Unit
    }.onFailure { temporary?.delete() }
}

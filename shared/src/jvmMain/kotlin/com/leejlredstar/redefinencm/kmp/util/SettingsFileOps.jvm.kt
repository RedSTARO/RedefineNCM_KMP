package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberImportFileLauncher(onImported: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        // JFileChooser must run on the EDT; invokeAndWait blocks the IO thread, not the EDT.
        scope.launch(Dispatchers.IO) {
            var selected: File? = null
            SwingUtilities.invokeAndWait {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("JSON 设置文件 (*.json)", "json")
                    dialogTitle = "选择设置文件"
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    selected = chooser.selectedFile
                }
            }
            selected?.readText()?.let { onImported(it) }
        }
    }
}

@Composable
actual fun rememberExportFileLauncher(): (String) -> Unit {
    val scope = rememberCoroutineScope()
    return { json ->
        scope.launch(Dispatchers.IO) {
            SwingUtilities.invokeAndWait {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("JSON 设置文件 (*.json)", "json")
                    dialogTitle = "保存设置文件"
                    selectedFile = File("RedefineNCM_KMP_settings.json")
                }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile.let { f ->
                        if (f.name.endsWith(".json")) f else File("${f.absolutePath}.json")
                    }
                    file.writeText(json)
                }
            }
        }
    }
}

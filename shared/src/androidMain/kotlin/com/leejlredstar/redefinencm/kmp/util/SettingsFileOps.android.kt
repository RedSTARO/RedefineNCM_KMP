package com.leejlredstar.redefinencm.kmp.util

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberImportFileLauncher(onFailed: () -> Unit, onImported: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnImported = rememberUpdatedState(onImported)
    val currentOnFailed = rememberUpdatedState(onFailed)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            // Off the main thread: the document may be in the cloud, and reading it there could
            // stall the app long enough for the system to report it as not responding.
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (json == null) currentOnFailed.value() else currentOnImported.value(json)
        }
    }
    return { launcher.launch("application/json") }
}

@Composable
actual fun rememberExportFileLauncher(onFailed: () -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val currentOnFailed = rememberUpdatedState(onFailed)
    return { json ->
        try {
            val file = File(context.cacheDir, "RedefineNCM_KMP_settings.json")
            file.writeText(json)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, strings.exportSettingsFile)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            currentOnFailed.value()
        }
    }
}

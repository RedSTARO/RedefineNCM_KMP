package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.applySettingsBackup
import com.leejlredstar.redefinencm.kmp.util.encodeSettingsBackup
import com.leejlredstar.redefinencm.kmp.util.rememberExportFileLauncher
import com.leejlredstar.redefinencm.kmp.util.rememberImportFileLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(scaffoldPadding: PaddingValues, settings: PlatformSettings = koinInject()) {
    var cookie by remember { mutableStateOf(settings.getString(SettingKeys.COOKIE, "")) }
    var server by remember { mutableStateOf(settings.getString(SettingKeys.SERVER, "")) }
    var onlineQuality by remember { mutableStateOf(settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.STANDARD.name)) }
    var dlQuality by remember { mutableStateOf(settings.getString(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)) }
    var replacePlaylist by remember { mutableStateOf(settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, false)) }
    var checkUpdate by remember { mutableStateOf(settings.getBoolean(SettingKeys.CHECK_UPDATE, false)) }
    var searchPrediction by remember { mutableStateOf(settings.getBoolean(SettingKeys.SEARCH_PREDICTION, true)) }
    var showDownloadStatus by remember { mutableStateOf(settings.getBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, false)) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val launchImport = rememberImportFileLauncher { json ->
        scope.launch(Dispatchers.Default) {
            if (applySettingsBackup(json, settings)) {
                cookie = settings.getString(SettingKeys.COOKIE, "")
                server = settings.getString(SettingKeys.SERVER, "")
                onlineQuality = settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.STANDARD.name)
                dlQuality = settings.getString(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)
                replacePlaylist = settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, false)
                checkUpdate = settings.getBoolean(SettingKeys.CHECK_UPDATE, false)
                searchPrediction = settings.getBoolean(SettingKeys.SEARCH_PREDICTION, true)
                importStatus = "✓ 导入成功"
            } else {
                importStatus = "✗ 导入失败，请检查文件格式"
            }
        }
    }
    val launchExport = rememberExportFileLauncher()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = scaffoldPadding.calculateBottomPadding())
            .background(MaterialTheme.colorScheme.surface),
    ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                ExpressiveSectionTitle("Server", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsTextField(server, "Server URL") { v ->
                    server = if (v.isNotEmpty() && !v.endsWith("/")) "$v/" else v
                    settings.setString(SettingKeys.SERVER, server)
                }

                ExpressiveSectionTitle("Account", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsTextField(cookie, "Cookie") { v ->
                    cookie = v
                    settings.setString(SettingKeys.COOKIE, v)
                }

                ExpressiveSectionTitle("Playback", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsDropdown(onlineQuality, "Music Quality (Online)", SoundQuality.entries) { v ->
                    onlineQuality = v.name
                    settings.setString(SettingKeys.ONLINE_PLAY_QUALITY, v.name)
                }
                SettingsDropdown(dlQuality, "Music Quality (Download)", SoundQuality.entries) { v ->
                    dlQuality = v.name
                    settings.setString(SettingKeys.DOWNLOAD_QUALITY, v.name)
                }
                SettingsSwitch(replacePlaylist, "Replace playlist on single song click") { v ->
                    replacePlaylist = v
                    settings.setBoolean(SettingKeys.REPLACE_PLAYLIST, v)
                }
                SettingsSwitch(searchPrediction, "Search prediction") { v ->
                    searchPrediction = v
                    settings.setBoolean(SettingKeys.SEARCH_PREDICTION, v)
                }
                SettingsSwitch(showDownloadStatus, "Show download status") { v ->
                    showDownloadStatus = v
                    settings.setBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, v)
                }

                ExpressiveSectionTitle("General", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsSwitch(checkUpdate, "Check update on startup") { v ->
                    checkUpdate = v
                    settings.setBoolean(SettingKeys.CHECK_UPDATE, v)
                }

                ExpressiveSectionTitle("Backup", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsButton("Export settings") { launchExport(encodeSettingsBackup(settings)) }
                SettingsButton("Import settings") { launchImport() }
                importStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.startsWith("✓")) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    )
                }

                Spacer(Modifier.height(48.dp))
            }
    }
}

@Composable
private fun SettingsTextField(value: String, label: String, onUpdate: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onUpdate(it) },
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .height(64.dp),
    )
}

@Composable
private fun SettingsSwitch(checked: Boolean, label: String, onUpdate: (Boolean) -> Unit) {
    var state by remember(checked) { mutableStateOf(checked) }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Switch(checked = state, onCheckedChange = { state = it; onUpdate(it) })
        }
    }
}

@Composable
private fun SettingsDropdown(
    selectedName: String,
    label: String,
    options: List<SoundQuality>,
    onUpdate: (SoundQuality) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.find { it.name == selectedName } ?: options.first()
    Surface(
        onClick = { expanded = true },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = current.toString(), style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.toString()) },
                    onClick = { expanded = false; onUpdate(opt) },
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(vertical = 4.dp),
        shape = CircleShape,
    ) {
        Text(label)
    }
}

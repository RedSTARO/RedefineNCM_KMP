package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
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
fun SettingsScreen(
    scaffoldPadding: PaddingValues,
    onOpenLogin: () -> Unit,
    settings: PlatformSettings = koinInject(),
    api: NCMApi = koinInject(),
) {
    var cookie by remember { mutableStateOf(settings.getString(SettingKeys.COOKIE, "")) }
    var server by remember { mutableStateOf(settings.getString(SettingKeys.SERVER, "")) }
    var onlineQuality by remember { mutableStateOf(settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.STANDARD.name)) }
    var dlQuality by remember { mutableStateOf(settings.getString(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)) }
    var replacePlaylist by remember { mutableStateOf(settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, false)) }
    var checkUpdate by remember { mutableStateOf(settings.getBoolean(SettingKeys.CHECK_UPDATE, false)) }
    var searchPrediction by remember { mutableStateOf(settings.getBoolean(SettingKeys.SEARCH_PREDICTION, true)) }
    var showDownloadStatus by remember { mutableStateOf(settings.getBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, false)) }
    var adaptOriginalLyric by remember { mutableStateOf(settings.getBoolean(SettingKeys.ADAPT_ORIGINAL_ANDROID_LYRIC, false)) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var serverCheckStatus by remember { mutableStateOf<String?>(null) }
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
            .padding(bottom = scaffoldPadding.calculateBottomPadding())
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SettingsHero()

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                ExpressiveSectionTitle("Server", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsTextField(server, "Server URL", index = 0, count = 2) { v ->
                    server = if (v.isNotEmpty() && !v.endsWith("/")) "$v/" else v
                    settings.setString(SettingKeys.SERVER, server)
                }
                // 原版 ServerItem：调 /inner/version/ 校验服务器可用性并显示版本
                SettingsButton("检查服务器 ($server)", index = 1, count = 2) {
                    serverCheckStatus = "检查中…"
                    scope.launch {
                        serverCheckStatus = try {
                            val result = api.innerVersion("${server}inner/version/")
                            if (result.code == 200) "服务器可用，版本：${result.data.version}"
                            else "服务器不可用（code ${result.code}）"
                        } catch (e: Exception) {
                            "服务器不可用：${e.message}"
                        }
                    }
                }
                serverCheckStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status.startsWith("服务器可用")) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    )
                }

                ExpressiveSectionTitle("Account", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsButton(
                    label = if (cookie.isBlank()) "扫码 / 登录" else "重新登录 / 换号",
                    leadingIcon = AppIcons.QrCode2,
                    index = 0,
                    count = 2,
                    onClick = onOpenLogin,
                )
                SettingsTextField(cookie, "Cookie", index = 1, count = 2) { v ->
                    cookie = v
                    settings.setString(SettingKeys.COOKIE, v)
                }

                ExpressiveSectionTitle("Playback", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsDropdown(onlineQuality, "Music Quality (Online)", SoundQuality.entries, index = 0, count = 6) { v ->
                    onlineQuality = v.name
                    settings.setString(SettingKeys.ONLINE_PLAY_QUALITY, v.name)
                }
                SettingsDropdown(dlQuality, "Music Quality (Download)", SoundQuality.entries, index = 1, count = 6) { v ->
                    dlQuality = v.name
                    settings.setString(SettingKeys.DOWNLOAD_QUALITY, v.name)
                }
                SettingsSwitch(replacePlaylist, "Replace playlist on single song click", index = 2, count = 6) { v ->
                    replacePlaylist = v
                    settings.setBoolean(SettingKeys.REPLACE_PLAYLIST, v)
                }
                SettingsSwitch(searchPrediction, "Search prediction", index = 3, count = 6) { v ->
                    searchPrediction = v
                    settings.setBoolean(SettingKeys.SEARCH_PREDICTION, v)
                }
                SettingsSwitch(showDownloadStatus, "Show download status", index = 4, count = 6) { v ->
                    showDownloadStatus = v
                    settings.setBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, v)
                }
                SettingsSwitch(adaptOriginalLyric, "Adapt original Android Live Update lyric", index = 5, count = 6) { v ->
                    adaptOriginalLyric = v
                    settings.setBoolean(SettingKeys.ADAPT_ORIGINAL_ANDROID_LYRIC, v)
                }

                ExpressiveSectionTitle("General", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsSwitch(checkUpdate, "Check update on startup", index = 0, count = 1) { v ->
                    checkUpdate = v
                    settings.setBoolean(SettingKeys.CHECK_UPDATE, v)
                }

                ExpressiveSectionTitle("Backup", Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
                SettingsButton("Export settings", index = 0, count = 2) { launchExport(encodeSettingsBackup(settings)) }
                SettingsButton("Import settings", index = 1, count = 2) { launchImport() }
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
private fun SettingsHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(188.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Account, playback, cache and backup",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    label: String,
    index: Int,
    count: Int,
    onUpdate: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onUpdate(it) },
        label = { Text(label) },
        singleLine = true,
        shape = connectedListItemShape(index, count),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp)
            .height(64.dp),
    )
}

@Composable
private fun SettingsSwitch(
    checked: Boolean,
    label: String,
    index: Int,
    count: Int,
    onUpdate: (Boolean) -> Unit,
) {
    var state by remember(checked) { mutableStateOf(checked) }
    Surface(
        shape = connectedListItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
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
    index: Int,
    count: Int,
    onUpdate: (SoundQuality) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.find { it.name == selectedName } ?: options.first()
    Surface(
        onClick = { expanded = true },
        shape = connectedListItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
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
                Icon(AppIcons.ArrowDropDown, contentDescription = null)
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
private fun SettingsButton(
    label: String,
    leadingIcon: ImageVector? = null,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 1.5.dp),
        shape = connectedListItemShape(index, count),
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.recognition.AudioFingerprint
import com.leejlredstar.redefinencm.kmp.recognition.rememberMicrophonePermissionRequester
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.BackHandler
import com.leejlredstar.redefinencm.kmp.viewmodel.RecognizedSongMatch
import com.leejlredstar.redefinencm.kmp.viewmodel.SongRecognitionUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.SongRecognitionViewModel
import org.koin.compose.koinInject

@Composable
fun SongRecognitionScreen(
    onBack: () -> Unit,
    viewModel: SongRecognitionViewModel = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()
    val accentPalette = contentAccentPalette(MaterialTheme.colorScheme.tertiaryContainer)
    var queuedSongId by remember { mutableStateOf<Long?>(null) }
    val requestMicrophonePermission = rememberMicrophonePermissionRequester(
        onResult = viewModel::onPermissionResult,
    )

    fun requestRecognition() {
        queuedSongId = null
        viewModel.beginPermissionRequest()
        requestMicrophonePermission()
    }

    fun leave() {
        viewModel.cancelRecognition()
        onBack()
    }

    BackHandler { leave() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }

    ExpressivePage(
        accentPalette = accentPalette,
        maxContentWidth = ExpressiveLayout.ReadingContentMaxWidth,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "recognition-header") {
                RecognitionHeader(onBack = ::leave)
            }

            when (val current = state) {
                SongRecognitionUiState.Idle -> item(key = "recognition-idle") {
                    RecognitionIdlePanel(
                        accentPalette = accentPalette,
                        onStart = ::requestRecognition,
                    )
                }
                SongRecognitionUiState.RequestingPermission -> item(key = "recognition-permission") {
                    RecognitionLoadingPanel(
                        label = "正在请求麦克风权限…",
                        accentPalette = accentPalette,
                        onCancel = viewModel::cancelRecognition,
                    )
                }
                is SongRecognitionUiState.Listening -> item(key = "recognition-listening") {
                    RecognitionListeningPanel(
                        elapsedMillis = current.elapsedMillis,
                        level = current.level,
                        accentPalette = accentPalette,
                        onCancel = viewModel::cancelRecognition,
                    )
                }
                SongRecognitionUiState.Recognizing -> item(key = "recognition-processing") {
                    RecognitionLoadingPanel(
                        label = "正在生成指纹并匹配歌曲…",
                        accentPalette = accentPalette,
                        onCancel = viewModel::cancelRecognition,
                    )
                }
                is SongRecognitionUiState.Results -> {
                    item(key = "recognition-results-title") {
                        Text(
                            text = "识别结果",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                        )
                    }
                    itemsIndexed(
                        items = current.matches,
                        key = { _, match -> match.song.id },
                    ) { index, match ->
                        RecognitionResultCard(
                            match = match,
                            index = index,
                            count = current.matches.size,
                            queued = queuedSongId == match.song.id,
                            accentPalette = accentPalette,
                            onPlay = {
                                viewModel.play(match)
                                onBack()
                            },
                            onAddToQueue = {
                                viewModel.addToQueue(match)
                                queuedSongId = match.song.id
                            },
                        )
                    }
                    item(key = "recognition-retry") {
                        TextButton(
                            onClick = ::requestRecognition,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Icon(AppIcons.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("重新识别")
                        }
                    }
                }
                is SongRecognitionUiState.NoMatch -> item(key = "recognition-no-match") {
                    ExpressiveStatePanel(
                        title = "没有识别到歌曲",
                        message = "请靠近音源，并选择较清晰的音乐片段后重试。",
                        icon = AppIcons.GraphicEq,
                        accentPalette = accentPalette,
                        actionLabel = "重新识别",
                        onAction = ::requestRecognition,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                SongRecognitionUiState.PermissionDenied -> item(key = "recognition-denied") {
                    ExpressiveStatePanel(
                        title = "需要麦克风权限",
                        message = "请在系统或浏览器的站点设置中允许麦克风，然后返回此页重新检查。听歌识曲只处理本次三秒录音。",
                        icon = AppIcons.GraphicEq,
                        tone = ExpressiveStateTone.Error,
                        actionLabel = "重新检查",
                        onAction = ::requestRecognition,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                is SongRecognitionUiState.MicrophoneUnavailable -> item(key = "recognition-unavailable") {
                    ExpressiveStatePanel(
                        title = "麦克风不可用",
                        message = current.message,
                        icon = AppIcons.GraphicEq,
                        tone = ExpressiveStateTone.Error,
                        actionLabel = if (current.canRetry) "重试" else null,
                        onAction = if (current.canRetry) ::requestRecognition else null,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                is SongRecognitionUiState.Error -> item(key = "recognition-error") {
                    ExpressiveStatePanel(
                        title = "听歌识曲失败",
                        message = current.message,
                        icon = AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        actionLabel = "重试",
                        onAction = ::requestRecognition,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognitionHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "听歌识曲",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "录制三秒环境音乐并匹配网易云歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecognitionIdlePanel(
    accentPalette: ContentAccentPalette,
    onStart: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentPalette.pageStart,
                            accentPalette.container,
                            accentPalette.quietContainer,
                        ),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = accentPalette.container,
                contentColor = accentPalette.onContainer,
                modifier = Modifier.size(112.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            Text(
                text = "让音乐更清晰地靠近麦克风",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "开始后会暂停当前播放，录制完成后不会自动恢复。",
                style = MaterialTheme.typography.bodyMedium,
                color = accentPalette.secondaryOnQuietContainer,
            )
            FilledTonalButton(onClick = onStart, shape = CircleShape) {
                Icon(AppIcons.GraphicEq, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("开始识别")
            }
        }
    }
}

@Composable
private fun RecognitionLoadingPanel(
    label: String,
    accentPalette: ContentAccentPalette,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ExpressiveLoadingState(
            label = label,
            accentColor = accentPalette.accent,
            modifier = Modifier.padding(top = 12.dp),
        )
        TextButton(onClick = onCancel) { Text("取消") }
    }
}

@Composable
private fun RecognitionListeningPanel(
    elapsedMillis: Long,
    level: Float,
    accentPalette: ContentAccentPalette,
    onCancel: () -> Unit,
) {
    val progress = (
        elapsedMillis.toFloat() / AudioFingerprint.DURATION_MILLIS.toFloat()
    ).coerceIn(0f, 1f)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = accentPalette.container,
                contentColor = accentPalette.onContainer,
                modifier = Modifier.size((88 + level * 24).dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Text(
                text = "正在聆听…",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { progress },
                color = accentPalette.accent,
                trackColor = accentPalette.container,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${elapsedMillis.coerceAtMost(AudioFingerprint.DURATION_MILLIS) / 100L / 10.0} / 3.0 秒",
                style = MaterialTheme.typography.labelLarge,
                color = accentPalette.secondaryOnQuietContainer,
            )
            TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}

@Composable
private fun RecognitionResultCard(
    match: RecognizedSongMatch,
    index: Int,
    count: Int,
    queued: Boolean,
    accentPalette: ContentAccentPalette,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    val song = match.song
    Surface(
        shape = connectedListItemShape(index, count),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveArtwork(
                    model = song.al.picUrl,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    shape = MaterialTheme.shapes.large,
                    containerColor = accentPalette.container,
                    contentColor = accentPalette.onContainer,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.name.ifBlank { "未知歌曲" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.ar.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = accentPalette.secondaryOnQuietContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (song.al.name.isNotBlank()) {
                        Text(
                            text = song.al.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = accentPalette.secondaryOnQuietContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAddToQueue, enabled = !queued) {
                    Text(if (queued) "已加入队列" else "加入队列")
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onPlay, shape = CircleShape) {
                    Icon(AppIcons.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("播放")
                }
            }
        }
    }
}

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
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.recognition.AudioFingerprint
import com.leejlredstar.redefinencm.kmp.recognition.rememberMicrophonePermissionRequester
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveWavyProgress
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.leejlredstar.redefinencm.kmp.viewmodel.MaxRecognitionAttempts

@Composable
fun SongRecognitionScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit = onBack,
    scaffoldPadding: PaddingValues = PaddingValues(),
    viewModel: SongRecognitionViewModel = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()
    val accentPalette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)
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
    // The entry is already a request to identify a song; listening starts on arrival instead of
    // after a second tap on this page.
    LaunchedEffect(Unit) {
        if (state is SongRecognitionUiState.Idle) requestRecognition()
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }

    ExpressivePage(
        accentPalette = accentPalette,
        maxContentWidth = ExpressiveLayout.ReadingContentMaxWidth,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
        ),
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
                        label = strings.recognitionRequestingMicPermission,
                        accentPalette = accentPalette,
                        onCancel = viewModel::cancelRecognition,
                    )
                }
                is SongRecognitionUiState.Listening -> item(key = "recognition-listening") {
                    RecognitionListeningPanel(
                        elapsedMillis = current.elapsedMillis,
                        level = current.level,
                        attempt = current.attempt,
                        accentPalette = accentPalette,
                        onCancel = viewModel::cancelRecognition,
                    )
                }
                SongRecognitionUiState.Recognizing -> item(key = "recognition-processing") {
                    RecognitionLoadingPanel(
                        label = strings.recognitionMatching,
                        accentPalette = accentPalette,
                        onCancel = viewModel::cancelRecognition,
                    )
                }
                is SongRecognitionUiState.Results -> {
                    item(key = "recognition-results-title") {
                        Text(
                            text = strings.recognitionResults,
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
                                onOpenPlayer()
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
                            Text(strings.recognizeAgain)
                        }
                    }
                }
                is SongRecognitionUiState.NoMatch -> item(key = "recognition-no-match") {
                    ExpressiveStatePanel(
                        title = strings.recognitionNoMatchTitle,
                        message = strings.recognitionNoMatchMessage(MaxRecognitionAttempts * 3),
                        icon = AppIcons.MusicNote,
                        accentPalette = accentPalette,
                        actionLabel = strings.recognizeAgain,
                        onAction = ::requestRecognition,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                SongRecognitionUiState.PermissionDenied -> item(key = "recognition-denied") {
                    ExpressiveStatePanel(
                        title = strings.recognitionMicPermissionNeeded,
                        message = strings.recognitionMicPermissionHelp,
                        icon = AppIcons.Mic,
                        tone = ExpressiveStateTone.Error,
                        actionLabel = strings.checkAgain,
                        onAction = ::requestRecognition,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                is SongRecognitionUiState.MicrophoneUnavailable -> item(key = "recognition-unavailable") {
                    ExpressiveStatePanel(
                        title = strings.microphoneUnavailable,
                        message = current.message,
                        icon = AppIcons.Mic,
                        tone = ExpressiveStateTone.Error,
                        actionLabel = if (current.canRetry) strings.retry else null,
                        onAction = if (current.canRetry) ::requestRecognition else null,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                is SongRecognitionUiState.Error -> item(key = "recognition-error") {
                    ExpressiveStatePanel(
                        title = strings.recognitionFailed,
                        message = current.message,
                        icon = AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        actionLabel = strings.retry,
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
                    contentDescription = strings.back,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = strings.songRecognition,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = strings.recognitionIntro,
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
                        imageVector = AppIcons.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            Text(
                text = strings.recognitionReadyTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = strings.recognitionPausesPlayback,
                style = MaterialTheme.typography.bodyMedium,
                color = accentPalette.secondaryOnQuietContainer,
            )
            FilledTonalButton(onClick = onStart, shape = CircleShape) {
                Icon(AppIcons.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.startRecognition)
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
        TextButton(onClick = onCancel) { Text(strings.cancel) }
    }
}

@Composable
private fun RecognitionListeningPanel(
    elapsedMillis: Long,
    level: Float,
    attempt: Int,
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
            // A fixed box with the circle scaled inside it: resizing the circle itself would move
            // everything below it up and down with every level sample.
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = accentPalette.container,
                    contentColor = accentPalette.onContainer,
                    modifier = Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            val scale = (88f + level * 24f) / 112f
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Text(
                text = if (attempt > 1) strings.recognitionListeningAgain(attempt) else strings.recognitionListening,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            ExpressiveWavyProgress(
                progress = { progress },
                color = accentPalette.accent,
                trackColor = accentPalette.container,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = strings.recognitionElapsed(elapsedMillis.coerceAtMost(AudioFingerprint.DURATION_MILLIS) / 100L / 10.0),
                style = MaterialTheme.typography.labelLarge,
                color = accentPalette.secondaryOnQuietContainer,
            )
            TextButton(onClick = onCancel) { Text(strings.cancel) }
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
                    shape = MaterialTheme.shapes.medium,
                    containerColor = accentPalette.container,
                    contentColor = accentPalette.onContainer,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.name.ifBlank { strings.unknownSong },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.ar.joinToString(" / ") { it.name }.ifBlank { strings.unknownArtist },
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
                    Text(if (queued) strings.addedToQueue else strings.addToQueue)
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onPlay, shape = CircleShape) {
                    Icon(AppIcons.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.play)
                }
            }
        }
    }
}

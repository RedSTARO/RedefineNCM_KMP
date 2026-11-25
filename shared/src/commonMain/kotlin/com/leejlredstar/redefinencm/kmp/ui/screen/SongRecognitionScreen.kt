package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.recognition.AudioFingerprint
import com.leejlredstar.redefinencm.kmp.recognition.LocalMicrophoneWindowActive
import com.leejlredstar.redefinencm.kmp.recognition.rememberMicrophonePermissionRequester
import com.leejlredstar.redefinencm.kmp.ui.adaptive.browseHeader
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveFeatureIcon
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageBar
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveWavyProgress
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.util.BackHandler
import com.leejlredstar.redefinencm.kmp.viewmodel.MaxRecognitionAttempts
import com.leejlredstar.redefinencm.kmp.viewmodel.RecognizedSongMatch
import com.leejlredstar.redefinencm.kmp.viewmodel.SongRecognitionUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.SongRecognitionViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongRecognitionScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit = onBack,
    scaffoldPadding: PaddingValues = PaddingValues(),
    viewModel: SongRecognitionViewModel = koinInject(),
    isActive: Boolean = true,
) {
    val state by viewModel.uiState.collectAsState()
    var queuedSongId by rememberSaveable { mutableStateOf<Long?>(null) }
    var autoStart by rememberSaveable { mutableStateOf(true) }
    val microphoneWindow = LocalMicrophoneWindowActive.current
    val windowActive = microphoneWindow?.collectAsState()?.value ?: true
    val canRecord = isActive && windowActive
    val requestMicrophonePermission = rememberMicrophonePermissionRequester(
        onResult = { granted ->
            viewModel.setMicrophoneWindowActive(isActive && (microphoneWindow?.value ?: true))
            viewModel.onPermissionResult(granted)
        },
    )

    fun requestRecognition() {
        // Read the platform signal directly on events: composition may not have drawn the loss
        // of top-resumed ownership yet.
        viewModel.setMicrophoneWindowActive(isActive && (microphoneWindow?.value ?: true))
        if (!viewModel.beginPermissionRequest()) return
        queuedSongId = null
        requestMicrophonePermission()
    }

    fun leave() {
        viewModel.cancelRecognition()
        onBack()
    }

    BackHandler(enabled = isActive) { leave() }
    // Release acquisition as soon as the activity loses ownership, even if Android has paused
    // the composition's frame clock and cannot draw an updated recognition panel yet.
    LaunchedEffect(viewModel, isActive, microphoneWindow) {
        if (isActive && microphoneWindow != null) {
            microphoneWindow.collect(viewModel::setMicrophoneWindowActive)
        } else {
            viewModel.setMicrophoneWindowActive(isActive)
        }
    }
    // Only the first arrival is a request to capture. Returning to a recreated or unfocused
    // window is not an implicit request to start recording again.
    LaunchedEffect(viewModel, canRecord) {
        if (autoStart && canRecord && viewModel.uiState.value is SongRecognitionUiState.Idle &&
            (microphoneWindow?.value ?: true)
        ) {
            autoStart = false
            requestRecognition()
        }
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    ExpressivePage(
        maxContentWidth = 680.dp,
        headerOwnsTopInsets = true,
    ) {
        val gutter = LocalExpressivePageGutter.current
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            ExpressivePageBar(
                title = strings.songRecognition,
                onBack = ::leave,
                windowInsets = WindowInsets.browseHeader,
                scrollBehavior = scrollBehavior,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = gutter,
                    end = gutter,
                    top = 8.dp,
                    bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                when (val current = state) {
                    SongRecognitionUiState.Idle,
                    SongRecognitionUiState.RequestingPermission,
                    is SongRecognitionUiState.Listening,
                    SongRecognitionUiState.Recognizing -> item(key = "recognition-task") {
                        RecognitionTaskPanel(
                            state = current,
                            canRecord = canRecord,
                            onStart = ::requestRecognition,
                            onCancel = viewModel::cancelRecognition,
                        )
                    }
                    is SongRecognitionUiState.Results -> {
                        item(key = "recognition-results-title") {
                            RecognitionResultsHeader(canRecord = canRecord, onRetry = ::requestRecognition)
                        }
                        itemsIndexed(items = current.matches, key = { _, match -> match.song.id }) { index, match ->
                            RecognitionResultRow(
                                match = match,
                                index = index,
                                count = current.matches.size,
                                queued = queuedSongId == match.song.id,
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
                    }
                    is SongRecognitionUiState.NoMatch -> item(key = "recognition-no-match") {
                        ExpressiveStatePanel(
                            title = strings.recognitionNoMatchTitle,
                            message = strings.recognitionNoMatchMessage(MaxRecognitionAttempts * (AudioFingerprint.DURATION_MILLIS / 1000L)),
                            icon = AppIcons.MusicNote,
                            actionLabel = strings.recognizeAgain.takeIf { canRecord },
                            onAction = ::requestRecognition,
                        )
                    }
                    SongRecognitionUiState.PermissionDenied -> item(key = "recognition-denied") {
                        ExpressiveStatePanel(
                            title = strings.recognitionMicPermissionNeeded,
                            message = strings.recognitionMicPermissionHelp,
                            icon = AppIcons.Mic,
                            tone = ExpressiveStateTone.Error,
                            actionLabel = strings.checkAgain.takeIf { canRecord },
                            onAction = ::requestRecognition,
                        )
                    }
                    is SongRecognitionUiState.MicrophoneUnavailable -> item(key = "recognition-unavailable") {
                        ExpressiveStatePanel(
                            title = strings.microphoneUnavailable,
                            message = current.message,
                            icon = AppIcons.Mic,
                            tone = ExpressiveStateTone.Error,
                            actionLabel = strings.retry.takeIf { current.canRetry && canRecord },
                            onAction = ::requestRecognition,
                        )
                    }
                    is SongRecognitionUiState.Error -> item(key = "recognition-error") {
                        ExpressiveStatePanel(
                            title = strings.recognitionFailed,
                            message = current.message,
                            icon = AppIcons.Refresh,
                            tone = ExpressiveStateTone.Error,
                            actionLabel = strings.retry.takeIf { canRecord },
                            onAction = ::requestRecognition,
                        )
                    }
                }
                if (state is SongRecognitionUiState.Idle) {
                    item(key = "recognition-playback-note") {
                        Text(
                            text = strings.recognitionPausesPlayback,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecognitionTaskPanel(
    state: SongRecognitionUiState,
    canRecord: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val idle = state is SongRecognitionUiState.Idle
    val capture = state as? SongRecognitionUiState.Listening
    val (title, supportingText) = when (state) {
        SongRecognitionUiState.Idle -> strings.recognitionIdentifySong to strings.recognitionIntro
        SongRecognitionUiState.RequestingPermission -> strings.recognitionRequestingMicPermission to null
        is SongRecognitionUiState.Listening -> (if (state.attempt > 1) {
            strings.recognitionListeningAgain(state.attempt)
        } else {
            strings.recognitionListening
        }) to strings.recognitionReadyTitle
        SongRecognitionUiState.Recognizing -> strings.recognitionFindingMatch to strings.recognitionMatching
        else -> return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ExpressiveLayout.FeatureCorner),
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val symbol: @Composable () -> Unit = {
                if (idle || capture != null) {
                    val reducedMotion = rememberReducedMotionEnabled()
                    ExpressiveFeatureIcon(
                        icon = if (capture == null) AppIcons.Mic else AppIcons.GraphicEq,
                        containerColor = if (capture == null) scheme.secondaryContainer else scheme.primaryContainer,
                        contentColor = if (capture == null) scheme.onSecondaryContainer else scheme.onPrimaryContainer,
                        modifier = Modifier.graphicsLayer {
                            // Paint the live microphone response without moving text or controls.
                            val scale = if (capture == null || reducedMotion) 1f else 0.88f + capture.level * 0.12f
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                } else {
                    LoadingIndicator()
                }
            }
            val heading: @Composable (Modifier) -> Unit = { modifier ->
                ExpressivePageHeading(
                    title = title,
                    supportingText = supportingText,
                    // Only phase text is live. Updating tenths of a second and audio level
                    // must not drown out the controls for someone using a screen reader.
                    modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 220.dp * LocalDensity.current.fontScale) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        symbol()
                        heading(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        symbol()
                        heading(Modifier)
                    }
                }
            }
            if (capture != null) {
                RecognitionCaptureProgress(capture.elapsedMillis, title)
            }
            if (idle) {
                RecognitionStartButton(enabled = canRecord, onStart = onStart)
            } else {
                OutlinedButton(
                    onClick = onCancel,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.SmallContentPadding,
                ) {
                    Text(strings.cancel)
                }
            }
        }
    }
}

@Composable
private fun RecognitionCaptureProgress(elapsedMillis: Long, label: String) {
    val elapsed = elapsedMillis.coerceIn(0L, AudioFingerprint.DURATION_MILLIS)
    val progress = elapsed.toFloat() / AudioFingerprint.DURATION_MILLIS.toFloat()
    Column(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExpressiveWavyProgress(
            progress = { progress },
            trackColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        Text(
            text = strings.recognitionElapsed(elapsed / 100L / 10.0),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecognitionStartButton(enabled: Boolean, onStart: () -> Unit) {
    BoxWithConstraints(Modifier.widthIn(max = 360.dp)) {
        val height = if (maxWidth >= 260.dp) ButtonDefaults.MediumContainerHeight else ExpressiveLayout.MinimumTouchTarget
        Button(
            onClick = onStart,
            enabled = enabled,
            modifier = Modifier.heightIn(min = height),
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
        ) {
            Icon(AppIcons.Mic, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
            Text(strings.startRecognition, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecognitionResultsHeader(canRecord: Boolean, onRetry: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ExpressivePageHeading(
            title = strings.recognitionResults,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(onClick = onRetry, enabled = canRecord, shapes = ButtonDefaults.shapes()) {
            Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.SmallIconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(strings.recognizeAgain)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecognitionResultRow(
    match: RecognizedSongMatch,
    index: Int,
    count: Int,
    queued: Boolean,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    val song = match.song
    val scheme = MaterialTheme.colorScheme
    val secondary = scheme.onSurfaceVariant
    val first = index == 0
    Surface(
        shape = connectedListItemShape(index, count),
        color = if (first) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(top = if (first) 0.dp else ExpressiveLayout.ConnectedItemGap),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
            val metadata: @Composable (Modifier) -> Unit = { modifier ->
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExpressiveArtwork(
                        model = song.al.picUrl,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = song.name.ifBlank { strings.unknownSong },
                            style = if (first) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.ar.joinToString(" / ") { it.name }.ifBlank { strings.unknownArtist },
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (song.al.name.isNotBlank()) {
                            Text(
                                text = song.al.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            val actions: @Composable () -> Unit = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onAddToQueue,
                        enabled = !queued,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.SmallContentPadding,
                    ) {
                        if (queued) {
                            Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.SmallIconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        }
                        Text(if (queued) strings.addedToQueue else strings.addToQueue)
                    }
                    FilledTonalButton(
                        onClick = onPlay,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.SmallContentPadding,
                    ) {
                        Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.SmallIconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(strings.play)
                    }
                }
            }
            if (maxWidth >= 520.dp * LocalDensity.current.fontScale) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    metadata(Modifier.weight(1f))
                    actions()
                }
            } else {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    metadata(Modifier.fillMaxWidth())
                    actions()
                }
            }
        }
    }
}

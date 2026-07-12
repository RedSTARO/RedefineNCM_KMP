package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.AudioMatch
import com.leejlredstar.redefinencm.kmp.data.api.dto.AudioMatchSong
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.recognition.AudioFingerprint
import com.leejlredstar.redefinencm.kmp.recognition.CapturedPcm
import com.leejlredstar.redefinencm.kmp.recognition.InsecureMicrophoneContextException
import com.leejlredstar.redefinencm.kmp.recognition.MicrophoneBusyException
import com.leejlredstar.redefinencm.kmp.recognition.MicrophonePermissionDeniedException
import com.leejlredstar.redefinencm.kmp.recognition.MicrophoneRecorder
import com.leejlredstar.redefinencm.kmp.recognition.MicrophoneUnavailableException
import com.leejlredstar.redefinencm.kmp.recognition.prepareRecognitionSamples
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecognizedSongMatch(
    val startTimeMs: Long,
    val song: SongDetailSongs,
)

sealed interface SongRecognitionUiState {
    data object Idle : SongRecognitionUiState
    data object RequestingPermission : SongRecognitionUiState
    data class Listening(
        val elapsedMillis: Long,
        val level: Float,
    ) : SongRecognitionUiState
    data object Recognizing : SongRecognitionUiState
    data class Results(val matches: List<RecognizedSongMatch>) : SongRecognitionUiState
    data class NoMatch(val reason: Int?) : SongRecognitionUiState
    data object PermissionDenied : SongRecognitionUiState
    data class MicrophoneUnavailable(
        val message: String,
        val canRetry: Boolean,
    ) : SongRecognitionUiState
    data class Error(val message: String) : SongRecognitionUiState
}

internal sealed interface AudioMatchOutcome {
    data class Results(val matches: List<RecognizedSongMatch>) : AudioMatchOutcome
    data class NoMatch(val reason: Int?) : AudioMatchOutcome
    data class Error(val message: String) : AudioMatchOutcome
}

internal fun classifyAudioMatch(response: AudioMatch?): AudioMatchOutcome {
    if (response == null) {
        return AudioMatchOutcome.Error("识曲请求失败，请检查网络后重试")
    }
    val data = response.data
        ?: return AudioMatchOutcome.Error("识曲服务未返回有效数据")
    if (data.type == 0) {
        return AudioMatchOutcome.NoMatch(data.noMatchReason)
    }
    if (data.type != 1) {
        return AudioMatchOutcome.Error("识曲服务返回了未知状态（type=${data.type}）")
    }

    val matches = data.result.orEmpty()
        .map { result ->
            RecognizedSongMatch(
                startTimeMs = result.startTime,
                song = result.song.toSongDetailSongs(),
            )
        }
        .filter { it.song.id != 0L }
        .distinctBy { it.song.id }
    return if (matches.isEmpty()) {
        AudioMatchOutcome.Error("识曲服务未返回匹配结果")
    } else {
        AudioMatchOutcome.Results(matches)
    }
}

internal fun AudioMatchSong.toSongDetailSongs(): SongDetailSongs = SongDetailSongs(
    id = id,
    name = name,
    ar = artists,
    al = album,
    dt = duration,
    mv = mvid,
)

internal fun SongDetailSongs.toRecognitionMediaInfo(): MediaInfo = MediaInfo(
    id = id.toString(),
    title = name,
    artist = ar.joinToString(", ") { it.name },
    albumTitle = al.name,
    artworkUri = al.picUrl,
    placeholderUri = "redefinencm://playbackPlaceHolder?id=$id",
    duration = dt,
)

class SongRecognitionViewModel internal constructor(
    private val recorder: MicrophoneRecorder,
    private val player: PlatformPlayer,
    private val matchAudio: suspend (durationSeconds: Int, fingerprint: String) -> AudioMatch?,
    private val prepareSamples: (CapturedPcm, durationMillis: Long) -> FloatArray,
    private val generateFingerprint: (FloatArray) -> String,
    private val processingDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {
    constructor(
        repository: Repository,
        recorder: MicrophoneRecorder,
        player: PlatformPlayer,
    ) : this(
        recorder = recorder,
        player = player,
        matchAudio = repository::audioMatch,
        prepareSamples = ::prepareRecognitionSamples,
        generateFingerprint = AudioFingerprint::generate,
        processingDispatcher = Dispatchers.Default,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    )

    private val _uiState = MutableStateFlow<SongRecognitionUiState>(SongRecognitionUiState.Idle)
    val uiState: StateFlow<SongRecognitionUiState> = _uiState.asStateFlow()

    private val recognitionGeneration = MutableStateFlow(0L)
    private var recognitionJob: Job? = null
    private var closed = false

    fun beginPermissionRequest() {
        if (closed) return
        invalidateActiveRecognition()
        _uiState.value = SongRecognitionUiState.RequestingPermission
    }

    fun onPermissionResult(granted: Boolean) {
        if (closed) return
        if (_uiState.value !is SongRecognitionUiState.RequestingPermission) return
        if (!granted) {
            _uiState.value = SongRecognitionUiState.PermissionDenied
            return
        }
        startRecognition()
    }

    fun cancelRecognition() {
        if (closed) return
        invalidateActiveRecognition()
        _uiState.value = SongRecognitionUiState.Idle
    }

    fun play(match: RecognizedSongMatch) {
        if (closed) return
        player.setQueue(listOf(match.song.toRecognitionMediaInfo()), 0)
        player.play()
    }

    fun addToQueue(match: RecognizedSongMatch) {
        if (closed) return
        player.addToQueue(match.song.toRecognitionMediaInfo())
    }

    fun close() {
        if (closed) return
        closed = true
        invalidateActiveRecognition()
        scope.cancel()
    }

    private fun startRecognition() {
        player.pause()
        recognitionJob?.cancel()
        val generation = recognitionGeneration.updateAndGet { it + 1L }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                _uiState.value = SongRecognitionUiState.Listening(0L, 0f)
                val captured = recorder.capture(
                    durationMillis = AudioFingerprint.DURATION_MILLIS,
                    onProgress = { elapsedMillis, level ->
                        if (recognitionGeneration.value == generation) {
                            _uiState.value = SongRecognitionUiState.Listening(
                                elapsedMillis = elapsedMillis.coerceIn(
                                    minimumValue = 0L,
                                    maximumValue = AudioFingerprint.DURATION_MILLIS,
                                ),
                                level = level.coerceIn(0f, 1f),
                            )
                        }
                    },
                )
                if (recognitionGeneration.value != generation) return@launch
                _uiState.value = SongRecognitionUiState.Recognizing

                val fingerprint = withContext(processingDispatcher) {
                    val samples = prepareSamples(captured, AudioFingerprint.DURATION_MILLIS)
                    generateFingerprint(samples)
                }
                if (recognitionGeneration.value != generation) return@launch

                val outcome = classifyAudioMatch(
                    matchAudio(
                        (AudioFingerprint.DURATION_MILLIS / 1_000L).toInt(),
                        fingerprint,
                    ),
                )
                if (recognitionGeneration.value != generation) return@launch
                _uiState.value = when (outcome) {
                    is AudioMatchOutcome.Results -> SongRecognitionUiState.Results(outcome.matches)
                    is AudioMatchOutcome.NoMatch -> SongRecognitionUiState.NoMatch(outcome.reason)
                    is AudioMatchOutcome.Error -> SongRecognitionUiState.Error(outcome.message)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: MicrophonePermissionDeniedException) {
                if (recognitionGeneration.value == generation) {
                    _uiState.value = SongRecognitionUiState.PermissionDenied
                }
            } catch (failure: MicrophoneBusyException) {
                if (recognitionGeneration.value == generation) {
                    _uiState.value = SongRecognitionUiState.MicrophoneUnavailable(
                        message = failure.message ?: "麦克风正在被使用",
                        canRetry = true,
                    )
                }
            } catch (failure: InsecureMicrophoneContextException) {
                if (recognitionGeneration.value == generation) {
                    _uiState.value = SongRecognitionUiState.MicrophoneUnavailable(
                        message = failure.message ?: "当前页面无法访问麦克风",
                        canRetry = false,
                    )
                }
            } catch (failure: MicrophoneUnavailableException) {
                if (recognitionGeneration.value == generation) {
                    _uiState.value = SongRecognitionUiState.MicrophoneUnavailable(
                        message = failure.message ?: "麦克风不可用",
                        canRetry = true,
                    )
                }
            } catch (failure: Exception) {
                if (recognitionGeneration.value == generation) {
                    _uiState.value = SongRecognitionUiState.Error(
                        failure.message ?: "听歌识曲失败",
                    )
                }
            } finally {
                if (
                    recognitionGeneration.value == generation &&
                    currentCoroutineContext()[Job] == recognitionJob
                ) {
                    recognitionJob = null
                }
            }
        }
        recognitionJob = job
        job.start()
    }

    private fun invalidateActiveRecognition() {
        recognitionGeneration.updateAndGet { it + 1L }
        recognitionJob?.cancel()
        recognitionJob = null
    }
}

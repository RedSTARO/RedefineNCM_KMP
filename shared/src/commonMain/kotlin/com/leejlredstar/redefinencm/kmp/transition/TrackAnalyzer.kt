package com.leejlredstar.redefinencm.kmp.transition

import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

private val RETRY_AFTER = 5.minutes

/** Where the beat model stands, for the Settings row that explains what smart transitions use. */
sealed interface BeatAcceleratorState {
    /** Not asked for yet: smart transitions have not needed it this session. */
    data object NotLoaded : BeatAcceleratorState

    data object Loading : BeatAcceleratorState

    data class Ready(val accelerator: InferenceAccelerator, val deviceLabel: String) : BeatAcceleratorState

    data class Unavailable(val reason: String) : BeatAcceleratorState
}

/**
 * Resolves where to read a track for analysis, without touching playback state.
 *
 * It must never go through the players' resolver: that one records a failure in the provider
 * registry's stream-failure channel, and a failed analysis of the *next* track would raise the
 * "this track cannot play" snackbar for a song nobody is playing.
 */
fun interface AnalysisUrlResolver {
    suspend fun resolve(media: MediaInfo): String?
}

/**
 * Analyses the two ends of a track once and remembers the answer.
 *
 * Results are cached in memory by media id (never the URL — stream URLs expire and are never
 * stored), and concurrent requests for the same track share one decode. The beat model is
 * loaded on first use and kept; when it cannot be loaded on an NPU or GPU, analysis still runs
 * and simply carries no beat grids.
 */
class TrackAnalyzer(
    private val decoder: TrackEndsDecoder,
    private val urls: AnalysisUrlResolver,
    private val modelLoader: BeatModelLoader,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val cacheSize: Int = 48,
) {
    private val scope = CoroutineScope(SupervisorJob() + computeDispatcher)
    private val cacheLock = Mutex()
    private val cache = LinkedHashMap<String, TrackAnalysis>()
    private val inFlight = HashMap<String, Deferred<TrackAnalysis?>>()
    // A track that could not be fetched or decoded is not retried on every re-plan: each retry
    // would download it again. It is tried again after RETRY_AFTER.
    private val failures = HashMap<String, TimeSource.Monotonic.ValueTimeMark>()
    private val modelLock = Mutex()
    private var model: BeatActivationModel? = null

    private val _accelerator = MutableStateFlow<BeatAcceleratorState>(BeatAcceleratorState.NotLoaded)
    val accelerator: StateFlow<BeatAcceleratorState> = _accelerator.asStateFlow()

    /** The cached analysis for [mediaId], without starting one. */
    suspend fun cached(mediaId: String): TrackAnalysis? = cacheLock.withLock { cache[mediaId] }

    /**
     * The analysis of [media], decoding it if needed. Null when the track cannot be fetched or
     * decoded; a cancelled caller does not cancel a decode another caller is waiting for.
     */
    suspend fun analyze(media: MediaInfo): TrackAnalysis? {
        val job = cacheLock.withLock {
            cache[media.id]?.let { hit ->
                // Refresh its place in the LRU order.
                cache.remove(media.id)
                cache[media.id] = hit
                return hit
            }
            failures[media.id]?.let { failedAt ->
                if (failedAt.elapsedNow() < RETRY_AFTER) return null
                failures.remove(media.id)
            }
            inFlight.getOrPut(media.id) {
                scope.async { runAnalysis(media) }
            }
        }
        val result = try {
            job.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        cacheLock.withLock {
            if (inFlight[media.id] === job) inFlight.remove(media.id)
            if (result != null) {
                cache[media.id] = result
                while (cache.size > cacheSize) cache.remove(cache.keys.first())
            } else {
                failures[media.id] = TimeSource.Monotonic.markNow()
            }
        }
        return result
    }

    /** Loads the model now if it is not loaded; for the Settings row's "check" action. */
    suspend fun prepareModel(): BeatAcceleratorState {
        loadedModel()
        return _accelerator.value
    }

    private suspend fun runAnalysis(media: MediaInfo): TrackAnalysis? {
        val url = urls.resolve(media) ?: return null
        val decoded = decoder.decode(url, TrackAnalysis.SECTION_MS, media.duration) ?: return null
        val duration = decoded.durationMs.takeIf { it > 0L } ?: media.duration.takeIf { it > 0L } ?: return null
        val beatModel = loadedModel()
        return withContext(computeDispatcher) {
            TrackAnalysis(
                mediaId = media.id,
                durationMs = duration,
                head = decoded.head?.let { analyzeSection(it, beatModel) },
                tail = decoded.tail?.let { analyzeSection(it, beatModel) },
                beatAccelerator = beatModel?.accelerator,
            )
        }
    }

    private suspend fun analyzeSection(window: AnalysisWindow, beatModel: BeatActivationModel?): SectionAnalysis {
        val energy = EnergyProfile.measure(window.samples, BeatModelFeatures.SAMPLE_RATE_HZ, window.startMs)
        val features = SpectralAnalyzer().analyze(window.samples)
        val grid = beatModel?.let { model ->
            val activations = modelLock.withLock { runBeatModel(model, features.mel) }
            fitBeatGrid(pickBeatEvents(activations), window.startMs, window.lengthMs)
        }
        return SectionAnalysis(
            startMs = window.startMs,
            endMs = window.startMs + window.lengthMs,
            energy = energy,
            grid = grid,
            key = estimateKey(features.chroma),
        )
    }

    private suspend fun loadedModel(): BeatActivationModel? = modelLock.withLock {
        model?.let { return@withLock it }
        if (_accelerator.value is BeatAcceleratorState.Unavailable) return@withLock null
        _accelerator.value = BeatAcceleratorState.Loading
        val availability = try {
            modelLoader.load()
        } catch (cancelled: CancellationException) {
            _accelerator.value = BeatAcceleratorState.NotLoaded
            throw cancelled
        } catch (failure: Throwable) {
            BeatModelAvailability.Unavailable(failure.message ?: failure::class.simpleName.orEmpty())
        }
        when (availability) {
            is BeatModelAvailability.Ready -> {
                model = availability.model
                _accelerator.value = BeatAcceleratorState.Ready(
                    availability.model.accelerator,
                    availability.model.deviceLabel,
                )
                availability.model
            }
            is BeatModelAvailability.Unavailable -> {
                _accelerator.value = BeatAcceleratorState.Unavailable(availability.reason)
                null
            }
        }
    }
}

package com.leejlredstar.redefinencm.kmp.transition

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.SoundQualityPreference
import com.leejlredstar.redefinencm.kmp.data.provider.StreamResolution
import com.leejlredstar.redefinencm.kmp.data.provider.toProviderItemIdOrNull
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SongTransitionPreferences(
    val mode: SongTransitionMode = SongTransitionMode.DEFAULT,
    val crossfadeSeconds: Long = DEFAULT_CROSSFADE_SECONDS,
)

/**
 * Plans the hand-over from the current track to the next one and arms it on the player.
 *
 * It owns no audio. The player executes a plan, starting the next track under the current one
 * at the planned moment and making it current halfway through, because only the player knows
 * where playback really is. Everything that decides the plan lives here, in common code:
 * reading the preference, analysing the two tracks, and choosing between a beat-matched blend,
 * a placed crossfade, or nothing.
 *
 * A plan names the pair of tracks it was made for. Every queue change, skip, shuffle toggle or
 * preference change produces a new pair and a new plan, and a player drops a plan whose pair is
 * no longer its current and next track, so a stale plan cannot fire.
 */
class SongTransitionCoordinator(
    private val player: PlatformPlayer,
    private val analyzer: TrackAnalyzer,
    private val settings: PlatformSettings,
    playerDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val scope = CoroutineScope(SupervisorJob() + playerDispatcher)
    private val _preferences = MutableStateFlow<SongTransitionPreferences?>(null)
    val preferences: StateFlow<SongTransitionPreferences?> = _preferences.asStateFlow()

    private val _lastPlan = MutableStateFlow<PlannedTransition?>(null)

    /** The most recent planning outcome, for the diagnostics line in Settings. */
    val lastPlan: StateFlow<PlannedTransition?> = _lastPlan.asStateFlow()

    val accelerator: StateFlow<BeatAcceleratorState> get() = analyzer.accelerator

    val capability: TransitionCapability get() = player.transitionCapability

    init {
        scope.launch {
            settings.awaitLoaded()
            _preferences.value = SongTransitionPreferences(
                mode = SongTransitionMode.fromWireValue(
                    settings.getString(SettingKeys.SONG_TRANSITION_MODE, SongTransitionMode.DEFAULT.wireValue),
                ),
                crossfadeSeconds = normalizeCrossfadeSeconds(
                    settings.getLong(SettingKeys.SONG_TRANSITION_CROSSFADE_SECONDS, DEFAULT_CROSSFADE_SECONDS),
                ),
            )
            combine(_preferences.filterNotNull(), player.queueSnapshot, player.duration) { prefs, queue, duration ->
                val current = queue.currentMedia
                PlanInputs(
                    preferences = prefs,
                    current = current,
                    next = queue.items.getOrNull(queue.currentIndex + 1),
                    outgoingDurationMs = duration.takeIf { it > 0L } ?: current?.duration ?: 0L,
                )
            }
                .distinctUntilChanged()
                .collectLatest(::planAndArm)
        }
    }

    fun setMode(mode: SongTransitionMode) {
        settings.setString(SettingKeys.SONG_TRANSITION_MODE, mode.wireValue)
        _preferences.update { (it ?: SongTransitionPreferences()).copy(mode = mode) }
    }

    fun setCrossfadeSeconds(seconds: Long) {
        val normalized = normalizeCrossfadeSeconds(seconds)
        settings.setLong(SettingKeys.SONG_TRANSITION_CROSSFADE_SECONDS, normalized)
        _preferences.update { (it ?: SongTransitionPreferences()).copy(crossfadeSeconds = normalized) }
    }

    /** Re-reads both values after a settings import replaced them underneath. */
    fun reloadPreferences() {
        _preferences.value = SongTransitionPreferences(
            mode = SongTransitionMode.fromWireValue(
                settings.getString(SettingKeys.SONG_TRANSITION_MODE, SongTransitionMode.DEFAULT.wireValue),
            ),
            crossfadeSeconds = normalizeCrossfadeSeconds(
                settings.getLong(SettingKeys.SONG_TRANSITION_CROSSFADE_SECONDS, DEFAULT_CROSSFADE_SECONDS),
            ),
        )
    }

    /** Loads the beat model now, so Settings can say what smart transitions will run on. */
    suspend fun probeAccelerator(): BeatAcceleratorState = analyzer.prepareModel()

    private data class PlanInputs(
        val preferences: SongTransitionPreferences,
        val current: MediaInfo?,
        val next: MediaInfo?,
        val outgoingDurationMs: Long,
    )

    private suspend fun planAndArm(inputs: PlanInputs) {
        val current = inputs.current
        val next = inputs.next
        val mode = inputs.preferences.mode
        if (mode == SongTransitionMode.OFF || current == null || next == null ||
            player.transitionCapability == TransitionCapability.NONE
        ) {
            player.disarmTransition()
            _lastPlan.value = null
            return
        }
        if (mode == SongTransitionMode.CROSSFADE) {
            arm(plan(inputs, null, null))
            return
        }
        // Smart: arm what is known now, so a blend still happens if analysis is slow or fails,
        // then replace it once both ends are analysed. Replacing is safe until the blend starts.
        arm(plan(inputs, analyzer.cached(current.id), analyzer.cached(next.id)))
        val (outgoing, incoming) = coroutineScope {
            val out = async { analyzer.analyze(current) }
            val inc = async { analyzer.analyze(next) }
            out.await() to inc.await()
        }
        arm(plan(inputs, outgoing, incoming))
    }

    private fun plan(inputs: PlanInputs, outgoing: TrackAnalysis?, incoming: TrackAnalysis?): PlannedTransition {
        val current = inputs.current!!
        val next = inputs.next!!
        return TransitionPlanner.plan(
            outgoingMediaId = current.id,
            outgoingAlbum = current.albumTitle,
            outgoingDurationMs = inputs.outgoingDurationMs.takeIf { it > 0L } ?: outgoing?.durationMs ?: 0L,
            outgoing = outgoing,
            incomingMediaId = next.id,
            incomingAlbum = next.albumTitle,
            incomingDurationMs = incoming?.durationMs ?: next.duration,
            incoming = incoming,
            mode = inputs.preferences.mode,
            crossfadeSeconds = inputs.preferences.crossfadeSeconds,
            capability = player.transitionCapability,
        )
    }

    private fun arm(planned: PlannedTransition) {
        _lastPlan.value = planned
        val plan = planned.plan
        if (plan == null) player.disarmTransition() else player.armTransition(plan)
    }
}

/** A platform's own copy of a downloaded NetEase track, in a form its analysis decoder opens. */
fun interface AnalysisLocalAudio {
    suspend fun uri(neteaseId: Long): String?
}

/**
 * Where to read a track for analysis: a local download when there is one, otherwise the lowest
 * quality the provider serves. Analysis needs rhythm and loudness, not fidelity, and the lowest
 * tier costs a fraction of the bandwidth.
 *
 * It asks the provider directly rather than through [MusicProviderRegistry.streamUrl], which
 * would record a failure against the track in the registry's failure channel.
 */
class ProviderAnalysisUrlResolver(
    private val providers: MusicProviderRegistry,
    private val repository: Repository,
    private val localAudioUri: suspend (neteaseId: Long) -> String?,
) : AnalysisUrlResolver {
    override suspend fun resolve(media: MediaInfo): String? {
        val itemId = media.id.toProviderItemIdOrNull() ?: return null
        val neteaseId = itemId.neteaseIdOrNull
        if (neteaseId == null) {
            val provider = providers[itemId.provider] ?: return null
            val resolution = try {
                provider.resolveStream(itemId, SoundQualityPreference.STANDARD)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                return null
            }
            return (resolution as? StreamResolution.Playable)?.url
        }
        localAudioUri(neteaseId)?.let { return it }
        return repository.getSongUrl(neteaseId, "standard")
    }
}

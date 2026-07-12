package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.TimeSource

private const val REPORTING_TICK_MILLIS = 1_000L
internal const val PLAY_STATE_REPORT_INTERVAL_MILLIS = 30_000L
internal const val UNKNOWN_DURATION_SCROBBLE_THRESHOLD_MILLIS = 30_000L
private const val PLAYBACK_SESSION_ID_LENGTH = 12
private const val PLAYBACK_SESSION_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
private const val POSITION_ADVANCE_TOLERANCE_MILLIS = 1_500L
private const val SELECTION_STABILIZATION_MILLIS = 250L
private const val RELAY_QUEUE_CAPACITY = 2

/**
 * Process-wide playback reporting pipeline.
 *
 * Platform players only publish playback state and [PlatformPlayer.playbackOccurrence]. This
 * coordinator is the single owner of NCBL scrobble timing and relay session IDs, preventing the
 * four platform implementations from reporting the same playback independently.
 */
class PlaybackReportingCoordinator(
    private val repository: Repository,
    private val player: PlatformPlayer,
    private val settings: PlatformSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clockOrigin = TimeSource.Monotonic.markNow()
    private val reducer = PlaybackReportingReducer(
        sessionIdGenerator = ::generatePlaybackSessionId,
    )
    private val dispatcher = PlaybackReportingDispatcher(scope, ::dispatch)

    init {
        scope.launch {
            merge(playerSignals(), reportingTicker()).collect { signal ->
                val actions = reducer.observe(
                    observation = currentObservation(),
                    nowMillis = clockOrigin.elapsedNow().inWholeMilliseconds,
                    signal = signal,
                )
                actions.forEach(dispatcher::enqueue)
            }
        }
    }

    fun close() {
        dispatcher.close()
        scope.cancel()
    }

    private fun playerSignals(): Flow<PlaybackReportingSignal> = merge(
        player.playbackOccurrence.map { PlaybackReportingSignal.OCCURRENCE },
        player.currentMedia.map { PlaybackReportingSignal.MEDIA },
        player.state.map { PlaybackReportingSignal.STATE },
        player.isPlaying.map { PlaybackReportingSignal.IS_PLAYING },
        player.position.map { PlaybackReportingSignal.POSITION },
        player.duration.map { PlaybackReportingSignal.DURATION },
        player.shuffleEnabled.map { PlaybackReportingSignal.SHUFFLE },
    )

    private fun reportingTicker(): Flow<PlaybackReportingSignal> = flow {
        while (currentCoroutineContext().isActive) {
            delay(REPORTING_TICK_MILLIS)
            emit(PlaybackReportingSignal.TICK)
        }
    }

    private fun currentObservation(): PlaybackReportingObservation {
        val cookie = HttpClientFactory.cleanCookie(settings.getString(SettingKeys.COOKIE, ""))
        return PlaybackReportingObservation(
            occurrence = player.playbackOccurrence.value,
            media = player.currentMedia.value,
            state = player.state.value,
            isPlaying = player.isPlaying.value,
            positionMs = player.position.value.coerceAtLeast(0L),
            durationMs = player.duration.value,
            shuffleEnabled = player.shuffleEnabled.value,
            qualityLevel = normalizePlaybackQuality(
                settings.getString(
                    SettingKeys.ONLINE_PLAY_QUALITY,
                    SoundQuality.EXHIGH.name,
                ),
            ),
            credentialKey = playbackCredentialKey(cookie),
            credentialCookie = cookie.takeIf(String::isNotEmpty),
        )
    }

    private suspend fun dispatch(action: PlaybackReportingAction) {
        settings.awaitLoaded()
        val currentCookie = HttpClientFactory.cleanCookie(
            settings.getStringAsync(SettingKeys.COOKIE, ""),
        )
        val currentCredentialKey = playbackCredentialKey(currentCookie)
        if (currentCredentialKey != action.credentialKey) return

        when (action) {
            is PlaybackReportingAction.SubmitPlayState -> repository.submitPlayState(
                id = action.songId,
                sessionId = action.sessionId,
                progressSeconds = action.progressSeconds,
                playMode = action.playMode,
                type = "song",
                credentialCookie = action.credentialCookie,
            )
            is PlaybackReportingAction.Scrobble -> repository.scrobbleV1(
                id = action.songId,
                timeSeconds = action.playedSeconds,
                sourceId = action.sourceId,
                source = action.source,
                name = action.name,
                artist = action.artist,
                bitrate = null,
                level = action.level,
                totalSeconds = action.totalSeconds,
                credentialCookie = action.credentialCookie,
            )
        }
    }
}

internal data class PlaybackReportingObservation(
    val occurrence: Long,
    val media: MediaInfo?,
    val state: PlayerState,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val shuffleEnabled: Boolean,
    val qualityLevel: String,
    val credentialKey: Long?,
    val credentialCookie: String?,
)

internal enum class PlaybackReportingSignal {
    OCCURRENCE,
    MEDIA,
    STATE,
    IS_PLAYING,
    POSITION,
    DURATION,
    SHUFFLE,
    TICK,
}

internal class PlaybackReportingDispatcher(
    private val scope: CoroutineScope,
    private val report: suspend (PlaybackReportingAction) -> Unit,
    relayQueueCapacity: Int = RELAY_QUEUE_CAPACITY,
) {
    private val relayQueue = Channel<PlaybackReportingAction.SubmitPlayState>(
        capacity = relayQueueCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        require(relayQueueCapacity > 0)
        scope.launch {
            for (action in relayQueue) report(action)
        }
    }

    fun enqueue(action: PlaybackReportingAction) {
        // Relay is current-state telemetry: under a 60-second timeout, preserve only the most
        // recent pending states. Scrobble is already one-shot and must not be evicted before send.
        when (action) {
            is PlaybackReportingAction.SubmitPlayState -> relayQueue.trySend(action)
            is PlaybackReportingAction.Scrobble -> scope.launch { report(action) }
        }
    }

    fun close() {
        relayQueue.close()
    }
}

internal sealed interface PlaybackReportingAction {
    val credentialKey: Long
    val credentialCookie: String

    data class SubmitPlayState(
        override val credentialKey: Long,
        override val credentialCookie: String,
        val songId: Long,
        val sessionId: String,
        val progressSeconds: Long,
        val playMode: String,
    ) : PlaybackReportingAction

    data class Scrobble(
        override val credentialKey: Long,
        override val credentialCookie: String,
        val songId: Long,
        val playedSeconds: Long,
        val sourceId: String?,
        val source: String,
        val name: String?,
        val artist: String?,
        val level: String,
        val totalSeconds: Long?,
    ) : PlaybackReportingAction
}

internal class PlaybackReportingReducer(
    private val sessionIdGenerator: () -> String,
    private val stateReportIntervalMillis: Long = PLAY_STATE_REPORT_INTERVAL_MILLIS,
    private val unknownDurationScrobbleThresholdMillis: Long =
        UNKNOWN_DURATION_SCROBBLE_THRESHOLD_MILLIS,
) {
    private var active: ActivePlaybackReportingSession? = null
    private var pendingOccurrence: Long? = null
    private var pendingSinceMs: Long = 0L

    init {
        require(stateReportIntervalMillis > 0L)
        require(unknownDurationScrobbleThresholdMillis > 0L)
    }

    fun observe(
        observation: PlaybackReportingObservation,
        nowMillis: Long,
        signal: PlaybackReportingSignal = PlaybackReportingSignal.TICK,
    ): List<PlaybackReportingAction> {
        val actions = mutableListOf<PlaybackReportingAction>()
        val now = nowMillis.coerceAtLeast(0L)
        var session = active

        if (session == null && signal == PlaybackReportingSignal.OCCURRENCE) {
            pendingOccurrence = observation.occurrence
            pendingSinceMs = now
        }

        if (session != null) {
            val credentialChanged = observation.credentialKey != session.credentialKey
            val occurrenceChanged = observation.occurrence != session.occurrence
            val mediaSnapshotMismatch =
                !occurrenceChanged && observation.media?.id != session.media.id

            if (credentialChanged) {
                // The old account cookie is no longer available. Drop the old session instead of
                // accidentally reporting its listening time under the new account.
                active = null
                session = null
            } else if (occurrenceChanged) {
                pendingOccurrence = observation.occurrence
                pendingSinceMs = now
                session.scheduleRelay(actions, now)
                session.scheduleScrobbleIfEligible(actions)
                active = null
                // The occurrence signal can arrive while the old item is still publishing its
                // final isPlaying/state values. Wait for the next signal (or the one-second tick)
                // before starting the new session from a stable playback snapshot.
                return actions
            } else if (mediaSnapshotMismatch) {
                // Players publish the new media snapshot before advancing occurrence. Ignore that
                // intermediate combination so it cannot create a short-lived session with the old
                // occurrence and the new song.
                if (observation.state.isReportingTerminal()) {
                    session.scheduleRelay(actions, now)
                    session.scheduleScrobbleIfEligible(actions)
                    active = null
                    session = null
                } else {
                    return actions
                }
            } else {
                session.accrueUntil(observation, now)
                if (observation.state.isReportingTerminal()) {
                    session.updateFrom(observation, now)
                    session.scheduleRelay(actions, now)
                    session.scheduleScrobbleIfEligible(actions)
                    active = null
                    session = null
                }
            }
        }

        val media = observation.media
        val songId = media?.id?.toLongOrNull()?.takeIf { it > 0L }
        val stableSelection = pendingOccurrence?.let { pending ->
            observation.occurrence == pending &&
                observation.isPlaying &&
                observation.state == PlayerState.PLAYING &&
                now - pendingSinceMs >= SELECTION_STABILIZATION_MILLIS &&
                when (signal) {
                    PlaybackReportingSignal.STATE,
                    PlaybackReportingSignal.IS_PLAYING -> true
                    PlaybackReportingSignal.TICK -> now - pendingSinceMs >= SELECTION_STABILIZATION_MILLIS
                    else -> false
                }
        } ?: true
        if (
            session == null &&
            observation.credentialKey != null &&
            observation.credentialCookie != null &&
            media != null &&
            songId != null &&
            observation.isActivelyPlaying &&
            !observation.state.isReportingTerminal() &&
            stableSelection
        ) {
            val initialPosition = boundedPositionMillis(observation.positionMs, observation.durationMs)
            session = ActivePlaybackReportingSession(
                occurrence = observation.occurrence,
                credentialKey = observation.credentialKey,
                credentialCookie = observation.credentialCookie,
                songId = songId,
                media = media,
                sessionId = validatedSessionId(sessionIdGenerator()),
                qualityLevel = normalizePlaybackQuality(observation.qualityLevel),
                durationMs = effectiveDurationMillis(observation),
                lastPositionMs = initialPosition,
                playMode = playMode(observation.shuffleEnabled),
                wasPlaying = true,
                accountingPositionMs = initialPosition,
                accountingObservedAtMs = now,
            )
            active = session
            pendingOccurrence = null
            session.scheduleRelay(actions, now)
        } else if (session != null) {
            val wasPlaying = session.wasPlaying
            val previousPlayMode = session.playMode
            session.updateFrom(observation, now)
            session.scheduleScrobbleIfEligible(actions)

            val playStateChanged = wasPlaying != observation.isActivelyPlaying
            val playModeChanged = previousPlayMode != session.playMode
            val intervalElapsed =
                observation.isActivelyPlaying &&
                    now - session.lastRelayScheduledAtMs >= stateReportIntervalMillis
            if (playStateChanged || playModeChanged || intervalElapsed) {
                session.scheduleRelay(actions, now)
            }
        }

        return actions
    }

    private fun ActivePlaybackReportingSession.scheduleScrobbleIfEligible(
        actions: MutableList<PlaybackReportingAction>,
    ) {
        if (scrobbleAttempted) return
        val totalSeconds = durationMs.takeIf { it > 0L }?.div(1_000L)?.takeIf { it > 0L }
        val thresholdMillis = if (totalSeconds != null) {
            maxOf(1L, totalSeconds / 2L) * 1_000L
        } else {
            unknownDurationScrobbleThresholdMillis
        }
        if (listenedMs < thresholdMillis) return

        val playedSeconds = (listenedMs / 1_000L)
            .let { played -> totalSeconds?.let { minOf(played, it) } ?: played }
            .coerceAtLeast(1L)
        scrobbleAttempted = true
        actions += PlaybackReportingAction.Scrobble(
            credentialKey = credentialKey,
            credentialCookie = credentialCookie,
            songId = songId,
            playedSeconds = playedSeconds,
            sourceId = media.sourceId.trim().takeIf(String::isNotEmpty),
            source = "list",
            name = media.title.trim().takeIf(String::isNotEmpty),
            artist = media.artist.trim().takeIf(String::isNotEmpty),
            level = qualityLevel,
            totalSeconds = totalSeconds,
        )
    }

    private fun ActivePlaybackReportingSession.scheduleRelay(
        actions: MutableList<PlaybackReportingAction>,
        nowMillis: Long,
    ) {
        val progressSeconds = lastPositionMs.coerceAtLeast(0L) / 1_000L
        if (
            progressSeconds == lastRelayProgressSeconds &&
            playMode == lastRelayPlayMode
        ) {
            return
        }
        actions += PlaybackReportingAction.SubmitPlayState(
            credentialKey = credentialKey,
            credentialCookie = credentialCookie,
            songId = songId,
            sessionId = sessionId,
            progressSeconds = progressSeconds,
            playMode = playMode,
        )
        lastRelayProgressSeconds = progressSeconds
        lastRelayPlayMode = playMode
        lastRelayScheduledAtMs = nowMillis
    }
}

private class ActivePlaybackReportingSession(
    val occurrence: Long,
    val credentialKey: Long,
    val credentialCookie: String,
    val songId: Long,
    val media: MediaInfo,
    val sessionId: String,
    val qualityLevel: String,
    var durationMs: Long,
    var lastPositionMs: Long,
    var playMode: String,
    var wasPlaying: Boolean,
    var accountingPositionMs: Long,
    var accountingObservedAtMs: Long,
) {
    var listenedMs: Long = 0L
    var scrobbleAttempted: Boolean = false
    var lastRelayScheduledAtMs: Long = Long.MIN_VALUE
    var lastRelayProgressSeconds: Long? = null
    var lastRelayPlayMode: String? = null

    fun accrueUntil(observation: PlaybackReportingObservation, nowMillis: Long) {
        val boundedNow = nowMillis.coerceAtLeast(accountingObservedAtMs)
        val observedDuration = effectiveDurationMillis(observation).takeIf { it > 0L } ?: durationMs
        val observedPosition = boundedPositionMillis(observation.positionMs, observedDuration)
        if (!wasPlaying) {
            accountingPositionMs = observedPosition
            accountingObservedAtMs = boundedNow
            return
        }

        val wallDelta = boundedNow - accountingObservedAtMs
        val positionDelta = observedPosition - accountingPositionMs
        when {
            positionDelta < 0L || positionDelta > wallDelta + POSITION_ADVANCE_TOLERANCE_MILLIS -> {
                // A backward jump or a position advance much larger than wall time is a seek.
                accountingPositionMs = observedPosition
                accountingObservedAtMs = boundedNow
            }
            positionDelta > 0L -> {
                listenedMs += minOf(positionDelta, wallDelta)
                accountingPositionMs = observedPosition
                accountingObservedAtMs = boundedNow
            }
            // Keep the old anchor while position is stalled. If the event loop resumes after a
            // long sleep, a later small position delta contributes only that real audio advance.
        }
    }

    fun updateFrom(observation: PlaybackReportingObservation, nowMillis: Long) {
        durationMs = effectiveDurationMillis(observation).takeIf { it > 0L } ?: durationMs
        val observedPosition = boundedPositionMillis(observation.positionMs, durationMs)
        val activelyPlaying = observation.isActivelyPlaying
        if (wasPlaying != activelyPlaying || !activelyPlaying) {
            accountingPositionMs = observedPosition
            accountingObservedAtMs = nowMillis.coerceAtLeast(accountingObservedAtMs)
        }
        lastPositionMs = observedPosition
        playMode = playMode(observation.shuffleEnabled)
        wasPlaying = activelyPlaying
    }
}

private fun effectiveDurationMillis(observation: PlaybackReportingObservation): Long =
    observation.durationMs.takeIf { it > 0L }
        ?: observation.media?.duration?.takeIf { it > 0L }
        ?: -1L

private fun boundedPositionMillis(positionMs: Long, durationMs: Long): Long =
    if (durationMs > 0L) {
        positionMs.coerceIn(0L, durationMs)
    } else {
        positionMs.coerceAtLeast(0L)
    }

private fun PlayerState.isReportingTerminal(): Boolean =
    this == PlayerState.IDLE || this == PlayerState.ENDED || this == PlayerState.ERROR

private val PlaybackReportingObservation.isActivelyPlaying: Boolean
    get() = isPlaying && state == PlayerState.PLAYING

internal fun playMode(shuffleEnabled: Boolean): String =
    if (shuffleEnabled) "random" else "list_loop"

internal fun normalizePlaybackQuality(raw: String): String =
    SoundQuality.entries
        .firstOrNull { quality -> quality.name.equals(raw.trim(), ignoreCase = true) }
        ?.name
        ?.lowercase()
        ?: SoundQuality.EXHIGH.name.lowercase()

internal fun playbackCredentialKey(cookie: String): Long? {
    if (cookie.isBlank()) return null
    var hash = -3750763034362895579L
    cookie.forEach { character ->
        hash = hash xor character.code.toLong()
        hash *= 1099511628211L
    }
    return hash
}

internal fun generatePlaybackSessionId(random: Random = Random.Default): String =
    buildString(PLAYBACK_SESSION_ID_LENGTH) {
        repeat(PLAYBACK_SESSION_ID_LENGTH) {
            append(PLAYBACK_SESSION_ALPHABET[random.nextInt(PLAYBACK_SESSION_ALPHABET.length)])
        }
    }

private fun validatedSessionId(value: String): String {
    require(
        value.length == PLAYBACK_SESSION_ID_LENGTH &&
            value.all { it in PLAYBACK_SESSION_ALPHABET },
    ) { "Playback session ID must be 12 uppercase letters or digits" }
    return value
}

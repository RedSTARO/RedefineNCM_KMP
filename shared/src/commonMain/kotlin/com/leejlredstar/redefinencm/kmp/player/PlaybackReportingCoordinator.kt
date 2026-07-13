package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.PlaybackAccountComparison
import com.leejlredstar.redefinencm.kmp.data.PlaybackAccountSnapshot
import com.leejlredstar.redefinencm.kmp.data.PlaybackReportEndpoint
import com.leejlredstar.redefinencm.kmp.data.PlaybackReportResult
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.comparePlaybackAccountSnapshots
import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.cookieFingerprint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
private const val FIRST_ACCOUNT_READBACK_DELAY_MILLIS = 3_000L
private const val SECOND_ACCOUNT_READBACK_DELAY_MILLIS = 7_000L
private const val ACCOUNT_BASELINE_TIMEOUT_MILLIS = 3_000L
private const val ACCOUNT_READBACK_TIMEOUT_MILLIS = 15_000L

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
    private val _reportingState = MutableStateFlow(PlaybackReportingState())
    val reportingState: StateFlow<PlaybackReportingState> = _reportingState.asStateFlow()
    private val verificationEventQueue = PlaybackVerificationEventQueue()
    val verificationEvents: Flow<PlaybackAccountVerificationEvent> = verificationEventQueue.events

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
        try {
            settings.awaitLoaded()
            if (!isCurrentCredential(action)) {
                updateStatus(accountChangedStatus(action))
                return
            }

            when (action) {
                is PlaybackReportingAction.SubmitPlayState -> dispatchRelay(action)
                is PlaybackReportingAction.Scrobble -> dispatchScrobble(action)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            updateStatus(
                PlaybackReportingStatus(
                    kind = action.kind,
                    songId = action.songId,
                    credentialKey = action.credentialKey,
                    reportingGeneration = action.reportingGeneration,
                    phase = PlaybackReportingPhase.TRANSPORT_FAILURE,
                    message = "上报处理失败",
                ),
            )
        }
    }

    private suspend fun dispatchRelay(action: PlaybackReportingAction.SubmitPlayState) {
        updateStatus(
            PlaybackReportingStatus(
                kind = PlaybackReportingKind.RELAY,
                songId = action.songId,
                credentialKey = action.credentialKey,
                reportingGeneration = action.reportingGeneration,
                phase = PlaybackReportingPhase.SENDING,
                endpoint = PlaybackReportEndpoint.RELAY,
            ),
        )
        val result = repository.submitPlayState(
            id = action.songId,
            sessionId = action.sessionId,
            progressSeconds = action.progressSeconds,
            playMode = action.playMode,
            type = "song",
            credentialCookie = action.credentialCookie,
        )
        updateStatus(result.toStatus(action))
    }

    private suspend fun dispatchScrobble(action: PlaybackReportingAction.Scrobble) {
        updateStatus(
            PlaybackReportingStatus(
                kind = PlaybackReportingKind.SCROBBLE,
                songId = action.songId,
                credentialKey = action.credentialKey,
                reportingGeneration = action.reportingGeneration,
                phase = PlaybackReportingPhase.SENDING,
            ),
        )
        val uid = settings.getLongAsync(SettingKeys.UID, 0L).takeIf { it > 0L }
        val before = uid?.let {
            withTimeoutOrNull(ACCOUNT_BASELINE_TIMEOUT_MILLIS) {
                accountSnapshotOrNull(it, action.credentialCookie)
            }
        }
        if (!isCurrentCredential(action, uid)) {
            updateStatus(accountChangedStatus(action))
            return
        }
        val result = repository.scrobbleV1(
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
        val acceptedStatus = result.toStatus(action)
        updateStatus(acceptedStatus)
        if (result !is PlaybackReportResult.Accepted || uid == null) {
            return
        }
        if (!isCurrentCredential(action, uid)) {
            updateStatus(accountChangedStatus(action))
            return
        }

        updateStatus(acceptedStatus.copy(phase = PlaybackReportingPhase.VERIFYING))
        verifyScrobbleReadback(action, uid, before)
    }

    private suspend fun verifyScrobbleReadback(
        action: PlaybackReportingAction.Scrobble,
        uid: Long,
        before: PlaybackAccountSnapshot?,
    ) {
        var comparison: PlaybackAccountComparison? = null
        var latestSnapshot: PlaybackAccountSnapshot? = null
        for (readbackDelay in listOf(
            FIRST_ACCOUNT_READBACK_DELAY_MILLIS,
            SECOND_ACCOUNT_READBACK_DELAY_MILLIS,
        )) {
            delay(readbackDelay)
            if (!isCurrentCredential(action, uid)) {
                updateStatus(accountChangedStatus(action))
                return
            }
            val snapshot = accountSnapshotOrNull(uid, action.credentialCookie)
            if (!isCurrentCredential(action, uid)) {
                updateStatus(accountChangedStatus(action))
                return
            }
            if (snapshot != null) {
                latestSnapshot = snapshot
                comparison = comparePlaybackAccountSnapshots(
                    before = before ?: PlaybackAccountSnapshot(uid, null, null, null),
                    after = snapshot,
                    songId = action.songId,
                )
                if (comparison.accountEffectObserved) break
            }
        }

        val verifiedComparison = comparison ?: PlaybackAccountComparison(
            sameAccount = true,
            recordAppeared = false,
            recordPlayCountDelta = null,
            recordScoreDelta = null,
            recentBecameLatest = false,
            recentPlayTimeAdvanced = false,
            levelPlayCountDelta = null,
            accountEffectObserved = false,
        )
        if (!isCurrentCredential(action, uid)) {
            updateStatus(accountChangedStatus(action))
            return
        }
        verificationEventQueue.emit(
            PlaybackAccountVerificationEvent(
                uid = uid,
                credentialKey = action.credentialKey,
                reportingGeneration = action.reportingGeneration,
                songId = action.songId,
                userLevel = latestSnapshot?.userLevel,
                comparison = verifiedComparison,
            ),
        )
        val current = _reportingState.value.scrobble
        if (current?.reportingGeneration != action.reportingGeneration) return
        updateStatus(
            current.copy(
                phase = if (verifiedComparison.accountEffectObserved) {
                    PlaybackReportingPhase.ACCOUNT_VERIFIED
                } else {
                    PlaybackReportingPhase.NOT_REFLECTED
                },
                accountComparison = verifiedComparison,
            ),
        )
    }

    private suspend fun accountSnapshotOrNull(
        uid: Long,
        credentialCookie: String,
    ): PlaybackAccountSnapshot? = withTimeoutOrNull(ACCOUNT_READBACK_TIMEOUT_MILLIS) {
        try {
            repository.getPlaybackAccountSnapshot(uid, credentialCookie = credentialCookie)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun isCurrentCredential(
        action: PlaybackReportingAction,
        uid: Long? = null,
    ): Boolean {
        val currentCookie = HttpClientFactory.cleanCookie(
            settings.getStringAsync(SettingKeys.COOKIE, ""),
        )
        return playbackCredentialKey(currentCookie) == action.credentialKey &&
            (uid == null || settings.getLongAsync(SettingKeys.UID, 0L) == uid)
    }

    private fun updateStatus(status: PlaybackReportingStatus) {
        _reportingState.update { current -> current.withStatus(status) }
    }
}

private val PlaybackReportingAction.kind: PlaybackReportingKind
    get() = when (this) {
        is PlaybackReportingAction.SubmitPlayState -> PlaybackReportingKind.RELAY
        is PlaybackReportingAction.Scrobble -> PlaybackReportingKind.SCROBBLE
    }

private val PlaybackReportingAction.songId: Long
    get() = when (this) {
        is PlaybackReportingAction.SubmitPlayState -> this.songId
        is PlaybackReportingAction.Scrobble -> this.songId
    }

private fun accountChangedStatus(action: PlaybackReportingAction): PlaybackReportingStatus =
    PlaybackReportingStatus(
        kind = action.kind,
        songId = action.songId,
        credentialKey = action.credentialKey,
        reportingGeneration = action.reportingGeneration,
        phase = PlaybackReportingPhase.ACCOUNT_CHANGED,
    )

private fun PlaybackReportResult.toStatus(
    action: PlaybackReportingAction,
): PlaybackReportingStatus = when (this) {
    is PlaybackReportResult.Accepted -> PlaybackReportingStatus(
        kind = action.kind,
        songId = action.songId,
        credentialKey = action.credentialKey,
        reportingGeneration = action.reportingGeneration,
        phase = PlaybackReportingPhase.ACCEPTED,
        endpoint = endpoint,
        httpStatus = httpStatus,
        serverCode = serverCode,
        details = details,
    )
    is PlaybackReportResult.Rejected -> PlaybackReportingStatus(
        kind = action.kind,
        songId = action.songId,
        credentialKey = action.credentialKey,
        reportingGeneration = action.reportingGeneration,
        phase = PlaybackReportingPhase.REJECTED,
        endpoint = endpoint,
        httpStatus = httpStatus,
        serverCode = serverCode,
        details = details,
        message = details.message ?: "服务器拒绝了播放上报",
    )
    is PlaybackReportResult.Unsupported -> PlaybackReportingStatus(
        kind = action.kind,
        songId = action.songId,
        credentialKey = action.credentialKey,
        reportingGeneration = action.reportingGeneration,
        phase = PlaybackReportingPhase.UNSUPPORTED,
        endpoint = endpoint,
        httpStatus = httpStatus,
        message = when (endpoint) {
            PlaybackReportEndpoint.RELAY -> "当前服务器未部署 relay 播放进度接口"
            else -> "当前服务器不支持播放记录上报"
        },
    )
    is PlaybackReportResult.TransportFailure -> PlaybackReportingStatus(
        kind = action.kind,
        songId = action.songId,
        credentialKey = action.credentialKey,
        reportingGeneration = action.reportingGeneration,
        phase = PlaybackReportingPhase.TRANSPORT_FAILURE,
        endpoint = endpoint,
        httpStatus = httpStatus,
        message = "播放上报请求失败",
    )
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

/** Ordered single-consumer queue: account verification events must never evict newer results. */
internal class PlaybackVerificationEventQueue(
    capacity: Int = Channel.BUFFERED,
) {
    private val channel = Channel<PlaybackAccountVerificationEvent>(capacity)
    val events: Flow<PlaybackAccountVerificationEvent> = channel.receiveAsFlow()

    suspend fun emit(event: PlaybackAccountVerificationEvent) {
        channel.send(event)
    }
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
    val reportingGeneration: Long

    data class SubmitPlayState(
        override val credentialKey: Long,
        override val credentialCookie: String,
        val songId: Long,
        val sessionId: String,
        val progressSeconds: Long,
        val playMode: String,
        override val reportingGeneration: Long = 0L,
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
        override val reportingGeneration: Long = 0L,
    ) : PlaybackReportingAction
}

internal class PlaybackReportingReducer(
    private val sessionIdGenerator: () -> String,
    private val stateReportIntervalMillis: Long = PLAY_STATE_REPORT_INTERVAL_MILLIS,
    private val unknownDurationScrobbleThresholdMillis: Long =
        UNKNOWN_DURATION_SCROBBLE_THRESHOLD_MILLIS,
) {
    private var active: ActivePlaybackReportingSession? = null
    private var nextReportingGeneration: Long = 0L
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
                reportingGeneration = ++nextReportingGeneration,
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
            reportingGeneration = reportingGeneration,
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
            reportingGeneration = reportingGeneration,
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
    val reportingGeneration: Long,
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

internal fun playbackCredentialKey(cookie: String): Long? =
    if (cookie.isBlank()) null else cookieFingerprint(cookie)

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

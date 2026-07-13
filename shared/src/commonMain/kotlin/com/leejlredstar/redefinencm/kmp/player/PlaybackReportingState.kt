package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.PlaybackAccountComparison
import com.leejlredstar.redefinencm.kmp.data.PlaybackReportDetails
import com.leejlredstar.redefinencm.kmp.data.PlaybackReportEndpoint
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserLevelResponse

enum class PlaybackReportingKind {
    RELAY,
    SCROBBLE,
}

enum class PlaybackReportingPhase {
    SENDING,
    ACCEPTED,
    VERIFYING,
    ACCOUNT_VERIFIED,
    NOT_REFLECTED,
    REJECTED,
    UNSUPPORTED,
    TRANSPORT_FAILURE,
    ACCOUNT_CHANGED,
}

/** Credential-free, in-memory diagnostics for the latest outbound playback actions. */
data class PlaybackReportingStatus(
    val kind: PlaybackReportingKind,
    val songId: Long,
    val credentialKey: Long,
    val reportingGeneration: Long,
    val phase: PlaybackReportingPhase,
    val endpoint: PlaybackReportEndpoint? = null,
    val httpStatus: Int? = null,
    val serverCode: Int? = null,
    val message: String? = null,
    val details: PlaybackReportDetails? = null,
    val accountComparison: PlaybackAccountComparison? = null,
)

data class PlaybackReportingState(
    val relay: PlaybackReportingStatus? = null,
    val scrobble: PlaybackReportingStatus? = null,
)

internal fun PlaybackReportingState.withStatus(
    status: PlaybackReportingStatus,
): PlaybackReportingState {
    val previous = when (status.kind) {
        PlaybackReportingKind.RELAY -> relay
        PlaybackReportingKind.SCROBBLE -> scrobble
    }
    if (previous != null && previous.reportingGeneration > status.reportingGeneration) {
        return this
    }
    return when (status.kind) {
        PlaybackReportingKind.RELAY -> copy(relay = status)
        PlaybackReportingKind.SCROBBLE -> copy(scrobble = status)
    }
}

internal fun PlaybackReportingState.forCredential(
    credentialKey: Long?,
): PlaybackReportingState = if (credentialKey == null) {
    PlaybackReportingState()
} else {
    PlaybackReportingState(
        relay = relay?.takeIf { it.credentialKey == credentialKey },
        scrobble = scrobble?.takeIf { it.credentialKey == credentialKey },
    )
}

/** One-shot event used to refresh account UI after a credential-bound readback. */
data class PlaybackAccountVerificationEvent(
    val uid: Long,
    val credentialKey: Long,
    val reportingGeneration: Long,
    val songId: Long,
    val userLevel: UserLevelResponse?,
    val comparison: PlaybackAccountComparison,
)

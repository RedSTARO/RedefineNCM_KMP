package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.api.dto.RecentSongsResponse
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserLevelResponse
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserRecordResponse

enum class PlaybackReportEndpoint {
    SCROBBLE_V1,
    WEBLOG,
    RELAY,
}

enum class PlaybackReportRejectionReason {
    SERVER_REJECTED,
    INVALID_INPUT,
}

enum class PlaybackReportFailureReason {
    REQUEST_FAILED,
    INVALID_RESPONSE,
}

/** Bounded, credential-free response context suitable for in-memory diagnostics. */
data class PlaybackReportDetails(
    val data: String? = null,
    val message: String? = null,
    val serverDetails: String? = null,
    val startplayCode: Int? = null,
    val playCode: Int? = null,
)

sealed interface PlaybackReportResult {
    val endpoint: PlaybackReportEndpoint
    val httpStatus: Int?

    data class Accepted(
        override val endpoint: PlaybackReportEndpoint,
        override val httpStatus: Int,
        val serverCode: Int,
        val details: PlaybackReportDetails = PlaybackReportDetails(),
    ) : PlaybackReportResult

    data class Rejected(
        override val endpoint: PlaybackReportEndpoint,
        override val httpStatus: Int? = null,
        val serverCode: Int? = null,
        val reason: PlaybackReportRejectionReason = PlaybackReportRejectionReason.SERVER_REJECTED,
        val details: PlaybackReportDetails = PlaybackReportDetails(),
    ) : PlaybackReportResult

    data class Unsupported(
        override val endpoint: PlaybackReportEndpoint,
        override val httpStatus: Int = 404,
        val htmlResponse: Boolean = false,
    ) : PlaybackReportResult

    data class TransportFailure(
        override val endpoint: PlaybackReportEndpoint,
        override val httpStatus: Int? = null,
        val reason: PlaybackReportFailureReason,
    ) : PlaybackReportResult
}

data class PlaybackAccountSnapshot(
    val uid: Long,
    val userLevel: UserLevelResponse?,
    val weeklyRecord: UserRecordResponse?,
    val recentSongs: RecentSongsResponse?,
)

data class PlaybackAccountComparison(
    val sameAccount: Boolean,
    val recordAppeared: Boolean,
    val recordPlayCountDelta: Long?,
    val recordScoreDelta: Long?,
    val recentBecameLatest: Boolean,
    val recentPlayTimeAdvanced: Boolean,
    val levelPlayCountDelta: Long?,
    val accountEffectObserved: Boolean,
)

fun comparePlaybackAccountSnapshots(
    before: PlaybackAccountSnapshot,
    after: PlaybackAccountSnapshot,
    songId: Long,
): PlaybackAccountComparison {
    require(songId > 0) { "songId must be positive" }
    val sameAccount = before.uid > 0 && before.uid == after.uid
    if (!sameAccount) {
        return PlaybackAccountComparison(
            sameAccount = false,
            recordAppeared = false,
            recordPlayCountDelta = null,
            recordScoreDelta = null,
            recentBecameLatest = false,
            recentPlayTimeAdvanced = false,
            levelPlayCountDelta = null,
            accountEffectObserved = false,
        )
    }

    val beforeRecords = before.weeklyRecord?.weekData
    val afterRecords = after.weeklyRecord?.weekData
    val beforeRecord = beforeRecords?.firstOrNull { it.song.id == songId }
    val afterRecord = afterRecords?.firstOrNull { it.song.id == songId }
    val recordAppeared = beforeRecords != null && afterRecords != null &&
        beforeRecord == null && afterRecord != null
    val recordPlayCountDelta = if (beforeRecords != null && afterRecord != null) {
        afterRecord.playCount - (beforeRecord?.playCount ?: 0L)
    } else {
        null
    }
    val recordScoreDelta = if (beforeRecords != null && afterRecord != null) {
        afterRecord.score - (beforeRecord?.score ?: 0L)
    } else {
        null
    }

    val beforeRecent = before.recentSongs?.data?.list
    val afterRecent = after.recentSongs?.data?.list
    val beforeRecentEntry = beforeRecent?.firstOrNull { it.resourceId == songId }
    val afterRecentEntry = afterRecent?.firstOrNull { it.resourceId == songId }
    val recentBecameLatest = beforeRecent != null && afterRecent != null &&
        beforeRecent.firstOrNull()?.resourceId != songId && afterRecent.firstOrNull()?.resourceId == songId
    val recentPlayTimeAdvanced = beforeRecent != null && afterRecentEntry != null &&
        afterRecentEntry.playTime > (beforeRecentEntry?.playTime ?: Long.MIN_VALUE)

    val beforeLevel = before.userLevel?.data
    val afterLevel = after.userLevel?.data
    val levelPlayCountDelta = if (
        beforeLevel?.userId == before.uid && afterLevel?.userId == after.uid
    ) {
        afterLevel.nowPlayCount - beforeLevel.nowPlayCount
    } else {
        null
    }
    val observed = recordAppeared ||
        (recordPlayCountDelta ?: 0L) > 0L ||
        recentBecameLatest ||
        recentPlayTimeAdvanced

    return PlaybackAccountComparison(
        sameAccount = true,
        recordAppeared = recordAppeared,
        recordPlayCountDelta = recordPlayCountDelta,
        recordScoreDelta = recordScoreDelta,
        recentBecameLatest = recentBecameLatest,
        recentPlayTimeAdvanced = recentPlayTimeAdvanced,
        levelPlayCountDelta = levelPlayCountDelta,
        accountEffectObserved = observed,
    )
}

package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.api.dto.RecentSongEntry
import com.leejlredstar.redefinencm.kmp.data.api.dto.RecentSongsData
import com.leejlredstar.redefinencm.kmp.data.api.dto.RecentSongsResponse
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserLevelData
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserLevelResponse
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserRecordEntry
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserRecordResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackReportingModelsTest {
    @Test
    fun accountSnapshotComparisonDetectsRecordRecentAndLevelChanges() {
        val before = snapshot(
            nowPlayCount = 10,
            records = emptyList(),
            recent = listOf(1L to 100L),
        )
        val after = snapshot(
            nowPlayCount = 11,
            records = listOf(UserRecordEntry(song = SongDetailSongs(id = 42), playCount = 1, score = 100)),
            recent = listOf(42L to 200L, 1L to 100L),
        )

        val comparison = comparePlaybackAccountSnapshots(before, after, songId = 42)

        assertTrue(comparison.sameAccount)
        assertTrue(comparison.recordAppeared)
        assertEquals(1L, comparison.recordPlayCountDelta)
        assertEquals(100L, comparison.recordScoreDelta)
        assertTrue(comparison.recentBecameLatest)
        assertTrue(comparison.recentPlayTimeAdvanced)
        assertEquals(1L, comparison.levelPlayCountDelta)
        assertTrue(comparison.accountEffectObserved)
    }

    @Test
    fun accountSnapshotComparisonRejectsCrossAccountEvidence() {
        val before = snapshot(nowPlayCount = 10, uid = 42)
        val after = snapshot(nowPlayCount = 11, uid = 99)

        val comparison = comparePlaybackAccountSnapshots(before, after, songId = 42)

        assertFalse(comparison.sameAccount)
        assertFalse(comparison.accountEffectObserved)
        assertEquals(null, comparison.levelPlayCountDelta)
    }

    @Test
    fun accountSnapshotComparisonDoesNotTreatLevelOnlyChangeAsSongEvidence() {
        val before = snapshot(nowPlayCount = 10)
        val after = snapshot(nowPlayCount = 11)

        val comparison = comparePlaybackAccountSnapshots(before, after, songId = 42)

        assertEquals(1L, comparison.levelPlayCountDelta)
        assertFalse(comparison.accountEffectObserved)
    }

    @Test
    fun accountSnapshotComparisonDoesNotTreatScoreOnlyChangeAsPlayEvidence() {
        val before = snapshot(
            nowPlayCount = 10,
            records = listOf(
                UserRecordEntry(song = SongDetailSongs(id = 42), playCount = 1, score = 80),
            ),
        )
        val after = snapshot(
            nowPlayCount = 10,
            records = listOf(
                UserRecordEntry(song = SongDetailSongs(id = 42), playCount = 1, score = 90),
            ),
        )

        val comparison = comparePlaybackAccountSnapshots(before, after, songId = 42)

        assertEquals(10L, comparison.recordScoreDelta)
        assertFalse(comparison.accountEffectObserved)
    }

    private fun snapshot(
        nowPlayCount: Long,
        uid: Long = 42,
        records: List<UserRecordEntry> = emptyList(),
        recent: List<Pair<Long, Long>> = emptyList(),
    ): PlaybackAccountSnapshot = PlaybackAccountSnapshot(
        uid = uid,
        userLevel = UserLevelResponse(
            code = 200,
            data = UserLevelData(userId = uid, nowPlayCount = nowPlayCount),
        ),
        weeklyRecord = UserRecordResponse(code = 200, weekData = records),
        recentSongs = RecentSongsResponse(
            code = 200,
            data = RecentSongsData(
                list = recent.map { (songId, playTime) ->
                    RecentSongEntry(
                        resourceId = songId,
                        playTime = playTime,
                        data = SongDetailSongs(id = songId),
                    )
                },
            ),
        ),
    )
}

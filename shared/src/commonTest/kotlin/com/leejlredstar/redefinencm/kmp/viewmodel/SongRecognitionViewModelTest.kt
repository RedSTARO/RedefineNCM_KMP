package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.api.dto.AudioMatch
import com.leejlredstar.redefinencm.kmp.data.api.dto.AudioMatchData
import com.leejlredstar.redefinencm.kmp.data.api.dto.AudioMatchResult
import com.leejlredstar.redefinencm.kmp.data.api.dto.AudioMatchSong
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongAlbum
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongArtist
import com.leejlredstar.redefinencm.kmp.player.InMemoryPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.recognition.CapturedPcm
import com.leejlredstar.redefinencm.kmp.recognition.MicrophoneRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SongRecognitionViewModelTest {
    @Test
    fun successfulRecognitionPausesPlaybackAndPublishesPlayableResults() = runTest {
        val player = InMemoryPlatformPlayer(scope = backgroundScope)
        player.setQueue(listOf(MediaInfo(id = "old", title = "Old", artist = "Artist")))
        player.play()
        val matchRequests = mutableListOf<Pair<Int, String>>()
        val viewModel = createViewModel(
            player = player,
            matchAudio = { durationSeconds, fingerprint ->
                matchRequests += durationSeconds to fingerprint
                matchedResponse(101L, 202L)
            },
        )

        viewModel.beginPermissionRequest()
        assertIs<SongRecognitionUiState.RequestingPermission>(viewModel.uiState.value)
        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertFalse(player.isPlaying.value)
        assertEquals(listOf(3 to "test-fingerprint"), matchRequests)
        val results = assertIs<SongRecognitionUiState.Results>(viewModel.uiState.value)
        assertEquals(listOf(101L, 202L), results.matches.map { it.song.id })

        viewModel.play(results.matches.first())
        assertEquals(listOf("101"), player.queueSnapshot.value.items.map { it.id })
        assertTrue(player.isPlaying.value)

        viewModel.addToQueue(results.matches.last())
        assertEquals(listOf("101", "202"), player.queueSnapshot.value.items.map { it.id })
        player.release()
    }

    @Test
    fun cancelledRecognitionIgnoresLateNetworkResult() = runTest {
        val player = InMemoryPlatformPlayer(scope = backgroundScope)
        val matchStarted = CompletableDeferred<Unit>()
        val releaseMatch = CompletableDeferred<AudioMatch?>()
        val viewModel = createViewModel(
            player = player,
            matchAudio = { _, _ ->
                matchStarted.complete(Unit)
                withContext(NonCancellable) { releaseMatch.await() }
            },
        )

        viewModel.beginPermissionRequest()
        viewModel.onPermissionResult(granted = true)
        runCurrent()
        matchStarted.await()

        viewModel.cancelRecognition()
        assertIs<SongRecognitionUiState.Idle>(viewModel.uiState.value)
        releaseMatch.complete(matchedResponse(999L))
        advanceUntilIdle()

        assertIs<SongRecognitionUiState.Idle>(viewModel.uiState.value)
        assertTrue(player.queueSnapshot.value.items.isEmpty())
        player.release()
    }

    @Test
    fun responseClassificationKeepsNoMatchAndMissingDataSeparate() {
        val noMatch = classifyAudioMatch(
            AudioMatch(
                code = 200,
                data = AudioMatchData(type = 0, noMatchReason = 10),
            ),
        )
        val protocolFailure = classifyAudioMatch(AudioMatch(code = 200, data = null))

        assertEquals(10, assertIs<AudioMatchOutcome.NoMatch>(noMatch).reason)
        assertIs<AudioMatchOutcome.Error>(protocolFailure)
    }

    private fun TestScope.createViewModel(
        player: InMemoryPlatformPlayer,
        matchAudio: suspend (Int, String) -> AudioMatch?,
    ): SongRecognitionViewModel = SongRecognitionViewModel(
        recorder = object : MicrophoneRecorder {
            override suspend fun capture(
                durationMillis: Long,
                onProgress: (elapsedMillis: Long, level: Float) -> Unit,
            ): CapturedPcm {
                onProgress(durationMillis / 2L, 0.5f)
                return CapturedPcm(FloatArray(24_000), 8_000)
            }
        },
        player = player,
        matchAudio = matchAudio,
        prepareSamples = { captured, _ -> captured.monoSamples },
        generateFingerprint = { "test-fingerprint" },
        processingDispatcher = StandardTestDispatcher(testScheduler),
        scope = this,
    )

    private fun matchedResponse(vararg ids: Long): AudioMatch = AudioMatch(
        code = 200,
        data = AudioMatchData(
            type = 1,
            queryId = "query-id",
            result = ids.mapIndexed { index, id ->
                AudioMatchResult(
                    startTime = index * 1_000L,
                    song = AudioMatchSong(
                        id = id,
                        name = "Song $id",
                        artists = listOf(SongArtist(id = id, name = "Artist $id")),
                        album = SongAlbum(
                            id = id,
                            name = "Album $id",
                            picUrl = "https://example.test/$id.jpg",
                        ),
                        duration = 180_000L,
                    ),
                )
            },
            noMatchReason = 0,
        ),
    )
}

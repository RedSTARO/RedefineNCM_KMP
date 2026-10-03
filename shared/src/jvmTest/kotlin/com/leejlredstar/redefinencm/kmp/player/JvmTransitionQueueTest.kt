package com.leejlredstar.redefinencm.kmp.player

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.transition.TransitionKind
import com.leejlredstar.redefinencm.kmp.transition.TransitionPlan
import com.leejlredstar.redefinencm.kmp.transition.*
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

/** Exercises the real renderer and the player's queue/arming boundary without opening speakers. */
class JvmTransitionQueueTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theCoordinatorReplansForAdjacentEqualQueueEntries() = runTest {
        val node = "transition-coordinator-test-${UUID.randomUUID()}"
        val settings = PlatformSettings(node)
        settings.setString(SettingKeys.SONG_TRANSITION_MODE, SongTransitionMode.CROSSFADE.wireValue)
        val base = InMemoryPlatformPlayer(scope = backgroundScope)
        val armedIndexes = mutableListOf<Int>()
        val player = object : PlatformPlayer by base {
            override val transitionCapability = TransitionCapability.CROSSFADE
            override fun armTransition(plan: TransitionPlan) { armedIndexes += currentIndex.value }
        }
        base.restoreQueue(List(3) { MediaInfo("same", "Same", "Test", duration = 180_000) }, 0, 0)
        val analyzer = TrackAnalyzer(
            TrackEndsDecoder { _, _, _ -> error("Plain crossfade does not analyze audio") },
            AnalysisUrlResolver { null }, UnavailableBeatModelLoader("not used"),
            StandardTestDispatcher(testScheduler),
        )
        val coordinator = SongTransitionCoordinator(player, analyzer, settings, StandardTestDispatcher(testScheduler))
        try {
            runCurrent()
            assertEquals(listOf(0), armedIndexes)
            base.skipToIndex(1)
            runCurrent()
            assertEquals(listOf(0, 1), armedIndexes)
        } finally {
            (SongTransitionCoordinator::class.java.getDeclaredField("scope").apply { isAccessible = true }
                .get(coordinator) as CoroutineScope).cancel()
            base.release()
            Preferences.userRoot().node(node).removeNode()
        }
    }

    @Test
    fun abandoningABlendKeepsTheAudibleOccurrenceWhenBothIdsMatch() {
        val directory = Files.createTempDirectory("transition-abandon-test").toFile()
        try {
            val files = List(2) { wave(directory, it) }
            for (afterSwap in listOf(false, true)) {
                val clock = PlaybackClock(Rate)
                DeckMixer(FfmpegAudioSource.open(files[0].toURI().toString(), 0, Rate, 1), "same", 0, clock).use { mixer ->
                    val plan = TransitionPlan("same", "same", TransitionKind.CROSSFADE, 6_000, 6_000, 0, 4_000, 1.0, 2_000)
                    mixer.armedPlan = plan
                    mixer.offerIncoming(plan, FfmpegAudioSource.open(files[1].toURI().toString(), 0, Rate, 1))
                    val block = FloatArray(512)
                    var frames = 0L
                    while (frames < (if (afterSwap) 9 else 7) * Rate) frames += mixer.render(block, block.size)
                    val selected = clock.at(frames)!!.deckSequence
                    mixer.abandonBlend(selected)
                    repeat(10) { frames += mixer.render(block, block.size) }
                    assertEquals(selected, clock.at(frames)!!.deckSequence)
                    val expected = (if (afterSwap) 4_000f else 2_000f) / 32_768f
                    assertTrue(abs(block.last() - expected) < 0.0001f, "The wrong occurrence remained audible")
                }
            }
        } finally { assertTrue(directory.deleteRecursively()) }
    }

    @Test
    fun threeTracksCanCrossfadeConsecutively() = renderQueue(listOf("first", "second", "third"))

    @Test
    fun consecutiveOccurrencesOfOneSongStillAdvanceTheQueueTwice() = renderQueue(listOf("same", "same", "same"))

    private fun renderQueue(ids: List<String>) {
        val directory = Files.createTempDirectory("transition-queue-test").toFile()
        val node = "transition-queue-test-${UUID.randomUUID()}"
        val settings = PlatformSettings(node)
        val client = HttpClient(MockEngine { error("No network is needed for synthetic audio") })
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val files = ids.indices.map { wave(directory, it) }
        val player = JvmMediaPlayer(
            Repository(NCMApi(client), AppDatabase(driver)), settings, MusicProviderRegistry(emptyList(), settings),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            incomingResolver = StreamUrlResolver { mediaId -> files[ids.indexOf(mediaId)].toURI().toString() },
        )
        try {
            player.restoreQueue(ids.map { MediaInfo(it, it, "Test", duration = 12_000L) }, 0, 0L)
            val initialOccurrence = player.playbackOccurrence.value
            val clock = PlaybackClock(Rate)
            val mixer = DeckMixer(FfmpegAudioSource.open(files[0].toURI().toString(), 0, Rate, 1), ids[0], 0, clock)
            // Inject the output side instead of a SourceDataLine: no real audio device is touched.
            // Everything after this uses actual clock publication and the public arming API.
            JvmMediaPlayer::class.java.getDeclaredField("mixer").apply { isAccessible = true }.set(player, mixer)
            val generation = JvmMediaPlayer::class.java.getDeclaredField("playbackGeneration")
                .apply { isAccessible = true }.getLong(player)
            val publish = JvmMediaPlayer::class.java.getDeclaredMethod("onClockReading", ClockReading::class.java, Long::class.javaPrimitiveType)
                .apply { isAccessible = true }
            val prepare = JvmMediaPlayer::class.java.getDeclaredMethod(
                "prepareIncoming", Long::class.javaPrimitiveType, DeckMixer::class.java, TransitionPlan::class.java,
            ).apply { isAccessible = true }
            publish.invoke(player, ClockReading(ids[0], 99_999L), generation - 1)
            assertEquals(0L, player.position.value, "A previous playback session cannot overwrite position")
            fun plan(index: Int) = TransitionPlan(
                ids[index], ids[index + 1], TransitionKind.CROSSFADE,
                6_000L, 6_000L, 0L, 4_000L, 1.0, 2_000L,
            )
            player.armTransition(plan(0))
            val block = FloatArray(512)
            var outputFrames = 0L
            var selected = 0
            var preparations = 0
            while (outputFrames < Rate * 40L) {
                val frames = mixer.render(block, block.size)
                if (frames == 0) break
                outputFrames += frames
                publish.invoke(player, clock.at(outputFrames)!!, generation)
                if (selected != player.currentIndex.value) {
                    selected = player.currentIndex.value
                    if (selected < ids.lastIndex) player.armTransition(plan(selected)) else player.disarmTransition()
                }
                mixer.planNeedingIncoming(3_000L)?.let { wanted ->
                    val next = player.currentIndex.value + 1
                    assertTrue(next < files.size, "A stale plan requested audio after the last queue item")
                    preparations++
                    prepare.invoke(player, generation, mixer, wanted)
                    runBlocking {
                        withTimeout(10_000L) {
                            while (mixer.planNeedingIncoming(3_000L) != null) delay(5)
                        }
                    }
                }
            }
            assertEquals(2, player.currentIndex.value, "Both handovers must reach the player's queue")
            assertEquals(initialOccurrence + 2, player.playbackOccurrence.value)
            assertEquals(2, preparations)
            assertTrue(outputFrames < Rate * 40L, "The renderer must finish the three-track queue")
        } finally {
            player.release()
            driver.close()
            client.close()
            Preferences.userRoot().node(node).removeNode()
            assertTrue(directory.deleteRecursively())
        }
    }

    private fun wave(directory: File, index: Int): File {
        val pcm = ByteArray(Rate * 12 * 2)
        val sample = (index + 1) * 2_000
        for (frame in 0 until Rate * 12) {
            pcm[frame * 2] = sample.toByte()
            pcm[frame * 2 + 1] = (sample shr 8).toByte()
        }
        val format = AudioFormat(Rate.toFloat(), 16, 1, true, false)
        return directory.resolve("$index.wav").also { file ->
            AudioInputStream(ByteArrayInputStream(pcm), format, (pcm.size / 2).toLong()).use {
                AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
            }
        }
    }

    private companion object { const val Rate = 8_000 }
}

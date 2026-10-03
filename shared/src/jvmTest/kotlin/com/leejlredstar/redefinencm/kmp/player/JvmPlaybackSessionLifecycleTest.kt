package com.leejlredstar.redefinencm.kmp.player

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.prefs.Preferences
import javax.sound.sampled.AudioFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmPlaybackSessionLifecycleTest {
    private fun withPlayer(block: (JvmMediaPlayer) -> Unit) {
        val node = "playback-lifecycle-test-${UUID.randomUUID()}"
        val settings = PlatformSettings(node)
        val client = HttpClient(MockEngine { error("No provider requests in this fixture") })
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val player = JvmMediaPlayer(
            Repository(NCMApi(client), AppDatabase(driver)), settings, MusicProviderRegistry(emptyList(), settings),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        try { block(player) } finally {
            player.release()
            driver.close()
            client.close()
            Preferences.userRoot().node(node).removeNode()
        }
    }

    @Test
    fun aCancelledPlaybackSessionNeverOpensItsUrl() = withPlayer { player ->
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/cancelled.wav") { exchange ->
            requests.incrementAndGet()
            // Even the old implementation fails before it could open a real audio output device.
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()
        try {
            val media = MediaInfo("one", "Test", "Test")
            player.restoreQueue(listOf(media), 0, 0L)
            val generation = JvmMediaPlayer::class.java.getDeclaredField("playbackGeneration")
                .apply { isAccessible = true }.getLong(player)
            JvmMediaPlayer::class.java.getDeclaredMethod(
                "runPlayback", Long::class.javaPrimitiveType, MediaInfo::class.java, String::class.java, Long::class.javaPrimitiveType,
            ).apply { isAccessible = true }.invoke(
                player, generation - 1, media, "http://127.0.0.1:${server.address.port}/cancelled.wav", 0L,
            )
            assertEquals(0, requests.get())
        } finally { server.stop(0) }
    }

    @Test
    fun stoppingDoesNotCloseABusyDecoderOnTheCallingThread() = withPlayer { player ->
        val readFinished = CountDownLatch(1)
        val closeMayFinish = CountDownLatch(1)
        val workerStarted = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        val closedBy = AtomicReference<String?>(null)
        val stopFailure = AtomicReference<Throwable?>(null)
        val grabber = object : FFmpegFrameGrabber("unused-fixture") {
            override fun stop() {
                closedBy.set(Thread.currentThread().name)
                closeMayFinish.await()
            }
            override fun release() = Unit
        }
        val source = FfmpegAudioSource::class.java.declaredConstructors.single { it.parameterCount == 3 }
            .apply { isAccessible = true }
            .newInstance(grabber, AudioFormat(8_000f, 16, 1, true, false), 12_000L) as FfmpegAudioSource
        val mixer = DeckMixer(source, "one", 0L, PlaybackClock(8_000))
        val owner = Thread({
            workerStarted.countDown()
            // Model a native read that does not react to Java's interrupt flag.
            while (readFinished.count > 0) {
                try { readFinished.await() } catch (_: InterruptedException) { }
            }
            mixer.close()
        }, "simulated-decoder-owner").apply { isDaemon = true }
        val caller = Thread({
            try { player.release() } catch (failure: Throwable) { stopFailure.set(failure) }
            finally { stopReturned.countDown() }
        }, "playback-control").apply { isDaemon = true }
        JvmMediaPlayer::class.java.getDeclaredField("mixer").apply { isAccessible = true }.set(player, mixer)
        JvmMediaPlayer::class.java.getDeclaredField("playbackThread").apply { isAccessible = true }.set(player, owner)
        owner.start()
        try {
            assertTrue(workerStarted.await(5, TimeUnit.SECONDS))
            caller.start()
            assertTrue(stopReturned.await(1, TimeUnit.SECONDS), "Stopping waited on native decoder cleanup")
            assertNull(closedBy.get(), "The decoder is still owned by the blocked worker")
        } finally {
            readFinished.countDown()
            closeMayFinish.countDown()
            caller.join(5_000)
            owner.join(5_000)
        }
        assertNull(stopFailure.get())
        assertEquals(owner.name, closedBy.get())
    }
}

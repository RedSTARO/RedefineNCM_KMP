package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestorer
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class PlaybackStartupWiringTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theRealDependencyGraphWaitsForTheRestorerBeforeCreatingPlayback() = runBlocking {
        val tasks = LinkedBlockingQueue<Runnable>()
        Dispatchers.setMain(object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        })
        val nodeName = "startup-test-${UUID.randomUUID()}"
        val settings = PlatformSettings(nodeName)
        var playersCreated = 0
        val app = koinApplication {
            modules(sharedModule, module {
                single { settings }
                single<HttpClient> { error("HTTP client created outside the restorer") }
                single<PlatformPlayer> {
                    playersCreated++
                    // Stop before any audio device or repository is opened in this wiring test.
                    error("stopped at player construction")
                }
            })
        }
        try {
            // With either old eager coordinator this resolves Repository/HttpClient or player
            // synchronously here, bypassing the restorer's settings and Main-thread gate.
            app.createEagerInstances()
            assertEquals(0, playersCreated)
            assertNotNull(tasks.poll(5, TimeUnit.SECONDS)).run()
            val result = withTimeout(5_000) { app.koin.get<PlayerStatusRestorer>().awaitRestored() }
            assertIs<PlayerStatusRestoreState.Failed>(result)
            assertEquals(1, playersCreated)
        } finally {
            app.close()
            Preferences.userRoot().node(nodeName).removeNode()
            Dispatchers.resetMain()
        }
    }
}

package com.leejlredstar.redefinencm.kmp.smtc

import com.leejlredstar.redefinencm.kmp.player.InMemoryPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import java.awt.EventQueue
import java.awt.Frame
import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

class DesktopMediaControlsTest {
    @Test
    fun selectsTheNativeBackendForEverySupportedDesktopOs() {
        assertEquals(DesktopTransportKind.WindowsSmtc, desktopTransportKind("Windows 11"))
        assertEquals(DesktopTransportKind.LinuxMpris, desktopTransportKind("Linux"))
        assertEquals(DesktopTransportKind.MacOsNowPlaying, desktopTransportKind("Mac OS X"))
        assertEquals(DesktopTransportKind.MacOsNowPlaying, desktopTransportKind("Darwin"))
        assertEquals(DesktopTransportKind.Unsupported, desktopTransportKind("FreeBSD"))
    }

    @Test
    fun windowsSmtcCreatesANativeSessionForARealTopLevelWindow() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return

        val player = InMemoryPlatformPlayer(tickerIntervalMs = 60_000L)
        val frame = createOffscreenTestFrame()
        val controls = WindowsMediaControls(player)
        try {
            // Compose Desktop starts/stops the binding on the AWT event thread. The implementation
            // then owns one dedicated MTA thread for native creation, publication, and release.
            EventQueue.invokeAndWait { controls.start(frame) }
            awaitCondition { controls.status.value != WindowsMediaControls.IntegrationStatus.NotStarted }
            assertEquals(
                WindowsMediaControls.IntegrationStatus.Forwarding,
                controls.status.value,
                "Windows SMTC failed to bind to the test HWND",
            )

            val expectedMetadata = MediaControlMetadata(
                title = "Windows SMTC Test",
                artist = "RedefineNCM",
                album = "Native Integration",
                duration = 120_000L,
                position = 4_000L,
                isPlaying = true,
            )
            MediaControlsIntegrator.updateMetadata(
                title = expectedMetadata.title,
                artist = expectedMetadata.artist,
                album = expectedMetadata.album,
                duration = expectedMetadata.duration,
                position = expectedMetadata.position,
                isPlaying = expectedMetadata.isPlaying,
            )
            awaitCondition { controls.lastPublishedMetadata == expectedMetadata }
            assertEquals(
                WindowsMediaControls.IntegrationStatus.Forwarding,
                controls.status.value,
                controls.lastError.value.orEmpty(),
            )
        } finally {
            EventQueue.invokeAndWait {
                controls.stop()
                frame.dispose()
            }
            MediaControlsIntegrator.clear()
            player.release()
        }
    }

    @Test
    fun exportsMprisPropertiesAndRoutesRemoteCommandsWhenSessionBusIsAvailable() {
        if (!System.getProperty("os.name").contains("Linux", ignoreCase = true)) return
        if (System.getenv("DBUS_SESSION_BUS_ADDRESS").isNullOrBlank()) return

        val player = InMemoryPlatformPlayer(tickerIntervalMs = 60_000L)
        val processId = ProcessHandle.current().pid() + (System.nanoTime() % 100_000L).absoluteValue + 1L
        val backend = LinuxMprisMediaControls(player, processId)
        var client: org.freedesktop.dbus.connections.impl.DBusConnection? = null
        try {
            player.setQueue(
                listOf(
                    MediaInfo("101", "First", "Artist", "Album", duration = 120_000L),
                    MediaInfo("102", "Second", "Artist", "Album", duration = 180_000L),
                ),
            )
            MediaControlsIntegrator.updateMetadata(
                title = "First",
                artist = "Artist",
                album = "Album",
                duration = 120_000L,
                position = 4_000L,
                isPlaying = true,
            )
            backend.startForSmokeTest()
            awaitCondition { backend.status.value == DesktopMediaControlsStatus.Forwarding }

            client = DBusConnectionBuilder.forSessionBus().build()
            val properties = client.getRemoteObject(
                backend.busName,
                MPRIS_OBJECT_PATH,
                Properties::class.java,
            )
            val remote = client.getRemoteObject(
                backend.busName,
                MPRIS_OBJECT_PATH,
                MprisPlayer::class.java,
            )

            awaitCondition {
                properties.GetAll(MPRIS_PLAYER_INTERFACE)["PlaybackStatus"]?.value == "Playing"
            }
            val metadata = properties.GetAll(MPRIS_PLAYER_INTERFACE)["Metadata"]?.value
                as Map<String, Variant<*>>
            assertEquals("First", metadata.getValue("xesam:title").value)
            assertEquals(120_000_000L, metadata.getValue("mpris:length").value)
            val trackPath = metadata.getValue("mpris:trackid").value as DBusPath

            remote.Pause()
            awaitCondition { !player.isPlaying.value }
            remote.Play()
            awaitCondition { player.isPlaying.value }

            remote.SetPosition(trackPath, 12_000_000L)
            awaitCondition { player.position.value == 12_000L }
            remote.Seek(-2_000_000L)
            awaitCondition { player.position.value == 10_000L }

            remote.Next()
            awaitCondition { player.queueSnapshot.value.currentMedia?.id == "102" }
            remote.Previous()
            awaitCondition { player.queueSnapshot.value.currentMedia?.id == "101" }

            properties.Set(MPRIS_PLAYER_INTERFACE, "Volume", Variant(0.4))
            awaitCondition { player.volume.value in 0.39f..0.41f }
            properties.Set(MPRIS_PLAYER_INTERFACE, "Shuffle", Variant(true))
            awaitCondition { player.shuffleEnabled.value }
        } finally {
            runCatching { client?.close() }
            backend.stop()
            MediaControlsIntegrator.clear()
            player.release()
        }
    }

    @Test
    fun macOsNativeBridgeStartsAndPublishesMetadataOnMacHost() {
        val osName = System.getProperty("os.name")
        if (!osName.contains("Mac", ignoreCase = true) && !osName.contains("Darwin", ignoreCase = true)) return

        val player = InMemoryPlatformPlayer(tickerIntervalMs = 60_000L)
        val backend = MacOsMediaControls(player)
        try {
            player.setQueue(listOf(MediaInfo("201", "Mac Track", "Artist", duration = 90_000L)))
            backend.startForSmokeTest()
            assertEquals(
                DesktopMediaControlsStatus.Forwarding,
                backend.status.value,
                backend.lastError.value.orEmpty(),
            )
            MediaControlsIntegrator.updateMetadata(
                title = "Mac Track",
                artist = "Artist",
                duration = 90_000L,
                position = 5_000L,
                isPlaying = true,
            )
            awaitCondition { backend.status.value == DesktopMediaControlsStatus.Forwarding }
            assertTrue(backend.lastError.value == null, backend.lastError.value.orEmpty())
        } finally {
            backend.stop()
            MediaControlsIntegrator.clear()
            player.release()
        }
    }
}

private fun createOffscreenTestFrame(): Frame {
    var frame: Frame? = null
    EventQueue.invokeAndWait {
        frame = Frame("RedefineNCM SMTC Test").apply {
            setSize(320, 180)
            setLocation(-10_000, -10_000)
            isVisible = true
        }
    }
    return checkNotNull(frame)
}

private fun awaitCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000L
    while (System.nanoTime() < deadline) {
        if (predicate()) return
        Thread.sleep(25L)
    }
    assertTrue(predicate(), "Condition was not met within ${timeoutMs}ms")
}

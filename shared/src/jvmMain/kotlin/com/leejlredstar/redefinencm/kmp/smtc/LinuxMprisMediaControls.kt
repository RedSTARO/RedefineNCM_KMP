package com.leejlredstar.redefinencm.kmp.smtc

import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlayerQueueSnapshot
import java.awt.EventQueue
import java.awt.Window
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

/** MPRIS 2.2 implementation backed by the Linux session D-Bus. */
internal class LinuxMprisMediaControls(
    private val player: PlatformPlayer,
    private val processId: Long = ProcessHandle.current().pid(),
) : DesktopMediaControlsBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow(DesktopMediaControlsStatus.NotStarted)
    override val status: StateFlow<DesktopMediaControlsStatus> = _status.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    internal val busName = "org.mpris.MediaPlayer2.redefinencm.instance$processId"
    private var connection: DBusConnection? = null
    private var collectJob: Job? = null

    override fun start(window: Window) {
        startInternal(window)
    }

    internal fun startForSmokeTest() {
        startInternal(null)
    }

    private fun startInternal(window: Window?) {
        stop()
        try {
            val newConnection = DBusConnectionBuilder.forSessionBus().build()
            try {
                newConnection.requestBusName(busName)
                val service = MprisService(player, window)
                service.attach(newConnection)
                newConnection.exportObject(MPRIS_OBJECT_PATH, service)
                connection = newConnection
                collectJob = scope.launch {
                    combine(
                        MediaControlsIntegrator.metadata,
                        player.queueSnapshot,
                        player.volume,
                    ) { metadata, queue, volume ->
                        MprisState(metadata, queue, volume.toDouble().coerceIn(0.0, 1.0))
                    }.collect(service::update)
                }
                _lastError.value = null
                _status.value = DesktopMediaControlsStatus.Forwarding
            } catch (error: Throwable) {
                runCatching { newConnection.releaseBusName(busName) }
                runCatching { newConnection.close() }
                throw error
            }
        } catch (error: Throwable) {
            val message = "Linux MPRIS initialization failed: ${error.message ?: error::class.simpleName}"
            _lastError.value = message
            _status.value = DesktopMediaControlsStatus.NativeError
            System.err.println(message)
        }
    }

    override fun stop() {
        collectJob?.cancel()
        collectJob = null
        connection?.let { current ->
            runCatching { current.unExportObject(MPRIS_OBJECT_PATH) }
            runCatching { current.releaseBusName(busName) }
            runCatching { current.close() }
        }
        connection = null
        _lastError.value = null
        _status.value = DesktopMediaControlsStatus.NotStarted
    }
}

@DBusInterfaceName(MPRIS_ROOT_INTERFACE)
interface MprisMediaPlayer2 : DBusInterface {
    fun Raise()
    fun Quit()
}

@DBusInterfaceName(MPRIS_PLAYER_INTERFACE)
interface MprisPlayer : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)

    class Seeked(path: String, val position: Long) : DBusSignal(path, position)
}

private data class MprisState(
    val metadata: MediaControlMetadata = MediaControlMetadata(),
    val queue: PlayerQueueSnapshot = PlayerQueueSnapshot(),
    val volume: Double = 1.0,
) {
    val hasTrack: Boolean get() = queue.currentMedia != null || metadata.title.isNotBlank()
    val playbackStatus: String get() = when {
        !hasTrack -> "Stopped"
        metadata.isPlaying -> "Playing"
        else -> "Paused"
    }

    val trackPath: DBusPath?
        get() = if (!hasTrack) null else DBusPath(mprisTrackPath(queue.currentMedia?.id ?: metadata.title))
}

private class MprisService(
    private val player: PlatformPlayer,
    private val window: Window?,
) : MprisMediaPlayer2, MprisPlayer, Properties {
    @Volatile
    private var state = MprisState()

    @Volatile
    private var connection: DBusConnection? = null

    fun attach(connection: DBusConnection) {
        this.connection = connection
    }

    fun update(next: MprisState) {
        val previous = state
        state = next

        val changed = linkedMapOf<String, Variant<*>>()
        if (metadataKey(previous) != metadataKey(next)) {
            changed["Metadata"] = Variant(metadataFor(next), "a{sv}")
        }
        if (previous.playbackStatus != next.playbackStatus) {
            changed["PlaybackStatus"] = Variant(next.playbackStatus)
        }
        if (previous.volume != next.volume) changed["Volume"] = Variant(next.volume)
        if (previous.queue.shuffleEnabled != next.queue.shuffleEnabled) {
            changed["Shuffle"] = Variant(next.queue.shuffleEnabled)
        }
        if (previous.queue != next.queue) {
            changed["CanGoNext"] = Variant(canGoNext(next))
            changed["CanGoPrevious"] = Variant(canGoPrevious(next))
            changed["CanPlay"] = Variant(next.hasTrack)
            changed["CanPause"] = Variant(next.hasTrack)
            changed["CanSeek"] = Variant(next.hasTrack && next.metadata.duration > 0L)
        }
        if (changed.isNotEmpty()) {
            connection?.sendMessage(
                Properties.PropertiesChanged(
                    MPRIS_OBJECT_PATH,
                    MPRIS_PLAYER_INTERFACE,
                    changed,
                    emptyList(),
                ),
            )
        }
    }

    override fun Raise() {
        val targetWindow = window ?: return
        EventQueue.invokeLater {
            targetWindow.isVisible = true
            targetWindow.toFront()
            targetWindow.requestFocus()
        }
    }

    override fun Quit() = Unit
    override fun Next() = player.seekToNext()
    override fun Previous() = player.seekToPrevious()
    override fun Pause() = player.pause()
    override fun PlayPause() = player.togglePlayPause()
    override fun Play() = player.play()

    override fun Stop() {
        player.pause()
        seekTo(0L)
    }

    override fun Seek(offset: Long) {
        val duration = state.metadata.duration.coerceAtLeast(0L)
        // Read the player directly: a client may issue SetPosition followed immediately by Seek,
        // before the shared metadata collector has published the new position.
        val targetMs = player.position.value + offset / MICROSECONDS_PER_MILLISECOND
        when {
            duration > 0L && targetMs > duration -> player.seekToNext()
            else -> seekTo(targetMs.coerceAtLeast(0L))
        }
    }

    override fun SetPosition(trackId: DBusPath, position: Long) {
        val current = state
        if (trackId != current.trackPath || position < 0L) return
        val targetMs = position / MICROSECONDS_PER_MILLISECOND
        val duration = current.metadata.duration.coerceAtLeast(0L)
        if (duration > 0L && targetMs > duration) return
        seekTo(targetMs)
    }

    override fun OpenUri(uri: String) = Unit

    private fun seekTo(targetMs: Long) {
        player.seekTo(targetMs)
        connection?.sendMessage(
            MprisPlayer.Seeked(MPRIS_OBJECT_PATH, targetMs * MICROSECONDS_PER_MILLISECOND),
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A {
        return (propertiesFor(interfaceName)[propertyName]
            ?: throw dbusError("Unknown MPRIS property $interfaceName.$propertyName")) as A
    }

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
        val raw = if (value is Variant<*>) value.value else value
        when (interfaceName) {
            MPRIS_ROOT_INTERFACE -> when (propertyName) {
                "Fullscreen" -> if (raw != false) throw dbusError("Fullscreen is not supported")
                else -> throw dbusError("Property $interfaceName.$propertyName is read-only")
            }
            MPRIS_PLAYER_INTERFACE -> when (propertyName) {
                "LoopStatus" -> if (raw != "None") throw dbusError("Repeat mode is not supported")
                "Rate" -> if ((raw as? Number)?.toDouble() != 1.0) throw dbusError("Only playback rate 1.0 is supported")
                "Shuffle" -> player.setShuffleEnabled(raw as? Boolean ?: throw dbusError("Shuffle must be boolean"))
                "Volume" -> player.setVolume(
                    (raw as? Number)?.toFloat()?.coerceAtLeast(0f)
                        ?: throw dbusError("Volume must be numeric"),
                )
                else -> throw dbusError("Property $interfaceName.$propertyName is read-only")
            }
            else -> throw dbusError("Unknown MPRIS interface $interfaceName")
        }
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = propertiesFor(interfaceName)

    private fun propertiesFor(interfaceName: String): Map<String, Variant<*>> {
        val current = state
        return when (interfaceName) {
            MPRIS_ROOT_INTERFACE -> linkedMapOf(
                "CanQuit" to Variant(false),
                "Fullscreen" to Variant(false),
                "CanSetFullscreen" to Variant(false),
                "CanRaise" to Variant(true),
                "HasTrackList" to Variant(false),
                "Identity" to Variant("RedefineNCM"),
                "DesktopEntry" to Variant("redefinencm"),
                "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
                "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
            )
            MPRIS_PLAYER_INTERFACE -> linkedMapOf(
                "PlaybackStatus" to Variant(current.playbackStatus),
                "LoopStatus" to Variant("None"),
                "Rate" to Variant(1.0),
                "Shuffle" to Variant(current.queue.shuffleEnabled),
                "Metadata" to Variant(metadataFor(current), "a{sv}"),
                "Volume" to Variant(current.volume),
                "Position" to Variant(current.metadata.position.coerceAtLeast(0L) * MICROSECONDS_PER_MILLISECOND),
                "MinimumRate" to Variant(1.0),
                "MaximumRate" to Variant(1.0),
                "CanGoNext" to Variant(canGoNext(current)),
                "CanGoPrevious" to Variant(canGoPrevious(current)),
                "CanPlay" to Variant(current.hasTrack),
                "CanPause" to Variant(current.hasTrack),
                "CanSeek" to Variant(current.hasTrack && current.metadata.duration > 0L),
                "CanControl" to Variant(true),
            )
            else -> throw dbusError("Unknown MPRIS interface $interfaceName")
        }
    }

    override fun isRemote(): Boolean = false
    override fun getObjectPath(): String = MPRIS_OBJECT_PATH
}

private data class MprisMetadataKey(
    val trackPath: DBusPath?,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String,
    val duration: Long,
)

private fun metadataKey(state: MprisState) = MprisMetadataKey(
    trackPath = state.trackPath,
    title = state.metadata.title,
    artist = state.metadata.artist,
    album = state.metadata.album,
    artworkUri = state.metadata.artworkUri,
    duration = state.metadata.duration,
)

private fun metadataFor(state: MprisState): Map<String, Variant<*>> {
    val trackPath = state.trackPath ?: return emptyMap()
    return linkedMapOf<String, Variant<*>>(
        "mpris:trackid" to Variant(trackPath, "o"),
        "mpris:length" to Variant(state.metadata.duration.coerceAtLeast(0L) * MICROSECONDS_PER_MILLISECOND),
        "xesam:title" to Variant(state.metadata.title),
        "xesam:artist" to Variant(listOf(state.metadata.artist).filter(String::isNotBlank), "as"),
        "xesam:album" to Variant(state.metadata.album),
    ).apply {
        state.metadata.artworkUri.takeIf(::isAbsoluteUri)?.let { put("mpris:artUrl", Variant(it)) }
    }
}

private fun canGoNext(state: MprisState): Boolean =
    state.queue.currentIndex in 0 until state.queue.items.lastIndex

private fun canGoPrevious(state: MprisState): Boolean = state.queue.currentIndex > 0

private fun mprisTrackPath(mediaId: String): String {
    val component = mediaId.replace(MPRIS_TRACK_PATH_COMPONENT, "_").ifBlank { "unknown" }
    return "/com/leejlredstar/redefinencm/track/$component"
}

private fun isAbsoluteUri(value: String): Boolean = runCatching { URI(value).isAbsolute }.getOrDefault(false)

private fun dbusError(message: String): DBusExecutionException = DBusExecutionException(message)

internal const val MPRIS_OBJECT_PATH = "/org/mpris/MediaPlayer2"
internal const val MPRIS_ROOT_INTERFACE = "org.mpris.MediaPlayer2"
internal const val MPRIS_PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
private const val MICROSECONDS_PER_MILLISECOND = 1_000L
private val MPRIS_TRACK_PATH_COMPONENT = Regex("[^A-Za-z0-9_]")

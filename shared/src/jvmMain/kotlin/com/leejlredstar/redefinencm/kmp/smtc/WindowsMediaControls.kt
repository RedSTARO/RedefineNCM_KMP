package com.leejlredstar.redefinencm.kmp.smtc

import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Windows System Media Transport Controls (SMTC) integration.
 *
 * Desktop WinRT apps obtain SMTC through `ISystemMediaTransportControlsInterop::GetForWindow`.
 * Compose Desktop gives us a real HWND through JNA, so this binding can stay in JVM code: no
 * bundled helper DLL and no build-time Windows SDK requirement. Session creation, updates, and
 * release stay on one dedicated MTA thread so RoInitialize/RoUninitialize remain balanced.
 */
class WindowsMediaControls(
    private val player: PlatformPlayer,
) {
    enum class IntegrationStatus {
        NotStarted,
        UnsupportedHost,
        Forwarding,
        NativeError,
    }

    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow(IntegrationStatus.NotStarted)
    val status: StateFlow<IntegrationStatus> = _status.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    private val lastPublishedMetadataRef = AtomicReference<MediaControlMetadata?>(null)
    internal val lastPublishedMetadata: MediaControlMetadata?
        get() = lastPublishedMetadataRef.get()

    private var nativeDispatcher: ExecutorCoroutineDispatcher? = null
    private var integrationJob: Job? = null
    private var session: WindowsSmtcSession? = null
    private var lastDisplayKey: DisplayKey? = null
    private var lastPlaybackKey: PlaybackKey? = null

    /** Begin observing shared metadata and forwarding it to Windows SMTC. */
    fun start(window: Window) {
        stop()
        _lastError.value = null
        lastPublishedMetadataRef.set(null)
        if (!isWindows()) {
            _lastError.value = "Windows SMTC is unavailable on ${System.getProperty("os.name")}"
            _status.value = IntegrationStatus.UnsupportedHost
            return
        }

        val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull()
        if (hwnd == null || hwnd == Pointer.NULL) {
            _lastError.value = "Compose Desktop did not expose a valid top-level HWND"
            _status.value = IntegrationStatus.NativeError
            return
        }

        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "RedefineNCM-Windows-SMTC").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        nativeDispatcher = dispatcher
        integrationJob = CoroutineScope(SupervisorJob() + dispatcher).launch {
            try {
                session = WindowsSmtcSession.create(hwnd, ::handleButtonPressed)
                lastDisplayKey = null
                lastPlaybackKey = null
                _lastError.value = null
                _status.value = IntegrationStatus.Forwarding
                MediaControlsIntegrator.metadata.collect(::pushToOs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _lastError.value = "SMTC initialization failed: ${error.message ?: error::class.simpleName}"
                System.err.println(_lastError.value)
                _status.value = IntegrationStatus.NativeError
            } finally {
                closeNativeSession()
            }
        }
    }

    fun stop() {
        val job = integrationJob
        val dispatcher = nativeDispatcher
        integrationJob = null
        nativeDispatcher = null
        if (job != null && dispatcher != null) {
            runBlocking { job.cancelAndJoin() }
            dispatcher.close()
        }
        lastPublishedMetadataRef.set(null)
        _lastError.value = null
        _status.value = IntegrationStatus.NotStarted
    }

    private fun pushToOs(meta: MediaControlMetadata) {
        runCatching {
            val currentSession = session ?: return
            if (meta.title.isBlank() && meta.artist.isBlank()) {
                currentSession.clear()
                lastDisplayKey = null
                lastPlaybackKey = null
            } else {
                val displayKey = DisplayKey(meta.title, meta.artist, meta.album)
                if (displayKey != lastDisplayKey) {
                    currentSession.updateDisplay(meta)
                    lastDisplayKey = displayKey
                }

                val playbackKey = PlaybackKey(
                    isPlaying = meta.isPlaying,
                    durationMs = meta.duration.coerceAtLeast(0L),
                    positionSecond = meta.position.coerceAtLeast(0L) / 1000L,
                )
                if (playbackKey != lastPlaybackKey) {
                    currentSession.updatePlayback(meta)
                    lastPlaybackKey = playbackKey
                }
            }
            lastPublishedMetadataRef.set(meta)
            _lastError.value = null
            _status.value = IntegrationStatus.Forwarding
        }.onFailure { error ->
            _lastError.value = "SMTC update failed: ${error.message ?: error::class.simpleName}"
            System.err.println(_lastError.value)
            _status.value = IntegrationStatus.NativeError
        }
    }

    private fun handleButtonPressed(button: Int) {
        commandScope.launch {
            when (button) {
                BUTTON_PLAY -> player.play()
                BUTTON_PAUSE -> player.pause()
                BUTTON_STOP -> player.pause()
                BUTTON_NEXT -> player.seekToNext()
                BUTTON_PREVIOUS -> player.seekToPrevious()
            }
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").contains("Windows", ignoreCase = true)

    private fun closeNativeSession() {
        val currentSession = session
        session = null
        lastDisplayKey = null
        lastPlaybackKey = null
        runCatching { currentSession?.close() }.onFailure { error ->
            System.err.println("SMTC shutdown failed: ${error.message ?: error::class.simpleName}")
        }
    }

    private data class DisplayKey(
        val title: String,
        val artist: String,
        val album: String,
    )

    private data class PlaybackKey(
        val isPlaying: Boolean,
        val durationMs: Long,
        val positionSecond: Long,
    )

    private class WindowsSmtcSession private constructor(
        private val controls: Pointer,
        private val controls2: Pointer?,
        private val buttonHandler: ButtonPressedHandler,
        private val buttonToken: Long,
        private val roInitialized: Boolean,
    ) {
        private var closed = false

        fun updateDisplay(meta: MediaControlMetadata) {
            enableControls(true)
            val updater = PointerByReference()
            checkHr(comInvokeInt(controls, VTABLE_SMTC_GET_DISPLAY_UPDATER, updater), "DisplayUpdater")
            val updaterPointer = updater.value
            try {
                checkHr(comInvokeInt(updaterPointer, VTABLE_DISPLAY_PUT_TYPE, MEDIA_PLAYBACK_TYPE_MUSIC), "DisplayUpdater.Type")
                val music = PointerByReference()
                checkHr(comInvokeInt(updaterPointer, VTABLE_DISPLAY_GET_MUSIC_PROPERTIES, music), "MusicProperties")
                val musicPointer = music.value
                try {
                    putHString(musicPointer, VTABLE_MUSIC_PUT_TITLE, meta.title, "MusicProperties.Title")
                    putHString(musicPointer, VTABLE_MUSIC_PUT_ARTIST, meta.artist, "MusicProperties.Artist")
                    putHString(musicPointer, VTABLE_MUSIC_PUT_ALBUM_ARTIST, meta.artist, "MusicProperties.AlbumArtist")
                    queryInterface(musicPointer, IID_MUSIC_DISPLAY_PROPERTIES2)?.let { music2 ->
                        try {
                            putHString(music2, VTABLE_MUSIC2_PUT_ALBUM_TITLE, meta.album, "MusicProperties.AlbumTitle")
                        } finally {
                            release(music2)
                        }
                    }
                } finally {
                    release(musicPointer)
                }
                checkHr(comInvokeInt(updaterPointer, VTABLE_DISPLAY_UPDATE), "DisplayUpdater.Update")
            } finally {
                release(updaterPointer)
            }
        }

        fun updatePlayback(meta: MediaControlMetadata) {
            enableControls(true)
            val status = if (meta.isPlaying) MEDIA_PLAYBACK_STATUS_PLAYING else MEDIA_PLAYBACK_STATUS_PAUSED
            checkHr(comInvokeInt(controls, VTABLE_SMTC_PUT_PLAYBACK_STATUS, status), "PlaybackStatus")
            updateTimeline(meta)
        }

        fun clear() {
            runCatching {
                checkHr(
                    comInvokeInt(controls, VTABLE_SMTC_PUT_PLAYBACK_STATUS, MEDIA_PLAYBACK_STATUS_CLOSED),
                    "PlaybackStatus.Closed",
                )
                val updater = PointerByReference()
                checkHr(comInvokeInt(controls, VTABLE_SMTC_GET_DISPLAY_UPDATER, updater), "DisplayUpdater")
                val updaterPointer = updater.value
                try {
                    checkHr(comInvokeInt(updaterPointer, VTABLE_DISPLAY_CLEAR_ALL), "DisplayUpdater.ClearAll")
                    checkHr(comInvokeInt(updaterPointer, VTABLE_DISPLAY_UPDATE), "DisplayUpdater.Update")
                } finally {
                    release(updaterPointer)
                }
                enableControls(false)
            }
        }

        fun close() {
            if (closed) return
            closed = true
            try {
                runCatching { clear() }
                runCatching {
                    if (buttonToken != 0L) {
                        comInvokeInt(controls, VTABLE_SMTC_REMOVE_BUTTON_PRESSED, buttonToken)
                    }
                }
                buttonHandler.releaseReference()
                controls2?.let { runCatching { release(it) } }
                runCatching { release(controls) }
            } finally {
                if (roInitialized) {
                    Combase.INSTANCE.RoUninitialize()
                }
            }
        }

        private fun enableControls(enabled: Boolean) {
            val value = if (enabled) TRUE else FALSE
            checkHr(comInvokeInt(controls, VTABLE_SMTC_PUT_IS_ENABLED, value), "IsEnabled")
            if (enabled) {
                checkHr(comInvokeInt(controls, VTABLE_SMTC_PUT_IS_PLAY_ENABLED, TRUE), "IsPlayEnabled")
                checkHr(comInvokeInt(controls, VTABLE_SMTC_PUT_IS_PAUSE_ENABLED, TRUE), "IsPauseEnabled")
                checkHr(comInvokeInt(controls, VTABLE_SMTC_PUT_IS_PREVIOUS_ENABLED, TRUE), "IsPreviousEnabled")
                checkHr(comInvokeInt(controls, VTABLE_SMTC_PUT_IS_NEXT_ENABLED, TRUE), "IsNextEnabled")
            }
        }

        private fun updateTimeline(meta: MediaControlMetadata) {
            val controls2Pointer = controls2 ?: return
            val duration = meta.duration.coerceAtLeast(0L)
            if (duration <= 0L) return

            val inspectable = activateInstance(RUNTIME_CLASS_TIMELINE_PROPERTIES)
            try {
                val timeline = queryInterface(inspectable, IID_TIMELINE_PROPERTIES)
                    ?: error("TimelineProperties QueryInterface failed")
                try {
                    val position = meta.position.coerceIn(0L, duration)
                    checkHr(comInvokeInt(timeline, VTABLE_TIMELINE_PUT_START_TIME, 0L), "Timeline.StartTime")
                    checkHr(comInvokeInt(timeline, VTABLE_TIMELINE_PUT_END_TIME, duration.toHundredNanoseconds()), "Timeline.EndTime")
                    checkHr(comInvokeInt(timeline, VTABLE_TIMELINE_PUT_MIN_SEEK_TIME, 0L), "Timeline.MinSeekTime")
                    checkHr(comInvokeInt(timeline, VTABLE_TIMELINE_PUT_MAX_SEEK_TIME, duration.toHundredNanoseconds()), "Timeline.MaxSeekTime")
                    checkHr(comInvokeInt(timeline, VTABLE_TIMELINE_PUT_POSITION, position.toHundredNanoseconds()), "Timeline.Position")
                    checkHr(
                        comInvokeInt(controls2Pointer, VTABLE_SMTC2_UPDATE_TIMELINE_PROPERTIES, timeline),
                        "UpdateTimelineProperties",
                    )
                } finally {
                    release(timeline)
                }
            } finally {
                release(inspectable)
            }
        }

        companion object {
            fun create(hwnd: Pointer, onButtonPressed: (Int) -> Unit): WindowsSmtcSession {
                val roHr = Combase.INSTANCE.RoInitialize(RO_INIT_MULTITHREADED)
                val roInitialized = when {
                    succeeded(roHr) -> true
                    roHr == RPC_E_CHANGED_MODE -> false
                    else -> throw WindowsSmtcException("RoInitialize", roHr)
                }

                var factory: Pointer? = null
                var controls: Pointer? = null
                var controls2: Pointer? = null
                var handler: ButtonPressedHandler? = null
                try {
                    factory = getActivationFactory(
                        RUNTIME_CLASS_SYSTEM_MEDIA_TRANSPORT_CONTROLS,
                        IID_SYSTEM_MEDIA_TRANSPORT_CONTROLS_INTEROP,
                    )

                    val outControls = PointerByReference()
                    checkHr(
                        comInvokeInt(
                            factory,
                            VTABLE_INTEROP_GET_FOR_WINDOW,
                            hwnd,
                            WinGuid.byReference(IID_SYSTEM_MEDIA_TRANSPORT_CONTROLS),
                            outControls,
                        ),
                        "GetForWindow",
                    )
                    controls = outControls.value ?: error("GetForWindow returned null")
                    controls2 = queryInterface(controls, IID_SYSTEM_MEDIA_TRANSPORT_CONTROLS2)

                    handler = ButtonPressedHandler(onButtonPressed)
                    val tokenRef = LongByReference()
                    checkHr(
                        comInvokeInt(
                            controls,
                            VTABLE_SMTC_ADD_BUTTON_PRESSED,
                            handler.pointer,
                            tokenRef,
                        ),
                        "ButtonPressed",
                    )

                    val session = WindowsSmtcSession(
                        controls = controls,
                        controls2 = controls2,
                        buttonHandler = handler,
                        buttonToken = tokenRef.value,
                        roInitialized = roInitialized,
                    )
                    session.enableControls(true)
                    controls = null
                    controls2 = null
                    handler = null
                    return session
                } catch (error: Throwable) {
                    handler?.releaseReference()
                    controls2?.let { release(it) }
                    controls?.let { release(it) }
                    if (roInitialized) {
                        Combase.INSTANCE.RoUninitialize()
                    }
                    throw error
                } finally {
                    factory?.let { release(it) }
                }
            }
        }
    }

    private class ButtonPressedHandler(
        private val onButtonPressed: (Int) -> Unit,
    ) {
        private val refCount = AtomicInteger(1)
        private val vtable = Memory(Native.POINTER_SIZE.toLong() * 4L)
        private val instance = Memory(Native.POINTER_SIZE.toLong())

        private val queryInterfaceCallback = QueryInterfaceCallback { thisPointer, riid, out ->
            if (out == null) return@QueryInterfaceCallback E_POINTER
            if (guidMatches(riid, IID_IUNKNOWN) || guidMatches(riid, IID_TYPED_BUTTON_PRESSED_HANDLER)) {
                out.value = thisPointer
                addRefCallback.invoke(thisPointer)
                S_OK
            } else {
                out.value = Pointer.NULL
                E_NOINTERFACE
            }
        }

        private val addRefCallback = AddRefCallback {
            refCount.incrementAndGet()
        }

        private val releaseCallback = ReleaseCallback {
            refCount.decrementAndGet().coerceAtLeast(0)
        }

        private val invokeCallback = InvokeCallback { _, _, args ->
            if (args != null && args != Pointer.NULL) {
                val buttonRef = IntByReference()
                val hr = comInvokeInt(args, VTABLE_BUTTON_ARGS_GET_BUTTON, buttonRef)
                if (succeeded(hr)) {
                    onButtonPressed(buttonRef.value)
                }
            }
            S_OK
        }

        val pointer: Pointer = instance

        init {
            vtable.setPointer(0L, CallbackReference.getFunctionPointer(queryInterfaceCallback))
            vtable.setPointer(Native.POINTER_SIZE.toLong(), CallbackReference.getFunctionPointer(addRefCallback))
            vtable.setPointer(Native.POINTER_SIZE.toLong() * 2L, CallbackReference.getFunctionPointer(releaseCallback))
            vtable.setPointer(Native.POINTER_SIZE.toLong() * 3L, CallbackReference.getFunctionPointer(invokeCallback))
            instance.setPointer(0L, vtable)
        }

        fun releaseReference() {
            refCount.set(0)
        }

        private fun interface QueryInterfaceCallback : StdCallLibrary.StdCallCallback {
            fun invoke(thisPointer: Pointer, riid: Pointer?, out: PointerByReference?): Int
        }

        private fun interface AddRefCallback : StdCallLibrary.StdCallCallback {
            fun invoke(thisPointer: Pointer): Int
        }

        private fun interface ReleaseCallback : StdCallLibrary.StdCallCallback {
            fun invoke(thisPointer: Pointer): Int
        }

        private fun interface InvokeCallback : StdCallLibrary.StdCallCallback {
            fun invoke(thisPointer: Pointer, sender: Pointer?, args: Pointer?): Int
        }
    }

    // JNA reflects Structure fields from its own module. The structure class itself therefore
    // cannot be private on JDK 21+, even when every field is exposed with @JvmField.
    internal open class WinGuid() : Structure() {
        @JvmField var data1: Int = 0
        @JvmField var data2: Short = 0
        @JvmField var data3: Short = 0
        @JvmField var data4: ByteArray = ByteArray(8)

        override fun getFieldOrder(): List<String> =
            listOf("data1", "data2", "data3", "data4")

        class ByReference : WinGuid(), Structure.ByReference

        companion object {
            fun byReference(value: String): ByReference =
                ByReference().apply {
                    assignTo(this, value)
                    write()
                }

            fun assignTo(guid: WinGuid, value: String) {
                val normalized = value.trim().trim('{', '}').lowercase()
                val parts = normalized.split("-")
                require(parts.size == 5) { "Invalid GUID: $value" }
                guid.data1 = parts[0].toLong(16).toInt()
                guid.data2 = parts[1].toInt(16).toShort()
                guid.data3 = parts[2].toInt(16).toShort()
                val tail = parts[3] + parts[4]
                require(tail.length == 16) { "Invalid GUID: $value" }
                guid.data4 = ByteArray(8) { index ->
                    tail.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                }
            }
        }
    }

    private interface Combase : StdCallLibrary {
        fun RoInitialize(initType: Int): Int
        fun RoUninitialize()
        fun RoGetActivationFactory(classId: Pointer, iid: WinGuid.ByReference, factory: PointerByReference): Int
        fun RoActivateInstance(classId: Pointer, instance: PointerByReference): Int
        fun WindowsCreateString(sourceString: WString, length: Int, string: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer): Int

        companion object {
            val INSTANCE: Combase = Native.load("combase", Combase::class.java)
        }
    }

    private class WindowsSmtcException(operation: String, hresult: Int) :
        RuntimeException("$operation failed with HRESULT 0x${hresult.toUInt().toString(16)}")

    companion object {
        private const val S_OK = 0
        private val E_POINTER = unchecked(0x80004003u)
        private val E_NOINTERFACE = unchecked(0x80004002u)
        private val RPC_E_CHANGED_MODE = unchecked(0x80010106u)

        private const val RO_INIT_MULTITHREADED = 1
        private const val TRUE: Byte = 1
        private const val FALSE: Byte = 0

        private const val MEDIA_PLAYBACK_TYPE_MUSIC = 1
        private const val MEDIA_PLAYBACK_STATUS_CLOSED = 0
        private const val MEDIA_PLAYBACK_STATUS_PLAYING = 3
        private const val MEDIA_PLAYBACK_STATUS_PAUSED = 4

        private const val BUTTON_PLAY = 0
        private const val BUTTON_PAUSE = 1
        private const val BUTTON_STOP = 2
        private const val BUTTON_NEXT = 6
        private const val BUTTON_PREVIOUS = 7

        private const val VTABLE_INTEROP_GET_FOR_WINDOW = 6

        private const val VTABLE_SMTC_PUT_PLAYBACK_STATUS = 7
        private const val VTABLE_SMTC_GET_DISPLAY_UPDATER = 8
        private const val VTABLE_SMTC_PUT_IS_ENABLED = 11
        private const val VTABLE_SMTC_PUT_IS_PLAY_ENABLED = 13
        private const val VTABLE_SMTC_PUT_IS_PAUSE_ENABLED = 17
        private const val VTABLE_SMTC_PUT_IS_PREVIOUS_ENABLED = 25
        private const val VTABLE_SMTC_PUT_IS_NEXT_ENABLED = 27
        private const val VTABLE_SMTC_ADD_BUTTON_PRESSED = 32
        private const val VTABLE_SMTC_REMOVE_BUTTON_PRESSED = 33

        private const val VTABLE_SMTC2_UPDATE_TIMELINE_PROPERTIES = 12

        private const val VTABLE_DISPLAY_PUT_TYPE = 7
        private const val VTABLE_DISPLAY_GET_MUSIC_PROPERTIES = 12
        private const val VTABLE_DISPLAY_CLEAR_ALL = 16
        private const val VTABLE_DISPLAY_UPDATE = 17

        private const val VTABLE_MUSIC_PUT_TITLE = 7
        private const val VTABLE_MUSIC_PUT_ALBUM_ARTIST = 9
        private const val VTABLE_MUSIC_PUT_ARTIST = 11
        private const val VTABLE_MUSIC2_PUT_ALBUM_TITLE = 7

        private const val VTABLE_BUTTON_ARGS_GET_BUTTON = 6

        private const val VTABLE_TIMELINE_PUT_START_TIME = 7
        private const val VTABLE_TIMELINE_PUT_END_TIME = 9
        private const val VTABLE_TIMELINE_PUT_MIN_SEEK_TIME = 11
        private const val VTABLE_TIMELINE_PUT_MAX_SEEK_TIME = 13
        private const val VTABLE_TIMELINE_PUT_POSITION = 15

        private const val IID_IUNKNOWN = "00000000-0000-0000-c000-000000000046"
        private const val IID_SYSTEM_MEDIA_TRANSPORT_CONTROLS_INTEROP = "ddb0472d-c911-4a1f-86d9-dc3d71a95f5a"
        private const val IID_SYSTEM_MEDIA_TRANSPORT_CONTROLS = "99fa3ff4-1742-42a6-902e-087d41f965ec"
        private const val IID_SYSTEM_MEDIA_TRANSPORT_CONTROLS2 = "ea98d2f6-7f3c-4af2-a586-72889808efb1"
        private const val IID_MUSIC_DISPLAY_PROPERTIES2 = "00368462-97d3-44b9-b00f-008afcefaf18"
        private const val IID_TIMELINE_PROPERTIES = "5125316a-c3a2-475b-8507-93534dc88f15"
        private const val IID_TYPED_BUTTON_PRESSED_HANDLER = "0557e996-7b23-5bae-aa81-ea0d671143a4"

        private const val RUNTIME_CLASS_SYSTEM_MEDIA_TRANSPORT_CONTROLS =
            "Windows.Media.SystemMediaTransportControls"
        private const val RUNTIME_CLASS_TIMELINE_PROPERTIES =
            "Windows.Media.SystemMediaTransportControlsTimelineProperties"

        private fun unchecked(value: UInt): Int = value.toInt()

        private fun succeeded(hr: Int): Boolean = hr >= 0

        private fun checkHr(hr: Int, operation: String) {
            if (!succeeded(hr)) throw WindowsSmtcException(operation, hr)
        }

        private fun comInvokeInt(pointer: Pointer, vtableIndex: Int, vararg args: Any?): Int =
            vtableFunction(pointer, vtableIndex).invokeInt(arrayOf(pointer, *args))

        private fun vtableFunction(pointer: Pointer, vtableIndex: Int): Function {
            val vtable = pointer.getPointer(0L)
            val functionPointer = vtable.getPointer(vtableIndex.toLong() * Native.POINTER_SIZE)
            return Function.getFunction(functionPointer, Function.ALT_CONVENTION)
        }

        private fun getActivationFactory(runtimeClass: String, iid: String): Pointer =
            withHString(runtimeClass) { classId ->
                val factory = PointerByReference()
                checkHr(
                    Combase.INSTANCE.RoGetActivationFactory(classId, WinGuid.byReference(iid), factory),
                    "RoGetActivationFactory($runtimeClass)",
                )
                factory.value ?: error("RoGetActivationFactory($runtimeClass) returned null")
            }

        private fun activateInstance(runtimeClass: String): Pointer =
            withHString(runtimeClass) { classId ->
                val instance = PointerByReference()
                checkHr(Combase.INSTANCE.RoActivateInstance(classId, instance), "RoActivateInstance($runtimeClass)")
                instance.value ?: error("RoActivateInstance($runtimeClass) returned null")
            }

        private fun queryInterface(pointer: Pointer, iid: String): Pointer? {
            val result = PointerByReference()
            val hr = comInvokeInt(pointer, 0, WinGuid.byReference(iid), result)
            return if (succeeded(hr)) result.value else null
        }

        private fun release(pointer: Pointer) {
            comInvokeInt(pointer, 2)
        }

        private fun putHString(pointer: Pointer, vtableIndex: Int, value: String, operation: String) {
            withHString(value) { hstring ->
                checkHr(comInvokeInt(pointer, vtableIndex, hstring), operation)
            }
        }

        private fun <T> withHString(value: String, block: (Pointer) -> T): T {
            val hstring = PointerByReference()
            checkHr(
                Combase.INSTANCE.WindowsCreateString(WString(value), value.length, hstring),
                "WindowsCreateString",
            )
            val pointer = hstring.value ?: Pointer.NULL
            return try {
                block(pointer)
            } finally {
                if (pointer != Pointer.NULL) {
                    Combase.INSTANCE.WindowsDeleteString(pointer)
                }
            }
        }

        private fun guidMatches(pointer: Pointer?, expected: String): Boolean {
            if (pointer == null || pointer == Pointer.NULL) return false
            val parsed = WinGuid().apply { WinGuid.assignTo(this, expected) }
            return pointer.getInt(0L) == parsed.data1 &&
                pointer.getShort(4L) == parsed.data2 &&
                pointer.getShort(6L) == parsed.data3 &&
                pointer.getByteArray(8L, 8).contentEquals(parsed.data4)
        }

        private fun Long.toHundredNanoseconds(): Long =
            if (this >= Long.MAX_VALUE / 10_000L) Long.MAX_VALUE else this * 10_000L
    }
}

package com.leejlredstar.redefinencm.kmp.smtc

import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.awt.Window
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing

/** macOS MPNowPlayingInfoCenter + MPRemoteCommandCenter integration through Objective-C/JNA. */
internal class MacOsMediaControls(
    private val player: PlatformPlayer,
) : DesktopMediaControlsBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val _status = MutableStateFlow(DesktopMediaControlsStatus.NotStarted)
    override val status: StateFlow<DesktopMediaControlsStatus> = _status.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var bridge: MacNowPlayingBridge? = null
    private var collectJob: Job? = null

    override fun start(window: Window) {
        startNative()
    }

    internal fun startForSmokeTest() {
        startNative()
    }

    private fun startNative() {
        stop()
        try {
            val newBridge = MacNowPlayingBridge(player)
            newBridge.start()
            bridge = newBridge
            collectJob = scope.launch {
                MediaControlsIntegrator.metadata.collect { metadata ->
                    try {
                        newBridge.update(metadata)
                        _lastError.value = null
                        _status.value = DesktopMediaControlsStatus.Forwarding
                    } catch (error: Throwable) {
                        fail("macOS Now Playing update failed", error)
                    }
                }
            }
            _lastError.value = null
            _status.value = DesktopMediaControlsStatus.Forwarding
        } catch (error: Throwable) {
            fail("macOS Now Playing initialization failed", error)
            bridge?.close()
            bridge = null
        }
    }

    override fun stop() {
        collectJob?.cancel()
        collectJob = null
        bridge?.close()
        bridge = null
        _lastError.value = null
        _status.value = DesktopMediaControlsStatus.NotStarted
    }

    private fun fail(context: String, error: Throwable) {
        val message = "$context: ${error.message ?: error::class.simpleName}"
        _lastError.value = message
        _status.value = DesktopMediaControlsStatus.NativeError
        System.err.println(message)
    }
}

private class MacNowPlayingBridge(
    private val player: PlatformPlayer,
) : MacRemoteCommandHandler {
    private val runtime = MacObjcRuntime()
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val center = runtime.autorelease {
        runtime.sendPointer(runtime.objcClass("MPNowPlayingInfoCenter"), "defaultCenter")
            .requirePointer("MPNowPlayingInfoCenter.defaultCenter")
    }
    private val commandCenter = runtime.autorelease {
        runtime.sendPointer(runtime.objcClass("MPRemoteCommandCenter"), "sharedCommandCenter")
            .requirePointer("MPRemoteCommandCenter.sharedCommandCenter")
    }
    private val targetClass = MacRemoteTargetRegistry.targetClass(runtime)
    private val target = runtime.autorelease {
        runtime.sendPointer(runtime.sendPointer(targetClass, "alloc"), "init")
            .requirePointer("remote-command target")
    }
    private val registrations = mutableListOf<MacCommandRegistration>()

    fun start() {
        MacRemoteTargetRegistry.register(target, this)
        try {
            register("playCommand", "redefineNcmPlay:", MacRemoteCommand.Play)
            register("pauseCommand", "redefineNcmPause:", MacRemoteCommand.Pause)
            register("togglePlayPauseCommand", "redefineNcmToggle:", MacRemoteCommand.Toggle)
            register("nextTrackCommand", "redefineNcmNext:", MacRemoteCommand.Next)
            register("previousTrackCommand", "redefineNcmPrevious:", MacRemoteCommand.Previous)
            register("changePlaybackPositionCommand", "redefineNcmChangePosition:", MacRemoteCommand.ChangePosition)
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun update(metadata: MediaControlMetadata) = runtime.autorelease {
        if (metadata.title.isBlank() && metadata.artist.isBlank()) {
            runtime.sendVoid(center, "setNowPlayingInfo:", Pointer.NULL)
            runtime.sendVoid(center, "setPlaybackState:", MAC_PLAYBACK_STOPPED)
            setCommandAvailability(hasTrack = false, canSeek = false)
            return@autorelease
        }

        val dictionary = runtime.sendPointer(runtime.objcClass("NSMutableDictionary"), "dictionary")
            .requirePointer("NSMutableDictionary.dictionary")
        dictionary.put(runtime, runtime.mediaPlayerKey("MPMediaItemPropertyTitle"), runtime.nsString(metadata.title))
        dictionary.put(runtime, runtime.mediaPlayerKey("MPMediaItemPropertyArtist"), runtime.nsString(metadata.artist))
        dictionary.put(runtime, runtime.mediaPlayerKey("MPMediaItemPropertyAlbumTitle"), runtime.nsString(metadata.album))
        dictionary.put(
            runtime,
            runtime.mediaPlayerKey("MPMediaItemPropertyPlaybackDuration"),
            runtime.nsNumber(metadata.duration.coerceAtLeast(0L) / 1_000.0),
        )
        dictionary.put(
            runtime,
            runtime.mediaPlayerKey("MPNowPlayingInfoPropertyElapsedPlaybackTime"),
            runtime.nsNumber(metadata.position.coerceAtLeast(0L) / 1_000.0),
        )
        dictionary.put(
            runtime,
            runtime.mediaPlayerKey("MPNowPlayingInfoPropertyPlaybackRate"),
            runtime.nsNumber(if (metadata.isPlaying) 1.0 else 0.0),
        )
        createLocalArtwork(metadata.artworkUri)?.let { artwork ->
            dictionary.put(runtime, runtime.mediaPlayerKey("MPMediaItemPropertyArtwork"), artwork)
            runtime.sendVoid(artwork, "release")
        }

        runtime.sendVoid(center, "setNowPlayingInfo:", dictionary)
        runtime.sendVoid(
            center,
            "setPlaybackState:",
            if (metadata.isPlaying) MAC_PLAYBACK_PLAYING else MAC_PLAYBACK_PAUSED,
        )
        setCommandAvailability(hasTrack = true, canSeek = metadata.duration > 0L)
    }

    override fun handle(command: MacRemoteCommand, event: Pointer?): Boolean {
        if (player.currentMedia.value == null) return false
        val requestedPositionMs = if (command == MacRemoteCommand.ChangePosition) {
            val pointer = event ?: return false
            runtime.autorelease { (runtime.sendDouble(pointer, "positionTime") * 1_000.0).toLong() }
                .coerceAtLeast(0L)
        } else {
            null
        }
        actionScope.launch {
            when (command) {
                MacRemoteCommand.Play -> player.play()
                MacRemoteCommand.Pause -> player.pause()
                MacRemoteCommand.Toggle -> player.togglePlayPause()
                MacRemoteCommand.Next -> player.seekToNext()
                MacRemoteCommand.Previous -> player.seekToPrevious()
                MacRemoteCommand.ChangePosition -> player.seekTo(requestedPositionMs ?: return@launch)
            }
        }
        return true
    }

    fun close() = runtime.autorelease {
        registrations.forEach { registration ->
            runtime.sendVoid(
                registration.command,
                "removeTarget:action:",
                target,
                runtime.selector(registration.selector),
            )
            runtime.sendVoid(registration.command, "setEnabled:", 0)
        }
        registrations.clear()
        MacRemoteTargetRegistry.unregister(target)
        runtime.sendVoid(center, "setNowPlayingInfo:", Pointer.NULL)
        runtime.sendVoid(center, "setPlaybackState:", MAC_PLAYBACK_STOPPED)
        runtime.sendVoid(target, "release")
        actionScope.cancel()
    }

    private fun register(commandSelector: String, actionSelector: String, commandKind: MacRemoteCommand) {
        check(MacRemoteTargetRegistry.commandForSelector(actionSelector) == commandKind) {
            "Objective-C selector $actionSelector is not registered for $commandKind"
        }
        val command = runtime.sendPointer(commandCenter, commandSelector)
            .requirePointer("MPRemoteCommandCenter.$commandSelector")
        runtime.sendVoid(command, "addTarget:action:", target, runtime.selector(actionSelector))
        runtime.sendVoid(command, "setEnabled:", 1)
        registrations += MacCommandRegistration(command, actionSelector, commandKind)
    }

    private fun setCommandAvailability(hasTrack: Boolean, canSeek: Boolean) {
        registrations.forEach { registration ->
            val enabled = hasTrack && (registration.kind != MacRemoteCommand.ChangePosition || canSeek)
            runtime.sendVoid(registration.command, "setEnabled:", if (enabled) 1 else 0)
        }
    }

    private fun createLocalArtwork(uriText: String): Pointer? {
        val file = runCatching {
            val uri = URI(uriText)
            when {
                uri.scheme.equals("file", ignoreCase = true) -> File(uri)
                uri.scheme == null -> File(uriText)
                else -> null
            }
        }.getOrNull()?.takeIf(File::isFile) ?: return null

        val url = runtime.sendPointer(runtime.objcClass("NSURL"), "fileURLWithPath:", runtime.nsString(file.absolutePath))
            ?: return null
        val image = runtime.sendPointer(
            runtime.sendPointer(runtime.objcClass("NSImage"), "alloc"),
            "initWithContentsOfURL:",
            url,
        ) ?: return null
        return try {
            val artwork = runtime.sendPointer(runtime.objcClass("MPMediaItemArtwork"), "alloc") ?: return null
            if (runtime.sendLong(artwork, "respondsToSelector:", runtime.selector("initWithImage:")) == 0L) {
                runtime.sendVoid(artwork, "release")
                return null
            }
            runtime.sendPointer(
                artwork,
                "initWithImage:",
                image,
            )
        } finally {
            runtime.sendVoid(image, "release")
        }
    }
}

private data class MacCommandRegistration(
    val command: Pointer,
    val selector: String,
    val kind: MacRemoteCommand,
)

private enum class MacRemoteCommand {
    Play,
    Pause,
    Toggle,
    Next,
    Previous,
    ChangePosition,
}

private fun interface MacRemoteCommandHandler {
    fun handle(command: MacRemoteCommand, event: Pointer?): Boolean
}

/**
 * One Objective-C class is registered for the process lifetime. Its IMP callbacks are strongly
 * retained here and route by the native target pointer, so restarting the facade cannot leave a
 * dangling JNA callback in the Objective-C runtime.
 */
private object MacRemoteTargetRegistry {
    private val handlers = ConcurrentHashMap<Long, MacRemoteCommandHandler>()
    private val selectorCommands = linkedMapOf(
        "redefineNcmPlay:" to MacRemoteCommand.Play,
        "redefineNcmPause:" to MacRemoteCommand.Pause,
        "redefineNcmToggle:" to MacRemoteCommand.Toggle,
        "redefineNcmNext:" to MacRemoteCommand.Next,
        "redefineNcmPrevious:" to MacRemoteCommand.Previous,
        "redefineNcmChangePosition:" to MacRemoteCommand.ChangePosition,
    )
    private val callbacks = selectorCommands.mapValues { (_, command) ->
        CommandImp { self, _, event ->
            val handled = handlers[Pointer.nativeValue(self)]?.handle(command, event) == true
            if (handled) MAC_COMMAND_SUCCESS else MAC_COMMAND_NO_ACTIONABLE_ITEM
        }
    }
    @Volatile
    private var registeredClass: Pointer? = null

    fun targetClass(runtime: MacObjcRuntime): Pointer = synchronized(this) {
        registeredClass?.let { return@synchronized it }
        runtime.findClass(TARGET_CLASS_NAME)?.let {
            registeredClass = it
            return@synchronized it
        }
        val superclass = runtime.objcClass("NSObject")
        val targetClass = runtime.allocateClass(superclass, TARGET_CLASS_NAME)
            .requirePointer("objc_allocateClassPair($TARGET_CLASS_NAME)")
        selectorCommands.keys.forEach { selectorName ->
            val callback = callbacks.getValue(selectorName)
            check(
                runtime.addMethod(
                    targetClass,
                    runtime.selector(selectorName),
                    CallbackReference.getFunctionPointer(callback),
                    "q@:@",
                ),
            ) { "class_addMethod failed for $selectorName" }
        }
        runtime.registerClass(targetClass)
        registeredClass = targetClass
        targetClass
    }

    fun register(target: Pointer, handler: MacRemoteCommandHandler) {
        handlers[Pointer.nativeValue(target)] = handler
    }

    fun unregister(target: Pointer) {
        handlers.remove(Pointer.nativeValue(target))
    }

    fun commandForSelector(selector: String): MacRemoteCommand? = selectorCommands[selector]

    private fun interface CommandImp : Callback {
        fun invoke(self: Pointer, selector: Pointer, event: Pointer?): Long
    }

    private const val TARGET_CLASS_NAME = "RedefineNCMJnaRemoteCommandTarget"
}

private class MacObjcRuntime {
    private val objc = NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib")
    @Suppress("unused")
    private val foundation = NativeLibrary.getInstance("/System/Library/Frameworks/Foundation.framework/Foundation")
    @Suppress("unused")
    private val appKit = NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit")
    private val mediaPlayer =
        NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer")

    private val getClass: Function = objc.getFunction("objc_getClass")
    private val registerSelector: Function = objc.getFunction("sel_registerName")
    private val messageSend: Function = objc.getFunction("objc_msgSend")
    private val allocateClassPair: Function = objc.getFunction("objc_allocateClassPair")
    private val registerClassPair: Function = objc.getFunction("objc_registerClassPair")
    private val classAddMethod: Function = objc.getFunction("class_addMethod")
    private val poolPush: Function = objc.getFunction("objc_autoreleasePoolPush")
    private val poolPop: Function = objc.getFunction("objc_autoreleasePoolPop")
    private val selectors = ConcurrentHashMap<String, Pointer>()
    private val classes = ConcurrentHashMap<String, Pointer>()
    private val keys = ConcurrentHashMap<String, Pointer>()

    fun objcClass(name: String): Pointer = classes.computeIfAbsent(name) {
        getClass.invokePointer(arrayOf(name)).requirePointer("Objective-C class $name")
    }

    fun findClass(name: String): Pointer? =
        getClass.invokePointer(arrayOf(name))?.takeUnless { it == Pointer.NULL }

    fun selector(name: String): Pointer = selectors.computeIfAbsent(name) {
        registerSelector.invokePointer(arrayOf(name)).requirePointer("Objective-C selector $name")
    }

    fun sendPointer(receiver: Pointer?, selector: String, vararg args: Any?): Pointer? {
        if (receiver == null || receiver == Pointer.NULL) return null
        return messageSend.invokePointer(arrayOf(receiver, this.selector(selector), *args))
    }

    fun sendVoid(receiver: Pointer?, selector: String, vararg args: Any?) {
        if (receiver == null || receiver == Pointer.NULL) return
        messageSend.invokeVoid(arrayOf(receiver, this.selector(selector), *args))
    }

    fun sendDouble(receiver: Pointer, selector: String, vararg args: Any?): Double =
        messageSend.invokeDouble(arrayOf(receiver, this.selector(selector), *args))

    fun sendLong(receiver: Pointer, selector: String, vararg args: Any?): Long =
        messageSend.invokeLong(arrayOf(receiver, this.selector(selector), *args))

    fun allocateClass(superclass: Pointer, name: String): Pointer? =
        allocateClassPair.invokePointer(arrayOf(superclass, name, 0L))

    fun addMethod(targetClass: Pointer, selector: Pointer, implementation: Pointer, types: String): Boolean =
        classAddMethod.invokeInt(arrayOf(targetClass, selector, implementation, types)) != 0

    fun registerClass(targetClass: Pointer) {
        registerClassPair.invokeVoid(arrayOf(targetClass))
    }

    fun nsString(value: String): Pointer =
        sendPointer(objcClass("NSString"), "stringWithUTF8String:", value)
            .requirePointer("NSString($value)")

    fun nsNumber(value: Double): Pointer =
        sendPointer(objcClass("NSNumber"), "numberWithDouble:", value)
            .requirePointer("NSNumber($value)")

    fun mediaPlayerKey(symbol: String): Pointer = keys.computeIfAbsent(symbol) {
        mediaPlayer.getGlobalVariableAddress(symbol).getPointer(0)
            .requirePointer("MediaPlayer global $symbol")
    }

    fun <T> autorelease(block: () -> T): T {
        val token = poolPush.invokePointer(emptyArray())
        return try {
            block()
        } finally {
            poolPop.invokeVoid(arrayOf(token))
        }
    }
}

private fun Pointer.put(runtime: MacObjcRuntime, key: Pointer, value: Pointer) {
    runtime.sendVoid(this, "setObject:forKey:", value, key)
}

private fun Pointer?.requirePointer(label: String): Pointer =
    this?.takeUnless { it == Pointer.NULL } ?: error("Native object unavailable: $label")

private const val MAC_PLAYBACK_PLAYING = 1L
private const val MAC_PLAYBACK_PAUSED = 2L
private const val MAC_PLAYBACK_STOPPED = 3L
private const val MAC_COMMAND_SUCCESS = 0L
private const val MAC_COMMAND_NO_ACTIONABLE_ITEM = 110L

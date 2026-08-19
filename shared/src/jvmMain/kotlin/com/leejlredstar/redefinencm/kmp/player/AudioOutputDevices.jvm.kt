package com.leejlredstar.redefinencm.kmp.player

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Line
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine

actual val supportsAudioOutputDeviceSelection: Boolean = true

/**
 * Format-less probe for "can this mixer play anything at all".
 *
 * Settings enumerates before any track is open, so there is no PCM format to match against and
 * a concrete [DataLine.Info] would reject every mixer at that point. Matching on the line class
 * alone still drops the capture-only mixers and the `Port` mixers (volume controls, jack-presence
 * sensors) that [AudioSystem.getMixerInfo] returns alongside the real outputs.
 */
private val anySourceDataLine = Line.Info(SourceDataLine::class.java)

actual fun availableAudioOutputDevices(): List<AudioOutputDevice> =
    runCatching { playbackMixers().map { (info, _) -> info.toAudioOutputDevice() } }
        .getOrDefault(emptyList())
        .distinctBy { it.id }

/**
 * Opens a playback line on the device the user picked, or on the JVM default when they picked
 * none — [persistedDeviceId] is the raw settings value.
 *
 * The choice is resolved on every open rather than cached in a [Mixer]: the device list changes
 * while the app runs, and re-reading it also lets the default follow the OS instead of pinning
 * whichever endpoint existed at startup.
 *
 * A device can be listed and still refuse *this* track — a mixer that reports no support for the
 * decoded PCM format, or one another process holds exclusively. That falls back to the default
 * output, because failing the whole track would make picking a device worse than not offering
 * the choice at all.
 */
internal fun openAudioOutputLine(format: AudioFormat, persistedDeviceId: String): SourceDataLine {
    val info = DataLine.Info(SourceDataLine::class.java, format)
    // No stored choice means no enumeration — the untouched-setting path stays what it was.
    val selected = persistedDeviceId.takeUnless { it.isBlank() }?.let { deviceId ->
        val mixers = runCatching { playbackMixers() }.getOrDefault(emptyList())
        val choice = resolveAudioOutputSelection(
            deviceId,
            mixers.map { (mixerInfo, _) -> mixerInfo.toAudioOutputDevice() },
        ) ?: return@let null
        runCatching {
            val mixer = mixers.first { (mixerInfo, _) ->
                audioOutputDeviceId(mixerInfo) == choice.id
            }.second
            (mixer.getLine(info) as SourceDataLine).apply { open(format) }
        }.getOrElse {
            System.err.println(
                "JvmMediaPlayer: audio output '${choice.displayName}' cannot play $format, " +
                    "falling back to the system default",
            )
            null
        }
    }
    return selected ?: (AudioSystem.getLine(info) as SourceDataLine).apply { open(format) }
}

/** Mixers that can accept a [SourceDataLine], paired with the info they were resolved from. */
private fun playbackMixers(): List<Pair<Mixer.Info, Mixer>> =
    AudioSystem.getMixerInfo().mapNotNull { info ->
        val mixer = runCatching { AudioSystem.getMixer(info) }.getOrNull() ?: return@mapNotNull null
        if (!mixer.isLineSupported(anySourceDataLine)) return@mapNotNull null
        info to mixer
    }

private fun Mixer.Info.toAudioOutputDevice(): AudioOutputDevice = AudioOutputDevice(
    id = audioOutputDeviceId(this),
    displayName = name.trim().ifBlank { description.trim() },
)

private fun audioOutputDeviceId(info: Mixer.Info): String =
    audioOutputDeviceId(info.name.trim(), info.vendor.orEmpty().trim())

package com.leejlredstar.redefinencm.kmp.player

/**
 * One audio endpoint playback can be routed to.
 *
 * [id] identifies the endpoint well enough to survive the list being reordered while the app
 * runs; [displayName] is what Settings shows. It is not durable across restarts, and nothing
 * asks it to be — a chosen device only holds for the session that chose it.
 */
data class AudioOutputDevice(val id: String, val displayName: String)

/**
 * The [SettingKeys.AUDIO_OUTPUT_DEVICE][com.leejlredstar.redefinencm.kmp.util.SettingKeys] value
 * meaning "follow whatever the platform is currently using", and the value every launch starts
 * from — desktop startup clears any device pinned by an earlier session.
 */
const val SYSTEM_DEFAULT_AUDIO_OUTPUT_ID: String = ""

/**
 * Whether this target lets the user choose which output device playback opens.
 *
 * False on the platforms that route through an OS-level picker (Android, iOS, browser), where
 * an in-app list would only duplicate — and fight with — the system one.
 */
expect val supportsAudioOutputDeviceSelection: Boolean

/**
 * Output devices the platform currently offers, freshly enumerated on every call.
 *
 * Empty when the target has no selection to make, and empty rather than throwing when
 * enumeration fails — callers treat "no devices" and "use the default" the same way.
 */
expect fun availableAudioOutputDevices(): List<AudioOutputDevice>

/**
 * Joins a device's identifying parts into the persisted id.
 *
 * The name alone is not unique across audio providers and the position in the enumerated list
 * silently retargets as soon as hardware is plugged in, so the id carries both parts.
 */
internal fun audioOutputDeviceId(name: String, vendor: String): String =
    if (vendor.isBlank()) name else "$name|$vendor"

/** The device-name half of an id built by [audioOutputDeviceId]. */
internal fun audioOutputDeviceName(id: String): String =
    id.substringBeforeLast('|', missingDelimiterValue = id)

/**
 * Picks which of [available] a persisted id refers to, or null for "open the platform default".
 *
 * Null covers both "nothing was ever chosen" and "the chosen device is gone" — a device that
 * was unplugged, or renamed by a driver update. Falling back to the default beats reporting an
 * error: the alternative is silence until the user notices and reopens Settings.
 */
internal fun resolveAudioOutputSelection(
    persistedId: String,
    available: List<AudioOutputDevice>,
): AudioOutputDevice? {
    if (persistedId == SYSTEM_DEFAULT_AUDIO_OUTPUT_ID || persistedId.isBlank()) return null
    available.firstOrNull { it.id == persistedId }?.let { return it }
    // Vendor strings move between JDK builds while the device name stays put, so a vendor-only
    // mismatch should keep pointing at the same speakers instead of silently reverting.
    val persistedName = audioOutputDeviceName(persistedId)
    return available.firstOrNull { audioOutputDeviceName(it.id) == persistedName }
}

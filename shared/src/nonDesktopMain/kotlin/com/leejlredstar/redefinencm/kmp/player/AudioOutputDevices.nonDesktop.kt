package com.leejlredstar.redefinencm.kmp.player

/**
 * Routing is the operating system's job on these targets: Android's output picker, iOS's
 * AVAudioSession route (Control Centre / AirPlay) and the browser's own picker already move
 * audio without the app choosing an endpoint. An in-app list would only duplicate — and fight
 * with — the system one.
 */
actual val supportsAudioOutputDeviceSelection: Boolean = false

actual fun availableAudioOutputDevices(): List<AudioOutputDevice> = emptyList()

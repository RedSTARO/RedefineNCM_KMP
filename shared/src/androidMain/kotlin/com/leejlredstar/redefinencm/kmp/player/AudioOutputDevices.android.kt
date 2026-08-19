package com.leejlredstar.redefinencm.kmp.player

// Routing is the system's job here: the OS output picker (and Bluetooth/USB handover) already
// moves ExoPlayer's audio without the app choosing an endpoint.
actual val supportsAudioOutputDeviceSelection: Boolean = false

actual fun availableAudioOutputDevices(): List<AudioOutputDevice> = emptyList()

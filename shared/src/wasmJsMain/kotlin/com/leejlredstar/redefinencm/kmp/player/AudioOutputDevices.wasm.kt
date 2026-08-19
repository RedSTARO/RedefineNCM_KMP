package com.leejlredstar.redefinencm.kmp.player

// setSinkId() needs a device id from an already-granted getUserMedia permission prompt, which
// a music player has no other reason to ask for. The browser's own output picker handles it.
actual val supportsAudioOutputDeviceSelection: Boolean = false

actual fun availableAudioOutputDevices(): List<AudioOutputDevice> = emptyList()

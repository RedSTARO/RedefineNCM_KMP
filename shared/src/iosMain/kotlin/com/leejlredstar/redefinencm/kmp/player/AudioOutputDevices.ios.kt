package com.leejlredstar.redefinencm.kmp.player

// AVAudioSession owns the route; the system picker (Control Centre / AirPlay) is the only
// supported way to move it.
actual val supportsAudioOutputDeviceSelection: Boolean = false

actual fun availableAudioOutputDevices(): List<AudioOutputDevice> = emptyList()

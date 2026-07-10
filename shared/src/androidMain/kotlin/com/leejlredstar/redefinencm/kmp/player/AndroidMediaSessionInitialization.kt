package com.leejlredstar.redefinencm.kmp.player

/** Observable service-initialization phases used by the Android MediaSession gate. */
enum class AndroidMediaSessionInitializationState {
    Loading,
    Ready,
    Failed,
    Destroyed,
}

/** A controller is accepted only after the fully configured MediaSession has been published. */
fun canExposeAndroidMediaSession(
    state: AndroidMediaSessionInitializationState,
    hasSession: Boolean,
): Boolean = state == AndroidMediaSessionInitializationState.Ready && hasSession

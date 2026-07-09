package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmAudioSupportTest {

    @Test
    fun highResolutionQualitiesAreMappedToMp3Level() {
        assertEquals("standard", jvmPlaybackQualityLevel(SoundQuality.STANDARD))
        assertEquals("higher", jvmPlaybackQualityLevel(SoundQuality.HIGHER))
        assertEquals("exhigh", jvmPlaybackQualityLevel(SoundQuality.EXHIGH))
        assertEquals("exhigh", jvmPlaybackQualityLevel(SoundQuality.LOSSLESS))
        assertEquals("exhigh", jvmPlaybackQualityLevel(SoundQuality.JYMASTER))
    }

    @Test
    fun localResolverOnlyAcceptsJvmDecodableAudio() {
        assertTrue(isJvmPlayableAudioUri("file:/C:/Music/RedefineNCM/2097485077.mp3"))
        assertTrue(isJvmPlayableAudioUri("https://example.com/audio/track.wav?token=abc"))
        assertFalse(isJvmPlayableAudioUri("file:/C:/Music/RedefineNCM/2097485077.flac"))
        assertFalse(isJvmPlayableAudioUri("https://example.com/audio/track.m4a"))
    }
}

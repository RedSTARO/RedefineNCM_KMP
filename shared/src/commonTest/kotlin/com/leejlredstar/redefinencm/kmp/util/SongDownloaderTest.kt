package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertEquals

class SongDownloaderTest {
    @Test
    fun extractsNativeDownloadExtensionWithoutChangingLegacyRules() {
        assertEquals("flac", extensionFromUrl("https://cdn.example/song.flac?token=1"))
        assertEquals("mp3", extensionFromUrl("https://cdn.example/song"))
        assertEquals("mp3", extensionFromUrl("https://cdn.example/song."))
        assertEquals("abcdefghijkl", extensionFromUrl("https://cdn.example/song.abcdefghijklmnop"))
    }
}

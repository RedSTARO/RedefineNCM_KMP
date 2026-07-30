package com.leejlredstar.redefinencm.kmp.util

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidPrivateArtworkStorageTest {
    @Test
    fun lyricMimeTypeMatchesThePersistedFileExtension() {
        assertEquals("application/ttml+xml", androidLyricMimeType("42.lyric.ttml"))
        assertEquals("application/x-yrc", androidLyricMimeType("42.lyric.yrc"))
        assertEquals("application/x-lrc", androidLyricMimeType("42.lyric.lrc"))
        assertEquals(
            "application/x-lrc",
            androidLyricMimeType("42.lyric.translation.lrc"),
        )
    }

    @Test
    fun artworkDirectoryIsNestedUnderAppStorageAndBlocksMediaScanning() {
        withTemporaryDirectory { storageRoot ->
            val directory = ensureAndroidPrivateArtworkDirectory(storageRoot)

            assertEquals(
                File(storageRoot, ANDROID_LOCAL_ARTWORK_SUBDIR).canonicalFile,
                directory.canonicalFile,
            )
            assertTrue(File(directory, ".nomedia").isFile)
        }
    }

    @Test
    fun ensuringArtworkDirectoryIsIdempotent() {
        withTemporaryDirectory { storageRoot ->
            val first = ensureAndroidPrivateArtworkDirectory(storageRoot)
            val second = ensureAndroidPrivateArtworkDirectory(storageRoot)

            assertEquals(first.canonicalFile, second.canonicalFile)
            assertTrue(File(second, ".nomedia").isFile)
        }
    }
}

private inline fun withTemporaryDirectory(block: (File) -> Unit) {
    val directory = Files.createTempDirectory("redefinencm-artwork-test").toFile()
    try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}

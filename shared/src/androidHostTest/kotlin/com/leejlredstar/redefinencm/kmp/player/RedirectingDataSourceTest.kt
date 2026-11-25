package com.leejlredstar.redefinencm.kmp.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class RedirectingDataSourceTest {
    @Test
    fun placeholderPassesTheOpaqueIdToTheResolver() {
        val seen = mutableListOf<String>()
        val stream = Uri.parse("https://example.test/song.mp3")
        val wrapped = ByteArrayDataSource(byteArrayOf(1, 2))
        val source = RedirectingDataSource(wrapped) { mediaId ->
            seen += mediaId
            stream.toString()
        }

        for (id in listOf("123", "qq:track-mid")) {
            val placeholder = Uri.parse("redefinencm://playbackPlaceHolder?id=$id")
            try {
                assertEquals(2L, source.open(DataSpec(placeholder)))
                assertEquals(stream, wrapped.uri)
            } finally {
                source.close()
            }
        }
        assertEquals(listOf("123", "qq:track-mid"), seen)
    }

    @Test
    fun anOrdinaryUriStillReachesTheWrappedSourceUnchanged() {
        val uri = Uri.parse("https://example.test/song.mp3?id=123")
        val wrapped = ByteArrayDataSource(byteArrayOf(1, 2))
        val source = RedirectingDataSource(wrapped) { error("non-placeholder URI must not resolve") }

        try {
            assertEquals(2L, source.open(DataSpec(uri)))
            assertEquals(uri, wrapped.uri)
        } finally {
            source.close()
        }
    }
}

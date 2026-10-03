package com.leejlredstar.redefinencm.kmp.util

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmSongDownloadIntegrityTest {
    private suspend fun response(
        status: Int = 200,
        declared: Long,
        bytes: ByteArray,
        block: suspend (DownloadRequestItem, File) -> Unit,
    ) {
        val directory = Files.createTempDirectory("song-integrity-test").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/song.mp3") { exchange ->
            exchange.sendResponseHeaders(status, declared)
            runCatching { exchange.responseBody.use { it.write(bytes) } }
            exchange.close()
        }
        server.start()
        try {
            block(DownloadRequestItem(
                987L, "standard", "standard", "Test", "Test", "",
                "http://127.0.0.1:${server.address.port}/song.mp3",
            ), directory)
        } finally {
            server.stop(0)
            assertTrue(directory.deleteRecursively())
        }
    }

    @Test
    fun truncatedHttpContentNeverReachesThePublishPhase() = runTest {
        response(declared = 10, bytes = byteArrayOf(1, 2, 3)) { request, dir ->
            var published = false
            assertTrue(runCatching {
                downloadSongToDirectory(request, dir, { _, _ -> }, { published = true; true })
            }.isFailure)
            assertFalse(published)
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun emptyAndUnrequestedPartialResponsesCannotBecomeDownloadedSongs() = runTest {
        for (status in listOf(200, 204, 206)) {
            response(status, declared = -1, bytes = byteArrayOf()) { request, dir ->
                var published = false
                assertTrue(runCatching {
                    downloadSongToDirectory(request, dir, { _, _ -> }, { published = true; true })
                }.isFailure)
                assertFalse(published)
                assertTrue(dir.listFiles().orEmpty().isEmpty())
            }
        }
    }

    @Test
    fun aChunkedResponseUsesTheBytesReceivedRatherThanTheAdvisoryApiSize() = runTest {
        response(declared = 0, bytes = byteArrayOf(1, 2, 3)) { request, dir ->
            var finalProgress: Pair<Long, Long?>? = null
            var published = false
            val result = downloadSongToDirectory(request.copy(expectedBytes = 999L), dir,
                { received, total -> finalProgress = received to total }, {
                    assertEquals(3L to 3L, finalProgress)
                    published = true
                    true
                })
            assertTrue(published)
            assertEquals(3L to 3L, finalProgress)
            assertEquals(listOf<Byte>(1, 2, 3), dir.resolve(result.fileName).readBytes().toList())
            assertEquals(1, dir.listFiles().orEmpty().size)
        }
    }

    @Test
    fun anEmptyFileLeftByAnOlderVersionIsReplacedOnlyAfterACompleteDownload() = runTest {
        response(declared = 3, bytes = byteArrayOf(1, 2, 3)) { request, dir ->
            dir.resolve("987.mp3").writeBytes(byteArrayOf())
            downloadSongToDirectory(request, dir, { _, _ -> }, { true })
            assertEquals(3L, dir.resolve("987.mp3").length())
        }
    }

    @Test
    fun aRejectedPublishLeavesNoNewFile() = runTest {
        response(declared = 3, bytes = byteArrayOf(1, 2, 3)) { request, dir ->
            val error = runCatching {
                downloadSongToDirectory(request, dir, { _, _ -> }, { false })
            }.exceptionOrNull()
            assertTrue(error is CancellationException)
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        }
    }
}

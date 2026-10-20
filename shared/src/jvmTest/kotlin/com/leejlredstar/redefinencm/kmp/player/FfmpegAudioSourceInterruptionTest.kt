package com.leejlredstar.redefinencm.kmp.player

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A stream that stops before its track must not pass for the track's end: the player would play
 * the next song early, or end the queue at what looked like the last song's end.
 */
class FfmpegAudioSourceInterruptionTest {

    private fun wave(seconds: Int): ByteArray {
        val frames = Rate * seconds
        val pcm = ByteArray(frames * 2)
        for (frame in 0 until frames) {
            val sample = if (frame % 40 < 20) 4_000 else -4_000
            pcm[frame * 2] = sample.toByte()
            pcm[frame * 2 + 1] = (sample shr 8).toByte()
        }
        val format = AudioFormat(Rate.toFloat(), 16, 1, true, false)
        return ByteArrayOutputStream().also { out ->
            AudioInputStream(ByteArrayInputStream(pcm), format, frames.toLong()).use {
                AudioSystem.write(it, AudioFileFormat.Type.WAVE, out)
            }
        }.toByteArray()
    }

    /** Serves [body] while declaring [declared] bytes, then closes the connection. */
    private fun <T> serving(body: ByteArray, declared: Long, block: (String) -> T): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/song.wav") { exchange ->
            exchange.responseHeaders.add("Content-Type", "audio/wav")
            exchange.sendResponseHeaders(200, declared)
            runCatching { exchange.responseBody.use { it.write(body) } }
            exchange.close()
        }
        server.start()
        try {
            return block("http://127.0.0.1:${server.address.port}/song.wav")
        } finally {
            server.stop(0)
        }
    }

    private fun FfmpegAudioSource.readToTheEnd(): Long {
        val buffer = FloatArray(4_096 * channels)
        var total = 0L
        while (true) {
            val got = read(buffer, 0, 4_096)
            if (got == 0) return total
            total += got
        }
    }

    @Test
    fun aStreamThatStopsShortOfItsLengthIsInterrupted() {
        val wave = wave(seconds = 6)
        serving(wave.copyOf(wave.size / 2), declared = wave.size.toLong()) { url ->
            FfmpegAudioSource.open(url).use { source ->
                val frames = source.readToTheEnd()
                assertTrue(frames < Rate * 6L, "read $frames frames of a stream cut in half")
                assertTrue(source.interrupted)
            }
        }
    }

    @Test
    fun aStreamThatEndsWhereItSaidIsNot() {
        val wave = wave(seconds = 3)
        serving(wave, declared = wave.size.toLong()) { url ->
            FfmpegAudioSource.open(url).use { source ->
                assertEquals(Rate * 3L, source.readToTheEnd())
                assertFalse(source.interrupted)
            }
        }
    }

    @Test
    fun aLocalFileReadToItsEndIsNot() {
        val file = Files.createTempFile("interruption", ".wav").toFile()
        try {
            file.writeBytes(wave(seconds = 2))
            FfmpegAudioSource.open(file.toURI().toString()).use { source ->
                assertEquals(Rate * 2L, source.readToTheEnd())
                assertFalse(source.interrupted)
            }
        } finally {
            file.delete()
        }
    }

    /** Java Sound's Windows lines take one or two channels: a surround master is downmixed. */
    @Test
    fun aSixChannelTrackIsDecodedToStereo() {
        val frames = Rate * 2
        val pcm = ByteArray(frames * 6 * 2)
        for (frame in 0 until frames) {
            val sample = if (frame % 40 < 20) 8_000 else -8_000
            // Front left only: what reaches the stereo pair must come from the downmix.
            pcm[frame * 12] = sample.toByte()
            pcm[frame * 12 + 1] = (sample shr 8).toByte()
        }
        val file = Files.createTempFile("surround", ".wav").toFile()
        try {
            val format = AudioFormat(Rate.toFloat(), 16, 6, true, false)
            AudioInputStream(ByteArrayInputStream(pcm), format, frames.toLong()).use {
                AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
            }
            FfmpegAudioSource.open(file.toURI().toString(), forcedChannels = 2).use { source ->
                assertEquals(2, source.channels)
                val buffer = FloatArray(4_096 * 2)
                val got = source.read(buffer, 0, 4_096)
                assertTrue(got > 0)
                assertTrue((0 until got * 2).any { kotlin.math.abs(buffer[it]) > 0.05f }, "the downmix is silent")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun aSelectionReopensAFewTimesInARowAndAgainAfterPlayingOn() {
        val budget = StreamReopenBudget(limit = 2, progressMs = 10_000L)
        assertTrue(budget.allow(selectionRevision = 1, positionMs = 30_000))
        assertTrue(budget.allow(selectionRevision = 1, positionMs = 31_000))
        assertFalse(budget.allow(selectionRevision = 1, positionMs = 32_000))
        // Played on: the connection was good for a while, so it may fail again.
        assertTrue(budget.allow(selectionRevision = 1, positionMs = 45_000))
        // Another selection starts its own count.
        assertTrue(budget.allow(selectionRevision = 2, positionMs = 0))
    }

    private companion object {
        const val Rate = 8_000
    }
}

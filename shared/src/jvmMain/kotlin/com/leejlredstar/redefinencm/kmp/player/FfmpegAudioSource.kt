package com.leejlredstar.redefinencm.kmp.player

import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.net.URI
import java.nio.FloatBuffer
import java.nio.file.Paths
import javax.sound.sampled.AudioFormat

/**
 * Interleaved float PCM decoded out of anything FFmpeg can open, which on desktop is every stream
 * and every download this app produces: MP3, FLAC, M4A, and the Hi-Res masters above them.
 *
 * Java Sound's own SPI decoding is not used. `AudioSystem.getAudioInputStream()` reaches only
 * MP3, through mp3spi, so every lossless tier would have to be downgraded to a 320k MP3 before
 * it was requested. That path also decodes through JLayer, whose tables are loaded with a
 * package-relative `Class.getResourceAsStream()` that release obfuscation silently breaks.
 *
 * Samples come out as float so the player can mix two tracks and change one's tempo during a
 * song transition. [format] is the 16-bit format they are written to the device in.
 *
 * FFmpeg is already shipped for the dynamic-cover decoder, so this adds no new native payload.
 */
internal class FfmpegAudioSource private constructor(
    private val grabber: FFmpegFrameGrabber,
    /** Always PCM_SIGNED / 16-bit / little-endian; only the rate and channel count vary. */
    val format: AudioFormat,
    /** Track length in milliseconds, or -1 when the container does not declare one. */
    val durationMs: Long,
) : AutoCloseable {

    val channels: Int get() = format.channels
    val sampleRate: Int get() = format.sampleRate.toInt()

    private var pending: FloatArray? = null
    private var pendingOffset = 0
    private var awaitingLanding = false

    /**
     * The decoder's timestamp for the first sample after the last [seekTo], in milliseconds, once
     * it has been read. For an MP3 it is off from the decoded-from-the-start timeline by the
     * encoder delay; [FfmpegTrackEndsDecoder] measures that offset and corrects for it.
     */
    var landedAtMs: Double? = null
        private set

    /**
     * Fills [target] from frame [offset] with up to [frames] interleaved frames; returns how many
     * were written, fewer only once the track is decoded to its end.
     */
    fun read(target: FloatArray, offset: Int, frames: Int): Int {
        var written = 0
        while (written < frames) {
            val block = pending ?: nextBlock() ?: break
            val available = (block.size - pendingOffset) / channels
            val take = minOf(available, frames - written)
            block.copyInto(
                target,
                (offset + written) * channels,
                pendingOffset,
                pendingOffset + take * channels,
            )
            written += take
            pendingOffset += take * channels
            if (pendingOffset >= block.size) {
                pending = null
                pendingOffset = 0
            }
        }
        return written
    }

    /**
     * The next decoded block. Frames arrive on a direct buffer FFmpeg reuses for the next grab,
     * so every block is copied out. Grabs that carry no samples are skipped rather than reported
     * as the end of the track: a container can hand back metadata-only frames mid-stream.
     */
    private fun nextBlock(): FloatArray? {
        while (true) {
            val frame = grabber.grabSamples() ?: return null
            val samples = frame.samples ?: continue
            if (samples.isEmpty()) continue
            // AV_SAMPLE_FMT_FLT is a packed format, so swresample always emits exactly one
            // plane. Fail loudly if that ever stops holding: silently taking plane 0 of a
            // planar frame would play one channel at double speed.
            check(samples.size == 1) {
                "Expected packed PCM from FFmpeg, got ${samples.size} planes"
            }
            val buffer = samples[0] as? FloatBuffer ?: continue
            val pcm = buffer.duplicate()
            val count = pcm.remaining() - pcm.remaining() % channels
            if (count == 0) continue
            if (awaitingLanding) {
                awaitingLanding = false
                landedAtMs = frame.timestamp / 1_000.0
            }
            val block = FloatArray(count)
            pcm.get(block)
            pending = block
            pendingOffset = 0
            return block
        }
    }

    /**
     * Decodes and drops [frames] frames. Slower than [seekTo] but exact: a position reached by
     * decoding from the start is the same position analysis measured, while a seek into an MP3
     * lands a frame's worth of encoder delay away from it.
     */
    fun skip(frames: Long) {
        val scratch = FloatArray(4_096 * channels)
        var left = frames
        while (left > 0) {
            val got = read(scratch, 0, minOf(left, 4_096L).toInt())
            if (got == 0) return
            left -= got
        }
    }

    /** Seeks the decoder itself, which lands on a real frame boundary at any bit rate. */
    fun seekTo(positionMs: Long) {
        pending = null
        pendingOffset = 0
        landedAtMs = null
        awaitingLanding = true
        grabber.setAudioTimestamp(positionMs.coerceAtLeast(0L) * 1_000L)
    }

    override fun close() {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }

    companion object {
        /**
         * The rate to fall back to when an output line refuses the track's own.
         *
         * 48 kHz is what every current endpoint mixes at, so a master that arrives at 192 kHz
         * still plays instead of failing the track outright.
         */
        const val FallbackSampleRate: Int = 48_000

        /** Bounds a stalled CDN rather than parking the playback thread on it forever. */
        private const val NetworkTimeoutMicros = 15_000_000L

        /**
         * Opens [source] (a local file URI or a CDN URL) positioned at [startMs].
         *
         * [forcedSampleRate] and [forcedChannels] make FFmpeg convert on the way out. Leaving
         * them null keeps the track's own, so a Hi-Res tier is not quietly downsampled on a
         * device that can take it. A song transition forces the incoming track to the outgoing
         * one's format, because both are mixed into one line.
         */
        fun open(
            source: String,
            startMs: Long = 0L,
            forcedSampleRate: Int? = null,
            forcedChannels: Int? = null,
        ): FfmpegAudioSource {
            val grabber = FFmpegFrameGrabber(ffmpegAudioInput(source)).apply {
                sampleMode = FrameGrabber.SampleMode.FLOAT
                sampleFormat = avutil.AV_SAMPLE_FMT_FLT
                setOption("rw_timeout", NetworkTimeoutMicros.toString())
                forcedSampleRate?.let { sampleRate = it }
                forcedChannels?.let { audioChannels = it }
            }
            val opened = runCatching {
                grabber.start()
                val channels = grabber.audioChannels
                val rate = grabber.sampleRate
                require(channels > 0 && rate > 0) {
                    "No audio stream in $source (channels=$channels rate=$rate)"
                }
                FfmpegAudioSource(
                    grabber = grabber,
                    format = AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        rate.toFloat(),
                        16,
                        channels,
                        channels * 2,
                        rate.toFloat(),
                        false,
                    ),
                    durationMs = grabber.lengthInTime.takeIf { it > 0L }?.div(1_000L) ?: -1L,
                )
            }.getOrElse { failure ->
                runCatching { grabber.release() }
                throw failure
            }
            if (startMs > 0L) opened.seekTo(startMs)
            return opened
        }
    }
}

/**
 * Writes [frames] interleaved float frames from [source] into [target] as signed 16-bit
 * little-endian, clipping at full scale; returns the byte count.
 */
internal fun floatToPcm16(source: FloatArray, frames: Int, channels: Int, target: ByteArray): Int {
    val samples = frames * channels
    var at = 0
    for (i in 0 until samples) {
        val scaled = source[i].coerceIn(-1f, 1f) * 32767f
        val value = (if (scaled >= 0f) scaled + 0.5f else scaled - 0.5f).toInt()
        target[at++] = value.toByte()
        target[at++] = (value shr 8).toByte()
    }
    return at
}

/**
 * Hands FFmpeg a plain filesystem path for local files, and the URL as-is for streams.
 *
 * Offline downloads arrive as the `file:/C:/...` form `File.toURI()` produces, and
 * `avformat_open_input()` rejects that with EINVAL. Anything that is not a file: URI, notably
 * the CDN's http(s) URLs, is passed through untouched, and a file: URI that will not parse is
 * left alone so FFmpeg reports the real failure rather than this function inventing one.
 */
internal fun ffmpegAudioInput(source: String): String {
    val trimmed = source.trim()
    if (!trimmed.startsWith("file:", ignoreCase = true)) return trimmed
    return runCatching { Paths.get(URI(trimmed)).toString() }.getOrDefault(trimmed)
}

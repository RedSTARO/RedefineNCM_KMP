package com.leejlredstar.redefinencm.kmp.player

import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat

/**
 * Signed 16-bit PCM decoded out of anything FFmpeg can open, which on desktop is every stream
 * and every download this app produces — MP3, FLAC, M4A, and the Hi-Res masters above them.
 *
 * This replaced Java Sound's own SPI decoding. `AudioSystem.getAudioInputStream()` could only
 * reach MP3, through mp3spi, which is why every lossless tier used to be downgraded to a 320k
 * MP3 before it was ever requested. It also decoded through JLayer, whose tables are loaded with
 * a package-relative `Class.getResourceAsStream()` that release obfuscation silently broke.
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

    /**
     * The next block of interleaved PCM, or null once the track is decoded.
     *
     * Frames arrive on a direct buffer FFmpeg reuses for the next grab, so every block is copied
     * out before returning. Grabs that carry no samples are skipped rather than reported as the
     * end of the track: a container can hand back metadata-only frames mid-stream.
     */
    fun read(): ByteArray? {
        while (true) {
            val frame = grabber.grabSamples() ?: return null
            val samples = frame.samples ?: continue
            if (samples.isEmpty()) continue
            // AV_SAMPLE_FMT_S16 is a packed format, so swresample always emits exactly one
            // plane. Fail loudly if that ever stops holding — silently taking plane 0 of a
            // planar frame would play one channel at double speed.
            check(samples.size == 1) {
                "Expected packed PCM from FFmpeg, got ${samples.size} planes"
            }
            val buffer = samples[0] as? ShortBuffer ?: continue
            val pcm = buffer.duplicate()
            val count = pcm.remaining()
            if (count == 0) continue
            val bytes = ByteArray(count * 2)
            var at = 0
            while (pcm.hasRemaining()) {
                val sample = pcm.get().toInt()
                bytes[at++] = sample.toByte()
                bytes[at++] = (sample shr 8).toByte()
            }
            return bytes
        }
    }

    /** Seeks the decoder itself, which lands on a real frame boundary at any bit rate. */
    fun seekTo(positionMs: Long) {
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
         * Opens [source] — a local file URI or a CDN URL — positioned at [startMs].
         *
         * [forcedSampleRate] makes FFmpeg resample on the way out; leaving it null keeps the
         * track's own rate so a Hi-Res tier is not quietly downsampled on a device that can
         * take it.
         */
        fun open(
            source: String,
            startMs: Long = 0L,
            forcedSampleRate: Int? = null,
        ): FfmpegAudioSource {
            val grabber = FFmpegFrameGrabber(source).apply {
                sampleMode = FrameGrabber.SampleMode.SHORT
                sampleFormat = avutil.AV_SAMPLE_FMT_S16
                setOption("rw_timeout", NetworkTimeoutMicros.toString())
                forcedSampleRate?.let { sampleRate = it }
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

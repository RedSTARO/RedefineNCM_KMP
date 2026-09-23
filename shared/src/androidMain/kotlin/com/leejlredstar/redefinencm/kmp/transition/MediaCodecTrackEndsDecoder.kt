package com.leejlredstar.redefinencm.kmp.transition

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MediaExtractorCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * Decodes a track's two ends for analysis with the extractors ExoPlayer plays through (Media3's
 * [MediaExtractorCompat]) and the platform's MediaCodec, resampled to the beat model's 22 050 Hz
 * mono by [MonoResampler].
 *
 * The extractors are ExoPlayer's so that the analysis timeline is the one playback reports:
 * ExoPlayer trims a track's encoder delay, the few dozen milliseconds of priming an MP3 or AAC
 * encoder puts before the music, and so does this decoder, by the amount the same extractor
 * reports. The head is decoded from the start. The tail is reached with a seek, which over HTTP
 * is a range request, so a CDN stream costs its two ends and not the whole file. A seek into an
 * MP3 lands a frame or so away from where it claims, so the decoder first seeks once inside the
 * head it already has, finds by matching samples where it really landed ([measureSeekErrorMs]),
 * and moves the tail's start by the same error.
 */
@OptIn(UnstableApi::class)
internal class MediaCodecTrackEndsDecoder(private val context: Context) : TrackEndsDecoder {
    override suspend fun decode(url: String, sectionMs: Long, knownDurationMs: Long): DecodedTrackEnds? =
        withContext(Dispatchers.IO) {
            val scope = this
            try {
                TrackReader(context, url) { scope.ensureActive() }.use { reader ->
                    decodeEnds(reader, sectionMs, knownDurationMs)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                println("MediaCodecTrackEndsDecoder: ${failure.javaClass.simpleName}: ${failure.message}")
                null
            }
        }

    private fun decodeEnds(reader: TrackReader, sectionMs: Long, knownDurationMs: Long): DecodedTrackEnds {
        val duration = reader.durationMs.takeIf { it > 0L } ?: knownDurationMs.takeIf { it > 0L } ?: -1L
        val head = AnalysisWindow(0L, reader.read(framesFor(sectionMs), skipSourceFrames = reader.encoderDelayFrames))
        if (duration <= 0L) return DecodedTrackEnds(-1L, head, null)
        val tailStart = duration - sectionMs
        if (tailStart <= head.lengthMs) {
            // Short enough that the head already reaches the end: decode the rest and use the
            // whole track for both.
            val rest = reader.read(framesFor(duration - head.lengthMs + TAIL_MARGIN_MS))
            val whole = AnalysisWindow(0L, head.samples + rest)
            return DecodedTrackEnds(duration, whole, whole)
        }
        val seekErrorMs = measureSeekError(reader, head.samples) ?: 0.0
        val landedMs = reader.seekTo(tailStart)
        val samples = reader.read(framesFor(sectionMs + TAIL_MARGIN_MS))
        return DecodedTrackEnds(duration, head, AnalysisWindow((landedMs - seekErrorMs).roundToLong(), samples))
    }

    /** How far this stream's seek timestamps are from its decoded-from-the-start timeline. */
    private fun measureSeekError(reader: TrackReader, head: FloatArray): Double? {
        if (head.size < framesFor(PROBE_AT_MS + PROBE_SETTLE_MS + PROBE_LENGTH_MS + 200)) return null
        val landedMs = reader.seekTo(PROBE_AT_MS)
        // The first frames after a seek can still be warming up the decoder; compare later ones.
        reader.read(framesFor(PROBE_SETTLE_MS))
        val probe = reader.read(framesFor(PROBE_LENGTH_MS))
        return measureSeekErrorMs(head, probe, landedMs + PROBE_SETTLE_MS, RATE)
    }

    private fun framesFor(ms: Long): Int = (ms * RATE / 1_000L).toInt()

    private companion object {
        const val RATE = BeatModelFeatures.SAMPLE_RATE_HZ

        /** A duration from the container can be a little short; read past it to the real end. */
        const val TAIL_MARGIN_MS = 3_000L

        const val PROBE_AT_MS = 20_000L
        const val PROBE_SETTLE_MS = 200L
        const val PROBE_LENGTH_MS = 1_000L
    }
}

/**
 * One audio track, decoded on demand into 22 050 Hz mono. Times are on the playback timeline,
 * which starts after the encoder delay.
 */
@OptIn(UnstableApi::class)
private class TrackReader(
    context: Context,
    url: String,
    private val checkCancelled: () -> Unit,
) : AutoCloseable {
    private val extractor = MediaExtractorCompat(context)
    private val codec: MediaCodec
    private val sourceRate: Int

    /** Container duration without the encoder delay; -1 when the container does not say. */
    val durationMs: Long

    /** Frames of priming ExoPlayer drops from the start of this track, at the source rate. */
    val encoderDelayFrames: Int

    private var inputDone = false
    private var outputDone = false
    private var resampler: MonoResampler? = null
    private var outputChannels = 0
    private var floatOutput = false
    private var pending = FloatArray(0)
    private val info = MediaCodec.BufferInfo()

    init {
        extractor.setDataSource(context, Uri.parse(url), emptyMap())
        val track = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            error("no audio track")
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        encoderDelayFrames = format.intOr(KEY_ENCODER_DELAY, 0).coerceAtLeast(0)
        durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION) / 1_000L - encoderDelayFrames * 1_000L / sourceRate
        } else {
            -1L
        }
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        // Float output where the decoder offers it; a decoder that ignores the request answers
        // with 16-bit PCM, and the output format says which.
        format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
        codec = try {
            MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
        } catch (failure: Throwable) {
            extractor.release()
            throw failure
        }
    }

    /**
     * Seeks to [positionMs] on the playback timeline and returns where the extractor claims the
     * next sample starts, on the same timeline.
     */
    fun seekTo(positionMs: Long): Double {
        val delayUs = encoderDelayFrames * 1_000_000L / sourceRate
        extractor.seekTo(positionMs * 1_000L + delayUs, MediaExtractorCompat.SEEK_TO_PREVIOUS_SYNC)
        codec.flush()
        inputDone = false
        outputDone = false
        resampler = null
        pending = FloatArray(0)
        return (extractor.sampleTime - delayUs) / 1_000.0
    }

    /**
     * The next [frames] frames of 22 050 Hz mono, fewer at the end of the track, after dropping
     * [skipSourceFrames] decoded frames at the source rate.
     */
    fun read(frames: Int, skipSourceFrames: Int = 0): FloatArray {
        val out = FloatArray(frames)
        var produced = 0
        var toSkip = skipSourceFrames
        fun take(mono: FloatArray) {
            val used = minOf(mono.size, frames - produced)
            mono.copyInto(out, produced, 0, used)
            produced += used
            if (used < mono.size) pending = mono.copyOfRange(used, mono.size)
        }
        if (pending.isNotEmpty()) {
            val carried = pending
            pending = FloatArray(0)
            take(carried)
        }
        while (produced < frames && !outputDone) {
            checkCancelled()
            if (!inputDone) queueInput()
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> configureOutput(codec.outputFormat)
                index >= 0 -> {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    val interleaved = decodedSamples(index)
                    codec.releaseOutputBuffer(index, false)
                    val channels = outputChannels
                    var available = interleaved.size / channels
                    var offset = 0
                    if (toSkip > 0) {
                        val drop = minOf(toSkip, available)
                        toSkip -= drop
                        offset = drop * channels
                        available -= drop
                    }
                    if (available > 0) {
                        val block = if (offset == 0) interleaved else interleaved.copyOfRange(offset, interleaved.size)
                        take(resampler!!.process(block, available))
                    }
                }
            }
        }
        return if (produced == frames) out else out.copyOf(produced)
    }

    private fun queueInput() {
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return
        val buffer = codec.getInputBuffer(index) ?: return
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    private fun configureOutput(format: MediaFormat) {
        outputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        floatOutput = format.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) ==
            AudioFormat.ENCODING_PCM_FLOAT
        resampler = MonoResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), RATE, outputChannels)
    }

    private fun decodedSamples(index: Int): FloatArray {
        if (resampler == null) configureOutput(codec.outputFormat)
        val buffer = codec.getOutputBuffer(index) ?: return FloatArray(0)
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val ordered = buffer.slice().order(ByteOrder.nativeOrder())
        return if (floatOutput) {
            val floats = ordered.asFloatBuffer()
            FloatArray(floats.remaining()).also(floats::get)
        } else {
            val shorts = ordered.asShortBuffer()
            FloatArray(shorts.remaining()) { shorts.get(it) / 32_768f }
        }
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
        extractor.release()
    }

    private companion object {
        const val RATE = BeatModelFeatures.SAMPLE_RATE_HZ
        const val TIMEOUT_US = 10_000L

        /** MediaFormat.KEY_ENCODER_DELAY, which only has a name from API 29. */
        const val KEY_ENCODER_DELAY = "encoder-delay"
    }
}

private fun MediaFormat.intOr(key: String, fallback: Int): Int =
    if (containsKey(key)) getInteger(key) else fallback

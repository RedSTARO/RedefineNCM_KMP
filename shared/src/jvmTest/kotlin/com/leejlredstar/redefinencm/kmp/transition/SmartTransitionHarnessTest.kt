package com.leejlredstar.redefinencm.kmp.transition

import com.leejlredstar.redefinencm.kmp.player.DeckMixer
import com.leejlredstar.redefinencm.kmp.player.FfmpegAudioSource
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlaybackClock
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end check against real music, run by hand on a machine that has some:
 *
 *     gradlew :shared:jvmTest --tests "*SmartTransitionHarnessTest*" \
 *         -Dredefinencm.harness.audio=C:/Users/me/Music/RedefineNCM -Dredefinencm.harness.out=C:/tmp
 *
 * Without `redefinencm.harness.audio` every test returns at once, which is what CI sees. With it,
 * the beat model must load on an NPU or GPU, the tail window must start where the decoder says it
 * does, and a planned transition between two tracks is rendered to a WAV file for listening.
 */
class SmartTransitionHarnessTest {
    private val audioDirectory = System.getProperty("redefinencm.harness.audio")?.let(::File)
    private val outputDirectory = System.getProperty("redefinencm.harness.out")?.let(::File)

    private fun tracks(): List<File> =
        audioDirectory?.listFiles { file -> file.extension.lowercase() in setOf("mp3", "flac") }
            ?.sortedBy { it.name }
            .orEmpty()

    @Test
    fun beatModelRunsOnAnAccelerator() = runBlocking {
        if (audioDirectory == null) return@runBlocking
        val availability = OnnxBeatModelLoader().load()
        println("beat model: $availability")
        assertTrue(availability is BeatModelAvailability.Ready, "beat model unavailable: $availability")
        val model = availability.model
        val started = System.nanoTime()
        repeat(8) { model.infer(FloatArray(BEAT_MODEL_CHUNK_FRAMES * BeatModelFeatures.MEL_BINS)) }
        println("beat model on ${model.accelerator} (${model.deviceLabel}): " +
            "${(System.nanoTime() - started) / 8 / 1_000_000} ms per 15 s chunk")
    }

    @Test
    fun tailWindowStartsWhereTheDecoderLanded() = runBlocking {
        val file = tracks().firstOrNull { it.extension.lowercase() == "mp3" } ?: return@runBlocking
        val url = file.toURI().toString()
        val ends = FfmpegTrackEndsDecoder().decode(url, TrackAnalysis.SECTION_MS, -1L)!!
        val tail = ends.tail!!
        // Decode the whole file from the start and find where the tail's samples really are.
        val full = FfmpegAudioSource.open(url, 0L, BeatModelFeatures.SAMPLE_RATE_HZ, 1).use { source ->
            val frames = ((ends.durationMs + 5_000) * BeatModelFeatures.SAMPLE_RATE_HZ / 1000).toInt()
            val buffer = FloatArray(frames)
            buffer.copyOf(source.read(buffer, 0, frames))
        }
        val claimed = (tail.startMs * BeatModelFeatures.SAMPLE_RATE_HZ / 1000).toInt()
        val probe = tail.samples.copyOfRange(BeatModelFeatures.SAMPLE_RATE_HZ, BeatModelFeatures.SAMPLE_RATE_HZ * 3)
        var bestShift = 0
        var bestError = Double.MAX_VALUE
        for (shift in -2_000..2_000) {
            val at = claimed + BeatModelFeatures.SAMPLE_RATE_HZ + shift
            var error = 0.0
            for (i in probe.indices step 4) error += abs(full[at + i] - probe[i])
            if (error < bestError) {
                bestError = error
                bestShift = shift
            }
        }
        val offsetMs = bestShift * 1000.0 / BeatModelFeatures.SAMPLE_RATE_HZ
        println("tail of ${file.name}: claimed ${tail.startMs} ms, real offset ${"%.2f".format(offsetMs)} ms")
        assertTrue(abs(offsetMs) <= 2.0, "tail window is ${"%.1f".format(offsetMs)} ms from where it claims to start")
    }

    @Test
    fun rendersAPlannedTransitionBetweenTwoRealTracks() = runBlocking {
        val files = tracks()
        if (files.size < 2 || outputDirectory == null) return@runBlocking
        val loader = OnnxBeatModelLoader()
        val analyzer = TrackAnalyzer(
            decoder = FfmpegTrackEndsDecoder(),
            urls = AnalysisUrlResolver { media -> media.placeholderUri },
            modelLoader = loader,
        )
        // Try pairs until one plans a beat-matched blend, so the render exercises the stretcher.
        var rendered = 0
        for (index in 0 until minOf(files.size - 1, 40)) {
            val outgoingFile = files[index]
            val incomingFile = files[index + 1]
            val outgoing = MediaInfo(outgoingFile.nameWithoutExtension, outgoingFile.name, "", albumTitle = "a",
                placeholderUri = outgoingFile.toURI().toString())
            val incoming = MediaInfo(incomingFile.nameWithoutExtension, incomingFile.name, "", albumTitle = "b",
                placeholderUri = incomingFile.toURI().toString())
            val outAnalysis = analyzer.analyze(outgoing) ?: continue
            val inAnalysis = analyzer.analyze(incoming) ?: continue
            val planned = TransitionPlanner.plan(
                outgoingMediaId = outgoing.id,
                outgoingAlbum = outgoing.albumTitle,
                outgoingDurationMs = outAnalysis.durationMs,
                outgoing = outAnalysis,
                incomingMediaId = incoming.id,
                incomingAlbum = incoming.albumTitle,
                incomingDurationMs = inAnalysis.durationMs,
                incoming = inAnalysis,
                mode = SongTransitionMode.SMART,
                crossfadeSeconds = 6,
                capability = TransitionCapability.TEMPO_MATCHED,
            )
            val tailGrid = outAnalysis.tail?.grid
            val headGrid = inAnalysis.head?.grid
            println("${outgoingFile.name} → ${incomingFile.name}: tail ${tailGrid?.bpm?.let { "%.1f".format(it) }} BPM " +
                "(conf ${tailGrid?.confidence?.let { "%.2f".format(it) }}, key ${outAnalysis.tail?.key}), " +
                "head ${headGrid?.bpm?.let { "%.1f".format(it) }} BPM (conf ${headGrid?.confidence?.let { "%.2f".format(it) }}, " +
                "key ${inAnalysis.head?.key}) on ${outAnalysis.beatAccelerator} → ${planned.note}: ${planned.plan}")
            val plan = planned.plan ?: continue
            if (plan.kind != TransitionKind.BEAT_MATCHED && rendered == 0 && index < 30) continue
            render(plan, outgoingFile, incomingFile, File(outputDirectory, "transition-${rendered + 1}-${plan.kind}.wav"))
            rendered += 1
            if (rendered >= 2) break
        }
        assertTrue(rendered > 0, "no pair of tracks produced a plan")
    }

    /** Renders from 20 s before the blend to 20 s after it, as the desktop player would. */
    private fun render(plan: TransitionPlan, outgoingFile: File, incomingFile: File, target: File) {
        val startMs = (plan.rampStartMs - 20_000L).coerceAtLeast(0L)
        val source = FfmpegAudioSource.open(outgoingFile.toURI().toString(), startMs)
        val rate = source.sampleRate
        val channels = source.channels
        val clock = PlaybackClock(rate)
        val mixer = DeckMixer(source, plan.outgoingMediaId, startMs, clock)
        mixer.armedPlan = plan
        val pcm = java.io.ByteArrayOutputStream()
        val block = FloatArray(2_048 * channels)
        val bytes = ByteArray(2_048 * channels * 2)
        var frames = 0L
        val limit = ((plan.startMs - startMs) / plan.outgoingRate + plan.overlapMs + 20_000L) * rate / 1000
        while (frames < limit) {
            val got = mixer.render(block, 2_048)
            if (got == 0) break
            val count = com.leejlredstar.redefinencm.kmp.player.floatToPcm16(block, got, channels, bytes)
            pcm.write(bytes, 0, count)
            frames += got
            mixer.planNeedingIncoming(12_000L)?.let { wanted ->
                mixer.offerIncoming(
                    wanted,
                    FfmpegAudioSource.open(incomingFile.toURI().toString(), wanted.incomingEntryMs, rate, channels),
                )
            }
        }
        val swap = generateSequence(0L) { it + rate / 100 }.takeWhile { it < frames }
            .firstOrNull { clock.at(it)?.mediaId == plan.incomingMediaId }
        mixer.close()
        val data = pcm.toByteArray()
        val format = AudioFormat(rate.toFloat(), 16, channels, true, false)
        AudioSystem.write(
            AudioInputStream(ByteArrayInputStream(data), format, (data.size / (2 * channels)).toLong()),
            AudioFileFormat.Type.WAVE,
            target,
        )
        println("rendered ${target.absolutePath}: ${frames / rate} s, swap at ${swap?.let { it / rate }} s")
        assertEquals(true, swap != null, "the blend never handed over")
    }
}

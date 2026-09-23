@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.transition

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import kotlin.js.Promise

/**
 * Decodes a track for analysis with the browser's own decoder: the file is fetched and handed to
 * `decodeAudioData` on an `OfflineAudioContext` at 22 050 Hz, which resamples it on the way, and
 * the channels are averaged to mono.
 *
 * A browser cannot fetch a range of a CDN file it may not read, so the whole file is fetched,
 * which is one reason analysis asks for the lowest quality. The fetch needs the CDN to allow this
 * page's origin; when it does not, the track simply has no analysis, and its transition is placed
 * without beat grids.
 */
internal class WebTrackEndsDecoder : TrackEndsDecoder {
    override suspend fun decode(url: String, sectionMs: Long, knownDurationMs: Long): DecodedTrackEnds? {
        val decoded = try {
            fetchAndDecodeMono(url, BeatModelFeatures.SAMPLE_RATE_HZ).await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            println("WebTrackEndsDecoder: ${failure.message}")
            return null
        }
        val total = decoded.length
        if (total == 0) return null
        val rate = BeatModelFeatures.SAMPLE_RATE_HZ
        val durationMs = total * 1_000L / rate
        val section = (sectionMs * rate / 1_000L).toInt().coerceAtMost(total)
        val head = AnalysisWindow(0L, FloatArray(section) { decoded[it] })
        val tailStart = total - section
        val tail = if (tailStart <= 0) {
            head
        } else {
            AnalysisWindow(tailStart * 1_000L / rate, FloatArray(section) { decoded[tailStart + it] })
        }
        return DecodedTrackEnds(durationMs, head, tail)
    }
}

@JsFun(
    """(url, rate) => fetch(url).then(response => {
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.arrayBuffer();
    }).then(buffer => new OfflineAudioContext(1, 1, rate).decodeAudioData(buffer))
      .then(audio => {
        const length = audio.length;
        const channels = audio.numberOfChannels;
        const mono = new Float32Array(length);
        for (let c = 0; c < channels; c++) {
            const data = audio.getChannelData(c);
            for (let i = 0; i < length; i++) mono[i] += data[i] / channels;
        }
        return mono;
      })""",
)
private external fun fetchAndDecodeMono(url: String, rate: Int): Promise<Float32Array>

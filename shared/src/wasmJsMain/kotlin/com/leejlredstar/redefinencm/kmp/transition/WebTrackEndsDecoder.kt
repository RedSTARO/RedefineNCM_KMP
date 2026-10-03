@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.transition

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.await
import kotlinx.coroutines.withContext
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import kotlin.js.Promise
import kotlin.js.JsAny

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
        val rate = BeatModelFeatures.SAMPLE_RATE_HZ
        val operation = fetchAndDecodeEnds(url, rate, (sectionMs * rate / 1_000L).toInt())
        val decoded = try {
            operation.result.await()
        } catch (cancelled: CancellationException) {
            operation.cancel()
            // Fetch can be aborted; decodeAudioData cannot. Keep the analysis slot until its
            // native work settles, so repeated skips cannot exceed the shared concurrency cap.
            withContext(NonCancellable) {
                try { operation.result.await() } catch (_: Throwable) { }
            }
            throw cancelled
        } catch (failure: Throwable) {
            println("WebTrackEndsDecoder: ${failure.message}")
            return null
        }
        if (decoded.head.length == 0) return null
        val head = AnalysisWindow(0L, FloatArray(decoded.head.length) { decoded.head[it] })
        val tail = if (decoded.tailStartMs <= 0) {
            head
        } else {
            AnalysisWindow(decoded.tailStartMs.toLong(), FloatArray(decoded.tail.length) { decoded.tail[it] })
        }
        return DecodedTrackEnds(decoded.durationMs.toLong(), head, tail)
    }
}

@JsFun(
    """(url, rate, sectionFrames) => {
      const controller = new AbortController();
      const checkCancelled = () => {
        if (controller.signal.aborted) throw new DOMException('Cancelled', 'AbortError');
      };
      const result = fetch(url, {signal: controller.signal, credentials: 'omit'}).then(response => {
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.arrayBuffer();
      }).then(buffer => {
        checkCancelled();
        return new OfflineAudioContext(1, 1, rate).decodeAudioData(buffer);
      })
      .then(audio => {
        checkCancelled();
        const length = audio.length;
        const channels = audio.numberOfChannels;
        const count = Math.min(length, Math.max(0, sectionFrames));
        const tailStart = length - count;
        // Only retain the two ends. A whole-track mono copy would duplicate a large AudioBuffer.
        const head = new Float32Array(count);
        const tail = tailStart === 0 ? head : new Float32Array(count);
        for (let c = 0; c < channels; c++) {
            const data = audio.getChannelData(c);
            for (let i = 0; i < count; i++) {
                head[i] += data[i] / channels;
                if (tail !== head) tail[i] += data[tailStart + i] / channels;
            }
        }
        return {head, tail, durationMs: length * 1000 / rate, tailStartMs: tailStart * 1000 / rate};
      });
      return { result, cancel: () => controller.abort() };
    }""",
)
private external fun fetchAndDecodeEnds(url: String, rate: Int, sectionFrames: Int): WebAudioDecodeOperation

private external interface WebAudioDecodeOperation : JsAny {
    val result: Promise<WebDecodedEnds>
    fun cancel()
}

private external interface WebDecodedEnds : JsAny {
    val head: Float32Array
    val tail: Float32Array
    val durationMs: Double
    val tailStartMs: Double
}

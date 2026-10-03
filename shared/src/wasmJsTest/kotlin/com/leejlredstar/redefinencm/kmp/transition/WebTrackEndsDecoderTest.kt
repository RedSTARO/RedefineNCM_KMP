@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.transition

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.math.abs

class WebTrackEndsDecoderTest {
    @Test
    fun decodingRetainsTheRequestedEndsWithTheirOriginalTimeline() = runTest {
        val rate = BeatModelFeatures.SAMPLE_RATE_HZ
        val url = makeAnalysisWaveUrl(rate)
        try {
            val result = assertNotNull(WebTrackEndsDecoder().decode(url, 2_000, 6_000))
            val head = assertNotNull(result.head)
            val tail = assertNotNull(result.tail)
            assertEquals(6_000L, result.durationMs)
            assertEquals(2_000L, head.lengthMs)
            assertEquals(2_000L, tail.lengthMs)
            assertEquals(4_000L, tail.startMs)
            assertTrue(abs(head.samples[100] - 0.25f) < 0.001f)
            assertTrue(abs(tail.samples[100] + 0.25f) < 0.001f)
        } finally { releaseWaveUrl(url) }
    }

    @Test
    fun cancellingAnalysisAbortsItsFetchInsteadOfLeavingItInTheBrowser() = runTest {
        val started = CompletableDeferred<Unit>()
        installWaitingFetch { started.complete(Unit) }
        val worker = launch { WebTrackEndsDecoder().decode("https://audio.invalid/test", 45_000, 180_000) }
        try {
            started.await()
            worker.cancelAndJoin()
            assertTrue(fetchWasAborted())
        } finally {
            worker.cancelAndJoin()
            restoreFetch()
        }
    }
}

@JsFun("""(started) => {
    const saved = globalThis.fetch;
    const fixture = { saved, aborted: false, reject: null };
    globalThis.__redefineAnalysisFetchTest = fixture;
    globalThis.fetch = (url, options) => new Promise((resolve, reject) => {
        fixture.reject = reject;
        options?.signal?.addEventListener('abort', () => {
            fixture.aborted = true;
            reject(new DOMException('Cancelled', 'AbortError'));
        }, {once:true});
        started();
    });
}""")
private external fun installWaitingFetch(started: () -> Unit)

@JsFun("() => globalThis.__redefineAnalysisFetchTest.aborted")
private external fun fetchWasAborted(): Boolean

@JsFun("""() => {
    const fixture = globalThis.__redefineAnalysisFetchTest;
    globalThis.fetch = fixture.saved;
    fixture.reject?.(new DOMException('Fixture finished', 'AbortError'));
    delete globalThis.__redefineAnalysisFetchTest;
}""")
private external fun restoreFetch()

@JsFun("""(rate) => {
    const frames = 6 * rate;
    const data = new DataView(new ArrayBuffer(44 + frames * 2));
    const text = (offset, value) => { for (let i=0; i<value.length; i++) data.setUint8(offset+i, value.charCodeAt(i)); };
    text(0, 'RIFF'); data.setUint32(4, 36 + frames * 2, true); text(8, 'WAVEfmt ');
    data.setUint32(16, 16, true); data.setUint16(20, 1, true); data.setUint16(22, 1, true);
    data.setUint32(24, rate, true); data.setUint32(28, rate * 2, true);
    data.setUint16(32, 2, true); data.setUint16(34, 16, true);
    text(36, 'data'); data.setUint32(40, frames * 2, true);
    for (let i=0; i<frames; i++) data.setInt16(44+i*2, i < 2*rate ? 8192 : (i < 4*rate ? 16384 : -8192), true);
    return URL.createObjectURL(new Blob([data.buffer], {type:'audio/wav'}));
}""")
private external fun makeAnalysisWaveUrl(rate: Int): String

@JsFun("(url) => URL.revokeObjectURL(url)")
private external fun releaseWaveUrl(url: String)

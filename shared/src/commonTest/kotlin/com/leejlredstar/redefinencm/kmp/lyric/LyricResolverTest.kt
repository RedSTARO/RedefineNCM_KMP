package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.data.api.dto.Lyric
import com.leejlredstar.redefinencm.kmp.data.api.dto.LyricLrc
import com.leejlredstar.redefinencm.kmp.util.LyricParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LyricResolverTest {
    @Test
    fun ttmlPreferredFallsBackToBackendOnNoMatch() = runTest {
        val calls = mutableListOf<LyricSource>()
        val resolver = LyricResolver(
            listOf(
                fakeProvider(LyricSource.AMLL_TTML, calls, LyricProviderResult.NoMatch),
                fakeProvider(LyricSource.NCM_BACKEND, calls, found(LyricSource.NCM_BACKEND)),
            ),
        )

        val results = mutableListOf<LyricResolution>()
        resolver.resolve(LyricQuery(songId = 1), LyricSourceMode.TTML_PREFERRED)
            .collect(results::add)

        assertEquals(
            listOf(LyricSource.AMLL_TTML, LyricSource.NCM_BACKEND),
            calls,
        )
        assertEquals(LyricSource.NCM_BACKEND, assertIs<LyricResolution.Found>(results.single()).document.source)
    }

    @Test
    fun onlyModeNeverTouchesOtherProvider() = runTest {
        val calls = mutableListOf<LyricSource>()
        val resolver = LyricResolver(
            listOf(
                fakeProvider(LyricSource.AMLL_TTML, calls, LyricProviderResult.NoMatch),
                fakeProvider(LyricSource.NCM_BACKEND, calls, found(LyricSource.NCM_BACKEND)),
            ),
        )

        val results = mutableListOf<LyricResolution>()
        resolver.resolve(LyricQuery(songId = 1), LyricSourceMode.TTML_ONLY)
            .collect(results::add)

        assertEquals(listOf(LyricSource.AMLL_TTML), calls)
        assertIs<LyricResolution.Empty>(results.single())
    }

    @Test
    fun firstFoundSourceShortCircuitsFallback() = runTest {
        val calls = mutableListOf<LyricSource>()
        val resolver = LyricResolver(
            listOf(
                fakeProvider(LyricSource.AMLL_TTML, calls, found(LyricSource.AMLL_TTML)),
                fakeProvider(LyricSource.NCM_BACKEND, calls, found(LyricSource.NCM_BACKEND)),
            ),
        )

        val results = mutableListOf<LyricResolution>()
        resolver.resolve(LyricQuery(songId = 1), LyricSourceMode.TTML_PREFERRED)
            .collect(results::add)

        assertEquals(listOf(LyricSource.AMLL_TTML), calls)
        assertEquals(LyricSource.AMLL_TTML, assertIs<LyricResolution.Found>(results.single()).document.source)
    }

    @Test
    fun transientFailureIsReportedWhenNoFallbackFindsLyrics() = runTest {
        val resolver = LyricResolver(
            listOf(
                object : LyricSourceProvider {
                    override val source = LyricSource.AMLL_TTML
                    override fun load(query: LyricQuery) =
                        flowOf(LyricProviderResult.Unavailable("TTML unavailable"))
                },
                object : LyricSourceProvider {
                    override val source = LyricSource.NCM_BACKEND
                    override fun load(query: LyricQuery) =
                        flowOf(LyricProviderResult.NoMatch)
                },
            ),
        )

        val results = mutableListOf<LyricResolution>()
        resolver.resolve(LyricQuery(songId = 1), LyricSourceMode.TTML_PREFERRED)
            .collect(results::add)

        assertEquals("TTML unavailable", assertIs<LyricResolution.Error>(results.single()).message)
    }

    @Test
    fun cancellationIsNeverConvertedToFallback() = runTest {
        val resolver = LyricResolver(
            listOf(
                object : LyricSourceProvider {
                    override val source = LyricSource.AMLL_TTML
                    override fun load(query: LyricQuery): Flow<LyricProviderResult> = flow {
                        throw CancellationException("track changed")
                    }
                },
            ),
        )

        assertFailsWith<CancellationException> {
            resolver.resolve(LyricQuery(songId = 1), LyricSourceMode.TTML_ONLY).collect { }
        }
    }

    @Test
    fun backendRetriesOnlyWhenFlowEmitsNothing() = runTest {
        var calls = 0
        val provider = BackendLyricProvider(
            lyricFlow = {
                calls += 1
                if (calls < 4) flow<Lyric?> { } else flowOf(
                    Lyric(lrc = LyricLrc(lyric = "[00:00.00]line")),
                )
            },
            retryDelayMillis = 0,
        )

        val results = mutableListOf<LyricProviderResult>()
        provider.load(LyricQuery(songId = 1)).collect(results::add)

        assertEquals(4, calls)
        assertIs<LyricProviderResult.Found>(results.single())
    }

    @Test
    fun backendDoesNotRetryAnExplicitNoMatch() = runTest {
        var calls = 0
        val provider = BackendLyricProvider(
            lyricFlow = {
                calls += 1
                flowOf(Lyric())
            },
            retryDelayMillis = 0,
        )

        val results = mutableListOf<LyricProviderResult>()
        provider.load(LyricQuery(songId = 1)).collect(results::add)

        assertEquals(1, calls)
        assertIs<LyricProviderResult.NoMatch>(results.single())
    }

    private fun fakeProvider(
        source: LyricSource,
        calls: MutableList<LyricSource>,
        result: LyricProviderResult,
    ): LyricSourceProvider = object : LyricSourceProvider {
        override val source = source
        override fun load(query: LyricQuery): Flow<LyricProviderResult> = flow {
            calls += source
            emit(result)
        }
    }

    private fun found(source: LyricSource): LyricProviderResult.Found =
        LyricProviderResult.Found(
            LyricDocument(
                source = source,
                lines = listOf(
                    LyricParser.WordLine(
                        startTimeMs = 0,
                        endTimeMs = 1_000,
                        words = listOf(LyricParser.Word(0, 1_000, "line")),
                    ),
                ),
            ),
        )
}

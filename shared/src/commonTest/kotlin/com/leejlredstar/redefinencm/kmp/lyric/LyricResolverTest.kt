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
import kotlin.test.assertNull

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
    fun matchingLocalSourceShortCircuitsItsUpstreamProvider() = runTest {
        val providerCalls = mutableListOf<LyricSource>()
        val localCalls = mutableListOf<LyricSource>()
        val localDocument = found(LyricSource.NCM_BACKEND).document
        val resolver = LyricResolver(
            providers = listOf(
                fakeProvider(
                    LyricSource.NCM_BACKEND,
                    providerCalls,
                    found(LyricSource.NCM_BACKEND),
                ),
            ),
            localLyricLoader = { _, source ->
                localCalls += source
                localDocument.takeIf { source == LyricSource.NCM_BACKEND }
            },
        )

        val result = resolver.resolveLatest(
            LyricQuery(songId = 1),
            LyricSourceMode.BACKEND_ONLY,
            preferLocal = true,
        )

        assertEquals(listOf(LyricSource.NCM_BACKEND), localCalls)
        assertEquals(emptyList(), providerCalls)
        assertEquals(
            LyricSource.NCM_BACKEND,
            assertIs<LyricResolution.Found>(result).document.source,
        )
    }

    @Test
    fun localUntimedTextStillChecksTheSameSourceForTimedLyrics() = runTest {
        val providerCalls = mutableListOf<LyricSource>()
        val resolver = LyricResolver(
            providers = listOf(
                fakeProvider(
                    LyricSource.NCM_BACKEND,
                    providerCalls,
                    found(LyricSource.NCM_BACKEND),
                ),
            ),
            localLyricLoader = { _, source ->
                untimed(source).takeIf { source == LyricSource.NCM_BACKEND }
            },
        )

        val result = resolver.resolveLatest(
            LyricQuery(songId = 1),
            LyricSourceMode.BACKEND_ONLY,
            preferLocal = true,
        )

        assertEquals(listOf(LyricSource.NCM_BACKEND), providerCalls)
        assertEquals(
            LyricCapabilityLevel.LINE_SYNCED,
            assertIs<LyricResolution.Found>(result).document.capabilityLevel,
        )
    }

    @Test
    fun onlyModeDoesNotReadLocalLyricsFromTheOtherSource() = runTest {
        val localCalls = mutableListOf<LyricSource>()
        val resolver = LyricResolver(
            providers = listOf(
                object : LyricSourceProvider {
                    override val source = LyricSource.AMLL_TTML
                    override fun load(query: LyricQuery) =
                        flowOf(LyricProviderResult.NoMatch)
                },
            ),
            localLyricLoader = { _, source ->
                localCalls += source
                found(LyricSource.NCM_BACKEND).document
                    .takeIf { source == LyricSource.NCM_BACKEND }
            },
        )

        val result = resolver.resolveLatest(
            LyricQuery(songId = 1),
            LyricSourceMode.TTML_ONLY,
            preferLocal = true,
        )

        assertEquals(listOf(LyricSource.AMLL_TTML), localCalls)
        assertIs<LyricResolution.Empty>(result)
    }

    @Test
    fun upstreamRefreshDoesNotReadExistingLocalSidecar() = runTest {
        var localRead = false
        val providerCalls = mutableListOf<LyricSource>()
        val resolver = LyricResolver(
            providers = listOf(
                fakeProvider(
                    LyricSource.NCM_BACKEND,
                    providerCalls,
                    found(LyricSource.NCM_BACKEND),
                ),
            ),
            localLyricLoader = { _, _ ->
                localRead = true
                found(LyricSource.NCM_BACKEND).document
            },
        )

        val result = resolver.resolveLatest(
            LyricQuery(songId = 1),
            LyricSourceMode.BACKEND_ONLY,
            preferLocal = false,
        )

        assertEquals(false, localRead)
        assertEquals(listOf(LyricSource.NCM_BACKEND), providerCalls)
        assertIs<LyricResolution.Found>(result)
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

    @Test
    fun backendCapabilityUsesTheRepresentationThatActuallyParsed() {
        val query = LyricQuery(songId = 1, durationMs = 5_000)
        val validYrc = backendLyricDocument(
            query = query,
            lrcText = "[00:00.00]閫愬瓧姝岃瘝",
            yrcText = "[0,1000](0,500,0)閫愬瓧",
            translatedText = "",
            romanText = "",
        )
        val invalidYrcWithLineFallback = backendLyricDocument(
            query = query,
            lrcText = "[00:00.00]閫愯姝岃瘝",
            yrcText = "invalid yrc payload",
            translatedText = "",
            romanText = "",
        )
        val lineOnly = backendLyricDocument(
            query = query,
            lrcText = "[00:00.00]鏅€氶€愯",
            yrcText = "",
            translatedText = "",
            romanText = "",
        )

        assertEquals(LyricCapabilityLevel.NCM_YRC, validYrc?.capabilityLevel)
        assertEquals(LyricCapabilityLevel.LINE_SYNCED, invalidYrcWithLineFallback?.capabilityLevel)
        assertEquals("invalid yrc payload", invalidYrcWithLineFallback?.rawWordLyric)
        assertEquals(LyricCapabilityLevel.LINE_SYNCED, lineOnly?.capabilityLevel)
    }

    @Test
    fun backendPlainTextIsUntimedButSupplementOnlyIsNotPrimaryLyrics() {
        val query = LyricQuery(songId = 1)
        val plain = backendLyricDocument(
            query = query,
            lrcText = "[ar:artist]\nfirst plain\nsecond plain",
            yrcText = "",
            translatedText = "",
            romanText = "",
        )
        val supplementOnly = backendLyricDocument(
            query = query,
            lrcText = "",
            yrcText = "",
            translatedText = "[00:00.00]translation",
            romanText = "[00:00.00]romanization",
        )

        assertEquals(LyricCapabilityLevel.UNSYNCED, plain?.capabilityLevel)
        assertEquals(listOf("first plain", "second plain"), plain?.untimedLines)
        assertNull(supplementOnly)
    }

    @Test
    fun untimedCandidateDoesNotBlockALaterTimedSource() = runTest {
        val calls = mutableListOf<LyricSource>()
        val resolver = LyricResolver(
            listOf(
                fakeProvider(
                    LyricSource.NCM_BACKEND,
                    calls,
                    LyricProviderResult.Untimed(untimed(LyricSource.NCM_BACKEND)),
                ),
                fakeProvider(
                    LyricSource.AMLL_TTML,
                    calls,
                    found(LyricSource.AMLL_TTML),
                ),
            ),
        )

        val result = resolver.resolveLatest(
            LyricQuery(songId = 1),
            LyricSourceMode.BACKEND_PREFERRED,
        )

        assertEquals(
            listOf(LyricSource.NCM_BACKEND, LyricSource.AMLL_TTML),
            calls,
        )
        assertEquals(
            LyricSource.AMLL_TTML,
            assertIs<LyricResolution.Found>(result).document.source,
        )
    }

    @Test
    fun resolverReturnsUntimedOnlyAfterAllowedSourcesAreExhausted() = runTest {
        val calls = mutableListOf<LyricSource>()
        val resolver = LyricResolver(
            listOf(
                fakeProvider(
                    LyricSource.NCM_BACKEND,
                    calls,
                    LyricProviderResult.Untimed(untimed(LyricSource.NCM_BACKEND)),
                ),
            ),
        )

        val result = resolver.resolveLatest(
            LyricQuery(songId = 1),
            LyricSourceMode.BACKEND_ONLY,
        )

        assertEquals(listOf(LyricSource.NCM_BACKEND), calls)
        assertEquals(
            LyricCapabilityLevel.UNSYNCED,
            assertIs<LyricResolution.Untimed>(result).document.capabilityLevel,
        )
    }

    @Test
    fun memoryCacheIsScopedByModeLocalPreferenceAndDuration() = runTest {
        val resolver = LyricResolver(
            listOf(
                object : LyricSourceProvider {
                    override val source = LyricSource.NCM_BACKEND
                    override fun load(query: LyricQuery) =
                        flowOf(found(LyricSource.NCM_BACKEND))
                },
            ),
        )
        val query = LyricQuery(songId = 42, durationMs = 10_000)

        resolver.resolveLatest(query, LyricSourceMode.BACKEND_ONLY)

        assertIs<LyricResolution.Found>(
            resolver.cachedResolution(query, LyricSourceMode.BACKEND_ONLY),
        )
        assertNull(
            resolver.cachedResolution(query, LyricSourceMode.TTML_PREFERRED),
        )
        assertNull(
            resolver.cachedResolution(
                query,
                LyricSourceMode.BACKEND_ONLY,
                preferLocal = true,
            ),
        )
        assertNull(
            resolver.cachedResolution(
                query.copy(durationMs = 20_000),
                LyricSourceMode.BACKEND_ONLY,
            ),
        )
    }

    @Test
    fun cachedContentSurvivesARefreshFailureWithoutEmittingAnError() = runTest {
        var firstLoad = true
        val resolver = LyricResolver(
            listOf(
                object : LyricSourceProvider {
                    override val source = LyricSource.NCM_BACKEND
                    override fun load(query: LyricQuery): Flow<LyricProviderResult> =
                        if (firstLoad) {
                            firstLoad = false
                            flowOf(found(LyricSource.NCM_BACKEND))
                        } else {
                            flowOf(LyricProviderResult.Unavailable("offline"))
                        }
                },
            ),
        )
        val query = LyricQuery(songId = 7, durationMs = 5_000)
        resolver.resolveLatest(query, LyricSourceMode.BACKEND_ONLY)

        val refreshed = mutableListOf<LyricResolution>()
        resolver.resolve(query, LyricSourceMode.BACKEND_ONLY).collect(refreshed::add)

        assertEquals(1, refreshed.size)
        assertIs<LyricResolution.Found>(refreshed.single())
    }

    @Test
    fun emptyResultIsNotKeptInMemory() = runTest {
        val resolver = LyricResolver(
            listOf(
                object : LyricSourceProvider {
                    override val source = LyricSource.NCM_BACKEND
                    override fun load(query: LyricQuery) =
                        flowOf(LyricProviderResult.NoMatch)
                },
            ),
        )
        val query = LyricQuery(songId = 9)

        assertIs<LyricResolution.Empty>(
            resolver.resolveLatest(query, LyricSourceMode.BACKEND_ONLY),
        )
        assertNull(resolver.cachedResolution(query, LyricSourceMode.BACKEND_ONLY))
    }


    @Test
    fun backendLrcKeepsSameTimestampMainAndBackgroundLinesInSourceOrder() = runTest {
        val provider = BackendLyricProvider(
            lyricFlow = {
                flowOf(
                    Lyric(
                        lrc = LyricLrc(
                            lyric = """
                            [00:01.000]main
                            [00:01.000](echo)
                            [00:03.000]next
                            """.trimIndent(),
                        ),
                    ),
                )
            },
            retryDelayMillis = 0,
        )

        val results = mutableListOf<LyricProviderResult>()
        provider.load(
            LyricQuery(
                songId = 1,
                durationMs = 5_000L,
            ),
        ).collect(results::add)

        val document = assertIs<LyricProviderResult.Found>(results.single()).document
        assertEquals(listOf("main", "echo", "next"), document.lines.map { it.text })
        assertEquals(listOf(false, true, false), document.lines.map { it.isBackground })
        assertEquals(listOf(1_000L, 1_000L, 3_000L), document.lines.map { it.startTimeMs })
        assertEquals(5_000L, document.lines.last().endTimeMs)
        assertEquals(5_000L, document.lines.last().exactEndTimeMs.toLong())
        assertEquals(5_000L, document.lines.last().words.last().endTimeMs)
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
                capabilityLevel = if (source == LyricSource.AMLL_TTML) {
                    LyricCapabilityLevel.TTML_FULL
                } else {
                    LyricCapabilityLevel.LINE_SYNCED
                },
                lines = listOf(
                    LyricParser.WordLine(
                        startTimeMs = 0,
                        endTimeMs = 1_000,
                        words = listOf(LyricParser.Word(0, 1_000, "line")),
                    ),
                ),
            ),
        )

    private fun untimed(source: LyricSource): LyricDocument = LyricDocument(
        source = source,
        capabilityLevel = LyricCapabilityLevel.UNSYNCED,
        lines = emptyList(),
        untimedLines = listOf("plain line"),
        rawLineLyric = "plain line",
    )
}

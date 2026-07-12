package com.leejlredstar.redefinencm.kmp.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepositoryCacheThenNetworkTest {

    @Test
    fun cachedValueIsEmittedBeforeSuspendedNetworkThenReplacedAndCached() = runTest {
        val networkStarted = CompletableDeferred<Unit>()
        val releaseNetwork = CompletableDeferred<Unit>()
        val emissions = mutableListOf<CacheThenNetworkData<String>>()
        var writtenValue: String? = null

        val collection = launch {
            cacheThenNetworkFlow(
                readCache = { "cached" },
                fetchNetwork = {
                    networkStarted.complete(Unit)
                    releaseNetwork.await()
                    "network"
                },
                writeCache = { writtenValue = it },
            ).toList(emissions)
        }

        networkStarted.await()
        assertEquals(
            listOf(CacheThenNetworkData("cached", CacheThenNetworkSource.CACHE)),
            emissions,
        )
        assertNull(writtenValue)

        releaseNetwork.complete(Unit)
        collection.join()

        assertEquals(
            listOf(
                CacheThenNetworkData("cached", CacheThenNetworkSource.CACHE),
                CacheThenNetworkData("network", CacheThenNetworkSource.NETWORK),
            ),
            emissions,
        )
        assertEquals("network", writtenValue)
    }

    @Test
    fun nullNetworkKeepsOnlyCachedEmission() = runTest {
        var networkFetchCount = 0

        val emissions = cacheThenNetworkFlow<String>(
            readCache = { "cached" },
            fetchNetwork = {
                networkFetchCount += 1
                null
            },
            writeCache = { error("null network value must not be cached") },
        ).toList()

        assertEquals(1, networkFetchCount)
        assertEquals(
            listOf(CacheThenNetworkData("cached", CacheThenNetworkSource.CACHE)),
            emissions,
        )
    }

    @Test
    fun missingCacheAndNetworkEmitsNothing() = runTest {
        val emissions = cacheThenNetworkFlow<String>(
            readCache = { null },
            fetchNetwork = { null },
            writeCache = { error("missing network value must not be cached") },
        ).toList()

        assertEquals(emptyList(), emissions)
    }
}

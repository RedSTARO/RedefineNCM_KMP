package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeNcmClientFactoryTest {
    private class Store(var cookie: String = "MUSIC_U=current") : NcmSessionStore {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key].orEmpty()
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun credential() = cookie
    }

    @Test
    fun freshCredentialsAndExplicitReportingSnapshotsStaySeparate() = runBlocking {
        val seen = mutableListOf<String>()
        val engine = MockEngine { request ->
            assertEquals("music.163.com", request.url.host)
            assertEquals("/weapi/user/level", request.url.encodedPath)
            seen += request.headers[HttpHeaders.Cookie].orEmpty()
            respond("""{"code":200}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = Store()
        val client = NativeNcmClientFactory.create(store, object : io.ktor.client.engine.HttpClientEngineFactory<io.ktor.client.engine.HttpClientEngineConfig> {
            override fun create(block: io.ktor.client.engine.HttpClientEngineConfig.() -> Unit) = engine
        })
        try {
            val api = NCMApi(client)
            api.userLevel()
            store.cookie = "MUSIC_U=changed"
            api.userLevel("MUSIC_U=bound")
            api.userLevel()
            assertTrue("MUSIC_U=current" in seen[0])
            assertTrue("MUSIC_U=bound" in seen[1])
            assertFalse("changed" in seen[1])
            assertTrue("MUSIC_U=changed" in seen[2])
            assertFailsWith<IllegalArgumentException> { client.get("https://evil.test/user/level") }
            assertEquals(3, seen.size)
        } finally {
            client.close()
        }
        // The factory owns the real network engine. Closing the facade must release it too.
        engine.coroutineContext[Job]!!.join()
        assertTrue(engine.coroutineContext[Job]!!.isCompleted)
    }

    @Test
    fun cancellingTheFacadeCancelsItsNestedNativeNetworkRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val engine = MockEngine {
            entered.complete(Unit)
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally { cancelled.complete(Unit) }
        }
        val client = NativeNcmClientFactory.create(Store(), object : io.ktor.client.engine.HttpClientEngineFactory<io.ktor.client.engine.HttpClientEngineConfig> {
            override fun create(block: io.ktor.client.engine.HttpClientEngineConfig.() -> Unit) = engine
        })
        try {
            val operation = async { NCMApi(client).userLevel() }
            entered.await()
            operation.cancel()
            assertFailsWith<CancellationException> { operation.await() }
            cancelled.await()
        } finally { client.close() }
    }
}

package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.data.api.ExternalHttpClient
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.LanguageSetting
import com.leejlredstar.redefinencm.kmp.i18n.strings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

/** A cover URL comes from the server; one that streams without end must not fill the heap. */
class LocalMediaAssetsArtworkLimitTest {
    @BeforeTest
    fun showChinese() {
        I18n.apply(LanguageSetting.ZH)
    }

    @AfterTest
    fun followTheSystemAgain() {
        I18n.apply(LanguageSetting.SYSTEM)
    }

    @Test
    fun stopsReadingAnOversizedCoverAtTheLimit() {
        runBlocking {
            val written = AtomicLong()
            val quietScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
            val client = HttpClient(
                MockEngine {
                    val body = quietScope.writer {
                        val chunk = ByteArray(64 * 1024) { 0xFF.toByte() }
                        repeat(1024) {
                            channel.writeFully(chunk)
                            written.addAndGet(chunk.size.toLong())
                        }
                    }.channel
                    respond(content = body, status = HttpStatusCode.OK)
                },
            )
            try {
                val failure = runCatching {
                    LocalMediaAssets(ExternalHttpClient(client)).saveArtwork(1L, "https://cover.test/1.jpg")
                }.exceptionOrNull()

                assertEquals(strings.coverTooLarge(16), failure?.message)
                assertTrue(written.get() < 24L * 1024 * 1024, "read ${written.get()} bytes of an endless cover")
            } finally {
                quietScope.cancel()
                client.close()
            }
        }
    }
}

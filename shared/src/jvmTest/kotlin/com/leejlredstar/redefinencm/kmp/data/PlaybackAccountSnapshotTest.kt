package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.test.parseQuery
import com.sun.net.httpserver.HttpServer
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaybackAccountSnapshotTest {
    @Test
    fun snapshotReadsAllAccountEndpointsWithOneCredentialAndRefreshesLevelCache() = runBlocking {
        val requests = ConcurrentLinkedQueue<Triple<String, Map<String, String>, String?>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests += Triple(
                    exchange.requestURI.path,
                    parseQuery(exchange.requestURI.rawQuery.orEmpty()),
                    exchange.requestHeaders.getFirst("Cookie"),
                )
                val response = when (exchange.requestURI.path) {
                    "/user/level" ->
                        """{"code":200,"data":{"userId":42,"nowPlayCount":11,"level":3}}"""
                    "/user/record" ->
                        """{"code":200,"weekData":[{"song":{"id":7},"playCount":2,"score":90}]}"""
                    "/record/recent/song" ->
                        """{"code":200,"data":{"total":1,"list":[{"resourceId":7,"playTime":123,"resourceType":"song","data":{"id":7},"banned":false}]}}"""
                    else -> error("Unexpected path ${exchange.requestURI.path}")
                }.encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }
        val client = HttpClientFactory.create(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            realIP = "127.0.0.1",
            cookieProvider = { "" },
            engineFactory = OkHttp,
        )
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val database = AppDatabase(driver)
        database.cachedUserLevelQueries.upsert(42, """{"code":200,"data":{"userId":42,"nowPlayCount":1}}""")
        val repository = Repository(NCMApi(client), database)

        try {
            val snapshot = repository.getPlaybackAccountSnapshot(
                uid = 42,
                recentLimit = 20,
                credentialCookie = "MUSIC_U=account-snapshot",
            )

            assertEquals(11L, snapshot.userLevel?.data?.nowPlayCount)
            assertEquals(7L, snapshot.weeklyRecord?.weekData?.single()?.song?.id)
            assertEquals(7L, snapshot.recentSongs?.data?.list?.single()?.resourceId)
            assertEquals(3, requests.size)
            assertTrue(requests.all { (_, _, cookie) -> cookie == "MUSIC_U=account-snapshot" })
            assertEquals(
                mapOf("uid" to "42", "type" to "1"),
                requests.single { it.first == "/user/record" }.second
                    .filterKeys { it == "uid" || it == "type" },
            )
            assertEquals(
                "20",
                requests.single { it.first == "/record/recent/song" }.second["limit"],
            )
            val cached = assertNotNull(database.cachedUserLevelQueries.selectByUid(42).executeAsOneOrNull())
            assertTrue(cached.contains("\"nowPlayCount\":11"))
        } finally {
            driver.close()
            client.close()
            server.stop(0)
        }
    }
}

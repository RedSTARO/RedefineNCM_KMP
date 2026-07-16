package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.test.CapturedRequest
import com.leejlredstar.redefinencm.kmp.test.parseQuery
import com.leejlredstar.redefinencm.kmp.test.testHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RepositorySongWikiSummaryTest {
    @Test
    fun summaryUsesOnlySongBasicBlockAndDeduplicatesDisplayValues() = runBlocking {
        val requests = ConcurrentLinkedQueue<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests += CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    query = parseQuery(exchange.requestURI.rawQuery.orEmpty()),
                )
                val body = RESPONSE.encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        val client = testHttpClient(server.address.port)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val repository = Repository(NCMApi(client), AppDatabase(driver))

        try {
            val summary = repository.getSongWikiSummary(1_958_384_591)

            assertEquals(
                SongWikiSummary(
                    sections = listOf(
                        SongWikiSection("曲风", listOf("原声带", "二次元-动漫主题曲")),
                        SongWikiSection("语种", listOf("日语")),
                        SongWikiSection("乐谱", listOf("2个", "吉他")),
                        SongWikiSection(
                            title = "乐评",
                            values = listOf("乐评来自 九九煎包"),
                            description = "这是一段简要乐评。",
                        ),
                    ),
                ),
                summary,
            )
            assertFailsWith<IllegalArgumentException> {
                repository.getSongWikiSummary(0)
            }
            assertEquals(
                listOf(
                    CapturedRequest(
                        method = "GET",
                        path = "/song/wiki/summary",
                        query = mapOf("id" to "1958384591"),
                    ),
                ),
                requests.toList(),
            )
        } finally {
            driver.close()
            client.close()
            server.stop(0)
        }
    }

    private companion object {
        val RESPONSE =
            """
            {
              "code": 200,
              "data": {
                "blocks": [
                  {
                    "showType": "LIST_SONG",
                    "uiElement": {"mainTitle": {"title": "相似歌曲"}},
                    "creatives": [
                      {"uiElement": {"mainTitle": {"title": "不能展示"}}}
                    ]
                  },
                  {
                    "showType": "SONG_PLAY_ABOUT_TAB_SONG_BASIC",
                    "uiElement": {"mainTitle": {"title": "音乐百科"}},
                    "creatives": [
                      {
                        "creativeType": "songTag",
                        "uiElement": {
                          "mainTitle": {"title": "曲风"},
                          "textLinks": null,
                          "descriptions": null,
                          "buttons": null,
                          "images": null
                        },
                        "resources": [
                          {"uiElement": {"mainTitle": {"title": "原声带"}}},
                          {"uiElement": {"mainTitle": {"title": "原声带"}}},
                          {"uiElement": {"mainTitle": {"title": "二次元-动漫主题曲"}}}
                        ]
                      },
                      {
                        "creativeType": "language",
                        "uiElement": {
                          "mainTitle": {"title": "语种"},
                          "textLinks": [{"text": "日语"}]
                        },
                        "resources": []
                      },
                      {
                        "creativeType": "sheet",
                        "uiElement": {
                          "mainTitle": {"title": "乐谱"},
                          "buttons": [{"text": "2个"}]
                        },
                        "resources": [
                          {"uiElement": {"mainTitle": null, "images": [{"title": "吉他"}]}},
                          {"uiElement": {"mainTitle": null, "images": [{"title": "吉他"}]}}
                        ]
                      },
                      {
                        "creativeType": "songComment",
                        "uiElement": {"mainTitle": {"title": "乐评"}},
                        "resources": [
                          {
                            "uiElement": {
                              "mainTitle": {"title": "乐评来自 九九煎包"},
                              "textLinks": [{"text": null}],
                              "descriptions": [{"description": "这是一段简要乐评。"}]
                            }
                          }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
    }
}

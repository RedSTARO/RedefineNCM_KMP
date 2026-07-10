package com.leejlredstar.redefinencm.kmp.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class RepositoryStorageFailureTest {
    @Test
    fun playerStatusReadAndWriteExposeClosedDatabaseFailures() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val client = HttpClient(OkHttp)
        val repository = Repository(NCMApi(client), AppDatabase(driver))
        driver.close()

        try {
            assertTrue(repository.getPlayerStatus().isFailure)
            assertTrue(repository.savePlayerStatus(PlayerStatus()).isFailure)
        } finally {
            client.close()
        }
    }
}

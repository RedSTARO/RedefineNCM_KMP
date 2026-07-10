package com.leejlredstar.redefinencm.kmp.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.sql.DriverManager

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbDir = File(System.getProperty("user.home"), ".redefinencm")
        dbDir.mkdirs()
        return openJvmDatabase(File(dbDir, "redefinencm.db"))
    }
}

internal fun openJvmDatabase(databaseFile: File): SqlDriver {
    databaseFile.parentFile?.mkdirs()
    val jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"
    val driver = JdbcSqliteDriver(jdbcUrl)
    return try {
        val currentVersion = DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { result ->
                    if (result.next()) result.getLong(1) else 0L
                }
            }
        }

        when {
            currentVersion == 0L -> AppDatabase.Schema.create(driver)
            currentVersion < AppDatabase.Schema.version -> {
                AppDatabase.Schema.migrate(
                    driver = driver,
                    oldVersion = currentVersion,
                    newVersion = AppDatabase.Schema.version,
                )
            }
            currentVersion > AppDatabase.Schema.version -> error(
                "Database schema $currentVersion is newer than supported " +
                    "${AppDatabase.Schema.version}",
            )
        }
        driver.execute(
            identifier = null,
            sql = "PRAGMA user_version = ${AppDatabase.Schema.version}",
            parameters = 0,
        )
        driver
    } catch (failure: Throwable) {
        runCatching { driver.close() }
        throw failure
    }
}

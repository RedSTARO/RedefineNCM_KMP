package com.leejlredstar.redefinencm.kmp.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbDir = File(System.getProperty("user.home"), ".redefinencm")
        dbDir.mkdirs()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(dbDir, "redefinencm.db").absolutePath}")
        AppDatabase.Schema.create(driver)
        return driver
    }
}

package com.leejlredstar.redefinencm.kmp.data.db

import app.cash.sqldelight.db.SqlDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = BrowserStorageSqlDriver().also { driver ->
        val storedVersion = driver.schemaVersion
        when {
            storedVersion == 0L -> AppDatabase.Schema.create(driver)
            storedVersion < AppDatabase.Schema.version -> AppDatabase.Schema.migrate(
                driver = driver,
                oldVersion = storedVersion,
                newVersion = AppDatabase.Schema.version,
            )
            storedVersion > AppDatabase.Schema.version -> error(
                "Database schema $storedVersion is newer than supported ${AppDatabase.Schema.version}",
            )
        }
        driver.schemaVersion = AppDatabase.Schema.version
    }
}

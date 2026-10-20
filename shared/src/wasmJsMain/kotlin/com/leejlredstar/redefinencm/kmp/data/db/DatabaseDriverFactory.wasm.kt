package com.leejlredstar.redefinencm.kmp.data.db

import app.cash.sqldelight.db.SqlDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = BrowserStorageSqlDriver().also(::openBrowserDatabase)
}

/**
 * Opens the stored database or, when it cannot be opened, an in-memory one for this session: a
 * newer schema written by a later release served from this origin, or an unreadable version,
 * would otherwise stop the app from starting at all. The stored rows are left untouched, and
 * the local library reports that it cannot be read instead of starting empty.
 */
internal fun openBrowserDatabase(driver: BrowserStorageSqlDriver) {
    try {
        initializeBrowserDatabase(driver)
    } catch (_: IllegalStateException) {
        driver.detachFromStorage()
        initializeBrowserDatabase(driver)
    }
}

/** Also used when permission to read storage returns after a failed startup read. */
internal fun initializeBrowserDatabase(driver: BrowserStorageSqlDriver) {
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

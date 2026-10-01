package com.leejlredstar.redefinencm.kmp.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.sql.DriverManager

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = openJvmDatabase(desktopDatabaseFile())
}

/**
 * Opens the desktop database the way the app will (creating or migrating it) and closes it
 * again, throwing what the open threw.
 *
 * For the app's entry point, before dependency injection starts: the eager singletons open the
 * database themselves, and a file that cannot be opened would end the process with an exception
 * nobody sees.
 */
fun verifyDesktopDatabase() {
    openJvmDatabase(desktopDatabaseFile()).close()
}

/** Where the desktop app keeps what it stores itself: the database, its log, its instance lock. */
fun desktopDataDirectory(): File = File(System.getProperty("user.home"), ".redefinencm")

/** The desktop database file. It holds caches, the saved queue, download tasks and the local library. */
fun desktopDatabaseFile(): File = File(desktopDataDirectory(), "redefinencm.db")

/**
 * The file was written by a newer build than this one. Opening it would run this build's queries
 * against tables it does not know, so it is refused, and the caller can say why.
 */
class DatabaseSchemaTooNewException(val found: Long, val supported: Long) :
    IllegalStateException("Database schema $found is newer than supported $supported")

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
            currentVersion > AppDatabase.Schema.version ->
                throw DatabaseSchemaTooNewException(currentVersion, AppDatabase.Schema.version)
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

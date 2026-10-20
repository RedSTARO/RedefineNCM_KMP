package com.leejlredstar.redefinencm.kmp.data.db

import android.content.Context
import android.database.CursorWindow
import android.os.Build
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        enlargeCursorWindowBeforeApi28()
        return AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "redefinencm.db",
            windowSizeBytes = CursorWindowBytes,
        )
    }
}

/**
 * The local library and the saved queue are one row each, and Android reads a row only when it
 * fits one cursor window, 2 MiB by default: past about 8,000 tracks the row is written and then
 * can never be read again.
 */
internal const val CursorWindowBytes = 32L * 1024 * 1024

/**
 * API 28 takes a window size per cursor ([AndroidSqliteDriver] passes [CursorWindowBytes]).
 * Earlier releases ignore it and size every window from one process-wide field, which is read when
 * the first window is made, so it is set here instead. A release without the field keeps 2 MiB.
 */
private fun enlargeCursorWindowBeforeApi28() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return
    runCatching {
        CursorWindow::class.java.getDeclaredField("sCursorWindowSize").apply {
            isAccessible = true
            setInt(null, CursorWindowBytes.toInt())
        }
    }.onFailure { System.err.println("Could not enlarge the cursor window: ${it.message}") }
}

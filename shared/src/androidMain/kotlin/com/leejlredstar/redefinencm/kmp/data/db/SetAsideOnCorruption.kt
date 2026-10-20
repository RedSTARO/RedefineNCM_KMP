package com.leejlredstar.redefinencm.kmp.data.db

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryDocument
import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryStore
import com.leejlredstar.redefinencm.kmp.util.StartupNotice
import com.leejlredstar.redefinencm.kmp.util.StartupNotices
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What happens to a database SQLite reports corrupt. Android's default deletes the file, and with
 * it the local library, the saved queue and the download list, for one damaged page that may hold
 * nothing but cache. As on the desktop, the file is put aside under a `.bak` name instead and the
 * next open starts an empty database. The local library, the one thing in it kept nowhere else, is
 * copied into that database when it can still be read from the old file.
 *
 * Android calls this either while opening the file, and then opens it again at once, or during a
 * query, after which the open connection is closed until the app starts again. A marker file beside
 * the database carries the old file's name to whichever open comes next.
 */
internal open class SetAsideOnCorruption : AndroidSqliteDriver.Callback(AppDatabase.Schema) {

    override fun onCorruption(db: SupportSQLiteDatabase) {
        val path = db.path
        if (path == null || path == ":memory:") return super.onCorruption(db)
        runCatching { if (db.isOpen) db.close() }
        val database = File(path)
        val aside = runCatching { putDatabaseAside(database, asideStamp()) }.getOrNull()
        if (aside == null) {
            System.err.println("The damaged database could not be put aside; it is deleted")
            return super.onCorruption(db)
        }
        System.err.println("The damaged database was put aside as ${aside.name}")
        runCatching { restoreMarker(database).writeText(aside.name) }
        StartupNotices.post(StartupNotice.DatabaseSetAside)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        val path = db.path ?: return
        val marker = restoreMarker(File(path))
        if (!marker.exists()) return
        val aside = File(marker.parentFile, runCatching { marker.readText().trim() }.getOrDefault(""))
        marker.delete()
        val library = readLocalLibrary(aside) ?: return
        runCatching {
            db.execSQL("INSERT OR REPLACE INTO LocalLibrary(singleton, json) VALUES (0, ?)", arrayOf<Any>(library))
        }.onSuccess { StartupNotices.post(StartupNotice.LocalLibraryRestored) }
    }
}

private fun asideStamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

private fun restoreMarker(database: File) = File(database.parentFile, database.name + ".restore")

/** The local library stored in [aside], if it can be read and is one this build reads exactly. */
private fun readLocalLibrary(aside: File): String? {
    if (!aside.isFile) return null
    val json = runCatching {
        // A handler that does nothing: the default one deletes a file it finds corrupt.
        SQLiteDatabase.openDatabase(aside.path, null, SQLiteDatabase.OPEN_READONLY, DatabaseErrorHandler { }).use { old ->
            old.rawQuery("SELECT json FROM LocalLibrary WHERE singleton = 0", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
    }.getOrNull() ?: return null
    val readable = runCatching {
        LocalLibraryStore.LibraryJson.decodeFromString(LocalLibraryDocument.serializer(), json)
    }.isSuccess
    return json.takeIf { readable }
}

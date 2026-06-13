package com.leejlredstar.redefinencm.kmp.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "redefinencm.db",
            callback = DevSchemaCallback(),
        )
}

/**
 * Re-runs every CREATE TABLE IF NOT EXISTS on each DB open so new tables take effect
 * without reinstalling. Safe in production — IF NOT EXISTS is a no-op for existing tables.
 */
private class DevSchemaCallback : AndroidSqliteDriver.Callback(AppDatabase.Schema) {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("CREATE TABLE IF NOT EXISTS CachedUserDetail (uid INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS CachedUserPlaylist (uid INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS CachedRecommendResource (singleton INTEGER NOT NULL PRIMARY KEY DEFAULT 0, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS CachedRecommendSongs (singleton INTEGER NOT NULL PRIMARY KEY DEFAULT 0, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS CachedLyric (song_id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS CachedPlaylistDetail (playlist_id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS CachedPlaylistTrackAll (playlist_id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
    }
}

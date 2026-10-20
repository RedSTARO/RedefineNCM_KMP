package com.leejlredstar.redefinencm.kmp.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryDocument
import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryStore
import com.leejlredstar.redefinencm.kmp.util.StartupNotice
import com.leejlredstar.redefinencm.kmp.util.StartupNotices
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Android deletes a database SQLite reports corrupt. The app's own handler puts it aside instead,
 * and the next database gets the local library back from it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class DatabaseCorruptionTest {
    private val name = "corruption-test.db"

    @AfterTest
    fun cleanUp() {
        StartupNotice.entries.forEach(StartupNotices::consume)
        RuntimeEnvironment.getApplication().getDatabasePath(name).parentFile
            ?.listFiles { file -> file.name.startsWith(name) }
            ?.forEach { it.delete() }
    }

    @Test
    fun aDamagedDatabaseIsPutAsideAndTheNextOneGetsTheLibraryBack() {
        val context = RuntimeEnvironment.getApplication()
        var opened: SupportSQLiteDatabase? = null
        val callback = object : SetAsideOnCorruption() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                opened = db
            }
        }
        val library = LocalLibraryStore.LibraryJson.encodeToString(LocalLibraryDocument.serializer(), LocalLibraryDocument())
        val first = AndroidSqliteDriver(AppDatabase.Schema, context, name, callback = callback)
        AppDatabase(first).localLibraryQueries.upsert(library)

        // What Android's error handler calls when a query meets a damaged page.
        callback.onCorruption(opened!!)
        first.close()

        val directory = context.getDatabasePath(name).parentFile!!
        val aside = directory.listFiles()!!.single { it.name.startsWith("$name.") && it.name.endsWith(".bak") }
        assertTrue(aside.length() > 0L, "the old file is kept, not emptied")
        assertFalse(context.getDatabasePath(name).exists())
        assertTrue(StartupNotice.DatabaseSetAside in StartupNotices.notices.value)

        val second = AndroidSqliteDriver(AppDatabase.Schema, context, name, callback = SetAsideOnCorruption())
        try {
            assertEquals(library, AppDatabase(second).localLibraryQueries.select().executeAsOne())
            assertTrue(StartupNotice.LocalLibraryRestored in StartupNotices.notices.value)
            assertFalse(directory.resolve("$name.restore").exists())
        } finally {
            second.close()
        }
    }
}

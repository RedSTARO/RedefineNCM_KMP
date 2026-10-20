package com.leejlredstar.redefinencm.kmp.data.db

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The local library and the saved queue are one row each. Android reads rows through a cursor
 * window of 2 MiB by default, and a row that does not fit can be written but never read again:
 * about 8,000 tracks in the library, or a queue of 10,000.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36], manifest = Config.NONE)
class AndroidLargeRowTest {
    @Test
    fun aRowLargerThanTheDefaultCursorWindowReadsBack() {
        val driver = DatabaseDriverFactory(RuntimeEnvironment.getApplication()).createDriver()
        try {
            val database = AppDatabase(driver)
            val json = "[\"" + "x".repeat(3 * 1024 * 1024) + "\"]"
            database.localLibraryQueries.upsert(json)

            assertEquals(json.length, database.localLibraryQueries.select().executeAsOne().length)
        } finally {
            driver.close()
        }
    }
}

package com.leejlredstar.redefinencm.kmp.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalLibraryValidationTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
    private val database = AppDatabase(driver)

    @AfterTest
    fun close() = driver.close()

    @Test
    fun invalidPersistedIdentitiesCannotReachListsOrBeOverwritten() = runBlocking {
        val track = """{"provider":"ncm","rawId":"1","title":"Keep"}"""
        val badPlaylists = listOf(
            """[{"id":"owned","name":"A"},{"id":"owned","name":"B"}]""",
            """[{"id":"","name":"A"}]""",
            """[{"id":"owned","name":"A","tracks":[$track,$track]}]""",
            """[{"id":"owned","name":"A","tracks":[{"provider":"ncm","rawId":"","title":"Keep"}]}]""",
            """[{"id":"a","name":"A","kind":"FAVORITES"},{"id":"b","name":"B","kind":"FAVORITES"}]""",
        )
        for (playlists in badPlaylists) {
            val stored = """{"playlists":$playlists}"""
            database.localLibraryQueries.upsert(stored)
            val library = LocalLibraryStore(database)
            assertTrue(library.createPlaylist("New").isFailure, stored)
            assertTrue(library.readFailed.value)
            assertEquals(stored, database.localLibraryQueries.select().executeAsOne())
        }
    }

    @Test
    fun malformedImportedTracksCannotReplaceAnOwnedPlaylist() = runBlocking {
        val library = LocalLibraryStore(database)
        val saved = library.createPlaylist("Saved", listOf(MediaInfo(id = "1", title = "Keep", artist = "Artist"))).getOrThrow()
        val original = database.localLibraryQueries.select().executeAsOne()
        val track = saved.tracks.single()
        for (tracks in listOf(listOf(track.copy(rawId = "")), listOf(track, track))) {
            val imported = saved.copy(name = "Invalid replacement", tracks = tracks)
            assertTrue(library.importPlaylists(listOf(imported)).isFailure)
            assertEquals(saved, library.snapshot().playlist(saved.id))
            assertEquals(original, database.localLibraryQueries.select().executeAsOne())
        }
    }
}

package com.leejlredstar.redefinencm.kmp.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.util.decodeBackupLocalLibrary
import com.leejlredstar.redefinencm.kmp.util.encodeSettingsBackup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The local library keeps tracks of any provider by provider and id, and survives a restart. */
class LocalLibraryStoreTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
    private val database = AppDatabase(driver)
    private var clock = 1_000L
    private fun store() = LocalLibraryStore(database) { clock++ }

    private val neteaseSong = MediaInfo(
        id = "347230",
        title = "海阔天空",
        artist = "Beyond",
        artworkUri = "https://p1.music.126.net/cover.jpg",
        duration = 326_000,
    )
    private val qqSong = MediaInfo(
        id = "qq:0039MnYb0qxYhV",
        title = "晴天",
        artist = "周杰伦",
        // A device's own artwork file is never stored.
        artworkUri = "content://media/cover/1",
        duration = 269_000,
    )

    @AfterTest
    fun close() = driver.close()

    @Test
    fun aPlaylistHoldsTracksOfEveryProviderOnceAndSurvivesARestart() = runBlocking {
        val store = store()
        val playlist = store.createPlaylist("  混合  ", listOf(neteaseSong)).getOrThrow()
        assertEquals("混合", playlist.name)

        assertEquals(1, store.addTracks(playlist.id, listOf(qqSong, neteaseSong)).getOrThrow())

        val restarted = store().snapshot()
        val tracks = restarted.playlist(playlist.id)!!.tracks
        assertEquals(listOf("ncm:347230", "qq:0039MnYb0qxYhV"), tracks.map { it.key })
        assertEquals("", tracks[1].artworkUri)
        // Played back, each track carries the id the player and the providers read.
        assertEquals(listOf("347230", "qq:0039MnYb0qxYhV"), tracks.map { it.toMediaInfo()!!.id })
    }

    @Test
    fun renamingRemovingAndDeletingChangeOnlyTheirPlaylist() = runBlocking {
        val store = store()
        val kept = store.createPlaylist("留下", listOf(neteaseSong)).getOrThrow()
        val changed = store.createPlaylist("改名", listOf(neteaseSong, qqSong)).getOrThrow()

        store.renamePlaylist(changed.id, "新名字").getOrThrow()
        store.removeTrack(changed.id, "ncm:347230").getOrThrow()
        assertEquals("新名字", store.snapshot().playlist(changed.id)!!.name)
        assertEquals(listOf("qq:0039MnYb0qxYhV"), store.snapshot().playlist(changed.id)!!.tracks.map { it.key })

        store.deletePlaylist(changed.id).getOrThrow()
        assertEquals(listOf(kept.id), store.snapshot().userPlaylists.map { it.id })
        assertTrue(store.addTracks(changed.id, listOf(qqSong)).isFailure)
    }

    @Test
    fun theHeartOfAnyProviderLandsInTheLocalFavourites() = runBlocking {
        val store = store()
        assertFalse(store.snapshot().isFavorite(qqSong.id))

        store.setFavorite(qqSong, favorite = true).getOrThrow()
        store.setFavorite(neteaseSong, favorite = true).getOrThrow()
        val library = store.snapshot()
        assertTrue(library.isFavorite("qq:0039MnYb0qxYhV"))
        assertTrue(library.isFavorite("347230"))
        assertEquals(LocalLibraryStore.FavoritesName, library.favorites!!.name)
        // The newest favourite comes first, as in the services' own liked lists.
        assertEquals(listOf("ncm:347230", "qq:0039MnYb0qxYhV"), library.favorites!!.tracks.map { it.key })

        store.setFavorite(qqSong, favorite = false).getOrThrow()
        assertFalse(store.snapshot().isFavorite(qqSong.id))
        assertEquals(1, store.snapshot().playlists.count { it.kind == LocalPlaylist.Kind.FAVORITES })
    }

    @Test
    fun aBackupReplacesPlaylistsByIdKeepsTheRestAndJoinsFavourites() = runBlocking {
        val source = store()
        val shared = source.createPlaylist("共享", listOf(neteaseSong)).getOrThrow()
        source.setFavorite(qqSong, favorite = true).getOrThrow()
        val json = encodeSettingsBackup(
            getString = { _, default -> default },
            getBoolean = { _, default -> default },
            localLibrary = source.snapshot(),
        )

        // Another device: the same playlist with other contents, its own playlist, its own favourites.
        val otherDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        try {
            val target = LocalLibraryStore(AppDatabase(otherDriver)) { clock++ }
            val own = target.createPlaylist("本机", listOf(qqSong)).getOrThrow()
            target.setFavorite(neteaseSong, favorite = true).getOrThrow()
            target.importPlaylists(listOf(shared.copy(name = "旧名字", tracks = emptyList()))).getOrThrow()

            target.importPlaylists(decodeBackupLocalLibrary(json)!!).getOrThrow()

            val library = target.snapshot()
            assertEquals(listOf("ncm:347230"), library.playlist(shared.id)!!.tracks.map { it.key })
            assertEquals("共享", library.playlist(shared.id)!!.name)
            assertTrue(library.playlist(own.id) != null)
            assertEquals(1, library.playlists.count { it.kind == LocalPlaylist.Kind.FAVORITES })
            assertTrue(library.isFavorite(neteaseSong.id) && library.isFavorite(qqSong.id))
        } finally {
            otherDriver.close()
        }
    }

    @Test
    fun aBackupWithoutALibraryCarriesNone() {
        assertEquals(null, decodeBackupLocalLibrary("""{"server":"http://server/"}"""))
    }
}

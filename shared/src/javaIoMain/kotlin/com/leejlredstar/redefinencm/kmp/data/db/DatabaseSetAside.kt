package com.leejlredstar.redefinencm.kmp.data.db

import java.io.File

/**
 * Renames [databaseFile], and SQLite's journal files beside it, to
 * `<name>.<stamp>[-n].bak` so the next open starts empty and nothing of the old data is deleted.
 * [stamp] is the time it happened as `yyyyMMdd-HHmmss`. Returns the new name of the database
 * file, or null when there is no database file or it could not be renamed: a name returned for a
 * file that was never there told the user their data was kept in it.
 *
 * The desktop does this when the user chooses to start over; Android when SQLite reports the file
 * corrupt, where the platform would otherwise delete it.
 */
fun putDatabaseAside(databaseFile: File, stamp: String): File? {
    if (!databaseFile.exists()) return null
    // A name nothing holds yet. Putting a second file aside within the same second would
    // otherwise rename it over the first on Linux and macOS, and the first is the only copy of
    // the old data.
    val target = generateSequence(0) { it + 1 }
        .map { attempt -> if (attempt == 0) ".$stamp.bak" else ".$stamp-$attempt.bak" }
        .map { suffix -> File(databaseFile.parentFile, databaseFile.name + suffix) }
        .first { !it.exists() }
    if (!databaseFile.renameTo(target)) return null
    for (journal in listOf("-journal", "-wal", "-shm")) {
        val side = File(databaseFile.parentFile, databaseFile.name + journal)
        if (side.exists() && !side.renameTo(File(side.parentFile, target.name + journal))) {
            // A journal left beside a new, empty database would be replayed into it.
            if (!side.delete()) return null
        }
    }
    return target
}

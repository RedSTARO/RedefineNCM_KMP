package com.leejlredstar.redefinencm.kmp.util

import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * The `java.io.File` half of local-media sidecar storage, shared by Desktop and Android.
 *
 * Both targets keep lyrics and artwork beside the audio file in a plain directory, and both have
 * to replace a set of them without ever leaving a half-written state on disk: a reader that
 * arrives mid-write must see either the old sidecars or the new ones. That transaction was
 * written once per target, and the copies had drifted — Desktop moved a backup aside and back
 * with an atomic move, Android renamed and checked the boolean, and the two reported failures
 * differently. The steps live here once; each target supplies only the move primitive it has.
 *
 * Desktop uses `Files.move` with `ATOMIC_MOVE`. Android cannot: `java.nio.file` needs API 26 and
 * this module's minSdk is 24, so its legacy path passes `File.renameTo`.
 */

/** Creates [directory] if it is missing. [label] names the path in user-visible failures. */
internal fun ensureLocalMediaAssetDirectory(directory: File, label: String): File =
    directory.also {
        check(it.isDirectory || it.mkdirs()) { "无法创建$label：$it" }
    }

/** Every file name directly in [directory], or none when it does not exist yet. */
internal fun localMediaAssetFileNames(directory: File, label: String): List<String> =
    localMediaAssetDirectoryEntries(directory, label).map(File::getName)

/** The files in [directory] that [predicate] accepts as belonging to [songId]. */
internal fun localMediaAssetFiles(
    directory: File,
    songId: Long,
    label: String,
    predicate: (Long, String) -> Boolean,
): List<File> = localMediaAssetDirectoryEntries(directory, label)
    .filter { predicate(songId, it.name) }

private fun localMediaAssetDirectoryEntries(directory: File, label: String): List<File> {
    if (!directory.exists()) return emptyList()
    check(directory.isDirectory) { "$label 不是目录：$directory" }
    return checkNotNull(directory.listFiles()) { "无法读取$label：$directory" }
        .filter(File::isFile)
}

/**
 * A staging name for [targetName].
 *
 * The leading dot keeps it out of media scanners, and the random component means two writers
 * racing on the same sidecar stage to different files rather than corrupting one.
 */
internal fun temporaryLocalMediaAssetFile(directory: File, targetName: String): File =
    File(directory, ".$targetName.${UUID.randomUUID()}.asset-pending")

/** A name to park the existing [targetName] under until the replacement is published. */
internal fun backupLocalMediaAssetFile(directory: File, targetName: String): File =
    File(directory, ".$targetName.${UUID.randomUUID()}.asset-backup")

/**
 * Writes [bytes] to [file] and forces them to the device.
 *
 * Without the sync the rename below can land before the contents do, which after a power loss
 * leaves a correctly named sidecar full of zeroes.
 */
internal fun writeLocalMediaAssetAndSync(file: File, bytes: ByteArray) {
    FileOutputStream(file, false).use { output ->
        output.write(bytes)
        output.fd.sync()
    }
}

/** Deletes [file], accepting that it may already be gone. */
internal fun deleteLocalMediaAssetOrThrow(file: File) {
    check(!file.exists() || file.delete() || !file.exists()) {
        "无法删除本地媒体边车：${file.name}"
    }
}

/** One sidecar to write: its final name and its contents. */
internal class LocalMediaAssetWrite(val fileName: String, val bytes: ByteArray)

/**
 * Publishes [replacements] into [directory], displacing [displaced].
 *
 * Ordering is what makes this recoverable. Each replacement is written to a staging name and
 * synced first, so a failure part-way through has touched nothing a reader can see. Only then
 * are the [displaced] files moved aside, the staged files published, and the backups dropped.
 * A failure at any point deletes whatever was staged or published and moves every backup back;
 * a backup that cannot be restored is attached to the original failure rather than replacing
 * it, because the first failure is the one that explains what happened.
 *
 * @param displaced the sidecars this replacement supersedes. Supplied by the caller rather than
 *   scanned here: Android's app-private artwork can sit in either the external or the internal
 *   files directory, so the set does not always come from one directory listing.
 * @param move renames a file. Every call this function makes has a free target, because an
 *   existing sidecar is moved aside before its replacement is published; a mover therefore need
 *   not handle an occupied target, which Android's `File.renameTo` cannot do portably.
 */
internal fun replaceLocalMediaAssetFiles(
    directory: File,
    replacements: List<LocalMediaAssetWrite>,
    displaced: List<File>,
    move: (File, File) -> Unit,
) {
    val staged = mutableListOf<Pair<File, File>>()
    val backups = mutableListOf<Pair<File, File>>()
    val published = mutableListOf<File>()
    try {
        replacements.forEach { replacement ->
            val target = File(directory, replacement.fileName)
            val temporary = temporaryLocalMediaAssetFile(directory, replacement.fileName)
            staged += temporary to target
            writeLocalMediaAssetAndSync(temporary, replacement.bytes)
        }
        displaced.forEach { original ->
            // Beside the original, not in [directory]: a rename across directories is not
            // guaranteed to be atomic, and on Android the two can be on different volumes.
            val backup = backupLocalMediaAssetFile(original.parentFile ?: directory, original.name)
            move(original, backup)
            backups += backup to original
        }
        staged.forEach { (temporary, target) ->
            move(temporary, target)
            published += target
        }
        backups.forEach { (backup, _) -> backup.delete() }
    } catch (failure: Throwable) {
        staged.forEach { (temporary, _) -> temporary.delete() }
        published.forEach { it.delete() }
        backups.forEach { (backup, original) ->
            if (backup.exists()) {
                runCatching { move(backup, original) }
                    .exceptionOrNull()
                    ?.let(failure::addSuppressed)
            }
        }
        throw failure
    }
}

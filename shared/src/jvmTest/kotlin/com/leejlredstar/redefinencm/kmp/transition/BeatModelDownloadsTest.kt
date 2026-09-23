package com.leejlredstar.redefinencm.kmp.transition

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeatModelDownloadsTest {

    @Test
    fun pinnedModelsAreTheFilesInTheRepository() {
        val pinned = listOf(
            BeatModelDownloads.onnx to BeatModelDownloads.ONNX_PATH,
            BeatModelDownloads.liteRt to BeatModelDownloads.LITERT_PATH,
        )
        for ((model, path) in pinned) {
            // The tests run in the shared project's directory.
            val file = File(path.removePrefix("shared/"))
            assertTrue(file.isFile, "$path is missing")
            assertEquals(model.sizeBytes, file.length(), "$path changed size; update BeatModelDownloads")
            assertEquals(model.sha256, sha256(file), "$path changed; update BeatModelDownloads")
            assertTrue(model.urls.size >= 2 && model.urls.all { it.startsWith("https://") && it.endsWith(path) })
        }
    }

    @Test
    fun aMissingMirrorFallsThroughToTheNextAndTheFileIsKept() = runBlocking<Unit> {
        val folder = Files.createTempDirectory("model-download").toFile()
        try {
            val source = File(folder, "source.bin").apply { writeBytes(ByteArray(300_000) { (it % 251).toByte() }) }
            val model = DownloadableModel(
                fileName = "model.bin",
                sizeBytes = source.length(),
                sha256 = sha256(source),
                urls = listOf(File(folder, "missing.bin").toURI().toString(), source.toURI().toString()),
            )
            val store = File(folder, "store")
            val download = ModelDownload(model) { store }
            val result = assertIs<ModelFile.Ready>(download.file())
            assertContentEquals(source.readBytes(), result.file.readBytes())
            assertNull(download.progress.value)
            // Once it is on disk and intact, no mirror is needed.
            source.delete()
            assertIs<ModelFile.Ready>(ModelDownload(model) { store }.file())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun aFileThatFailsItsChecksumIsNeverKept() = runBlocking<Unit> {
        val folder = Files.createTempDirectory("model-download").toFile()
        try {
            val source = File(folder, "source.bin").apply { writeBytes(ByteArray(10_000) { 7 }) }
            val model = DownloadableModel(
                fileName = "model.bin",
                sizeBytes = source.length(),
                sha256 = "0".repeat(64),
                urls = listOf(source.toURI().toString()),
            )
            val store = File(folder, "store")
            assertIs<ModelFile.Failed>(ModelDownload(model) { store }.file())
            assertEquals(emptyList(), store.listFiles().orEmpty().map { it.name })
            // A damaged copy already in the store is not loaded either.
            File(store, "model.bin").writeBytes(source.readBytes())
            assertIs<ModelFile.Failed>(ModelDownload(model.copyWithUrls(emptyList())) { store }.file())
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun DownloadableModel.copyWithUrls(urls: List<String>) =
        DownloadableModel(fileName, sizeBytes, sha256, urls)
}

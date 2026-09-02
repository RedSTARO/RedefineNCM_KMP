package com.leejlredstar.redefinencm.kmp.lyric

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * webview 0.11（系统 WebView2）的精简 Kotlin JNA 绑定。
 */
internal interface WebviewJna : Library {
    fun webview_create(debug: Int, window: Pointer?): Long
    fun webview_get_window(w: Long): Pointer?
    fun webview_destroy(w: Long)
    fun webview_run(w: Long)
    fun webview_terminate(w: Long)
    fun webview_dispatch(w: Long, fn: DispatchCallback, arg: Long)
    fun webview_set_size(w: Long, width: Int, height: Int, hints: Int)
    fun webview_navigate(w: Long, url: String)
    fun webview_init(w: Long, js: String)
    fun webview_eval(w: Long, js: String)
    fun webview_bind(w: Long, name: String, fn: BindCallback, arg: Long)
    fun webview_return(w: Long, seq: Long, status: Int, result: String)

    interface BindCallback : Callback {
        fun callback(seq: Long, req: String?, arg: Long)
    }

    interface DispatchCallback : Callback {
        fun callback(w: Long, arg: Long)
    }

    companion object {
        private const val NATIVE_RESOURCE =
            "/dev/webview/webview_java/natives/x86_64/windows_nt/webview.dll"

        val N: WebviewJna by lazy {
            Native.load(
                extractNativeLibrary().absolutePath,
                WebviewJna::class.java,
                mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"),
            )
        }

        /**
         * Unpacks the bundled WebView2 binding and returns it for an **absolute-path** load.
         *
         * Two details here are load-bearing, and getting either wrong leaves the Legacy renderer
         * silently falling back to Native Compose:
         *
         * - The extracted file must keep the upstream `webview.dll` name. JNA resolves a library
         *   by name through its own `LoadLibrary` search, so it never finds a differently named
         *   file even when `System.load` has already mapped it into this process.
         * - It must be loaded by absolute path rather than through `jna.library.path`. JNA
         *   captures that property while initializing `NativeLibrary`, which the Windows SMTC
         *   bindings already trigger during startup — long before a lyric page can set it.
         *
         * The directory is content-addressed for the same reason the AMLL assets are: a new
         * application version then never has to overwrite a DLL an older running instance still
         * has mapped, which Windows refuses anyway.
         */
        private fun extractNativeLibrary(): File {
            val bytes = WebviewJna::class.java.getResourceAsStream(NATIVE_RESOURCE)
                ?.use { it.readBytes() }
                ?: error("webview native missing on classpath: $NATIVE_RESOURCE")
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            val directory = File(
                File(System.getProperty("java.io.tmpdir"), "redefinencm-webview"),
                digest.take(16),
            )
            val target = File(directory, "webview.dll")
            if (target.isFile && target.length() == bytes.size.toLong()) return target

            check(directory.mkdirs() || directory.isDirectory) {
                "Cannot create webview native directory: $directory"
            }
            val staging = File(directory, "webview-${UUID.randomUUID()}.dll")
            staging.writeBytes(bytes)
            runCatching {
                Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }.onFailure { error ->
                staging.delete()
                // Another process published the same content first; its file is byte-identical.
                if (!target.isFile) throw error
            }
            return target
        }
    }
}

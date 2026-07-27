package com.leejlredstar.redefinencm.kmp.lyric

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File

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
        val N: WebviewJna by lazy {
            extractAndLoadNative()
            Native.load(
                "webview",
                WebviewJna::class.java,
                mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"),
            )
        }

        private fun extractAndLoadNative() {
            val res = "/dev/webview/webview_java/natives/x86_64/windows_nt/webview.dll"
            val target = File(System.getProperty("java.io.tmpdir"), "redefinencm-webview.dll")
            runCatching {
                WebviewJna::class.java.getResourceAsStream(res)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("webview native missing on classpath: $res")
            }.onFailure {
                if (!target.exists()) throw it
            }
            System.load(target.absolutePath)
            System.setProperty("jna.library.path", target.parent)
        }
    }
}

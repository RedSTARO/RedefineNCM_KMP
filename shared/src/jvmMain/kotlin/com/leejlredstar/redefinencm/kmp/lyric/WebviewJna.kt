package com.leejlredstar.redefinencm.kmp.lyric

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File

/**
 * webview 0.11（系统 WebView2）的精简 Kotlin JNA 绑定。
 *
 * 为什么不用上游 webview_java 的绑定：这版打包 dll 的**嵌入分支是坏的**——
 * 实测无论把窗口句柄直传（HWND）还是包装（HWND*），在纯 AWT Frame 上
 * webview_create 都以 JNA "Invalid memory access" 崩掉（构造器 C++ 异常穿越
 * C ABI 边界）；只有 window=null 的自建窗口路径稳定。因此嵌入通过
 * webview_get_window + Win32 SetParent 把自建窗口收编为 AWT Canvas 子窗口实现。
 *
 * native dll 复用上游 core jar 里打包的资源（/dev/webview/webview_java/natives/…）。
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

    /** C: void (*fn)(const char *seq, const char *req, void *arg) —— seq 以 long 透传回 webview_return。 */
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
                // 已有旧副本被占用时拷贝失败可以容忍，直接加载旧副本
                if (!target.exists()) throw it
            }
            System.load(target.absolutePath)
            System.setProperty("jna.library.path", target.parent)
        }
    }
}

package com.leejlredstar.redefinencm.kmp

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.UUID
import kotlin.concurrent.thread

/**
 * One running app per user.
 *
 * Closing the main window keeps the app in the tray by default, so starting it again from the
 * Start menu or the dock is how people get the window back. A second process would instead open
 * the same database and settings, add a second tray icon and media session, and play over the
 * first.
 *
 * The first process holds an exclusive lock on a file beside the database and listens on a
 * loopback port it writes next to that file. A later process that cannot take the lock asks that
 * port to show the window, and exits. The operating system drops the lock when its holder dies,
 * so a crash never leaves the app unable to start.
 *
 * A loopback port can be reached by every account on the machine, so the request carries a token
 * that only the owner of the port file can read, and what the listener reads from a connection is
 * bounded in size and in time.
 */
internal class DesktopSingleInstance private constructor(
    private val lockFile: RandomAccessFile?,
    private val lock: FileLock?,
    private val server: ServerSocket?,
    private val portFile: File,
) : AutoCloseable {

    override fun close() {
        runCatching { server?.close() }
        if (lock != null) runCatching { portFile.delete() }
        runCatching { lock?.release() }
        runCatching { lockFile?.close() }
    }

    /** What a launch found: this process is the app, or another one already is. */
    sealed interface Launch {
        class First(val instance: DesktopSingleInstance) : Launch

        /** [shown] is whether the running app answered the request to show its window. */
        class AlreadyRunning(val shown: Boolean) : Launch
    }

    companion object {
        private const val LOCK_FILE = "instance.lock"
        private const val PORT_FILE = "instance.port"
        private const val SHOW_REQUEST = "show"
        private const val SHOW_ACKNOWLEDGED = "ok"
        private const val ANSWER_TIMEOUT_MS = 1_500

        /** Longer than any request this protocol has; a connection that sends more is dropped. */
        private const val MAX_REQUEST_BYTES = 96

        /**
         * Takes the instance lock in [directory], or asks the process holding it to show its
         * window. [onActivate] runs on a background thread each time a later launch asks.
         *
         * A directory that cannot hold a lock at all (a read-only or network home) starts the app
         * unguarded: refusing to start would be worse than the duplicate this exists to prevent.
         */
        fun launch(directory: File, onActivate: () -> Unit): Launch {
            val portFile = File(directory, PORT_FILE)
            val lockFile: RandomAccessFile
            val lock: FileLock?
            try {
                directory.mkdirs()
                lockFile = RandomAccessFile(File(directory, LOCK_FILE), "rw")
                lock = try {
                    lockFile.channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    // Held by this very JVM, which only a test does.
                    null
                }
            } catch (failure: IOException) {
                System.err.println("Single-instance lock unavailable, starting without it: ${failure.message}")
                return Launch.First(DesktopSingleInstance(null, null, null, portFile))
            }
            if (lock == null) {
                runCatching { lockFile.close() }
                return Launch.AlreadyRunning(shown = askRunningInstanceToShow(portFile))
            }
            val server = runCatching { listen(portFile, onActivate) }
                .onFailure { System.err.println("Single-instance port unavailable: ${it.message}") }
                .getOrNull()
            return Launch.First(DesktopSingleInstance(lockFile, lock, server, portFile))
        }

        private fun listen(portFile: File, onActivate: () -> Unit): ServerSocket {
            val server = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
            val token = UUID.randomUUID().toString()
            writeOwnerOnly(portFile, "${server.localPort} $token")
            thread(name = "single-instance", isDaemon = true) {
                while (!server.isClosed) {
                    val socket = try {
                        server.accept()
                    } catch (_: IOException) {
                        break
                    }
                    runCatching {
                        socket.use { client ->
                            if (readRequest(client) == "$SHOW_REQUEST $token") {
                                client.getOutputStream().apply {
                                    write("$SHOW_ACKNOWLEDGED\n".toByteArray())
                                    flush()
                                }
                                onActivate()
                            }
                        }
                    }
                }
            }
            return server
        }

        /** Only this user reads the token, where the file system can say so. */
        private fun writeOwnerOnly(file: File, content: String) {
            file.delete()
            file.createNewFile()
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            file.writeText(content)
        }

        /**
         * One line of at most [MAX_REQUEST_BYTES], read within [ANSWER_TIMEOUT_MS] in total. A
         * connection that never ends its line, or sends it a byte at a time, gets null and is
         * closed: the listener serves one connection at a time and must stay free for the next.
         */
        private fun readRequest(client: Socket): String? {
            val deadline = System.nanoTime() + ANSWER_TIMEOUT_MS * 1_000_000L
            val input = client.getInputStream()
            val line = ByteArrayOutputStream()
            while (line.size() <= MAX_REQUEST_BYTES) {
                val remainingMs = (deadline - System.nanoTime()) / 1_000_000L
                if (remainingMs <= 0) return null
                client.soTimeout = remainingMs.toInt().coerceAtLeast(1)
                val next = input.read()
                if (next < 0 || next == '\n'.code) return line.toString(Charsets.UTF_8).trimEnd('\r')
                line.write(next)
            }
            return null
        }

        /** The holder may still be binding its port, so the request is retried for two seconds. */
        private fun askRunningInstanceToShow(portFile: File): Boolean {
            repeat(20) {
                val parts = runCatching { portFile.readText().trim().split(' ') }.getOrNull()
                val port = parts?.getOrNull(0)?.toIntOrNull()
                val token = parts?.getOrNull(1)
                if (port != null && token != null && requestShow(port, token)) return true
                Thread.sleep(100)
            }
            return false
        }

        private fun requestShow(port: Int, token: String): Boolean = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500)
                socket.soTimeout = ANSWER_TIMEOUT_MS
                socket.getOutputStream().apply {
                    write("$SHOW_REQUEST $token\n".toByteArray())
                    flush()
                }
                // A stale port file can point at some other program; its answer is bounded too.
                readRequest(socket) == SHOW_ACKNOWLEDGED
            }
        }.getOrDefault(false)
    }
}

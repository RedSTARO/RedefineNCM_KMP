package com.leejlredstar.redefinencm.kmp

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Keeps what the app prints.
 *
 * An installed build has no console: everything the players, the media controls and the HTTP
 * client report on the standard streams was lost, and so was the stack trace of a crash. Both
 * streams are copied into one file beside the database, which is what a bug report can attach.
 * The console, when there is one, still gets every line as it was printed.
 *
 * The file is meant to be handed to someone else, so it holds less than the console does: the
 * query string of every URL is dropped before a line is written (see [scrubLogLine]).
 */
internal object DesktopLog {
    const val FILE_NAME = "redefinencm.log"
    const val PREVIOUS_FILE_NAME = "redefinencm.previous.log"

    /** One file never grows past this; the one before it is kept, older ones are dropped. */
    const val MAX_BYTES = 2L * 1024 * 1024

    /** Starts copying the standard streams into [directory]; returns the log file, or null if it cannot be written. */
    fun install(directory: File): File? = runCatching {
        directory.mkdirs()
        val file = File(directory, FILE_NAME)
        val sink = RotatingLogFile(file, File(directory, PREVIOUS_FILE_NAME), MAX_BYTES)
        System.setOut(PrintStream(TeeStream(sink, System.out), true, Charsets.UTF_8))
        System.setErr(PrintStream(TeeStream(sink, System.err), true, Charsets.UTF_8))
        file
    }.onFailure { System.err.println("Log file unavailable: ${it.message}") }.getOrNull()
}

private val urlWithQuery = Regex("""(https?://[^\s"'<>?]+)\?[^\s"'<>]*""")

/**
 * A line as the log file stores it: every URL keeps its host and path and loses its query.
 *
 * The query is where the private part of a request lives. A stream address carries its access
 * token and, for QQ Music, the account number; the players print that address when a stream will
 * not open. The API lines carry what was searched for, the account's id, each song as it is
 * reported, and the key of a QR sign-in still waiting to be scanned. The host, the path and the
 * status are what diagnosing a failure needs.
 */
internal fun scrubLogLine(line: String): String =
    urlWithQuery.replace(line) { match -> "${match.groupValues[1]}?<redacted>" }

/**
 * Appends to [file], and starts it again once it would pass [maxBytes], keeping what it held as
 * [previous]. Shared by both standard streams, so writes are serialised here.
 */
internal class RotatingLogFile(
    private val file: File,
    private val previous: File,
    private val maxBytes: Long,
) : Closeable {
    init { require(maxBytes > 0) }
    private var output: OutputStream = FileOutputStream(file, true)
    private var written: Long = file.length()

    @Synchronized
    fun write(bytes: ByteArray, offset: Int, length: Int) {
        // Logging must remain bounded even when a caller skips TeeStream or rotation is blocked.
        if (length > maxBytes) return
        if (written + length > maxBytes && !rotate()) return
        output.write(bytes, offset, length)
        output.flush()
        written += length
    }

    @Synchronized
    override fun close() {
        output.close()
    }

    private fun rotate(): Boolean {
        runCatching { output.close() }
        runCatching { previous.delete() }
        // Keep the old log when it cannot be moved. Drop file output until rotation succeeds;
        // the console still receives it and the log cannot consume unbounded disk space.
        val renamed = file.renameTo(previous)
        output = FileOutputStream(file, !renamed)
        written = if (renamed) 0 else file.length()
        return renamed
    }
}

/**
 * Receives what the replacement stream encoded as UTF-8. The log gets each line once it is
 * complete, with the time in front and its URLs scrubbed; the stream that was there before gets
 * the same text straight away and encodes it for its own console.
 */
internal class TeeStream(
    private val log: RotatingLogFile,
    private val console: PrintStream,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : OutputStream() {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    /** The first bytes of a character whose remaining bytes arrive with the next write. */
    private var carried = ByteArray(0)

    /** The line being assembled for the log; a line is scrubbed whole or not at all. */
    private val pendingLine = ByteArrayOutputStream()
    private var discardingLine = false

    override fun write(byte: Int) = write(byteArrayOf(byte.toByte()), 0, 1)

    @Synchronized
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        runCatching { collectLogLines(bytes, offset, length) }
        val input = ByteBuffer.wrap(carried + bytes.copyOfRange(offset, offset + length))
        val text = CharBuffer.allocate(input.remaining() + 1)
        decoder.decode(input, text, false)
        carried = ByteArray(input.remaining()).also { input.get(it) }
        text.flip()
        if (text.hasRemaining()) console.print(text.toString())
    }

    override fun flush() = console.flush()

    private fun collectLogLines(bytes: ByteArray, offset: Int, length: Int) {
        val end = offset + length
        var start = offset
        while (start < end) {
            var stop = start
            while (stop < end && bytes[stop] != NEWLINE) stop++
            if (!discardingLine) {
                if (stop - start > MAX_PENDING_BYTES - pendingLine.size()) {
                    // Never scrub a partial line: a URL query may continue in the next write.
                    // Discard the entire oversized line, including the already buffered prefix.
                    pendingLine.reset()
                    discardingLine = true
                    writeLogLine("[overlong log line omitted]")
                } else {
                    pendingLine.write(bytes, start, stop - start)
                }
            }
            if (stop < end) {
                if (!discardingLine) writeLogLine(pendingLine.toString(Charsets.UTF_8))
                pendingLine.reset()
                discardingLine = false
                stop++
            }
            start = stop
        }
    }

    private fun writeLogLine(text: String) {
        val line = buildString {
            append(now().format(logStamp)).append(' ').append(scrubLogLine(text))
            append('\n')
        }.toByteArray()
        log.write(line, 0, line.size)
    }

    private companion object {
        const val NEWLINE: Byte = 10
        const val MAX_PENDING_BYTES = 16 * 1024
        val logStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}

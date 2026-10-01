package com.leejlredstar.redefinencm.kmp

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
 * The console, when there is one, still gets every line.
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

/**
 * Appends to [file], and starts it again once it would pass [maxBytes], keeping what it held as
 * [previous]. Shared by both standard streams, so writes are serialised here.
 */
internal class RotatingLogFile(
    private val file: File,
    private val previous: File,
    private val maxBytes: Long,
) {
    private var output: OutputStream = FileOutputStream(file, true)
    private var written: Long = file.length()

    @Synchronized
    fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (written > 0 && written + length > maxBytes) rotate()
        output.write(bytes, offset, length)
        output.flush()
        written += length
    }

    private fun rotate() {
        runCatching { output.close() }
        runCatching { previous.delete() }
        // A rename that fails (another program holds the file) leaves one growing file, which is
        // still a log; it must not stop the app writing.
        val renamed = file.renameTo(previous)
        output = FileOutputStream(file, !renamed)
        written = if (renamed) 0 else file.length()
    }
}

/**
 * Receives what the replacement stream encoded as UTF-8, writes those bytes to the log, and hands
 * the same text to the stream that was there before, which encodes it for its own console.
 */
private class TeeStream(
    private val log: RotatingLogFile,
    private val console: PrintStream,
) : OutputStream() {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    /** The first bytes of a character whose remaining bytes arrive with the next write. */
    private var carried = ByteArray(0)

    /** Whether the next byte for the log begins a line, and so gets the time in front of it. */
    private var atLineStart = true

    override fun write(byte: Int) = write(byteArrayOf(byte.toByte()), 0, 1)

    @Synchronized
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        runCatching { writeLogLines(bytes, offset, length) }
        val input = ByteBuffer.wrap(carried + bytes.copyOfRange(offset, offset + length))
        val text = CharBuffer.allocate(input.remaining() + 1)
        decoder.decode(input, text, false)
        carried = ByteArray(input.remaining()).also { input.get(it) }
        text.flip()
        if (text.hasRemaining()) console.print(text.toString())
    }

    override fun flush() = console.flush()

    /** The console shows lines as they come; in a file read later, each needs its time. */
    private fun writeLogLines(bytes: ByteArray, offset: Int, length: Int) {
        val end = offset + length
        var start = offset
        while (start < end) {
            if (atLineStart) {
                val stamp = "${LocalDateTime.now().format(logStamp)} ".toByteArray()
                log.write(stamp, 0, stamp.size)
                atLineStart = false
            }
            var stop = start
            while (stop < end && bytes[stop] != NEWLINE) stop++
            if (stop < end) {
                stop++
                atLineStart = true
            }
            log.write(bytes, start, stop - start)
            start = stop
        }
    }

    private companion object {
        const val NEWLINE: Byte = 10
        val logStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }
}

package com.leejlredstar.redefinencm.kmp.player

import java.lang.reflect.Proxy
import javax.sound.sampled.SourceDataLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A pause stops the line mid-write; the rest of that write must still be heard after it. */
class WriteFullyTest {
    /** A line that takes [shortFirst] bytes of the first write, as a stopped line does, then all. */
    private fun line(received: MutableList<Byte>, shortFirst: Int): SourceDataLine {
        var writes = 0
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "write" -> {
                    val bytes = args[0] as ByteArray
                    val offset = args[1] as Int
                    val length = args[2] as Int
                    val taken = if (writes++ == 0) minOf(shortFirst, length) else length
                    for (i in offset until offset + taken) received += bytes[i]
                    taken
                }
                "isRunning" -> true
                else -> null
            }
        } as SourceDataLine
    }

    @Test
    fun aWriteCutShortByAPauseIsFinishedOnResume() {
        val received = mutableListOf<Byte>()
        val bytes = ByteArray(16) { it.toByte() }
        var waited = 0
        assertTrue(writeFully(line(received, shortFirst = 6), bytes, 16, paused = { true }, awaitResume = { waited++; true }))
        assertEquals(bytes.toList(), received)
        assertEquals(1, waited)
    }

    @Test
    fun aSessionEndedDuringThePauseWritesNothingMore() {
        val received = mutableListOf<Byte>()
        assertFalse(writeFully(line(received, shortFirst = 6), ByteArray(16), 16, paused = { true }, awaitResume = { false }))
        assertEquals(6, received.size)
    }

    @Test
    fun aShortWriteWithoutAPauseIsLeftToTheCaller() {
        val received = mutableListOf<Byte>()
        var waited = false
        assertTrue(writeFully(line(received, shortFirst = 6), ByteArray(16), 16, paused = { false }, awaitResume = { waited = true; true }))
        assertFalse(waited)
        assertEquals(6, received.size)
    }
}

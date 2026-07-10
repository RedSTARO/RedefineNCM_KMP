package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.lyric.desktopEmbeddedWebViewSupported
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.random.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class JvmAudioSupportTest {

    @Test
    fun highResolutionQualitiesAreMappedToMp3Level() {
        assertEquals("standard", jvmPlaybackQualityLevel(SoundQuality.STANDARD))
        assertEquals("higher", jvmPlaybackQualityLevel(SoundQuality.HIGHER))
        assertEquals("exhigh", jvmPlaybackQualityLevel(SoundQuality.EXHIGH))
        assertEquals("exhigh", jvmPlaybackQualityLevel(SoundQuality.LOSSLESS))
        assertEquals("exhigh", jvmPlaybackQualityLevel(SoundQuality.JYMASTER))
    }

    @Test
    fun localResolverOnlyAcceptsJvmDecodableAudio() {
        assertTrue(isJvmPlayableAudioUri("file:/C:/Music/RedefineNCM/2097485077.mp3"))
        assertTrue(isJvmPlayableAudioUri("https://example.com/audio/track.wav?token=abc"))
        assertFalse(isJvmPlayableAudioUri("file:/C:/Music/RedefineNCM/2097485077.flac"))
        assertFalse(isJvmPlayableAudioUri("https://example.com/audio/track.m4a"))
    }

    @Test
    fun playerVolumeUsesBoundedPercentPersistence() {
        assertEquals(0f, normalizePlayerVolume(-0.25f))
        assertEquals(1f, normalizePlayerVolume(1.25f))
        assertEquals(1f, normalizePlayerVolume(Float.NaN))
        assertEquals(0.42f, playerVolumeFromPercent(42))
        assertEquals(0f, playerVolumeFromPercent(-1))
        assertEquals(1f, playerVolumeFromPercent(101))
        assertEquals(56L, playerVolumeToPercent(0.555f))
        assertEquals(0L, playerVolumeToPercent(-0.2f))
        assertEquals(100L, playerVolumeToPercent(1.2f))
    }

    @Test
    fun publishedQueueUsesTheSameShuffleSnapshotForRowsAndHighlight() {
        val current = Any()
        val queue = PlayQueue.of(listOf(Any(), current, Any()), startIndex = 1)
            .setShuffle(true, Random(7))

        val publication = queue.asJvmQueuePublication()

        assertEquals(queue.itemsInPlayOrder, publication.items)
        assertEquals(queue.positionInPlayOrder, publication.currentIndex)
        assertSame(current, publication.items[publication.currentIndex])
        assertSame(current, publication.currentMedia)
    }

    @Test
    fun concurrentQueueMutationCannotOvertakeOlderPublication() {
        val state = JvmQueueState<String>()
        val firstPublishing = CountDownLatch(1)
        val releaseFirstPublication = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val publications = mutableListOf<List<String>>()

        val first = thread(start = true, name = "queue-first") {
            state.mutateAndPublish(
                invalidatesPlaybackClaim = true,
                block = { PlayQueue.of(listOf("A"), startIndex = 0) },
                publish = { queue ->
                    firstPublishing.countDown()
                    assertTrue(releaseFirstPublication.await(5, TimeUnit.SECONDS))
                    publications += queue.items
                },
            )
        }
        assertTrue(firstPublishing.await(5, TimeUnit.SECONDS))

        val second = thread(start = true, name = "queue-second") {
            secondStarted.countDown()
            state.mutateAndPublish(
                invalidatesPlaybackClaim = true,
                block = { PlayQueue.of(listOf("B"), startIndex = 0) },
                publish = { queue -> publications += queue.items },
            )
            secondFinished.countDown()
        }
        assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
        assertFalse(secondFinished.await(200, TimeUnit.MILLISECONDS))

        releaseFirstPublication.countDown()
        first.join(5_000)
        second.join(5_000)

        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
        assertEquals(listOf(listOf("A"), listOf("B")), publications)
        assertEquals(listOf("B"), state.current().model.items)
    }

    @Test
    fun nonRepeatingQueueDoesNotWrapAtTheEnd() {
        val queue = PlayQueue.of(listOf("first", "last"), startIndex = 1)

        val afterEnd = queue.next(repeat = false)

        assertEquals(1, afterEnd.currentIndex)
        assertEquals("last", afterEnd.currentItem)
    }

    @Test
    fun embeddedLyricWebViewAdvertisesOnlyItsActualNativeTarget() {
        assertTrue(desktopEmbeddedWebViewSupported("Windows 11", "amd64"))
        assertTrue(desktopEmbeddedWebViewSupported("Windows 11", "x86_64"))
        assertFalse(desktopEmbeddedWebViewSupported("Windows 11", "aarch64"))
        assertFalse(desktopEmbeddedWebViewSupported("Linux", "amd64"))
        assertFalse(desktopEmbeddedWebViewSupported("Mac OS X", "x86_64"))
    }
}

package com.leejlredstar.redefinencm.kmp

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopSingleInstanceTest {

    @Test
    fun aSecondLaunchAsksTheFirstForItsWindowAndStepsAside() {
        val directory = Files.createTempDirectory("redefinencm-instance").toFile()
        val activated = CountDownLatch(1)
        val first = assertIs<DesktopSingleInstance.Launch.First>(
            DesktopSingleInstance.launch(directory) {
                activated.countDown()
                true
            },
        )

        try {
            val second = assertIs<DesktopSingleInstance.Launch.AlreadyRunning>(
                DesktopSingleInstance.launch(directory) { error("the second launch is not the app") },
            )

            assertTrue(second.shown)
            assertTrue(activated.await(5, TimeUnit.SECONDS), "the running app was not asked to show its window")
        } finally {
            first.instance.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun aRunningAppWithNoWindowYetSaysSoInsteadOfAcknowledging() {
        val directory = Files.createTempDirectory("redefinencm-instance").toFile()
        // Still starting: nothing has a window to show.
        val first = assertIs<DesktopSingleInstance.Launch.First>(DesktopSingleInstance.launch(directory) { false })

        try {
            val second = assertIs<DesktopSingleInstance.Launch.AlreadyRunning>(
                DesktopSingleInstance.launch(directory) { error("the second launch is not the app") },
            )

            assertFalse(second.shown)
        } finally {
            first.instance.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun onceTheFirstIsGoneTheNextLaunchIsTheApp() {
        val directory = Files.createTempDirectory("redefinencm-instance").toFile()
        assertIs<DesktopSingleInstance.Launch.First>(DesktopSingleInstance.launch(directory) { true }).instance.close()

        val next = assertIs<DesktopSingleInstance.Launch.First>(DesktopSingleInstance.launch(directory) { true })

        next.instance.close()
        directory.deleteRecursively()
    }
}

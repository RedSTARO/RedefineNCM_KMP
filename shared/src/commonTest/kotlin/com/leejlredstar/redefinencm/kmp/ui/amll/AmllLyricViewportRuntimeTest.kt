package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmllLyricViewportRuntimeTest {
    @Test
    fun layoutRequestInvalidatesTheMeasureObserverWhenTheEngineFrameDoesNotChange() {
        val engine = AmllPlayerEngine(
            lines = listOf(
                lyricLine(id = "line-0", startTimeMs = 0L, endTimeMs = 1_000L),
                lyricLine(id = "line-1", startTimeMs = 1_000L, endTimeMs = 2_000L),
            ),
        )
        val runtime = AmllViewportRuntime(engine)
        engine.layout(layoutInput())
        repeat(240) {
            engine.update(deltaMs = 16.0)
        }
        runtime.needsLayout = false

        val frameBeforeTimeChange = engine.currentFrame()
        val timeUpdate = engine.setTime(timeMs = 1_000.0)
        assertTrue(timeUpdate.shouldLayout)
        assertEquals(frameBeforeTimeChange, engine.currentFrame())

        var measureInvalidations = 0
        val observer = SnapshotStateObserver { callback -> callback() }
        observer.start()
        try {
            observer.observeReads(
                scope = Unit,
                onValueChangedForScope = { measureInvalidations += 1 },
            ) {
                assertFalse(runtime.hasPendingLayoutForMeasure())
            }

            runtime.requestLayout()
            Snapshot.sendApplyNotifications()

            assertEquals(1, measureInvalidations)
            assertTrue(runtime.hasPendingLayoutForMeasure())
        } finally {
            observer.stop()
        }
    }

    private fun lyricLine(
        id: String,
        startTimeMs: Long,
        endTimeMs: Long,
    ): AmllLyricLine = AmllLyricLine(
        id = id,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        mainText = id,
        words = listOf(
            AmllLyricWord(
                id = "$id-word",
                text = id,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
            ),
        ),
    )

    private fun layoutInput(): AmllLayoutInput = AmllLayoutInput(
        viewportWidthPx = 1_200.0,
        viewportHeightPx = 800.0,
        groupHeightsPx = listOf(100.0, 100.0),
        interludeDotsWidthPx = 0.0,
        interludeDotsHeightPx = 0.0,
        scrollOffsetPx = 0.0,
        fontSizePx = 48.0,
        bottomLineHeightPx = 0.0,
        hidePassedLines = false,
        isNonDynamic = false,
        enableSpring = true,
        enableScale = true,
        enableBlur = true,
        isUserScrolling = false,
        isCompact = false,
        isPlaying = true,
        sync = true,
        force = false,
        alwaysPostpositionBackground = false,
    )
}

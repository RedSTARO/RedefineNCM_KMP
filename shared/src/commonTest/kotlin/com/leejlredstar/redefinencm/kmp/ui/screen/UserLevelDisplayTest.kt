package com.leejlredstar.redefinencm.kmp.ui.screen

import com.leejlredstar.redefinencm.kmp.data.api.dto.UserLevelData
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserLevelResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserLevelDisplayTest {
    @Test
    fun formatsCurrentLevelCountsAndProgress() {
        val display = userLevelDisplay(
            response(
                data = levelData(
                    level = 8,
                    progress = 0.426,
                    nowPlayCount = 1_621,
                    nowLoginCount = 350,
                ),
            ),
        )

        assertNotNull(display)
        assertEquals("Lv.8 · 听歌 1621 首 · 登录 350 天", display.summary)
        assertEquals("下一级门槛：听歌 2000 首 · 登录 200 天", display.nextLevelLabel)
        assertEquals(0.426f, display.progress, absoluteTolerance = 0.0001f)
        assertEquals("等级进度 43%", display.progressLabel)
    }

    @Test
    fun fullLevelUsesCompletedProgressAndFullLabel() {
        val display = userLevelDisplay(
            response(
                full = true,
                data = levelData(level = 10, progress = 0.2),
            ),
        )

        assertNotNull(display)
        assertEquals(1f, display.progress)
        assertNull(display.nextLevelLabel)
        assertEquals("已达到最高等级", display.progressLabel)
    }

    @Test
    fun progressIsClampedToIndicatorRange() {
        val aboveRange = assertNotNull(
            userLevelDisplay(response(data = levelData(progress = 1.4))),
        )
        val belowRange = assertNotNull(
            userLevelDisplay(response(data = levelData(progress = -0.2))),
        )

        assertEquals(1f, aboveRange.progress)
        assertEquals(0f, belowRange.progress)
    }

    @Test
    fun missingResponseDataHasNoDisplayModel() {
        assertNull(userLevelDisplay(null))
        assertNull(userLevelDisplay(UserLevelResponse(code = 200, data = null)))
    }

    private fun response(
        full: Boolean = false,
        data: UserLevelData,
    ) = UserLevelResponse(
        code = 200,
        full = full,
        data = data,
    )

    private fun levelData(
        level: Int = 7,
        progress: Double = 0.5,
        nowPlayCount: Long = 800,
        nowLoginCount: Long = 100,
    ) = UserLevelData(
        userId = 42,
        info = "",
        progress = progress,
        nextPlayCount = 2_000,
        nextLoginCount = 200,
        nowPlayCount = nowPlayCount,
        nowLoginCount = nowLoginCount,
        level = level,
    )
}

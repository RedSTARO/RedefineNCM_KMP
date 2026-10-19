package com.leejlredstar.redefinencm.kmp.notification

import android.app.NotificationManager
import android.os.Build
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The lyric notification starts from Application.onCreate, so it must run on the minimum API, which
 * has no notification channels. Each SDK gets its own sandbox, so the object starts fresh in each.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 25, 36], manifest = Config.NONE)
class AndroidLyricNotificationTest {
    @Test
    fun startsAndPostsOnEveryApiLevelTheAppInstallsOn() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            AndroidLyricNotification.init(context)
            AndroidLyricNotification.setEnabled(true)
            AndroidLyricNotification.updateLyric(
                title = "Song",
                artist = "Artist",
                currentLyric = "First line",
                nextLyric = "Second line",
                artworkUri = null,
                isPlaying = true,
                positionMs = 1_000L,
                durationMs = 180_000L,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                assertNotNull(manager.getNotificationChannel("live_update_lyric"))
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Below 13 posting needs no permission, so the lyric must actually be shown.
                assertEquals(1, shadowOf(manager).allNotifications.size)
            }
        } finally {
            AndroidLyricNotification.setEnabled(false)
            AndroidLyricNotification.reset()
        }
    }
}

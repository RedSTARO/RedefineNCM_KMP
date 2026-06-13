package com.leejlredstar.redefinencm.kmp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.leejlredstar.redefinencm.kmp.notification.MediaActions
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import org.koin.java.KoinJavaComponent.get

class MediaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val player: PlatformPlayer = get(PlatformPlayer::class.java)
        when (intent.action) {
            MediaActions.TOGGLE -> player.togglePlayPause()
            MediaActions.NEXT -> player.seekToNext()
            MediaActions.PREV -> player.seekToPrevious()
        }
    }
}

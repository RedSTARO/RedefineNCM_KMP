package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

@Composable
actual fun rememberReducedMotionEnabled(): Boolean {
    var reducedMotionEnabled by remember {
        mutableStateOf(UIAccessibilityIsReduceMotionEnabled())
    }

    DisposableEffect(Unit) {
        reducedMotionEnabled = UIAccessibilityIsReduceMotionEnabled()
        val notificationCenter = NSNotificationCenter.defaultCenter
        val observer = notificationCenter.addObserverForName(
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            reducedMotionEnabled = UIAccessibilityIsReduceMotionEnabled()
        }
        onDispose {
            notificationCenter.removeObserver(observer)
        }
    }

    return reducedMotionEnabled
}

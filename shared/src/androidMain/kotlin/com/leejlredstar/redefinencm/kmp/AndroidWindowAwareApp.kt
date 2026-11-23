package com.leejlredstar.redefinencm.kmp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.leejlredstar.redefinencm.kmp.recognition.LocalMicrophoneWindowActive
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalSoftKeyboardVisible
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalWindowFold
import com.leejlredstar.redefinencm.kmp.ui.adaptive.WindowFold
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Keep platform window events out of shared screen and navigation state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AndroidWindowAwareApp(activity: ComponentActivity, microphoneWindowActive: StateFlow<Boolean>) {
    val windowFolds = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            val features = info.displayFeatures.filterIsInstance<FoldingFeature>()
            // An opaque hinge must never hide UI. A flexible fold only splits content in a
            // separating posture; when flat its entire surface can be used like a tablet.
            val feature = features.firstOrNull { it.occlusionType == FoldingFeature.OcclusionType.FULL }
                ?: features.firstOrNull { it.isSeparating }
            feature?.let {
                val bounds = it.bounds
                WindowFold(
                    bounds = IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                    isVertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
                    isOccluding = it.occlusionType == FoldingFeature.OcclusionType.FULL,
                )
            }
        }.distinctUntilChanged()
    }
    val fold by windowFolds.collectAsStateWithLifecycle(initialValue = null)
    CompositionLocalProvider(
        LocalWindowFold provides fold,
        LocalSoftKeyboardVisible provides WindowInsets.isImeVisible,
        LocalMicrophoneWindowActive provides microphoneWindowActive,
    ) {
        App()
    }
}

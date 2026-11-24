package com.leejlredstar.redefinencm.kmp

import android.os.Build
import android.view.SurfaceControl
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.app.MultiWindowModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.leejlredstar.redefinencm.kmp.recognition.LocalMicrophoneWindowActive
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalSoftKeyboardVisible
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalWindowFold
import com.leejlredstar.redefinencm.kmp.ui.adaptive.WindowFold
import com.leejlredstar.redefinencm.kmp.ui.component.AndroidReflowWindow
import com.leejlredstar.redefinencm.kmp.ui.component.LocalAndroidReflowWindow
import com.leejlredstar.redefinencm.kmp.ui.component.LocalReflowFrameCommit
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    var inMultiWindow by remember(activity) { mutableStateOf(activity.isInMultiWindowMode) }
    DisposableEffect(activity) {
        val listener = Consumer<MultiWindowModeChangedInfo> { inMultiWindow = it.isInMultiWindowMode }
        activity.addOnMultiWindowModeChangedListener(listener)
        onDispose { activity.removeOnMultiWindowModeChangedListener(listener) }
    }
    // Observe both configuration and the host's window metrics (not the IME-inset viewport).
    val orientation = LocalConfiguration.current.orientation
    val view = LocalView.current
    val window = AndroidReflowWindow(LocalWindowInfo.current.containerSize, view.display?.rotation ?: orientation, inMultiWindow)
    val awaitFrameCommit = remember(view) { suspend { awaitReflowFrameCommit(view) } }
    CompositionLocalProvider(
        LocalAndroidReflowWindow provides window,
        LocalReflowFrameCommit provides awaitFrameCommit,
        LocalWindowFold provides fold,
        LocalSoftKeyboardVisible provides WindowInsets.isImeVisible,
        LocalMicrophoneWindowActive provides microphoneWindowActive,
    ) {
        App()
    }
}

private suspend fun awaitReflowFrameCommit(view: View) {
    if (Build.VERSION.SDK_INT < 33 || !view.isAttachedToWindow || !view.isHardwareAccelerated) return
    val root = view.rootSurfaceControl ?: return
    // Unlike a Compose frame tick, this transaction follows the buffer through Android's
    // resize synchronization. It is not a guarantee that the outgoing snapshot is gone.
    suspendCancellableCoroutine { continuation ->
        val scheduled = SurfaceControl.Transaction().use { transaction ->
            transaction.addTransactionCommittedListener(view.context.mainExecutor) { continuation.resume(Unit) }
            root.applyTransactionOnDraw(transaction)
        }
        if (scheduled) view.invalidate() else continuation.resume(Unit)
    }
}

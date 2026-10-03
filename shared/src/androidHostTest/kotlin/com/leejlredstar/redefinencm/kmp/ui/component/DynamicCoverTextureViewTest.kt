package com.leejlredstar.redefinencm.kmp.ui.component

import android.os.Build
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36], manifest = Config.NONE)
class DynamicCoverTextureViewTest {
    @Test
    fun videoSurfaceCanBeCreatedWithoutAnOpaqueFrameOrBackgroundDrawable() {
        val context = RuntimeEnvironment.getApplication()
        // TextureView only ignores unsupported drawable setters for pre-N target apps.
        // Exercise the contract used by the production app, including on our minimum API.
        context.applicationInfo.targetSdkVersion = Build.VERSION_CODES.N

        val view = createDynamicCoverTextureView(context)

        assertFalse(view.isOpaque)
        assertNull(view.background)
        assertEquals(0f, view.alpha)
    }
}

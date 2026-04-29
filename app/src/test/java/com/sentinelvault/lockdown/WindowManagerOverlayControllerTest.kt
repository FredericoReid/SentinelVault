package com.sentinelvault.lockdown

import android.content.Context
import android.view.View
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the Epic 6 [WindowManagerOverlayController]. The Android `WindowManager`
 * and `View` types are stubbed via mockk; the `WindowManager.LayoutParams` constructor is
 * unreachable from `:app:testDebugUnitTest` (JVM android stubs throw on field mutation),
 * so we substitute the [LayoutParamsFactory] with a mocked instance returning a relaxed
 * mockk of `LayoutParams` itself.
 */
class WindowManagerOverlayControllerTest {

    private lateinit var context: Context
    private lateinit var windowManager: WindowManager
    private lateinit var view: View
    private lateinit var host: OverlayHost
    private lateinit var hostFactory: OverlayHostFactory
    private lateinit var layoutParamsFactory: LayoutParamsFactory
    private lateinit var controller: WindowManagerOverlayController

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        windowManager = mockk(relaxed = true)
        view = mockk(relaxed = true)
        host = object : OverlayHost {
            override val view: View get() = this@WindowManagerOverlayControllerTest.view
            var attached: Int = 0
            var detached: Int = 0
            override fun onAttached() { attached += 1 }
            override fun onDetached() { detached += 1 }
        }
        hostFactory = OverlayHostFactory { _, _ -> host }
        val params = mockk<WindowManager.LayoutParams>(relaxed = true)
        layoutParamsFactory = LayoutParamsFactory { _ -> params }

        controller = WindowManagerOverlayController(
            context = context,
            windowManager = windowManager,
            hostFactory = hostFactory,
            layoutParamsFactory = layoutParamsFactory,
            mainExecutor = { it.run() }
        )
    }

    @Test
    fun `arm transitions OFF to ARMED and adds a non-focusable view`() {
        controller.arm()

        assertThat(controller.mode).isEqualTo(SoftLockOverlayController.Mode.ARMED)
        verify(exactly = 1) { windowManager.addView(view, any<WindowManager.LayoutParams>()) }
    }

    @Test
    fun `arm is idempotent — second call from ARMED is a no-op`() {
        controller.arm()
        controller.arm()
        verify(exactly = 1) { windowManager.addView(any(), any()) }
        assertThat(controller.mode).isEqualTo(SoftLockOverlayController.Mode.ARMED)
    }

    @Test
    fun `show from OFF attaches and reaches SHOWING`() {
        controller.show()
        assertThat(controller.mode).isEqualTo(SoftLockOverlayController.Mode.SHOWING)
        verify(exactly = 1) { windowManager.addView(view, any<WindowManager.LayoutParams>()) }
        verify(exactly = 0) { windowManager.updateViewLayout(any(), any()) }
    }

    @Test
    fun `show from ARMED updates layout to capture focus without re-adding`() {
        controller.arm()
        controller.show()

        assertThat(controller.mode).isEqualTo(SoftLockOverlayController.Mode.SHOWING)
        verify(exactly = 1) { windowManager.addView(view, any<WindowManager.LayoutParams>()) }
        verify(exactly = 1) { windowManager.updateViewLayout(view, any<WindowManager.LayoutParams>()) }
    }

    @Test
    fun `show is idempotent — second call from SHOWING is a no-op`() {
        controller.show()
        controller.show()
        verify(exactly = 1) { windowManager.addView(any(), any()) }
        verify(exactly = 0) { windowManager.updateViewLayout(any(), any()) }
    }

    @Test
    fun `dismiss removes the view and falls back to OFF`() {
        controller.show()
        controller.dismiss()
        assertThat(controller.mode).isEqualTo(SoftLockOverlayController.Mode.OFF)
        verify(exactly = 1) { windowManager.removeView(view) }
    }

    @Test
    fun `dismiss is a no-op when nothing is attached`() {
        controller.dismiss()
        assertThat(controller.mode).isEqualTo(SoftLockOverlayController.Mode.OFF)
        verify(exactly = 0) { windowManager.removeView(any()) }
    }

    @Test
    fun `addView throwing keeps mode at OFF and discards the host`() {
        every { windowManager.addView(any(), any()) } throws RuntimeException("permission revoked")

        controller.arm()

        assertThat(controller.mode).isEqualTo(SoftLockOverlayController.Mode.OFF)
        // a subsequent show must therefore start a fresh attach attempt (which also throws),
        // never an updateViewLayout.
        controller.show()
        verify(exactly = 0) { windowManager.updateViewLayout(any(), any()) }
    }

    @Test
    fun `removeView throwing still leaves the controller in OFF`() {
        controller.show()
        every { windowManager.removeView(any()) } throws RuntimeException("already removed")

        controller.dismiss()

        assertThat(controller.mode).isEqualTo(SoftLockOverlayController.Mode.OFF)
    }

    @Test
    fun `re-arm after dismiss creates a brand-new host through the factory`() {
        controller.arm()
        controller.dismiss()
        controller.arm()
        assertThat(controller.mode).isEqualTo(SoftLockOverlayController.Mode.ARMED)
        verify(exactly = 2) { windowManager.addView(view, any<WindowManager.LayoutParams>()) }
    }
}

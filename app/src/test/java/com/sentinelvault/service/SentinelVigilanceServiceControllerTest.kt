package com.sentinelvault.service

import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.SentinelRuntime
import com.sentinelvault.vigilance.camera.HeadlessCameraSession
import com.sentinelvault.vigilance.orientation.OrientationTracker
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

/**
 * JVM test for [SentinelVigilanceServiceController] (Task 9.1 & 9.4). Verifies the
 * start/stop ordering contract that the guide §9.1 calls out explicitly:
 *  * onCreate: bind camera FIRST, then start runtime, then begin heartbeat.
 *  * onDestroy: cancel heartbeat, stop runtime, release camera, clear heartbeat marker.
 *  * onTaskRemoved: record one heartbeat — service does NOT stop.
 *  * onCreate / onDestroy are idempotent.
 */
class SentinelVigilanceServiceControllerTest {

    private val runtime: SentinelRuntime = mockk(relaxed = true)
    private val camera: HeadlessCameraSession = mockk(relaxed = true)
    private val heartbeat: VigilanceHeartbeat = mockk(relaxed = true)
    private val orientationTracker: OrientationTracker = mockk(relaxed = true)
    private val owner: LifecycleOwner = mockk(relaxed = true)

    private fun newController() =
        SentinelVigilanceServiceController(runtime, camera, heartbeat, orientationTracker)

    @Test
    fun `onCreate binds camera before starting runtime`() {
        val controller = newController()

        controller.onCreate(owner)

        verifyOrder {
            orientationTracker.start()
            camera.bind(owner)
            runtime.start()
        }
        assertThat(controller.started).isTrue()
    }

    @Test
    fun `onCreate is idempotent`() {
        val controller = newController()

        controller.onCreate(owner)
        controller.onCreate(owner)

        verify(exactly = 1) { orientationTracker.start() }
        verify(exactly = 1) { camera.bind(any()) }
        verify(exactly = 1) { runtime.start() }
    }

    @Test
    fun `onDestroy stops runtime before releasing camera and clears heartbeat`() {
        val controller = newController()
        controller.onCreate(owner)

        controller.onDestroy()

        verifyOrder {
            runtime.stop()
            camera.release()
            orientationTracker.stop()
            heartbeat.clear()
        }
        assertThat(controller.started).isFalse()
    }

    @Test
    fun `onDestroy without onCreate is a no-op`() {
        val controller = newController()

        controller.onDestroy()

        verify(exactly = 0) { runtime.stop() }
        verify(exactly = 0) { camera.release() }
        verify(exactly = 0) { orientationTracker.stop() }
        verify(exactly = 0) { heartbeat.clear() }
    }

    @Test
    fun `onTaskRemoved beats the heartbeat without stopping the service`() {
        val controller = newController()
        controller.onCreate(owner)

        controller.onTaskRemoved()

        // `beat()` has a default-arg `nowMs` so the recorded call carries an actual timestamp;
        // we match `any()` instead of `beat()` to keep the assertion deterministic across
        // wall-clock differences between the call and the verify.
        verify(atLeast = 1) { heartbeat.beat(any()) }
        verify(exactly = 0) { runtime.stop() }
        verify(exactly = 0) { camera.release() }
    }
}

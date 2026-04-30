package com.sentinelvault.service

import androidx.lifecycle.LifecycleOwner
import com.sentinelvault.SentinelRuntime
import com.sentinelvault.vigilance.camera.HeadlessCameraSession
import com.sentinelvault.vigilance.orientation.OrientationTracker
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Side-effect-only controller that owns the real start/stop sequence of [SentinelVigilanceService].
 * Lifted out of the service class itself so the unit tests in `:app:testDebugUnitTest` can drive
 * it with mocked dependencies — the service becomes a thin Android shim.
 *
 * Order matters in [onCreate]: bind the camera session FIRST (so the verification engine has
 * frames available the moment the runtime asks), then start the runtime. [onDestroy] reverses
 * the order: stop the runtime, then release the camera, then cancel the heartbeat scope.
 */
@Singleton
class SentinelVigilanceServiceController @Inject constructor(
    private val runtime: SentinelRuntime,
    private val cameraSession: HeadlessCameraSession,
    private val heartbeat: VigilanceHeartbeat,
    private val orientationTracker: OrientationTracker
) {

    private val supervisor: Job = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + supervisor)
    private var heartbeatJob: Job? = null

    @Volatile var started: Boolean = false
        private set

    fun onCreate(lifecycleOwner: LifecycleOwner) {
        if (started) return
        orientationTracker.start()
        cameraSession.bind(lifecycleOwner)
        runtime.start()
        startHeartbeat()
        started = true
    }

    fun onDestroy() {
        if (!started) return
        heartbeatJob?.cancel(); heartbeatJob = null
        runtime.stop()
        cameraSession.release()
        orientationTracker.stop()
        heartbeat.clear()
        started = false
    }

    /** Logs the Recents-swipe event without tearing the service down (Task 9.1). */
    fun onTaskRemoved() {
        // Intentionally a no-op beyond the heartbeat: the service must outlive the task.
        heartbeat.beat()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                heartbeat.beat()
                delay(VigilanceHeartbeat.HEARTBEAT_INTERVAL_MS)
            }
        }
    }
}

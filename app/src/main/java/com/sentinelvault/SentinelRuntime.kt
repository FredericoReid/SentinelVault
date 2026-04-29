package com.sentinelvault

import com.sentinelvault.lockdown.LockdownCoordinator
import com.sentinelvault.vigilance.VigilanceStateMachine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Process-scoped supervisor that brings the Epic 5 vigilance loop and the Epic 6 lockdown
 * coordinator online when [SentinelApp.onCreate] runs. Both consumers are long-lived
 * [kotlinx.coroutines.flow.Flow] collectors; tearing them down is only useful in tests, where
 * [stop] cancels the supervisor scope cleanly.
 *
 * The runtime is intentionally NOT marked `@HiltAndroidApp`-aware — it relies on the
 * `SentinelApp` entry point to receive its dependencies and start it. This keeps unit tests
 * able to drive the state machine directly without spinning up the application instance.
 */
@Singleton
class SentinelRuntime @Inject constructor(
    private val machine: VigilanceStateMachine,
    private val coordinator: LockdownCoordinator
) {

    private val supervisor: Job = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + supervisor)

    @Volatile private var started: Boolean = false

    /** Idempotent. Subsequent calls are ignored. */
    fun start() {
        if (started) return
        started = true
        scope.launch { machine.run(this) }
        scope.launch { coordinator.observe() }
    }

    /** Tears down both collectors. Primarily useful in instrumentation tests. */
    fun stop() {
        if (!started) return
        scope.cancel()
        started = false
    }
}

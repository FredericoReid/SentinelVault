package com.sentinelvault.vigilance

import com.sentinelvault.triggers.ContextTokenManager
import com.sentinelvault.triggers.TriggerClock
import com.sentinelvault.triggers.TriggerEvent
import com.sentinelvault.triggers.TriggerOrchestrator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Epic 5 brain. Subscribes to [TriggerOrchestrator.events], decides when to spend a
 * verification pulse, and walks the [VigilanceState] graph defined in guide.md §1.6.
 *
 * Concurrency model:
 *  * Triggers are serialised through [stateMutex]; verification (which suspends) runs
 *    *outside* the mutex so a slow inference cannot stall the trigger collector.
 *  * The pulsed-sampling loop runs in a single child coroutine ([pulseJob]); transitioning
 *    to [VigilanceState.Idle] or [VigilanceState.BreachConfirmed] cancels it deterministically.
 *  * The terminal [VigilanceState.BreachConfirmed] is sticky until [reset] is called by the
 *    Epic 6 lockdown handler after the OS keyguard has been re-armed.
 */
@Singleton
class VigilanceStateMachine @Inject constructor(
    private val orchestrator: TriggerOrchestrator,
    private val contextTokenManager: ContextTokenManager,
    private val engine: VerificationEngine,
    private val scheduler: PulseScheduler,
    private val config: VigilanceConfig,
    private val clock: TriggerClock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val _state: MutableStateFlow<VigilanceState> = MutableStateFlow(VigilanceState.Idle())
    val state: StateFlow<VigilanceState> = _state.asStateFlow()

    private val _verdicts: MutableSharedFlow<VerificationOutcome> =
        MutableSharedFlow(extraBufferCapacity = 16)
    val verdicts: SharedFlow<VerificationOutcome> = _verdicts.asSharedFlow()

    private val stateMutex = Mutex()
    private var pulseJob: Job? = null

    /** Hot-attach the state machine to [scope]. Cancel [scope] to tear it down. */
    suspend fun run(scope: CoroutineScope): Unit = coroutineScope {
        orchestrator.events.collect { handleTrigger(it, scope) }
    }

    /** Reset to [VigilanceState.Idle]. Called by Epic 6 after a confirmed lockdown. */
    suspend fun reset() = stateMutex.withLock {
        pulseJob?.cancel(); pulseJob = null
        _state.value = VigilanceState.Idle(clock.nowMs())
    }

    private suspend fun handleTrigger(event: TriggerEvent, scope: CoroutineScope) {
        val plan = stateMutex.withLock { decide(event) } ?: return
        when (plan) {
            is Plan.VerifyNow -> runSinglePulse(plan.reason, scope)
            is Plan.EnterAlert -> startAlertLoop(plan.atMs, scope)
        }
    }

    private fun decide(event: TriggerEvent): Plan? {
        val current = _state.value
        if (current is VigilanceState.BreachConfirmed) return null
        if (current !is VigilanceState.Idle) return null
        return when (event) {
            is TriggerEvent.UserPresent -> Plan.VerifyNow(VerifyReason.UserPresent)
            is TriggerEvent.SensitiveAppOpened -> {
                if (contextTokenManager.isCovered(event.packageName)) null
                else Plan.VerifyNow(VerifyReason.SensitiveAppOpened)
            }
            is TriggerEvent.ContextBreach -> Plan.VerifyNow(VerifyReason.ContextBreach)
            is TriggerEvent.SnatchDetected -> Plan.EnterAlert(event.timestampMs)
            is TriggerEvent.ForegroundAppChanged -> null
        }
    }

    private suspend fun runSinglePulse(reason: VerifyReason, scope: CoroutineScope) {
        stateMutex.withLock { _state.value = VigilanceState.VerifyOnce(reason, clock.nowMs()) }
        val outcome = engine.verifyOnce(clock.nowMs())
        _verdicts.emit(outcome)
        stateMutex.withLock { applyOutcome(outcome, scope) }
    }

    private fun startAlertLoop(atMs: Long, scope: CoroutineScope) {
        _state.value = VigilanceState.AlertLevel1(sinceMs = atMs, mismatchStreak = 0)
        ensurePulseJob(scope)
    }

    private fun ensurePulseJob(scope: CoroutineScope) {
        if (pulseJob?.isActive == true) return
        val alertStart = _state.value.sinceMs
        pulseJob = scope.launch(dispatcher) {
            scheduler.ticks(config.pulseIntervalMs).collect {
                val now = clock.nowMs()
                if (now - alertStart > config.alertWindowMs) {
                    stateMutex.withLock {
                        if (_state.value !is VigilanceState.BreachConfirmed) {
                            _state.value = VigilanceState.Idle(now)
                        }
                    }
                    this.cancel(); return@collect
                }
                val outcome = engine.verifyOnce(now)
                _verdicts.emit(outcome)
                stateMutex.withLock { applyOutcome(outcome, scope) }
            }
        }
    }

    private fun applyOutcome(outcome: VerificationOutcome, scope: CoroutineScope) {
        when (outcome) {
            is VerificationOutcome.Match -> {
                pulseJob?.cancel(); pulseJob = null
                _state.value = VigilanceState.Idle(outcome.timestampMs)
            }
            is VerificationOutcome.Mismatch, is VerificationOutcome.NotLive -> {
                val streak = _state.value.mismatchStreak + 1
                val ts = outcome.timestampMs
                _state.value = when {
                    streak >= config.mismatchesToConfirmBreach -> {
                        pulseJob?.cancel(); pulseJob = null
                        VigilanceState.BreachConfirmed(ts, streak)
                    }
                    streak >= config.mismatchesToEscalate -> VigilanceState.AlertLevel2(ts, streak)
                    else -> VigilanceState.AlertLevel1(ts, streak)
                }
                if (_state.value !is VigilanceState.BreachConfirmed) ensurePulseJob(scope)
            }
            else -> {
                // NoFace / OwnerNotEnrolled / EmbedderUnavailable / Failure are benign;
                // collapse a one-shot VerifyOnce back to Idle but never break an alert loop.
                if (_state.value is VigilanceState.VerifyOnce) {
                    _state.value = VigilanceState.Idle(outcome.timestampMs)
                }
            }
        }
    }

    private sealed interface Plan {
        data class VerifyNow(val reason: VerifyReason) : Plan
        data class EnterAlert(val atMs: Long) : Plan
    }
}

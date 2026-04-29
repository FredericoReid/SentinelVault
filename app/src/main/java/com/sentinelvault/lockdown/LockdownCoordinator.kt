package com.sentinelvault.lockdown

import com.sentinelvault.vigilance.VigilanceState
import com.sentinelvault.vigilance.VigilanceStateMachine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.collect

/**
 * Glue between the Epic 5 [VigilanceStateMachine] and the Epic 6 defensive surface:
 *
 *  * [VigilanceState.AlertLevel2] → [SoftLockOverlayController.arm] (latency budget, §3.8).
 *  * [VigilanceState.BreachConfirmed] → [SoftLockOverlayController.show] then
 *    [LockdownAction.trigger] (vault write + `lockNow`).
 *  * Transition into [VigilanceState.Idle] from a previously armed-but-not-confirmed state
 *    dismisses the overlay; the Idle that follows a confirmed breach is owner-initiated and
 *    is acknowledged through [acknowledgeOwnerReturn] instead.
 *
 * The coordinator does NOT call `machine.reset()` itself after a confirmed breach. The reset
 * is the gatekeeper-unlock side-effect (see [acknowledgeOwnerReturn]) so the overlay stays
 * up until the legitimate owner is back, exactly as guide.md §1.6 requires.
 */
@Singleton
class LockdownCoordinator @Inject constructor(
    private val machine: VigilanceStateMachine,
    private val overlay: SoftLockOverlayController,
    private val action: LockdownAction,
    private val selfHealing: SelfHealingController
) {

    /** Tracks the last breach so [acknowledgeOwnerReturn] can pass the right id to self-heal. */
    @Volatile private var lastBreachId: Long? = null

    /** Hot-attach to the state flow; cancel the surrounding scope to stop. */
    suspend fun observe() {
        machine.state.collect { state ->
            when (state) {
                is VigilanceState.AlertLevel2 -> overlay.arm()
                is VigilanceState.BreachConfirmed -> handleBreach(state)
                is VigilanceState.Idle -> {
                    if (overlay.mode == SoftLockOverlayController.Mode.ARMED) overlay.dismiss()
                }
                is VigilanceState.AlertLevel1, is VigilanceState.VerifyOnce -> Unit
            }
        }
    }

    /**
     * Called by the gatekeeper PIN screen after the legitimate owner re-authenticates.
     * Dismisses the overlay, resets the state machine to `Idle`, and — when a breach was
     * actually latched — feeds the [SelfHealingController] so the next few pulses run with
     * a relaxed cosine threshold (the owner just proved the previous lockdown was a false
     * reject by typing the right PIN).
     *
     * @return `true` when the call resolved a latched breach (i.e. a false reject was
     *         recorded), `false` when there was nothing to dismiss.
     */
    suspend fun acknowledgeOwnerReturn(): Boolean {
        val breachId = lastBreachId
        overlay.dismiss()
        machine.reset()
        return if (breachId != null) {
            selfHealing.markFalseReject(breachId)
            lastBreachId = null
            true
        } else {
            false
        }
    }

    private suspend fun handleBreach(state: VigilanceState.BreachConfirmed) {
        if (overlay.mode != SoftLockOverlayController.Mode.SHOWING) overlay.show()
        if (lastBreachId != null) return // idempotency guard against re-emission
        val result = action.trigger(
            BreachReason(
                timestampMs = state.sinceMs,
                mismatchStreak = state.mismatchStreak,
                foregroundPackage = null
            )
        )
        lastBreachId = when (result) {
            is LockdownAction.Result.HardLocked -> result.breachId
            is LockdownAction.Result.SoftLockedOnly -> result.breachId
        }
    }
}

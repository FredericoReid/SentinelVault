package com.sentinelvault.lockdown

/**
 * Defensive action invoked by the Epic 6 [LockdownCoordinator] the moment the vigilance
 * state machine reaches `BreachConfirmed`. Implementations MUST be idempotent under
 * back-to-back invocations — the state-machine flow can re-emit on collector re-attach.
 *
 * Concrete contract for the [DefaultLockdownAction]:
 *  1. Persist a `BREACH_CONFIRMED` row in the encrypted vault (`EventLogDao.insertBreach`).
 *  2. Invoke [DevicePolicyController.lockNow]. If the admin is not active the call is a
 *     no-op and a `LOCKDOWN_TRIGGERED` row carries `severity = 0` so the dashboard can
 *     surface the misconfiguration.
 *  3. Persist a follow-up `LOCKDOWN_TRIGGERED` row whose `notes` field captures whether the
 *     hardware lock actually fired (`true`) or was skipped (`false`).
 *
 * The action does NOT reset the state machine — that responsibility belongs to whoever
 * acknowledges the owner's return (typically the gatekeeper unlock callback).
 */
interface LockdownAction {
    suspend fun trigger(reason: BreachReason): Result

    sealed interface Result {
        /** Hardware lock fired and the breach was persisted. */
        data class HardLocked(val breachId: Long, val lockEventId: Long) : Result

        /** Breach was persisted but `DevicePolicyManager.lockNow()` failed or admin inactive. */
        data class SoftLockedOnly(val breachId: Long, val lockEventId: Long) : Result
    }
}

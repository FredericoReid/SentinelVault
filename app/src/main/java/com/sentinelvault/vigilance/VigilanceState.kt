package com.sentinelvault.vigilance

/**
 * Operating modes of the vigilance state machine (guide.md §1.6). Only the state machine is
 * allowed to mutate the [com.sentinelvault.vigilance.VigilanceStateMachine.state] flow; every
 * other component is a read-only observer.
 *
 *  * [Idle] – baseline. Camera completely off, only the trigger bus is being consumed.
 *  * [VerifyOnce] – a single silent frame is in flight (e.g. fired by `UserPresent` or by a
 *    sensitive-app launch outside an active token). Collapses back to [Idle] on Match,
 *    promotes to [AlertLevel1] on Mismatch / NotLive.
 *  * [AlertLevel1] – pulsed sampling at [VigilanceConfig.pulseIntervalMs]. One owner Match
 *    inside the alert window collapses back to [Idle].
 *  * [AlertLevel2] – pre-armed lockdown overlay; latency to lock ≤ 200 ms. One more
 *    consecutive Mismatch promotes to [BreachConfirmed].
 *  * [BreachConfirmed] – terminal until the Epic 6 lockdown action acks completion; Epic 6
 *    is responsible for invoking `DevicePolicyManager.lockNow` and persisting the vault row.
 */
sealed interface VigilanceState {
    val sinceMs: Long
    val mismatchStreak: Int

    data class Idle(override val sinceMs: Long = 0L) : VigilanceState {
        override val mismatchStreak: Int = 0
    }

    data class VerifyOnce(
        val reason: VerifyReason,
        override val sinceMs: Long
    ) : VigilanceState {
        override val mismatchStreak: Int = 0
    }

    data class AlertLevel1(
        override val sinceMs: Long,
        override val mismatchStreak: Int
    ) : VigilanceState

    data class AlertLevel2(
        override val sinceMs: Long,
        override val mismatchStreak: Int
    ) : VigilanceState

    data class BreachConfirmed(
        override val sinceMs: Long,
        override val mismatchStreak: Int
    ) : VigilanceState
}

/**
 * Why the state machine left [VigilanceState.Idle]. Carried through [VigilanceState.VerifyOnce]
 * so the dashboard timeline (Epic 7) can attribute pulses to the originating signal.
 */
enum class VerifyReason {
    UserPresent,
    SensitiveAppOpened,
    ContextBreach,
    SnatchDetected,
    ForegroundAppChanged
}

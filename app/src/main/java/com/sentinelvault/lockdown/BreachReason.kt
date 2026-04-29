package com.sentinelvault.lockdown

/**
 * Carrier struct that travels from the [com.sentinelvault.vigilance.VigilanceStateMachine]'s
 * `BreachConfirmed` state into [LockdownAction.trigger]. Decouples the lockdown handler from
 * the state-machine internals so Epic 7 can later enrich it with a hero-frame path without
 * widening the state-machine surface.
 *
 * @property timestampMs wall-clock millis at which the third consecutive mismatch landed
 *           (sourced from the same [com.sentinelvault.triggers.TriggerClock] the rest of the
 *           pipeline uses, NOT `System.currentTimeMillis()`).
 * @property mismatchStreak the consecutive non-owner verdicts that confirmed the breach;
 *           always ≥ [com.sentinelvault.vigilance.VigilanceConfig.mismatchesToConfirmBreach].
 * @property foregroundPackage the app the intruder had foregrounded when the breach landed,
 *           if known. `null` is treated as "lockscreen / launcher / unknown" by the dashboard.
 */
data class BreachReason(
    val timestampMs: Long,
    val mismatchStreak: Int,
    val foregroundPackage: String?
)

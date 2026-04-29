package com.sentinelvault.vigilance

/**
 * Tunable thresholds for the Epic 5 state machine. Centralised so the operator-facing
 * "Vault Settings" surface (future) can override them without ripping through the codebase,
 * and so unit tests can shrink the timeline without `Thread.sleep`.
 *
 * @property matchThreshold cosine similarity at or above which a frame is treated as the
 *           owner. MobileFaceNet INT8 embeddings keep a same-person cosine ≥ 0.55 in our
 *           internal corpus; we leave headroom and pin the production threshold at 0.62.
 * @property pulseIntervalMs spacing between two pulses inside an alert window
 *           ("Pulsed Sampling at 3 s" — guide.md §1.4).
 * @property alertWindowMs maximum lifetime of an alert before it auto-collapses back to
 *           IDLE without a confirmed breach (the "3-Minute Protocol", §1.4).
 * @property mismatchesToEscalate consecutive non-owner verdicts inside ALERT_LEVEL_1 that
 *           promote the state machine to ALERT_LEVEL_2 (overlay pre-armed, §1.6).
 * @property mismatchesToConfirmBreach consecutive non-owner verdicts that fire
 *           BREACH_CONFIRMED and trigger the Epic 6 hardware-lockdown action.
 */
data class VigilanceConfig(
    val matchThreshold: Float = 0.62f,
    val pulseIntervalMs: Long = 3_000L,
    val alertWindowMs: Long = 180_000L,
    val mismatchesToEscalate: Int = 2,
    val mismatchesToConfirmBreach: Int = 3
) {
    init {
        require(matchThreshold in -1f..1f) { "matchThreshold out of cosine range" }
        require(pulseIntervalMs > 0L) { "pulseIntervalMs must be positive" }
        require(alertWindowMs >= pulseIntervalMs) { "alertWindowMs must cover at least one pulse" }
        require(mismatchesToEscalate in 1..mismatchesToConfirmBreach) {
            "mismatchesToEscalate must be in [1, mismatchesToConfirmBreach]"
        }
    }
}

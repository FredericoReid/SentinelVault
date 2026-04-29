package com.sentinelvault.triggers

/**
 * Discrete signal emitted by every Epic 4 trigger source. The downstream state machine
 * (Epic 5) consumes a [kotlinx.coroutines.flow.Flow] of these to decide when to spend a
 * camera frame on verification.
 *
 *  * [UserPresent] – screen has just been unlocked (`ACTION_USER_PRESENT`).
 *  * [ForegroundAppChanged] – top activity changed (UsageStats / AccessibilityService).
 *  * [SensitiveAppOpened] – top activity matched the [SensitiveAppRegistry].
 *  * [SnatchDetected] – accelerometer crossed the snatch threshold ([SnatchHeuristic]).
 *  * [ContextBreach] – foreground app diverged from the [ContextToken] holder.
 *
 *  All events are immutable and carry a wall-clock timestamp so the orchestrator can
 *  correlate them across sources without touching `System.currentTimeMillis()` itself.
 */
sealed interface TriggerEvent {
    val timestampMs: Long

    data class UserPresent(override val timestampMs: Long) : TriggerEvent

    data class ForegroundAppChanged(
        val previousPackage: String?,
        val currentPackage: String,
        override val timestampMs: Long
    ) : TriggerEvent

    data class SensitiveAppOpened(
        val packageName: String,
        override val timestampMs: Long
    ) : TriggerEvent

    data class SnatchDetected(
        val peakMagnitudeMs2: Float,
        val jerkMs3: Float,
        override val timestampMs: Long
    ) : TriggerEvent

    data class ContextBreach(
        val tokenPackage: String,
        val attemptedPackage: String,
        override val timestampMs: Long
    ) : TriggerEvent
}

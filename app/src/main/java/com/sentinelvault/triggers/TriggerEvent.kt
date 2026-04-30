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
 *  * [DeskLiftDetected] – device was picked up from a stable desk pose.
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

    /**
     * The phone was resting flat on a table (display up or display down) and was then picked up.
     * This is stronger than a mere tilt because the detector requires both a stable desk pose and
     * a pickup impulse before the flatness breaks.
     */
    data class DeskLiftDetected(
        val fromFaceDown: Boolean,
        val pickupAccelerationMs2: Float,
        override val timestampMs: Long
    ) : TriggerEvent

    data class ContextBreach(
        val tokenPackage: String,
        val attemptedPackage: String,
        override val timestampMs: Long
    ) : TriggerEvent

    /**
     * The phone has been raised from a flat / pocket pose into an upright (portrait-held)
     * pose. Emitted by [UprightTriggerDetector] after a debounce + cooldown so the trigger
     * does not fan out a verification pulse on every micro-tilt.
     */
    data class DeviceUpright(
        val pitchDegrees: Float,
        override val timestampMs: Long
    ) : TriggerEvent
}

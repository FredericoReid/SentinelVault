package com.sentinelvault.triggers

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure, allocation-free snatch detector. Treats accelerometer samples as a stream and fires
 * when both:
 *  1. **Peak magnitude** of the linear acceleration crosses [magnitudeThresholdMs2]
 *     (default ≈ 25 m/s² ≈ 2.5 g — covers a yank-from-hand gesture without flagging
 *     pocketing the phone, which usually peaks below 18 m/s²).
 *  2. **Jerk** (Δmagnitude / Δt) crosses [jerkThresholdMs3] within [windowMs] of the peak.
 *     Static gravity drift can produce sustained 9.81 m/s² readings; gating on jerk filters
 *     them out and isolates the impulsive motion that defines a snatch.
 *
 * The class keeps two scalar variables ([previousMagnitude], [previousTimestampNs]) so it
 * can be hot-pathed from `SensorEventListener.onSensorChanged` at 50 Hz without GC pressure.
 *
 * After [refractoryMs] following a positive detection, the state machine is reset so the
 * device can re-trigger on the next, distinct snatch (avoids machine-gun events while the
 * phone tumbles in mid-air).
 */
@Singleton
class SnatchHeuristic @Inject constructor(
    val magnitudeThresholdMs2: Float = DEFAULT_MAGNITUDE_THRESHOLD,
    val jerkThresholdMs3: Float = DEFAULT_JERK_THRESHOLD,
    val refractoryMs: Long = DEFAULT_REFRACTORY_MS,
    val windowMs: Long = DEFAULT_WINDOW_MS
) {

    private var previousMagnitude: Float = Float.NaN
    private var previousTimestampNs: Long = 0L
    private var lastFireAtMs: Long = Long.MIN_VALUE

    /**
     * @param x linear or raw accelerometer X axis (m/s²)
     * @param y linear or raw accelerometer Y axis (m/s²)
     * @param z linear or raw accelerometer Z axis (m/s²)
     * @param timestampNs `SensorEvent.timestamp` (nanoseconds, monotonic)
     * @param wallClockMs wall clock used for the refractory window (mockable in tests)
     * @return a [Detection] when both thresholds are crossed, or `null` otherwise.
     */
    fun feed(x: Float, y: Float, z: Float, timestampNs: Long, wallClockMs: Long): Detection? {
        val magnitude = sqrt(x * x + y * y + z * z)
        val jerk = if (previousMagnitude.isNaN() || previousTimestampNs == 0L) {
            0f
        } else {
            val dtSec = (timestampNs - previousTimestampNs).coerceAtLeast(1L) / 1_000_000_000f
            val dtClamped = if (dtSec * 1_000f > windowMs) windowMs / 1_000f else dtSec
            abs(magnitude - previousMagnitude) / dtClamped
        }
        previousMagnitude = magnitude
        previousTimestampNs = timestampNs

        if (magnitude < magnitudeThresholdMs2) return null
        if (jerk < jerkThresholdMs3) return null
        if (lastFireAtMs != Long.MIN_VALUE && wallClockMs - lastFireAtMs < refractoryMs) return null
        lastFireAtMs = wallClockMs
        return Detection(peakMagnitudeMs2 = magnitude, jerkMs3 = jerk)
    }

    /** Wipe the rolling state — call when the screen turns off so we don't carry stale jerk. */
    fun reset() {
        previousMagnitude = Float.NaN
        previousTimestampNs = 0L
        lastFireAtMs = Long.MIN_VALUE
    }

    data class Detection(val peakMagnitudeMs2: Float, val jerkMs3: Float)

    companion object {
        const val DEFAULT_MAGNITUDE_THRESHOLD: Float = 25f
        const val DEFAULT_JERK_THRESHOLD: Float = 80f
        const val DEFAULT_REFRACTORY_MS: Long = 1_500L
        const val DEFAULT_WINDOW_MS: Long = 250L
    }
}

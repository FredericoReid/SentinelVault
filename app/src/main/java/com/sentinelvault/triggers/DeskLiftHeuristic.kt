package com.sentinelvault.triggers

import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects the "lying on a table → suddenly picked up" gesture.
 *
 * Sensor model:
 *  * gravity/accelerometer provides the stable "flat on a desk" pose via `|z| / |g|`
 *  * linear acceleration provides the pickup impulse so a slow slide or tiny desk vibration
 *    does not arm a facial verification burst.
 *
 * The gesture fires only after the phone remained flat long enough to be considered at rest,
 * then receives a pickup impulse, and finally leaves the flat pose within a short window.
 * Both face-up and face-down desk poses are accepted.
 */
@Singleton
class DeskLiftHeuristic(
    private val settleMs: Long = DEFAULT_SETTLE_MS,
    private val pickupWindowMs: Long = DEFAULT_PICKUP_WINDOW_MS,
    private val refractoryMs: Long = DEFAULT_REFRACTORY_MS,
    private val flatThreshold: Float = DEFAULT_FLAT_THRESHOLD,
    private val releasedThreshold: Float = DEFAULT_RELEASED_THRESHOLD,
    private val pickupAccelerationThresholdMs2: Float = DEFAULT_PICKUP_ACCELERATION_THRESHOLD
) {

    private var flatSinceMs: Long = NO_TIMESTAMP
    private var armedFaceDown: Boolean? = null
    private var pickupAtMs: Long = NO_TIMESTAMP
    private var pickupAccelerationMs2: Float = 0f
    private var lastFireAtMs: Long = NO_TIMESTAMP

    fun feedGravity(x: Float, y: Float, z: Float, wallClockMs: Long): Detection? {
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude < MIN_GRAVITY_MAGNITUDE) return null

        val flatness = abs(z) / magnitude
        if (pickupAtMs != NO_TIMESTAMP && wallClockMs - pickupAtMs > pickupWindowMs) {
            pickupAtMs = NO_TIMESTAMP
            pickupAccelerationMs2 = 0f
        }

        if (flatness >= flatThreshold) {
            if (flatSinceMs == NO_TIMESTAMP) flatSinceMs = wallClockMs
            if (armedFaceDown == null && wallClockMs - flatSinceMs >= settleMs) {
                armedFaceDown = z < 0f
            }
            return null
        }

        if (flatness <= releasedThreshold) {
            val faceDown = armedFaceDown
            val pickupAt = pickupAtMs
            if (
                faceDown != null &&
                pickupAt != NO_TIMESTAMP &&
                wallClockMs - pickupAt <= pickupWindowMs &&
                (lastFireAtMs == NO_TIMESTAMP || wallClockMs - lastFireAtMs >= refractoryMs)
            ) {
                lastFireAtMs = wallClockMs
                val detection = Detection(
                    faceDown = faceDown,
                    pickupAccelerationMs2 = pickupAccelerationMs2,
                    flatness = flatness
                )
                resetSession()
                return detection
            }
            resetSession()
        }
        return null
    }

    fun feedLinearAcceleration(x: Float, y: Float, z: Float, wallClockMs: Long): Detection? {
        if (armedFaceDown == null) return null
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude < pickupAccelerationThresholdMs2) return null
        pickupAtMs = wallClockMs
        pickupAccelerationMs2 = maxOf(pickupAccelerationMs2, magnitude)
        return null
    }

    fun reset() {
        resetSession()
        lastFireAtMs = NO_TIMESTAMP
    }

    private fun resetSession() {
        flatSinceMs = NO_TIMESTAMP
        armedFaceDown = null
        pickupAtMs = NO_TIMESTAMP
        pickupAccelerationMs2 = 0f
    }

    data class Detection(
        val faceDown: Boolean,
        val pickupAccelerationMs2: Float,
        val flatness: Float
    )

    companion object {
        const val DEFAULT_SETTLE_MS: Long = 1_200L
        const val DEFAULT_PICKUP_WINDOW_MS: Long = 1_600L
        const val DEFAULT_REFRACTORY_MS: Long = 6_000L
        const val DEFAULT_FLAT_THRESHOLD: Float = 0.92f
        const val DEFAULT_RELEASED_THRESHOLD: Float = 0.72f
        const val DEFAULT_PICKUP_ACCELERATION_THRESHOLD: Float = 1.8f
        private const val NO_TIMESTAMP: Long = Long.MIN_VALUE
        private const val MIN_GRAVITY_MAGNITUDE: Float = 4f
    }
}
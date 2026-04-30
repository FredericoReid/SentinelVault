package com.sentinelvault.triggers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Emits [TriggerEvent.DeviceUpright] when the user lifts the phone from a flat / pocket
 * pose into a portrait-held pose. Wired to `Sensor.TYPE_GRAVITY` (on devices that expose
 * it) at `SENSOR_DELAY_NORMAL` (~5 Hz) — the cheapest sample rate that still captures the
 * raise gesture and keeps the radio + sensor hub idle for the rest of the time.
 *
 * Battery guards (in priority order):
 *  * **Coarse sample rate.** SENSOR_DELAY_NORMAL maps to ~200 ms which is two orders of
 *    magnitude lighter than the SENSOR_DELAY_GAME used by [MotionTriggerDetector].
 *  * **Hold-time debounce.** The pose must persist for [holdMs] before the event fires —
 *    a quick wrist twist while the phone is on a desk is dropped.
 *  * **Cooldown.** After firing, no new event is emitted for [cooldownMs]. The vigilance
 *    state machine is the consumer; it already deduplicates verification pulses but the
 *    cooldown prevents the trigger bus from being spammed in the first place.
 *  * **Idempotent registration.** [start] / [stop] guard against double-registration so the
 *    SensorManager listener count stays at zero or one.
 */
@Singleton
class UprightTriggerDetector @Inject constructor(
    private val sensorManager: SensorManager,
    private val orchestrator: TriggerOrchestrator,
    private val clock: TriggerClock
) : SensorEventListener {

    // Tunables. Kept as mutable properties (instead of constructor params) so Hilt does
    // not try to find a binding for primitive defaults; tests can still override them via
    // the dedicated [forTesting] factory.
    private var holdMs: Long = DEFAULT_HOLD_MS
    private var cooldownMs: Long = DEFAULT_COOLDOWN_MS
    private var uprightPitchDegrees: Float = DEFAULT_UPRIGHT_PITCH_DEGREES
    private var flatPitchDegrees: Float = DEFAULT_FLAT_PITCH_DEGREES

    @Volatile private var registered: Boolean = false
    @Volatile private var lastFiredAtMs: Long = 0L
    @Volatile private var uprightSinceMs: Long = 0L
    @Volatile private var armed: Boolean = true

    @Synchronized
    fun start(): Boolean {
        if (registered) return true
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return false
        val ok = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        registered = ok
        return ok
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
        uprightSinceMs = 0L
        armed = true
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.size < 3) return
        val pitch = pitchDegrees(event.values[0], event.values[1], event.values[2])
        val now = clock.nowMs()

        if (pitch >= uprightPitchDegrees) {
            // Phone tilted upward (display facing the user). Start / continue the hold timer.
            if (uprightSinceMs == 0L) uprightSinceMs = now
            if (!armed) return
            if (now - uprightSinceMs < holdMs) return
            if (now - lastFiredAtMs < cooldownMs) return
            lastFiredAtMs = now
            armed = false
            orchestrator.emit(TriggerEvent.DeviceUpright(pitch, now))
        } else if (pitch <= flatPitchDegrees) {
            // Phone returned to a flat / pocket pose. Re-arm so the next raise fires again.
            uprightSinceMs = 0L
            armed = true
        }
        // Hysteresis band [flatPitchDegrees, uprightPitchDegrees) - keep the current state.
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* unused */ }

    /**
     * Pitch of the device, expressed as the angle between the screen normal and the
     * horizontal plane. 0° = lying flat on a table; 90° = held vertically in portrait.
     * Robust to gravity-vector magnitude noise and to which sign of `y` the OEM chose.
     */
    private fun pitchDegrees(x: Float, y: Float, z: Float): Float {
        val norm = sqrt(x * x + y * y + z * z)
        if (norm < MIN_GRAVITY_MAGNITUDE) return 0f
        // atan2(|y|, sqrt(x^2+z^2)) maps 0 -> flat, 90 -> portrait-held.
        val angleRad = atan2(abs(y), sqrt(x * x + z * z))
        return Math.toDegrees(angleRad.toDouble()).toFloat()
    }

    /** Test-only factory that exposes the tunables; production code uses the @Inject ctor. */
    @androidx.annotation.VisibleForTesting
    internal fun configureForTest(
        holdMs: Long = DEFAULT_HOLD_MS,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        uprightPitchDegrees: Float = DEFAULT_UPRIGHT_PITCH_DEGREES,
        flatPitchDegrees: Float = DEFAULT_FLAT_PITCH_DEGREES
    ) {
        this.holdMs = holdMs
        this.cooldownMs = cooldownMs
        this.uprightPitchDegrees = uprightPitchDegrees
        this.flatPitchDegrees = flatPitchDegrees
    }

    companion object {
        const val DEFAULT_HOLD_MS: Long = 700L
        const val DEFAULT_COOLDOWN_MS: Long = 30_000L
        const val DEFAULT_UPRIGHT_PITCH_DEGREES: Float = 60f
        const val DEFAULT_FLAT_PITCH_DEGREES: Float = 35f
        private const val MIN_GRAVITY_MAGNITUDE: Float = 4f
    }
}

package com.sentinelvault.triggers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the Android [SensorManager] callbacks to the pure [SnatchHeuristic] algorithm and
 * forwards positive detections to the [TriggerOrchestrator] as
 * [TriggerEvent.SnatchDetected].
 *
 * The class deliberately keeps [SensorEventListener] separate from the heuristic so the math
 * can be unit tested without instantiating any framework type. [start] / [stop] are safe to
 * call repeatedly; the underlying registration is idempotent.
 */
@Singleton
class MotionTriggerDetector @Inject constructor(
    private val sensorManager: SensorManager,
    private val heuristic: SnatchHeuristic,
    private val orchestrator: TriggerOrchestrator,
    private val clock: TriggerClock
) : SensorEventListener {

    @Volatile private var registered: Boolean = false

    /**
     * Subscribe to TYPE_LINEAR_ACCELERATION (gravity already removed). Falls back to
     * TYPE_ACCELEROMETER on devices that don't report linear acceleration. Sample rate is
     * `SENSOR_DELAY_GAME` (≈ 20 ms) — the cheapest rate that still resolves a 100 ms snatch.
     */
    @Synchronized
    fun start(): Boolean {
        if (registered) return true
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return false
        val ok = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        registered = ok
        return ok
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        heuristic.reset()
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.size < 3) return
        val now = clock.nowMs()
        val detection = heuristic.feed(
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
            timestampNs = event.timestamp,
            wallClockMs = now
        ) ?: return
        orchestrator.emit(
            TriggerEvent.SnatchDetected(
                peakMagnitudeMs2 = detection.peakMagnitudeMs2,
                jerkMs3 = detection.jerkMs3,
                timestampMs = now
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* unused */ }
}

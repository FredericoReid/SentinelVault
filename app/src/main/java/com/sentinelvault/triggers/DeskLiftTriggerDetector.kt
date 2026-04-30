package com.sentinelvault.triggers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sentinelvault.service.VigilanceSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sensor bridge for [DeskLiftHeuristic]. Emits [TriggerEvent.DeskLiftDetected] when the phone
 * is picked up from a stable desk pose, whether it was lying face-up or face-down.
 */
@Singleton
class DeskLiftTriggerDetector @Inject constructor(
    private val sensorManager: SensorManager,
    private val heuristic: DeskLiftHeuristic,
    private val orchestrator: TriggerOrchestrator,
    private val clock: TriggerClock,
    private val settings: VigilanceSettings
) : SensorEventListener {

    @Volatile private var registered: Boolean = false

    @Synchronized
    fun start(): Boolean {
        if (registered) return true
        val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return false
        val motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: return false

        val gravityOk = sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        val motionOk = sensorManager.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME)
        val ok = gravityOk && motionOk
        if (!ok) sensorManager.unregisterListener(this)
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
        if (!settings.isDeskLiftTriggerEnabled()) {
            heuristic.reset()
            return
        }
        val now = clock.nowMs()
        val detection = when (event.sensor.type) {
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_ACCELEROMETER -> heuristic.feedGravity(
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
                wallClockMs = now
            )

            Sensor.TYPE_LINEAR_ACCELERATION -> heuristic.feedLinearAcceleration(
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
                wallClockMs = now
            )

            else -> null
        } ?: return

        orchestrator.emit(
            TriggerEvent.DeskLiftDetected(
                fromFaceDown = detection.faceDown,
                pickupAccelerationMs2 = detection.pickupAccelerationMs2,
                timestampMs = now
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* unused */ }
}
package com.sentinelvault.triggers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Drives [SnatchHeuristic] with synthetic accelerometer arrays. Each sample is a
 * `(x, y, z, sensorTimestampNs, wallClockMs)` tuple — modelled on the real
 * `SensorEvent` payload but without instantiating the framework type.
 */
class SnatchHeuristicTest {

    private fun heuristic(
        magnitudeThreshold: Float = 25f,
        jerkThreshold: Float = 80f,
        refractoryMs: Long = 1_500L
    ) = SnatchHeuristic(magnitudeThreshold, jerkThreshold, refractoryMs)

    @Test
    fun `quiescent device under threshold never fires`() {
        val h = heuristic()
        // 50 samples around 1g (gravity-only, no motion), 20 ms apart.
        var ts = 0L
        repeat(50) {
            val detection = h.feed(0f, 0f, 9.81f, ts, ts / 1_000_000L)
            assertThat(detection).isNull()
            ts += 20_000_000L
        }
    }

    @Test
    fun `single high-magnitude impulse with high jerk fires once`() {
        val h = heuristic()
        // baseline ~1g for 5 samples
        var ts = 0L
        repeat(5) {
            assertThat(h.feed(0f, 0f, 9.81f, ts, ts / 1_000_000L)).isNull()
            ts += 20_000_000L
        }
        // sudden 30 m/s² spike on next sample (Δm ≈ 20 m/s² in 20 ms = 1000 m/s³ jerk)
        val detection = h.feed(0f, 30f, 0f, ts, ts / 1_000_000L)
        assertThat(detection).isNotNull()
        assertThat(detection!!.peakMagnitudeMs2).isAtLeast(25f)
        assertThat(detection.jerkMs3).isAtLeast(80f)
    }

    @Test
    fun `slow ramp above magnitude but below jerk does not fire`() {
        val h = heuristic()
        // 0 → 30 m/s² over 5 seconds = 6 m/s³ jerk, well under 80 m/s³ threshold.
        var magnitude = 0f
        var ts = 0L
        var fired = false
        repeat(250) {
            val detection = h.feed(magnitude, 0f, 0f, ts, ts / 1_000_000L)
            if (detection != null) fired = true
            magnitude += 30f / 250f
            ts += 20_000_000L
        }
        assertThat(fired).isFalse()
    }

    @Test
    fun `refractory window suppresses immediate retrigger`() {
        val h = heuristic(refractoryMs = 1_500L)
        // baseline
        var ts = 0L
        repeat(3) { h.feed(0f, 0f, 9.81f, ts, ts / 1_000_000L); ts += 20_000_000L }
        val first = h.feed(0f, 30f, 0f, ts, ts / 1_000_000L)
        assertThat(first).isNotNull()
        ts += 20_000_000L
        // identical second spike 20 ms later — must be swallowed by refractory window.
        val second = h.feed(0f, 30f, 0f, ts, ts / 1_000_000L)
        assertThat(second).isNull()
    }

    @Test
    fun `two distinct snatches separated beyond refractory each fire`() {
        val h = heuristic(refractoryMs = 100L)
        var ts = 0L
        repeat(3) { h.feed(0f, 0f, 9.81f, ts, ts / 1_000_000L); ts += 20_000_000L }
        assertThat(h.feed(0f, 30f, 0f, ts, ts / 1_000_000L)).isNotNull()
        // 200 ms later — well past refractory.
        ts += 200_000_000L
        // Reset baseline so jerk math isn't dampened by the residual previous magnitude.
        repeat(3) { h.feed(0f, 0f, 9.81f, ts, ts / 1_000_000L); ts += 20_000_000L }
        assertThat(h.feed(0f, 30f, 0f, ts, ts / 1_000_000L)).isNotNull()
    }

    @Test
    fun `reset wipes state so previous magnitude does not bleed into next session`() {
        val h = heuristic()
        var ts = 0L
        h.feed(0f, 0f, 30f, ts, ts / 1_000_000L) // primes magnitude=30
        h.reset()
        ts += 20_000_000L
        // After reset, the very first sample produces no jerk (NaN guard) so cannot fire.
        val detection = h.feed(0f, 30f, 0f, ts, ts / 1_000_000L)
        assertThat(detection).isNull()
    }
}

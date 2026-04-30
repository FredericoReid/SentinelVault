package com.sentinelvault.triggers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeskLiftHeuristicTest {

    private fun heuristic() = DeskLiftHeuristic(
        settleMs = 500L,
        pickupWindowMs = 800L,
        refractoryMs = 2_000L,
        flatThreshold = 0.92f,
        releasedThreshold = 0.72f,
        pickupAccelerationThresholdMs2 = 1.5f
    )

    @Test
    fun `face-up desk pickup fires after dwell impulse and release`() {
        val h = heuristic()
        var now = 0L

        repeat(4) {
            assertThat(h.feedGravity(0f, 0f, 9.81f, now)).isNull()
            now += 200L
        }

        assertThat(h.feedLinearAcceleration(0f, 2.2f, 0f, now)).isNull()
        val detection = h.feedGravity(0f, 7.5f, 4f, now + 100L)

        assertThat(detection).isNotNull()
        assertThat(detection!!.faceDown).isFalse()
        assertThat(detection.pickupAccelerationMs2).isAtLeast(1.5f)
    }

    @Test
    fun `face-down desk pickup is also detected`() {
        val h = heuristic()
        var now = 0L

        repeat(4) {
            assertThat(h.feedGravity(0f, 0f, -9.81f, now)).isNull()
            now += 200L
        }

        h.feedLinearAcceleration(0f, 0f, 2f, now)
        val detection = h.feedGravity(0f, 6.8f, -2.5f, now + 120L)

        assertThat(detection).isNotNull()
        assertThat(detection!!.faceDown).isTrue()
    }

    @Test
    fun `pickup without stable flat dwell does not fire`() {
        val h = heuristic()
        h.feedGravity(0f, 0f, 9.81f, 0L)
        h.feedLinearAcceleration(0f, 2.1f, 0f, 100L)

        val detection = h.feedGravity(0f, 7f, 3f, 200L)

        assertThat(detection).isNull()
    }

    @Test
    fun `leaving the table without pickup impulse does not fire`() {
        val h = heuristic()
        var now = 0L
        repeat(4) {
            h.feedGravity(0f, 0f, 9.81f, now)
            now += 200L
        }

        val detection = h.feedGravity(0f, 8f, 3f, now + 100L)

        assertThat(detection).isNull()
    }
}
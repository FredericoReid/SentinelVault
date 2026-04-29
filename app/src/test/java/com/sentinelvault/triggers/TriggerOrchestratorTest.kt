package com.sentinelvault.triggers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TriggerOrchestratorTest {

    @Test
    fun `events are observed in emission order with replay one`() = runBlockingCollect {
        val orchestrator = TriggerOrchestrator()
        val received = collect(orchestrator)

        orchestrator.emit(TriggerEvent.UserPresent(1L))
        orchestrator.emit(TriggerEvent.SnatchDetected(30f, 100f, 2L))
        orchestrator.emit(TriggerEvent.ForegroundAppChanged(null, "com.test", 3L))

        assertThat(received).hasSize(3)
        assertThat(received[0]).isInstanceOf(TriggerEvent.UserPresent::class.java)
        assertThat(received[1]).isInstanceOf(TriggerEvent.SnatchDetected::class.java)
        assertThat(received[2]).isInstanceOf(TriggerEvent.ForegroundAppChanged::class.java)
    }

    @Test
    fun `late subscriber sees the most recent replayed event`() = runBlockingCollect {
        val orchestrator = TriggerOrchestrator()
        // emit before any collector exists
        orchestrator.emit(TriggerEvent.UserPresent(42L))
        val received = collect(orchestrator)
        assertThat(received).hasSize(1)
        assertThat((received.first() as TriggerEvent.UserPresent).timestampMs).isEqualTo(42L)
    }

    @Test
    fun `emit returns true under the configured drop-oldest policy`() {
        val orchestrator = TriggerOrchestrator()
        repeat(TriggerOrchestrator.EXTRA_BUFFER * 2) {
            assertThat(orchestrator.emit(TriggerEvent.UserPresent(it.toLong()))).isTrue()
        }
    }
}

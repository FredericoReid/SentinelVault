package com.sentinelvault.vigilance

import com.google.common.truth.Truth.assertThat
import com.sentinelvault.triggers.ContextTokenManager
import com.sentinelvault.triggers.SensitiveAppRegistry
import com.sentinelvault.triggers.TriggerClock
import com.sentinelvault.triggers.TriggerEvent
import com.sentinelvault.triggers.TriggerOrchestrator
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VigilanceStateMachineTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeScheduler : PulseScheduler {
        val ticks: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 64)
        override fun ticks(intervalMs: Long): Flow<Unit> = ticks
        suspend fun pulse() = ticks.emit(Unit)
    }

    private class QueueEngine : VerificationEngine {
        val pending: ArrayDeque<VerificationOutcome> = ArrayDeque()
        var calls: Int = 0; private set
        fun enqueue(vararg outcomes: VerificationOutcome) { pending.addAll(outcomes) }
        override suspend fun verifyOnce(nowMs: Long): VerificationOutcome {
            calls += 1
            return pending.removeFirstOrNull() ?: VerificationOutcome.NoFace(nowMs)
        }
    }

    private class MutableClock(initial: Long = 1_000L) : TriggerClock {
        private val now = AtomicLong(initial)
        override fun nowMs(): Long = now.get()
        fun advance(deltaMs: Long) { now.addAndGet(deltaMs) }
    }

    private data class Fixture(
        val machine: VigilanceStateMachine,
        val orchestrator: TriggerOrchestrator,
        val tokens: ContextTokenManager,
        val engine: QueueEngine,
        val scheduler: FakeScheduler,
        val clock: MutableClock
    )

    private fun TestScope.fixture(
        config: VigilanceConfig = VigilanceConfig(
            matchThreshold = 0.6f,
            pulseIntervalMs = 3_000L,
            alertWindowMs = 12_000L,
            mismatchesToEscalate = 2,
            mismatchesToConfirmBreach = 3
        )
    ): Fixture {
        val clock = MutableClock()
        val orchestrator = TriggerOrchestrator()
        val tokens = ContextTokenManager(orchestrator, SensitiveAppRegistry(), clock)
        val scheduler = FakeScheduler()
        val engine = QueueEngine()
        val machine = VigilanceStateMachine(
            orchestrator = orchestrator,
            contextTokenManager = tokens,
            engine = engine,
            scheduler = scheduler,
            config = config,
            clock = clock,
            dispatcher = dispatcher
        )
        backgroundScope.launch(dispatcher) { machine.run(this) }
        return Fixture(machine, orchestrator, tokens, engine, scheduler, clock)
    }

    @Test
    fun `idle by default`() = runTest(dispatcher) {
        val f = fixture()
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.Idle::class.java)
    }

    @Test
    fun `UserPresent with match collapses back to Idle`() = runTest(dispatcher) {
        val f = fixture()
        f.engine.enqueue(VerificationOutcome.Match(0.9f, 1L))
        f.orchestrator.emit(TriggerEvent.UserPresent(1L))
        assertThat(f.engine.calls).isEqualTo(1)
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.Idle::class.java)
    }

    @Test
    fun `single mismatch promotes from VerifyOnce to AlertLevel1`() = runTest(dispatcher) {
        val f = fixture()
        f.engine.enqueue(VerificationOutcome.Mismatch(0.1f, 1L))
        f.orchestrator.emit(TriggerEvent.UserPresent(1L))
        val state = f.machine.state.value
        assertThat(state).isInstanceOf(VigilanceState.AlertLevel1::class.java)
        assertThat(state.mismatchStreak).isEqualTo(1)
    }

    @Test
    fun `two consecutive mismatches reach AlertLevel2`() = runTest(dispatcher) {
        val f = fixture()
        f.engine.enqueue(
            VerificationOutcome.Mismatch(0.1f, 1L),
            VerificationOutcome.Mismatch(0.1f, 2L)
        )
        f.orchestrator.emit(TriggerEvent.UserPresent(1L))
        f.scheduler.pulse()
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.AlertLevel2::class.java)
        assertThat(f.machine.state.value.mismatchStreak).isEqualTo(2)
    }

    @Test
    fun `three consecutive mismatches confirm breach and stop the pulse loop`() = runTest(dispatcher) {
        val f = fixture()
        f.engine.enqueue(
            VerificationOutcome.Mismatch(0.1f, 1L),
            VerificationOutcome.Mismatch(0.1f, 2L),
            VerificationOutcome.Mismatch(0.1f, 3L)
        )
        f.orchestrator.emit(TriggerEvent.UserPresent(1L))
        f.scheduler.pulse(); f.scheduler.pulse()
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.BreachConfirmed::class.java)
        // any further pulses are ignored — BreachConfirmed is sticky.
        f.scheduler.pulse()
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.BreachConfirmed::class.java)
        assertThat(f.engine.calls).isEqualTo(3)
    }

    @Test
    fun `match during alert downgrades back to Idle`() = runTest(dispatcher) {
        val f = fixture()
        f.engine.enqueue(
            VerificationOutcome.Mismatch(0.1f, 1L),
            VerificationOutcome.Match(0.9f, 2L)
        )
        f.orchestrator.emit(TriggerEvent.UserPresent(1L))
        f.scheduler.pulse()
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.Idle::class.java)
    }

    @Test
    fun `snatch enters AlertLevel1 directly without burning a single pulse`() = runTest(dispatcher) {
        val f = fixture()
        f.orchestrator.emit(TriggerEvent.SnatchDetected(30f, 100f, 1L))
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.AlertLevel1::class.java)
        assertThat(f.engine.calls).isEqualTo(0)
    }

    @Test
    fun `desk lift enters alert and spends an immediate verification pulse`() = runTest(dispatcher) {
        val f = fixture()

        f.orchestrator.emit(
            TriggerEvent.DeskLiftDetected(
                fromFaceDown = false,
                pickupAccelerationMs2 = 2.4f,
                timestampMs = 1L
            )
        )

        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.AlertLevel1::class.java)
        assertThat(f.engine.calls).isEqualTo(1)
    }

    @Test
    fun `sensitive-app open is suppressed when context token covers it`() = runTest(dispatcher) {
        val f = fixture()
        f.tokens.issue("com.whatsapp")
        f.orchestrator.emit(TriggerEvent.SensitiveAppOpened("com.whatsapp", 1L))
        assertThat(f.engine.calls).isEqualTo(0)
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.Idle::class.java)
    }

    @Test
    fun `NotLive counts as a mismatch toward escalation`() = runTest(dispatcher) {
        val f = fixture()
        f.engine.enqueue(VerificationOutcome.NotLive(2f, 1L))
        f.orchestrator.emit(TriggerEvent.UserPresent(1L))
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.AlertLevel1::class.java)
    }

    @Test
    fun `NoFace verdict during VerifyOnce returns to Idle without escalating`() = runTest(dispatcher) {
        val f = fixture()
        f.engine.enqueue(VerificationOutcome.NoFace(1L))
        f.orchestrator.emit(TriggerEvent.UserPresent(1L))
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.Idle::class.java)
    }

    @Test
    fun `alert window expiry collapses back to Idle`() = runTest(dispatcher) {
        val f = fixture(VigilanceConfig(pulseIntervalMs = 1_000L, alertWindowMs = 1_000L))
        f.engine.enqueue(VerificationOutcome.Mismatch(0.1f, 1L), VerificationOutcome.NoFace(2L))
        f.orchestrator.emit(TriggerEvent.UserPresent(1L))
        f.clock.advance(5_000L)         // jump well past the alert window
        f.scheduler.pulse()              // first pulse after expiry triggers cleanup
        assertThat(f.machine.state.value).isInstanceOf(VigilanceState.Idle::class.java)
    }
}

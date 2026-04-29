package com.sentinelvault.lockdown

import com.google.common.truth.Truth.assertThat
import com.sentinelvault.vault.BurstRecorder
import com.sentinelvault.vault.EvidenceVault
import com.sentinelvault.vault.FrameCandidate
import com.sentinelvault.vigilance.VigilanceState
import com.sentinelvault.vigilance.VigilanceStateMachine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockdownCoordinatorTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeOverlay : SoftLockOverlayController {
        var current: SoftLockOverlayController.Mode = SoftLockOverlayController.Mode.OFF
        val transitions: MutableList<SoftLockOverlayController.Mode> = mutableListOf()
        override val mode: SoftLockOverlayController.Mode get() = current
        override fun arm() { current = SoftLockOverlayController.Mode.ARMED; transitions += current }
        override fun show() { current = SoftLockOverlayController.Mode.SHOWING; transitions += current }
        override fun dismiss() { current = SoftLockOverlayController.Mode.OFF; transitions += current }
    }

    private data class Fixture(
        val coordinator: LockdownCoordinator,
        val overlay: FakeOverlay,
        val action: LockdownAction,
        val selfHealing: SelfHealingController,
        val machine: VigilanceStateMachine,
        val state: MutableStateFlow<VigilanceState>,
        val burstRecorder: BurstRecorder,
        val evidenceVault: EvidenceVault
    )

    private fun fixture(
        triggerResult: LockdownAction.Result = LockdownAction.Result.HardLocked(breachId = 9L, lockEventId = 10L),
        drained: List<FrameCandidate> = emptyList()
    ): Fixture {
        val state = MutableStateFlow<VigilanceState>(VigilanceState.Idle())
        val machine = mockk<VigilanceStateMachine>()
        every { machine.state } returns state
        coEvery { machine.reset() } returns Unit
        val action = mockk<LockdownAction>()
        coEvery { action.trigger(any()) } returns triggerResult
        val selfHealing = mockk<SelfHealingController>()
        coEvery { selfHealing.markFalseReject(any()) } returns 77L
        val overlay = FakeOverlay()
        val burstRecorder = mockk<BurstRecorder>(relaxed = true)
        coEvery { burstRecorder.drain() } returns drained
        val evidenceVault = mockk<EvidenceVault>(relaxed = true)
        coEvery { evidenceVault.storeBreachEvidence(any(), any()) } returns
            EvidenceVault.Outcome.Skipped.NoCandidates
        return Fixture(
            coordinator = LockdownCoordinator(
                machine, overlay, action, selfHealing, burstRecorder, evidenceVault
            ),
            overlay = overlay,
            action = action,
            selfHealing = selfHealing,
            machine = machine,
            state = state,
            burstRecorder = burstRecorder,
            evidenceVault = evidenceVault
        )
    }

    @Test
    fun `AlertLevel2 arms the overlay`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.AlertLevel2(sinceMs = 1L, mismatchStreak = 2)
        assertThat(f.overlay.mode).isEqualTo(SoftLockOverlayController.Mode.ARMED)
    }

    @Test
    fun `BreachConfirmed shows the overlay and triggers the lockdown action exactly once`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.AlertLevel2(sinceMs = 1L, mismatchStreak = 2)
        f.state.value = VigilanceState.BreachConfirmed(sinceMs = 2L, mismatchStreak = 3)

        assertThat(f.overlay.mode).isEqualTo(SoftLockOverlayController.Mode.SHOWING)
        coVerify(exactly = 1) {
            f.action.trigger(match {
                it.timestampMs == 2L && it.mismatchStreak == 3 && it.foregroundPackage == null
            })
        }
    }

    @Test
    fun `re-emitted BreachConfirmed does not re-trigger the lockdown action`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.BreachConfirmed(sinceMs = 1L, mismatchStreak = 3)
        f.state.value = VigilanceState.BreachConfirmed(sinceMs = 2L, mismatchStreak = 3)
        f.state.value = VigilanceState.BreachConfirmed(sinceMs = 3L, mismatchStreak = 3)
        coVerify(exactly = 1) { f.action.trigger(any()) }
    }

    @Test
    fun `Idle from ARMED dismisses the overlay`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.AlertLevel2(sinceMs = 1L, mismatchStreak = 2)
        f.state.value = VigilanceState.Idle(sinceMs = 5L)
        assertThat(f.overlay.mode).isEqualTo(SoftLockOverlayController.Mode.OFF)
    }

    @Test
    fun `acknowledgeOwnerReturn after a breach dismisses overlay, resets machine and records false reject`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.BreachConfirmed(sinceMs = 2L, mismatchStreak = 3)
        assertThat(f.overlay.mode).isEqualTo(SoftLockOverlayController.Mode.SHOWING)

        val resolvedFalseReject = f.coordinator.acknowledgeOwnerReturn()

        assertThat(resolvedFalseReject).isTrue()
        assertThat(f.overlay.mode).isEqualTo(SoftLockOverlayController.Mode.OFF)
        coVerify(exactly = 1) { f.machine.reset() }
        coVerify(exactly = 1) { f.selfHealing.markFalseReject(9L) }
    }

    @Test
    fun `acknowledgeOwnerReturn without latched breach reports nothing to resolve`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        val resolved = f.coordinator.acknowledgeOwnerReturn()
        assertThat(resolved).isFalse()
        coVerify(exactly = 0) { f.selfHealing.markFalseReject(any()) }
        coVerify(exactly = 1) { f.machine.reset() }
    }

    @Test
    fun `consecutive breaches after acknowledge re-trigger the action`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.BreachConfirmed(sinceMs = 2L, mismatchStreak = 3)
        f.coordinator.acknowledgeOwnerReturn()
        // simulate state machine returning to a fresh breach later
        f.state.value = VigilanceState.Idle(sinceMs = 10L)
        f.state.value = VigilanceState.BreachConfirmed(sinceMs = 11L, mismatchStreak = 3)
        coVerify(exactly = 2) { f.action.trigger(any()) }
    }

    @Test
    fun `intermediate states do not change the overlay`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.VerifyOnce(
            reason = com.sentinelvault.vigilance.VerifyReason.UserPresent,
            sinceMs = 1L
        )
        f.state.value = VigilanceState.AlertLevel1(sinceMs = 2L, mismatchStreak = 1)
        assertThat(f.overlay.mode).isEqualTo(SoftLockOverlayController.Mode.OFF)
        coVerify(exactly = 0) { f.action.trigger(any()) }
    }

    @Test
    fun `AlertLevel1 arms the burst recorder without arming the overlay`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.AlertLevel1(sinceMs = 1L, mismatchStreak = 1)
        coVerify(exactly = 1) { f.burstRecorder.arm() }
        assertThat(f.overlay.mode).isEqualTo(SoftLockOverlayController.Mode.OFF)
    }

    @Test
    fun `AlertLevel2 arms both the burst recorder and the overlay`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.AlertLevel2(sinceMs = 1L, mismatchStreak = 2)
        coVerify(exactly = 1) { f.burstRecorder.arm() }
        assertThat(f.overlay.mode).isEqualTo(SoftLockOverlayController.Mode.ARMED)
    }

    @Test
    fun `Idle disarms the burst recorder`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.AlertLevel2(sinceMs = 1L, mismatchStreak = 2)
        f.state.value = VigilanceState.Idle(sinceMs = 5L)
        coVerify(atLeast = 1) { f.burstRecorder.disarm() }
    }

    @Test
    fun `BreachConfirmed drains the burst and persists evidence with the latched breach id`() = runTest(dispatcher) {
        val burst = listOf(
            FrameCandidate(
                bitmap = io.mockk.mockk(relaxed = true),
                capturedAtMs = 42L,
                luma = ByteArray(4),
                width = 2,
                height = 2
            )
        )
        val f = fixture(drained = burst)
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.BreachConfirmed(sinceMs = 2L, mismatchStreak = 3)

        coVerify(exactly = 1) { f.burstRecorder.drain() }
        coVerify(exactly = 1) { f.evidenceVault.storeBreachEvidence(9L, burst) }
    }

    @Test
    fun `re-emitted BreachConfirmed does not re-drain or re-persist evidence`() = runTest(dispatcher) {
        val f = fixture()
        backgroundScope.launch(dispatcher) { f.coordinator.observe() }
        f.state.value = VigilanceState.BreachConfirmed(sinceMs = 1L, mismatchStreak = 3)
        f.state.value = VigilanceState.BreachConfirmed(sinceMs = 2L, mismatchStreak = 3)
        coVerify(exactly = 1) { f.burstRecorder.drain() }
        coVerify(exactly = 1) { f.evidenceVault.storeBreachEvidence(any(), any()) }
    }
}

package com.sentinelvault.ui.gatekeeper

import com.google.common.truth.Truth.assertThat
import com.sentinelvault.data.auth.PinRepository
import com.sentinelvault.lockdown.LockdownCoordinator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GatekeeperViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var pinRepo: PinRepository
    private lateinit var coordinator: LockdownCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        pinRepo = mockk(relaxed = true)
        every { pinRepo.lockoutRemainingMs() } returns 0L
        coordinator = mockk(relaxed = true)
        coEvery { coordinator.acknowledgeOwnerReturn() } returns false
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(): GatekeeperViewModel = GatekeeperViewModel(pinRepo, coordinator)

    @Test
    fun `initial mode is CreatePinChoose when pin is not set`() = runTest(dispatcher) {
        every { pinRepo.isPinSet() } returns false
        val vm = newViewModel()
        assertThat(vm.state.value.mode).isEqualTo(GatekeeperMode.CreatePinChoose)
    }

    @Test
    fun `initial mode is Login when pin is set`() = runTest(dispatcher) {
        every { pinRepo.isPinSet() } returns true
        val vm = newViewModel()
        assertThat(vm.state.value.mode).isEqualTo(GatekeeperMode.Login)
    }

    @Test
    fun `digits accumulate up to target length and trigger submit`() = runTest(dispatcher) {
        every { pinRepo.isPinSet() } returns false
        val vm = newViewModel()
        "123456".forEach { vm.onDigit(it) }
        advanceUntilIdle()
        assertThat(vm.state.value.mode).isEqualTo(GatekeeperMode.CreatePinConfirm)
        assertThat(vm.state.value.entry).isEmpty()
        assertThat(vm.state.value.pendingNewPin).isEqualTo("123456")
    }

    @Test
    fun `mismatched confirm resets to choose with PinMismatch error`() = runTest(dispatcher) {
        every { pinRepo.isPinSet() } returns false
        val vm = newViewModel()
        "123456".forEach { vm.onDigit(it) }
        "654321".forEach { vm.onDigit(it) }
        advanceUntilIdle()
        assertThat(vm.state.value.mode).isEqualTo(GatekeeperMode.CreatePinChoose)
        assertThat(vm.state.value.errorMessage).isEqualTo(ErrorReason.PinMismatch)
    }

    @Test
    fun `matching confirm calls setPin and emits PinCreated`() = runTest(dispatcher) {
        every { pinRepo.isPinSet() } returns false
        val vm = newViewModel()
        "123456".forEach { vm.onDigit(it) }
        "123456".forEach { vm.onDigit(it) }
        advanceUntilIdle()
        verify { pinRepo.setPin(any()) }
        assertThat(vm.events.value).isEqualTo(GatekeeperEvent.PinCreated)
    }

    @Test
    fun `successful login emits Authenticated and acknowledges owner return`() = runTest(dispatcher) {
        every { pinRepo.isPinSet() } returns true
        every { pinRepo.verify(any()) } returns PinRepository.VerifyResult.Success
        val vm = newViewModel()
        "123456".forEach { vm.onDigit(it) }
        advanceUntilIdle()
        assertThat(vm.events.value).isEqualTo(GatekeeperEvent.Authenticated)
        io.mockk.coVerify { coordinator.acknowledgeOwnerReturn() }
    }

    @Test
    fun `failed login surfaces WrongPin and starts lockout when needed`() = runTest(dispatcher) {
        every { pinRepo.isPinSet() } returns true
        every { pinRepo.verify(any()) } returns PinRepository.VerifyResult.Failure(3, 30_000L)
        val vm = newViewModel()
        "000000".forEach { vm.onDigit(it) }
        // Synchronous state assignment in startLockoutTicker is observed before the ticker
        // coroutine drains; runCurrent only flushes already-queued work without progressing time.
        runCurrent()
        val state = vm.state.value
        assertThat(state.errorMessage).isEqualTo(ErrorReason.WrongPin(3))
        assertThat(state.lockoutRemainingMs).isEqualTo(30_000L)
    }

    @Test
    fun `digits ignored while locked`() = runTest(dispatcher) {
        every { pinRepo.isPinSet() } returns true
        every { pinRepo.lockoutRemainingMs() } returns 30_000L
        val vm = newViewModel()
        vm.onDigit('1')
        assertThat(vm.state.value.entry).isEmpty()
    }
}

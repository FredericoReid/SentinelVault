package com.sentinelvault.ui.settings

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.service.VigilanceServiceLauncher
import com.sentinelvault.service.VigilanceSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * JVM test for the on/off switch view-model (Task 9.7). Verifies the view-model:
 *  * mirrors the persisted flag at construction time,
 *  * persists the new value AND mirrors it into the StateFlow on toggle,
 *  * routes ON → [VigilanceServiceLauncher.ensureRunning] and OFF → [VigilanceServiceLauncher.stop].
 */
class VigilanceSettingsViewModelTest {

    private val context: Context = mockk(relaxed = true)
    private val settings: VigilanceSettings = mockk(relaxed = true)
    private val launcher: VigilanceServiceLauncher = mockk(relaxed = true)

    @Test
    fun `initial value mirrors persisted setting`() {
        every { settings.isVigilanceEnabled() } returns true
        every { settings.isDeskLiftTriggerEnabled() } returns true
        every { settings.isLenientFramingEnabled() } returns true

        val vm = VigilanceSettingsViewModel(context, settings, launcher)

        assertThat(vm.uiState.value.enabled).isTrue()
        assertThat(vm.uiState.value.deskLiftEnabled).isTrue()
        assertThat(vm.uiState.value.lenientFramingEnabled).isTrue()
    }

    @Test
    fun `toggle on persists and starts the service`() {
        every { settings.isVigilanceEnabled() } returns false andThen true
        every { settings.isDeskLiftTriggerEnabled() } returns true
        every { settings.isLenientFramingEnabled() } returns true
        val vm = VigilanceSettingsViewModel(context, settings, launcher)

        vm.setEnabled(true)

        verify(exactly = 1) { settings.setVigilanceEnabled(true) }
        verify(exactly = 1) { launcher.ensureRunning(context) }
        verify(exactly = 0) { launcher.stop(any()) }
        assertThat(vm.uiState.value.enabled).isTrue()
    }

    @Test
    fun `toggle off persists and stops the service`() {
        every { settings.isVigilanceEnabled() } returns true andThen false
        every { settings.isDeskLiftTriggerEnabled() } returns true
        every { settings.isLenientFramingEnabled() } returns true
        val vm = VigilanceSettingsViewModel(context, settings, launcher)

        vm.setEnabled(false)

        verify(exactly = 1) { settings.setVigilanceEnabled(false) }
        verify(exactly = 1) { launcher.stop(context) }
        verify(exactly = 0) { launcher.ensureRunning(any()) }
        assertThat(vm.uiState.value.enabled).isFalse()
    }

    @Test
    fun `desk lift toggle persists and updates state`() {
        every { settings.isVigilanceEnabled() } returns true
        every { settings.isDeskLiftTriggerEnabled() } returns false andThen true
        every { settings.isLenientFramingEnabled() } returns true
        val vm = VigilanceSettingsViewModel(context, settings, launcher)

        vm.setDeskLiftEnabled(true)

        verify(exactly = 1) { settings.setDeskLiftTriggerEnabled(true) }
        assertThat(vm.uiState.value.deskLiftEnabled).isTrue()
    }

    @Test
    fun `lenient framing toggle persists and updates state`() {
        every { settings.isVigilanceEnabled() } returns true
        every { settings.isDeskLiftTriggerEnabled() } returns true
        every { settings.isLenientFramingEnabled() } returns false andThen true
        val vm = VigilanceSettingsViewModel(context, settings, launcher)

        vm.setLenientFramingEnabled(true)

        verify(exactly = 1) { settings.setLenientFramingEnabled(true) }
        assertThat(vm.uiState.value.lenientFramingEnabled).isTrue()
    }
}

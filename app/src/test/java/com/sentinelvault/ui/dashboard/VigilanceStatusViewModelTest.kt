package com.sentinelvault.ui.dashboard

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.service.VigilanceHeartbeat
import com.sentinelvault.service.VigilanceSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * JVM test for the truth table documented on [VigilanceStatusViewModel.compute]. We bypass
 * the [androidx.lifecycle.viewModelScope] poller by calling `compute()` directly with a
 * controlled clock — the per-second polling loop is a thin coroutine wrapper around the
 * same function and not worth a Robolectric dependency to validate. The Main dispatcher is
 * still required because the view-model launches the poller in its `init` block.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VigilanceStatusViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val heartbeat: VigilanceHeartbeat = mockk()
    private val settings: VigilanceSettings = mockk()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun checks(battery: Boolean, notifications: Boolean) =
        object : VigilanceStatusViewModel.EnvironmentChecks {
            override fun isBatteryOptimisationIgnored(context: Context): Boolean = battery
            override fun isPostNotificationsGranted(context: Context): Boolean = notifications
        }

    private fun viewModel(battery: Boolean = true, notifications: Boolean = true) =
        VigilanceStatusViewModel(context, heartbeat, settings, checks(battery, notifications))

    @Test
    fun `disabled toggle is INACTIVE regardless of heartbeat`() {
        every { settings.isVigilanceEnabled() } returns false
        every { heartbeat.isFresh(any()) } returns true

        val status = viewModel().compute(nowMs = 1_000L)

        assertThat(status).isEqualTo(VigilanceServiceStatus.INACTIVE)
    }

    @Test
    fun `stale heartbeat is INACTIVE`() {
        every { settings.isVigilanceEnabled() } returns true
        every { heartbeat.isFresh(any()) } returns false

        val status = viewModel().compute(nowMs = 1_000L)

        assertThat(status).isEqualTo(VigilanceServiceStatus.INACTIVE)
    }

    @Test
    fun `fresh heartbeat without battery exemption is DEGRADED`() {
        every { settings.isVigilanceEnabled() } returns true
        every { heartbeat.isFresh(any()) } returns true

        val status = viewModel(battery = false, notifications = true).compute(nowMs = 1_000L)

        assertThat(status).isEqualTo(VigilanceServiceStatus.DEGRADED)
    }

    @Test
    fun `fresh heartbeat without notifications is DEGRADED`() {
        every { settings.isVigilanceEnabled() } returns true
        every { heartbeat.isFresh(any()) } returns true

        val status = viewModel(battery = true, notifications = false).compute(nowMs = 1_000L)

        assertThat(status).isEqualTo(VigilanceServiceStatus.DEGRADED)
    }

    @Test
    fun `all preconditions met is ACTIVE`() {
        every { settings.isVigilanceEnabled() } returns true
        every { heartbeat.isFresh(any()) } returns true

        val status = viewModel(battery = true, notifications = true).compute(nowMs = 1_000L)

        assertThat(status).isEqualTo(VigilanceServiceStatus.ACTIVE)
    }
}

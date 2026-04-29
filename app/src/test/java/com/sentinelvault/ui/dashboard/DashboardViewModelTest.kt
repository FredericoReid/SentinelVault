package com.sentinelvault.ui.dashboard

import com.google.common.truth.Truth.assertThat
import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EventLogEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `empty timeline produces an empty state`() = runTest(dispatcher) {
        val dao = mockk<EventLogDao>()
        every { dao.getIncidentTimeline() } returns MutableStateFlow(emptyList())

        val vm = DashboardViewModel(dao)

        val state = vm.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.rows).isEmpty()
        assertThat(state.errorMessage).isNull()
        assertThat(state.isEmpty).isTrue()
    }

    @Test
    fun `rows from the dao are mapped through the formatter and sorted as emitted`() = runTest(dispatcher) {
        val rows = listOf(
            EventLogEntity(
                id = 1L,
                timestampMs = 1_714_566_896_000L,
                type = EventLogEntity.Type.BREACH_CONFIRMED,
                severity = 2,
                foregroundPackage = "com.example.bank",
                evidencePath = "/files/evidence/1.webp",
                notes = "mismatchStreak=3"
            ),
            EventLogEntity(
                id = 2L,
                timestampMs = 1_714_566_000_000L,
                type = EventLogEntity.Type.SNATCH_DETECTED,
                severity = 1,
                foregroundPackage = null,
                evidencePath = null,
                notes = null
            )
        )
        val dao = mockk<EventLogDao>()
        every { dao.getIncidentTimeline() } returns MutableStateFlow(rows)

        val vm = DashboardViewModel(dao)

        val state = vm.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.rows.map { it.id }).containsExactly(1L, 2L).inOrder()
        assertThat(state.rows[0].typeLabel).isEqualTo("Breach confirmed")
        assertThat(state.rows[0].severity).isEqualTo(2)
        assertThat(state.rows[1].typeLabel).isEqualTo("Snatch detected")
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `flow throwing surfaces an error message and stops loading`() = runTest(dispatcher) {
        val dao = mockk<EventLogDao>()
        every { dao.getIncidentTimeline() } returns flow { throw IllegalStateException("db locked") }

        val vm = DashboardViewModel(dao)

        val state = vm.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isEqualTo("db locked")
        assertThat(state.rows).isEmpty()
    }
}

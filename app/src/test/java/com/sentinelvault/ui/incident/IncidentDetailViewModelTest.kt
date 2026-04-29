package com.sentinelvault.ui.incident

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EventLogEntity
import com.sentinelvault.ui.navigation.Routes
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentDetailViewModelTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun savedState(id: Long): SavedStateHandle =
        SavedStateHandle(mapOf(Routes.INCIDENT_ID_ARG to id))

    @Test
    fun `id of zero short-circuits to notFound without hitting the dao`() = runTest(dispatcher) {
        val dao = mockk<EventLogDao>()
        val vm = IncidentDetailViewModel(dao, savedState(0L))
        val state = vm.state.value
        assertThat(state.notFound).isTrue()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `missing row from dao produces notFound`() = runTest(dispatcher) {
        val dao = mockk<EventLogDao>()
        coEvery { dao.findById(42L) } returns null
        val vm = IncidentDetailViewModel(dao, savedState(42L))
        val state = vm.state.value
        assertThat(state.notFound).isTrue()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `existing evidence path that resolves to a real file is not flagged as missing`() = runTest(dispatcher) {
        val webp: File = tempFolder.newFile("breach.webp").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val dao = mockk<EventLogDao>()
        coEvery { dao.findById(42L) } returns EventLogEntity(
            id = 42L,
            timestampMs = 1_714_566_896_000L,
            type = EventLogEntity.Type.BREACH_CONFIRMED,
            severity = 2,
            foregroundPackage = "com.example.bank",
            evidencePath = webp.absolutePath,
            notes = "mismatchStreak=3"
        )

        val vm = IncidentDetailViewModel(dao, savedState(42L))

        // pathExists hops to Dispatchers.IO, so wait until the load coroutine settles instead
        // of reading the synchronous initial value.
        val state = vm.state.first { !it.isLoading && it.evidencePath != null }
        assertThat(state.notFound).isFalse()
        assertThat(state.typeLabel).isEqualTo("Breach confirmed")
        assertThat(state.timestampLabel).isEqualTo("2024-05-01 12:34:56")
        assertThat(state.evidencePath).isEqualTo(webp.absolutePath)
        assertThat(state.evidenceMissing).isFalse()
        assertThat(state.foregroundPackage).isEqualTo("com.example.bank")
        assertThat(state.notes).isEqualTo("mismatchStreak=3")
    }

    @Test
    fun `evidence path pointing to a deleted file is flagged as missing`() = runTest(dispatcher) {
        val dao = mockk<EventLogDao>()
        coEvery { dao.findById(42L) } returns EventLogEntity(
            id = 42L,
            timestampMs = 1_714_566_896_000L,
            type = EventLogEntity.Type.BREACH_CONFIRMED,
            severity = 2,
            foregroundPackage = null,
            evidencePath = "/does/not/exist.webp",
            notes = null
        )

        val vm = IncidentDetailViewModel(dao, savedState(42L))

        val state = vm.state.first { !it.isLoading && it.evidencePath != null }
        assertThat(state.evidencePath).isEqualTo("/does/not/exist.webp")
        assertThat(state.evidenceMissing).isTrue()
    }

    @Test
    fun `null evidence path is not flagged as missing`() = runTest(dispatcher) {
        val dao = mockk<EventLogDao>()
        coEvery { dao.findById(42L) } returns EventLogEntity(
            id = 42L,
            timestampMs = 1_714_566_896_000L,
            type = EventLogEntity.Type.SNATCH_DETECTED,
            severity = 1,
            foregroundPackage = null,
            evidencePath = null,
            notes = null
        )

        val vm = IncidentDetailViewModel(dao, savedState(42L))

        val state = vm.state.value
        assertThat(state.evidencePath).isNull()
        assertThat(state.evidenceMissing).isFalse()
    }
}

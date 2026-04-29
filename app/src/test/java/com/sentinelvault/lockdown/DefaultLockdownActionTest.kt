package com.sentinelvault.lockdown

import com.google.common.truth.Truth.assertThat
import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EventLogEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultLockdownActionTest {

    private fun fixture(
        adminActive: Boolean = true,
        lockSucceeds: Boolean = true,
        breachIds: List<Long> = listOf(101L, 202L)
    ): Triple<DefaultLockdownAction, EventLogDao, DevicePolicyController> {
        val dao = mockk<EventLogDao>()
        val ids = ArrayDeque(breachIds)
        coEvery { dao.insertBreach(any()) } answers { ids.removeFirstOrNull() ?: 999L }
        val policy = mockk<DevicePolicyController>()
        every { policy.isAdminActive() } returns adminActive
        every { policy.lockNow() } returns lockSucceeds
        return Triple(DefaultLockdownAction(dao, policy), dao, policy)
    }

    private val sampleReason = BreachReason(
        timestampMs = 1_700_000_000L,
        mismatchStreak = 3,
        foregroundPackage = "com.example.bank"
    )

    @Test
    fun `happy path persists breach and lockdown rows then locks the device`() = runTest {
        val (action, dao, policy) = fixture()
        val rows = mutableListOf<EventLogEntity>()
        coEvery { dao.insertBreach(capture(rows)) } answers { rows.size.toLong() * 100 }

        val result = action.trigger(sampleReason)

        assertThat(result).isInstanceOf(LockdownAction.Result.HardLocked::class.java)
        val hard = result as LockdownAction.Result.HardLocked
        assertThat(hard.breachId).isEqualTo(100L)
        assertThat(hard.lockEventId).isEqualTo(200L)
        assertThat(rows).hasSize(2)
        assertThat(rows[0].type).isEqualTo(EventLogEntity.Type.BREACH_CONFIRMED)
        assertThat(rows[0].severity).isEqualTo(2)
        assertThat(rows[0].notes).isEqualTo("mismatchStreak=3")
        assertThat(rows[0].foregroundPackage).isEqualTo("com.example.bank")
        assertThat(rows[1].type).isEqualTo(EventLogEntity.Type.LOCKDOWN_TRIGGERED)
        assertThat(rows[1].severity).isEqualTo(2)
        assertThat(rows[1].notes).isEqualTo("lockNow=ok")
        coVerifyOrder {
            dao.insertBreach(match { it.type == EventLogEntity.Type.BREACH_CONFIRMED })
            policy.isAdminActive()
            policy.lockNow()
            dao.insertBreach(match { it.type == EventLogEntity.Type.LOCKDOWN_TRIGGERED })
        }
    }

    @Test
    fun `admin inactive degrades to SoftLockedOnly with severity one`() = runTest {
        val (action, dao, policy) = fixture(adminActive = false)
        val rows = mutableListOf<EventLogEntity>()
        coEvery { dao.insertBreach(capture(rows)) } returns 1L

        val result = action.trigger(sampleReason)

        assertThat(result).isInstanceOf(LockdownAction.Result.SoftLockedOnly::class.java)
        verify(exactly = 0) { policy.lockNow() }
        assertThat(rows[1].severity).isEqualTo(1)
        assertThat(rows[1].notes).isEqualTo("lockNow=skipped")
    }

    @Test
    fun `lockNow returning false also produces SoftLockedOnly`() = runTest {
        val (action, dao, _) = fixture(adminActive = true, lockSucceeds = false)
        val rows = mutableListOf<EventLogEntity>()
        coEvery { dao.insertBreach(capture(rows)) } returns 1L

        val result = action.trigger(sampleReason)

        assertThat(result).isInstanceOf(LockdownAction.Result.SoftLockedOnly::class.java)
        assertThat(rows[1].notes).isEqualTo("lockNow=skipped")
    }

    @Test
    fun `null foregroundPackage is forwarded verbatim`() = runTest {
        val (action, dao, _) = fixture()
        val captured = slot<EventLogEntity>()
        coEvery { dao.insertBreach(capture(captured)) } returns 1L
        action.trigger(sampleReason.copy(foregroundPackage = null))
        assertThat(captured.captured.foregroundPackage).isNull()
    }

    @Test
    fun `back-to-back triggers each persist their own pair under the mutex`() = runTest {
        val (action, dao, policy) = fixture()
        action.trigger(sampleReason)
        action.trigger(sampleReason.copy(timestampMs = sampleReason.timestampMs + 1))
        coVerify(exactly = 4) { dao.insertBreach(any()) }
        verify(exactly = 2) { policy.lockNow() }
    }
}

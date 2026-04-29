package com.sentinelvault.lockdown

import com.google.common.truth.Truth.assertThat
import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EventLogEntity
import com.sentinelvault.triggers.TriggerClock
import com.sentinelvault.vigilance.VigilanceConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SelfHealingControllerTest {

    private class MutableClock(initial: Long = 1_000L) : TriggerClock {
        private val now = AtomicLong(initial)
        override fun nowMs(): Long = now.get()
        fun advance(delta: Long) { now.addAndGet(delta) }
    }

    private fun fixture(
        config: VigilanceConfig = VigilanceConfig(matchThreshold = 0.62f),
        clock: MutableClock = MutableClock()
    ): Triple<SelfHealingController, EventLogDao, MutableClock> {
        val dao = mockk<EventLogDao>()
        coEvery { dao.insertBreach(any()) } returns 7L
        return Triple(SelfHealingController(dao, config, clock), dao, clock)
    }

    @Test
    fun `baseline threshold mirrors config when no false reject was recorded`() {
        val (controller, _, _) = fixture()
        assertThat(controller.currentMatchThreshold()).isEqualTo(0.62f)
        assertThat(controller.falseRejectCount.value).isEqualTo(0)
    }

    @Test
    fun `markFalseReject persists FALSE_REJECT_RESOLVED row with breach hyperlink`() = runTest {
        val (controller, dao, clock) = fixture()
        val captured = slot<EventLogEntity>()
        coEvery { dao.insertBreach(capture(captured)) } returns 11L

        val id = controller.markFalseReject(breachId = 42L)

        assertThat(id).isEqualTo(11L)
        assertThat(captured.captured.type).isEqualTo(EventLogEntity.Type.FALSE_REJECT_RESOLVED)
        assertThat(captured.captured.severity).isEqualTo(0)
        assertThat(captured.captured.notes).isEqualTo("Override of breach #42")
        assertThat(captured.captured.timestampMs).isEqualTo(clock.nowMs())
        coVerify(exactly = 1) { dao.insertBreach(any()) }
    }

    @Test
    fun `single false reject relaxes threshold by RELAX_PER_REJECT`() = runTest {
        val (controller, _, _) = fixture()
        controller.markFalseReject()
        assertThat(controller.currentMatchThreshold())
            .isWithin(1e-6f).of(0.62f - SelfHealingController.RELAX_PER_REJECT)
        assertThat(controller.falseRejectCount.value).isEqualTo(1)
    }

    @Test
    fun `relaxation is capped by MAX_RELAX even after many rejects`() = runTest {
        val (controller, _, _) = fixture()
        repeat(20) { controller.markFalseReject() }
        val expected = (0.62f - SelfHealingController.MAX_RELAX)
        assertThat(controller.currentMatchThreshold()).isWithin(1e-6f).of(expected)
    }

    @Test
    fun `relaxation is floored at MIN_THRESHOLD`() = runTest {
        val (controller, _, _) = fixture(config = VigilanceConfig(matchThreshold = 0.46f))
        controller.markFalseReject()
        assertThat(controller.currentMatchThreshold())
            .isAtLeast(SelfHealingController.MIN_THRESHOLD)
    }

    @Test
    fun `threshold returns to baseline once the grace window elapses`() = runTest {
        val clock = MutableClock()
        val (controller, _, _) = fixture(clock = clock)
        controller.markFalseReject()
        clock.advance(SelfHealingController.GRACE_WINDOW_MS + 1L)
        assertThat(controller.currentMatchThreshold()).isEqualTo(0.62f)
    }

    @Test
    fun `reset wipes the relaxation history`() = runTest {
        val (controller, _, _) = fixture()
        controller.markFalseReject()
        controller.markFalseReject()
        controller.reset()
        assertThat(controller.falseRejectCount.value).isEqualTo(0)
        assertThat(controller.currentMatchThreshold()).isEqualTo(0.62f)
    }

    @Test
    fun `null breachId yields a row with no notes`() = runTest {
        val (controller, dao, _) = fixture()
        val captured = slot<EventLogEntity>()
        coEvery { dao.insertBreach(capture(captured)) } returns 1L
        controller.markFalseReject(breachId = null)
        assertThat(captured.captured.notes).isNull()
    }
}

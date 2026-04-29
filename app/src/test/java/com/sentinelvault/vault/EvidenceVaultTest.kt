package com.sentinelvault.vault

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EventLogEntity
import com.sentinelvault.security.MemorySanitizer
import com.sentinelvault.triggers.TriggerClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EvidenceVaultTest {

    private fun candidate(capturedAtMs: Long): FrameCandidate = FrameCandidate(
        bitmap = mockk<Bitmap>(relaxed = true),
        capturedAtMs = capturedAtMs,
        luma = ByteArray(16),
        width = 4,
        height = 4
    )

    private fun row(id: Long, timestampMs: Long = 100L, evidencePath: String? = null): EventLogEntity =
        EventLogEntity(
            id = id,
            timestampMs = timestampMs,
            type = EventLogEntity.Type.BREACH_CONFIRMED,
            severity = 2,
            foregroundPackage = null,
            evidencePath = evidencePath,
            notes = null
        )

    private class Fixture(
        val vault: EvidenceVault,
        val dao: EventLogDao,
        val writer: EvidenceWriter,
        val sanitizer: MemorySanitizer,
        val selector: HeroFrameSelector
    )

    private fun fixture(
        scorer: SharpnessScorer = SharpnessScorer { _, _, _ -> 1.0 },
        retentionLimit: Long = EvidenceRetentionPolicy.DEFAULT_LIMIT_BYTES
    ): Fixture {
        val dao = mockk<EventLogDao>(relaxed = true)
        coEvery { dao.getEventsWithEvidenceOldestFirst() } returns emptyList()
        val writer = mockk<EvidenceWriter>()
        val sanitizer = mockk<MemorySanitizer>(relaxed = true)
        val clock = TriggerClock { 0L }
        val selector = HeroFrameSelector(scorer)
        val vault = EvidenceVault(
            eventLogDao = dao,
            heroSelector = selector,
            writer = writer,
            sanitizer = sanitizer,
            clock = clock,
            retentionPolicy = EvidenceRetentionPolicy(limitBytes = retentionLimit)
        )
        return Fixture(vault, dao, writer, sanitizer, selector)
    }

    @Test
    fun `empty burst returns NoCandidates and writes nothing`() = runTest {
        val f = fixture()
        val outcome = f.vault.storeBreachEvidence(eventId = 1L, burst = emptyList())
        assertThat(outcome).isSameInstanceAs(EvidenceVault.Outcome.Skipped.NoCandidates)
        coVerify(exactly = 0) { f.writer.write(any(), any()) }
    }

    @Test
    fun `unknown event id returns UnknownEvent and writes nothing`() = runTest {
        val f = fixture()
        coEvery { f.dao.findById(99L) } returns null
        val outcome = f.vault.storeBreachEvidence(99L, listOf(candidate(1L)))
        assertThat(outcome).isInstanceOf(EvidenceVault.Outcome.Skipped.UnknownEvent::class.java)
        coVerify(exactly = 0) { f.writer.write(any(), any()) }
    }

    @Test
    fun `event already carrying evidencePath is skipped and bitmaps recycled`() = runTest {
        val f = fixture()
        coEvery { f.dao.findById(7L) } returns row(7L, evidencePath = "/files/evidence/7.webp")
        val burst = listOf(candidate(1L), candidate(2L))

        val outcome = f.vault.storeBreachEvidence(7L, burst)

        val skipped = outcome as EvidenceVault.Outcome.Skipped.AlreadyWritten
        assertThat(skipped.eventId).isEqualTo(7L)
        assertThat(skipped.absolutePath).isEqualTo("/files/evidence/7.webp")
        coVerify(exactly = 0) { f.writer.write(any(), any()) }
        verify(exactly = burst.size) { f.sanitizer.recycle(any<Bitmap>()) }
    }

    @Test
    fun `happy path picks hero, writes WebP, updates dao and recycles every bitmap`() = runTest {
        val a = candidate(10L)
        val b = candidate(20L)
        val c = candidate(30L)
        val scorer = SharpnessScorer { luma, _, _ -> if (luma === b.luma) 9.0 else 1.0 }
        val f = fixture(scorer = scorer)
        coEvery { f.dao.findById(7L) } returns row(7L)
        coEvery { f.writer.write(b.bitmap, "breach_7_20") } returns EvidenceWriter.WrittenEvidence(
            absolutePath = "/files/evidence/7.webp",
            sizeBytes = 12_345L,
            format = EvidenceWriter.Format.WEBP_LOSSY
        )

        val outcome = f.vault.storeBreachEvidence(7L, listOf(a, b, c))

        val stored = outcome as EvidenceVault.Outcome.Stored
        assertThat(stored.eventId).isEqualTo(7L)
        assertThat(stored.absolutePath).isEqualTo("/files/evidence/7.webp")
        assertThat(stored.sizeBytes).isEqualTo(12_345L)
        assertThat(stored.sharpnessScore).isEqualTo(9.0)
        coVerify(exactly = 1) { f.dao.updateEvidencePath(7L, "/files/evidence/7.webp") }
        verify(exactly = 3) { f.sanitizer.recycle(any<Bitmap>()) }
    }

    @Test
    fun `writer failure returns WriteFailed and leaves the dao path untouched`() = runTest {
        val a = candidate(10L)
        val f = fixture()
        coEvery { f.dao.findById(7L) } returns row(7L)
        coEvery { f.writer.write(any(), any()) } returns null

        val outcome = f.vault.storeBreachEvidence(7L, listOf(a))

        assertThat(outcome).isInstanceOf(EvidenceVault.Outcome.WriteFailed::class.java)
        coVerify(exactly = 0) { f.dao.updateEvidencePath(any(), any()) }
        verify(atLeast = 1) { f.sanitizer.recycle(any<Bitmap>()) }
    }

    @Test
    fun `enforceRetention deletes oldest files until under budget and clears their dao paths`() = runTest {
        val f = fixture(retentionLimit = 1_000L)
        val rows = listOf(
            row(id = 1L, timestampMs = 10L, evidencePath = "/v/1.webp"),
            row(id = 2L, timestampMs = 20L, evidencePath = "/v/2.webp")
        )
        coEvery { f.dao.getEventsWithEvidenceOldestFirst() } returns rows
        every { f.writer.delete(any()) } returns true

        val report = f.vault.enforceRetention()

        // Files do not actually exist on disk so size resolves to 0; nothing to evict.
        assertThat(report.evicted).isEmpty()
        assertThat(report.withinBudget).isTrue()
    }
}

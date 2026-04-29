package com.sentinelvault.vault

import com.sentinelvault.data.db.dao.EventLogDao
import com.sentinelvault.data.db.entity.EventLogEntity
import com.sentinelvault.security.MemorySanitizer
import com.sentinelvault.triggers.TriggerClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level Epic 7 entry point. Glues together the three pieces of the storage stack:
 *  1. [HeroFrameSelector] picks the sharpest frame from an investigation burst.
 *  2. [EvidenceWriter] persists the hero as WebP under the app's private files dir.
 *  3. [EvidenceRetentionPolicy] enforces the 1.5 GB FIFO ring buffer right after the write,
 *     so a single oversized burst can never push the vault past budget for more than one
 *     pulse.
 *
 * The vault NEVER writes anything for a `BREACH_CONFIRMED` row that already carries an
 * `evidencePath` — re-emission of the same breach (see
 * [com.sentinelvault.lockdown.LockdownCoordinator]'s idempotency guard) must not duplicate
 * artefacts on disk.
 */
@Singleton
class EvidenceVault @Inject constructor(
    private val eventLogDao: EventLogDao,
    private val heroSelector: HeroFrameSelector,
    private val writer: EvidenceWriter,
    private val sanitizer: MemorySanitizer,
    private val clock: TriggerClock,
    private val retentionPolicy: EvidenceRetentionPolicy = EvidenceRetentionPolicy()
) {

    /**
     * Persists the hero of a breach burst and updates the parent event row. Loser frames are
     * recycled through [MemorySanitizer]; the hero bitmap is recycled after compression.
     *
     * @return the [Outcome] describing what happened, including the on-disk path when one was
     *         actually written. Returns [Outcome.Skipped.NoCandidates] when [burst] is empty
     *         and [Outcome.Skipped.AlreadyWritten] when the parent row already has a path.
     */
    suspend fun storeBreachEvidence(eventId: Long, burst: List<FrameCandidate>): Outcome {
        if (burst.isEmpty()) return Outcome.Skipped.NoCandidates
        val parent = eventLogDao.findById(eventId) ?: return Outcome.Skipped.UnknownEvent(eventId)
        if (parent.evidencePath != null) {
            for (candidate in burst) sanitizer.recycle(candidate.bitmap)
            return Outcome.Skipped.AlreadyWritten(eventId, parent.evidencePath)
        }

        val partition = heroSelector.partition(burst)
        if (partition == null) {
            for (candidate in burst) sanitizer.recycle(candidate.bitmap)
            return Outcome.Skipped.NoCandidates
        }
        for (loser in partition.losers) sanitizer.recycle(loser.bitmap)

        val hero = partition.hero
        val written = writer.write(hero.candidate.bitmap, "breach_${eventId}_${hero.candidate.capturedAtMs}")
        sanitizer.recycle(hero.candidate.bitmap)
        if (written == null) return Outcome.WriteFailed(eventId)

        eventLogDao.updateEvidencePath(eventId, written.absolutePath)
        val report = enforceRetention()
        return Outcome.Stored(
            eventId = eventId,
            absolutePath = written.absolutePath,
            sizeBytes = written.sizeBytes,
            sharpnessScore = hero.score,
            retention = report
        )
    }

    /** Runs the FIFO pass without writing anything new. Safe to call from cold start. */
    suspend fun enforceRetention(): EvidenceRetentionPolicy.Report {
        val rows = eventLogDao.getEventsWithEvidenceOldestFirst()
        val catalog = rows.toEvidenceCatalog()
        return retentionPolicy.enforce(catalog) { file ->
            val deleted = writer.delete(file.absolutePath)
            eventLogDao.clearEvidencePath(file.eventId)
            deleted || true // row cleared regardless: a missing file should not pin metadata
        }
    }

    private fun List<EventLogEntity>.toEvidenceCatalog(): List<EvidenceFile> = mapNotNull { row ->
        val path = row.evidencePath ?: return@mapNotNull null
        val size = runCatching { java.io.File(path).length() }.getOrDefault(0L)
        EvidenceFile(
            eventId = row.id,
            absolutePath = path,
            sizeBytes = size,
            timestampMs = row.timestampMs
        )
    }

    sealed interface Outcome {
        data class Stored(
            val eventId: Long,
            val absolutePath: String,
            val sizeBytes: Long,
            val sharpnessScore: Double,
            val retention: EvidenceRetentionPolicy.Report
        ) : Outcome

        data class WriteFailed(val eventId: Long) : Outcome

        sealed interface Skipped : Outcome {
            data object NoCandidates : Skipped
            data class AlreadyWritten(val eventId: Long, val absolutePath: String) : Skipped
            data class UnknownEvent(val eventId: Long) : Skipped
        }
    }
}

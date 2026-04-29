package com.sentinelvault.vault

/**
 * FIFO ring-buffer enforcer for the encrypted vault (guide.md Epic 7 / Task 7.1: "FIFO Ring
 * Buffer logic (1.5 GB limit)"). The algorithm is intentionally trivial — there is no
 * priority, no severity weighting and no per-event whitelist — because the dashboard already
 * carries the metadata: even when the artefact is evicted the timeline row survives with
 * `evidencePath = null`, so the user still knows *that* a breach happened.
 *
 * Eviction order: strictly oldest-first by [EvidenceFile.timestampMs]; ties broken by the
 * iteration order returned by the [EvidenceLister].
 *
 * The policy is pure logic: it does not delete anything itself. Callers wire two side-effects
 * via [evict]:
 *  1. Remove the file from disk (`EvidenceWriter.delete`).
 *  2. Clear `EventLogEntity.evidencePath` for the corresponding row.
 *
 * Both side-effects MUST be idempotent because the policy is safe to re-run (e.g. on every
 * breach and on every cold start).
 */
class EvidenceRetentionPolicy(
    private val limitBytes: Long = DEFAULT_LIMIT_BYTES
) {

    init {
        require(limitBytes > 0L) { "limitBytes must be positive (was $limitBytes)" }
    }

    /**
     * Walks the listed evidence in FIFO order and invokes [evict] until the catalog fits
     * inside the configured budget. Returns a [Report] summarising the pass.
     *
     * `evict` returns `true` when the side-effects (file delete + row clear) were both
     * acknowledged; `false` skips the entry without subtracting its size, so a transient I/O
     * error cannot make the policy claim phantom free space.
     */
    suspend fun enforce(
        catalog: List<EvidenceFile>,
        evict: suspend (EvidenceFile) -> Boolean
    ): Report {
        if (catalog.isEmpty()) return Report.empty(limitBytes)
        var totalBytes = catalog.sumOf { it.sizeBytes }
        val initialBytes = totalBytes
        if (totalBytes <= limitBytes) {
            return Report(
                limitBytes = limitBytes,
                initialBytes = initialBytes,
                finalBytes = totalBytes,
                evicted = emptyList()
            )
        }
        val evicted = ArrayList<EvidenceFile>()
        for (file in catalog) {
            if (totalBytes <= limitBytes) break
            val acknowledged = evict(file)
            if (acknowledged) {
                evicted += file
                totalBytes -= file.sizeBytes
            }
        }
        return Report(
            limitBytes = limitBytes,
            initialBytes = initialBytes,
            finalBytes = totalBytes,
            evicted = evicted
        )
    }

    /**
     * Outcome of a single [enforce] pass. [finalBytes] may still exceed [limitBytes] when one
     * or more `evict` calls returned `false`; the dashboard surfaces this as a soft warning.
     */
    data class Report(
        val limitBytes: Long,
        val initialBytes: Long,
        val finalBytes: Long,
        val evicted: List<EvidenceFile>
    ) {
        val freedBytes: Long get() = initialBytes - finalBytes
        val withinBudget: Boolean get() = finalBytes <= limitBytes

        companion object {
            fun empty(limitBytes: Long): Report =
                Report(limitBytes = limitBytes, initialBytes = 0L, finalBytes = 0L, evicted = emptyList())
        }
    }

    companion object {
        /** 1.5 GB, matches guide.md §8 Epic 7 / Task 7.1. */
        const val DEFAULT_LIMIT_BYTES: Long = 1_500_000_000L
    }
}

package com.sentinelvault.vault

/**
 * Enumerates every evidence artefact currently held in the vault. Implementations MUST
 * return entries sorted by [EvidenceFile.timestampMs] ascending so the FIFO retention pass
 * can iterate without re-sorting.
 *
 * The seam exists primarily for the unit tests: an in-memory list is enough to validate the
 * 1.6 GB ring-buffer scenario described in guide.md §8 / Epic 7 without paying for any real
 * file I/O.
 */
fun interface EvidenceLister {
    suspend fun list(): List<EvidenceFile>
}

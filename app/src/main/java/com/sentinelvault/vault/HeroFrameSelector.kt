package com.sentinelvault.vault

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the sharpest [FrameCandidate] from the investigation burst (guide.md §1.4 step 9 —
 * "the sharpest of the investigation"). Ordering is stable: ties are resolved in favour of
 * the **earliest** candidate so that a long burst with a momentary sharp peak does not bias
 * the dashboard timestamp by the full 3-minute window.
 *
 * The selector itself is allocation-free; the [SharpnessScorer] dependency owns whatever
 * scratch buffers it needs. Callers are expected to feed only valid candidates (non-empty
 * luma plane, dimensions ≥ [LaplacianVarianceScorer.MIN_DIMENSION]); malformed entries are
 * skipped silently rather than aborting the burst.
 */
@Singleton
class HeroFrameSelector @Inject constructor(
    private val scorer: SharpnessScorer
) {

    /**
     * @return the sharpest candidate paired with its score, or `null` when [candidates] is
     *         empty or every entry is malformed. The returned bitmap is **not** detached from
     *         the source list — the caller still owns recycling of the losers.
     */
    fun selectHero(candidates: List<FrameCandidate>): Selection? {
        if (candidates.isEmpty()) return null
        var bestIndex = -1
        var bestScore = Double.NEGATIVE_INFINITY
        for ((index, candidate) in candidates.withIndex()) {
            val score = runCatching { scorer.score(candidate.luma, candidate.width, candidate.height) }
                .getOrNull() ?: continue
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        if (bestIndex < 0) return null
        return Selection(candidate = candidates[bestIndex], score = bestScore, index = bestIndex)
    }

    /**
     * Convenience for the common Epic 6 path: pick the hero, return everything else as the
     * "to-recycle" set. Order of [losers] mirrors [candidates] minus the winner so caller-side
     * sanitisation can iterate once.
     */
    fun partition(candidates: List<FrameCandidate>): Partition? {
        val selection = selectHero(candidates) ?: return null
        val losers = ArrayList<FrameCandidate>(candidates.size - 1)
        for ((index, candidate) in candidates.withIndex()) {
            if (index != selection.index) losers += candidate
        }
        return Partition(hero = selection, losers = losers)
    }

    data class Selection(
        val candidate: FrameCandidate,
        val score: Double,
        val index: Int
    )

    data class Partition(
        val hero: Selection,
        val losers: List<FrameCandidate>
    )
}

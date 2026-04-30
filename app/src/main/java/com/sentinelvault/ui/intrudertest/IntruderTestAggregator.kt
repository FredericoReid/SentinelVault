package com.sentinelvault.ui.intrudertest

import com.sentinelvault.vigilance.VerificationOutcome

/**
 * Pure reducer that turns a list of per-frame [VerificationOutcome]s collected during a
 * pulse window into a single [PulseResult]. Kept outside the view-model so it can be
 * exercised by a vanilla unit test.
 *
 * Decision policy (mirrors the production "consecutive-mismatches" escalation):
 *  * Hard errors (`OwnerNotEnrolled`, `EmbedderUnavailable`, `Failure`) win immediately —
 *    one is enough to surface the diagnostic.
 *  * Otherwise the verdict with the highest count wins. Ties between *reject* outcomes
 *    (`Mismatch` / `NotLive`) collapse to the most recent reject so the displayed cosine
 *    score reflects the latest sample.
 *  * If only `NoFace` was observed → [VerificationOutcome.NoFace] (no escalation).
 *  * The empty list returns [VerificationOutcome.NoFace] at [fallbackTimestampMs].
 */
internal object IntruderTestAggregator {

    fun reduce(samples: List<VerificationOutcome>, fallbackTimestampMs: Long): VerificationOutcome {
        if (samples.isEmpty()) return VerificationOutcome.NoFace(fallbackTimestampMs)
        samples.firstOrNull { it is VerificationOutcome.OwnerNotEnrolled }?.let { return it }
        samples.firstOrNull { it is VerificationOutcome.EmbedderUnavailable }?.let { return it }
        samples.firstOrNull { it is VerificationOutcome.Failure }?.let { return it }

        val matches = samples.filterIsInstance<VerificationOutcome.Match>()
        val mismatches = samples.filterIsInstance<VerificationOutcome.Mismatch>()
        val notLive = samples.filterIsInstance<VerificationOutcome.NotLive>()
        val noFace = samples.filterIsInstance<VerificationOutcome.NoFace>()

        val rejectCount = mismatches.size + notLive.size

        return when {
            // A single match is not enough to overturn a reject majority — both production
            // and the test require the *user's face* to dominate the window.
            matches.size > rejectCount && matches.size > noFace.size ->
                matches.maxByOrNull { it.similarity } ?: matches.first()
            rejectCount >= matches.size && rejectCount > noFace.size -> {
                val lastMismatch = mismatches.lastOrNull()
                val lastNotLive = notLive.lastOrNull()
                when {
                    lastMismatch != null && lastNotLive == null -> lastMismatch
                    lastNotLive != null && lastMismatch == null -> lastNotLive
                    lastMismatch != null && lastNotLive != null ->
                        if (lastMismatch.timestampMs >= lastNotLive.timestampMs) lastMismatch
                        else lastNotLive
                    else -> noFace.firstOrNull() ?: VerificationOutcome.NoFace(fallbackTimestampMs)
                }
            }
            else -> noFace.firstOrNull() ?: VerificationOutcome.NoFace(fallbackTimestampMs)
        }
    }
}

package com.sentinelvault.vigilance

/**
 * Discrete result of a single verification pulse (capture → detect → liveness → embed →
 * cosine). The Epic 5 state machine consumes a stream of these to advance / collapse its
 * [VigilanceState]; it never inspects raw embeddings or bitmaps directly.
 *
 *  * [Match] – cosine ≥ acceptance threshold; treated as "owner is holding the device".
 *  * [Mismatch] – cosine < rejection threshold; counts toward an alert escalation.
 *  * [NoFace] – the detector found no face above its score threshold; treated as a benign
 *    non-event (the camera was pointing at a ceiling), the state machine does NOT escalate.
 *  * [NotLive] – liveness probe rejected the frame as flat / printed (presentation attack).
 *    Counted as a mismatch by the state machine because a confirmed spoof IS a breach.
 *  * [OwnerNotEnrolled] – `EmbeddingDao` has no row yet; the pipeline cannot decide and the
 *    state machine stays in `IDLE` (Epic 3 must run first).
 *  * [EmbedderUnavailable] – the TFLite asset is missing on this device; surfaced so the UI
 *    layer can warn the operator instead of silently failing.
 *  * [Failure] – any uncaught throwable from the inference stack; logged and treated as a
 *    skipped pulse, never as a mismatch (we do not punish the owner for a model crash).
 */
sealed interface VerificationOutcome {
    val timestampMs: Long

    data class Match(val similarity: Float, override val timestampMs: Long) : VerificationOutcome
    data class Mismatch(val similarity: Float, override val timestampMs: Long) : VerificationOutcome
    data class NoFace(override val timestampMs: Long) : VerificationOutcome
    data class NotLive(val variance: Float, override val timestampMs: Long) : VerificationOutcome
    data class OwnerNotEnrolled(override val timestampMs: Long) : VerificationOutcome
    data class EmbedderUnavailable(val reason: String, override val timestampMs: Long) : VerificationOutcome
    data class Failure(val cause: Throwable, override val timestampMs: Long) : VerificationOutcome
}

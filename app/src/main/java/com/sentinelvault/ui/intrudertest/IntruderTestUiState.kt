package com.sentinelvault.ui.intrudertest

import com.sentinelvault.vigilance.VerificationOutcome

/**
 * Phases walked by [IntruderTestViewModel] across one double-verification session:
 * Idle -> CapturingPulse1 -> EvaluatingPulse1 -> CapturingPulse2 -> EvaluatingPulse2 -> Done.
 * The UI is a pure function of the current phase + collected pulse results.
 */
enum class IntruderTestPhase {
    Idle,
    CapturingPulse1,
    EvaluatingPulse1,
    CapturingPulse2,
    EvaluatingPulse2,
    Done
}

/**
 * Owner-perspective verdict produced by combining the two pulse outcomes (see
 * [IntruderTestViewModel.combine]). Renders into a coloured banner on the test screen.
 */
enum class TestVerdict {
    OwnerConfirmed,
    IntruderConfirmed,
    Inconclusive,
    NoFace,
    OwnerNotEnrolled,
    ModelUnavailable,
    PipelineError
}

/**
 * Snapshot of one verification pulse, kept around so the UI can display the cosine score and
 * raw outcome label per attempt instead of just the merged verdict.
 */
data class PulseResult(
    val outcome: VerificationOutcome,
    val similarity: Float?,
    val label: String
)

/**
 * Top-level state read by [com.sentinelvault.ui.intrudertest.IntruderTestScreen]. `hasFace`
 * is decoupled from [phase] so the AR oval keeps reacting while a pulse is in flight.
 */
data class IntruderTestUiState(
    val phase: IntruderTestPhase = IntruderTestPhase.Idle,
    val hasFace: Boolean = false,
    val pulse1: PulseResult? = null,
    val pulse2: PulseResult? = null,
    val verdict: TestVerdict? = null,
    val errorMessage: String? = null
) {
    val isRunning: Boolean
        get() = phase != IntruderTestPhase.Idle && phase != IntruderTestPhase.Done
}

/** Maps a single verifier outcome into the per-pulse summary rendered on the screen. */
internal fun VerificationOutcome.toPulseResult(): PulseResult = when (this) {
    is VerificationOutcome.Match -> PulseResult(this, similarity, "Match (owner) %.2f".format(similarity))
    is VerificationOutcome.Mismatch -> PulseResult(this, similarity, "Mismatch %.2f".format(similarity))
    is VerificationOutcome.NoFace -> PulseResult(this, null, "No face detected")
    is VerificationOutcome.NotLive -> PulseResult(this, null, "Liveness rejected (flat)")
    is VerificationOutcome.OwnerNotEnrolled -> PulseResult(this, null, "Owner not enrolled")
    is VerificationOutcome.EmbedderUnavailable -> PulseResult(this, null, "Model unavailable: $reason")
    is VerificationOutcome.Failure -> PulseResult(this, null, "Pipeline error: ${cause.message ?: "unknown"}")
}

/**
 * Double-verification verdict: the test only declares an intruder when BOTH pulses rejected
 * the face. This mirrors the production policy of escalating only on consecutive mismatches.
 */
internal fun combine(p1: PulseResult?, p2: PulseResult): TestVerdict {
    val a = p1?.outcome ?: return TestVerdict.PipelineError
    val b = p2.outcome
    return when {
        a is VerificationOutcome.OwnerNotEnrolled || b is VerificationOutcome.OwnerNotEnrolled ->
            TestVerdict.OwnerNotEnrolled
        a is VerificationOutcome.EmbedderUnavailable || b is VerificationOutcome.EmbedderUnavailable ->
            TestVerdict.ModelUnavailable
        a is VerificationOutcome.Failure || b is VerificationOutcome.Failure ->
            TestVerdict.PipelineError
        a.isMatch() && b.isMatch() -> TestVerdict.OwnerConfirmed
        a.isReject() && b.isReject() -> TestVerdict.IntruderConfirmed
        a is VerificationOutcome.NoFace && b is VerificationOutcome.NoFace -> TestVerdict.NoFace
        else -> TestVerdict.Inconclusive
    }
}

private fun VerificationOutcome.isMatch(): Boolean = this is VerificationOutcome.Match

private fun VerificationOutcome.isReject(): Boolean =
    this is VerificationOutcome.Mismatch || this is VerificationOutcome.NotLive
